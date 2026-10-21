/**
 * Automatic self-update via the Tauri updater plugin.
 *
 * With "Update BLOOD automatically" on (the default), BLOOD checks shortly after start and then
 * every few hours, downloads a new version quietly in the background, and installs it as soon
 * as nobody is playing: never while Minecraft is running or a download/launch is in progress.
 * Before installing it shows a short countdown with a "Later" button.
 *
 * Update bundles are signed with BLOOD's private updater key at release time; the app verifies
 * them against the public key baked into the build before installing anything.
 */

import { check, type Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { app, dismissToast, endTask, reportError, startTask, toast, updateTask, updateToast } from "./app";
import { createStore } from "./store";

/** How often a running launcher looks for a new version. */
const CHECK_EVERY_MS = 4 * 60 * 60 * 1000;
/** Countdown before an automatic install. */
const COUNTDOWN_S = 15;
/** "Later" postpones the automatic install by this long. */
const POSTPONE_MS = 60 * 60 * 1000;

export type UpdateStatus = "idle" | "checking" | "available" | "downloading" | "ready" | "installing" | "current" | "unavailable";

/** Update state for the dashboard and Settings. "unavailable": this build has no updater configured (or the check failed). */
export const updateState = createStore<{ status: UpdateStatus; version?: string; progress?: number; checkedAt?: number; error?: string }>({ status: "idle" });

let pending: Update | null = null;
let downloaded = false;
let postponedUntil = 0;
let countdown: { timer: ReturnType<typeof setInterval>; toastId: number } | null = null;
let started = false;

function auto(): boolean {
  return app.get().settings.autoUpdate;
}

/** Nothing would be interrupted by restarting the launcher right now. */
function idle(): boolean {
  const s = app.get();
  return Object.keys(s.running).length === 0 && s.tasks.length === 0;
}

/** Call once after startup: schedules checks and watches for a good moment to install. */
export function startAutoUpdates() {
  if (started) return;
  started = true;
  setTimeout(() => void checkForUpdate(false), 4000);
  setInterval(() => {
    if (auto() && !pending) void checkForUpdate(false);
  }, CHECK_EVERY_MS);
  // When a game closes or tasks finish, a downloaded update can go in (and a postponed one later).
  app.subscribe(() => maybeInstall());
  setInterval(() => maybeInstall(), 60_000);
}

/**
 * Look for a new version. With automatic updates on it is downloaded right away; a manual check
 * (Settings / dashboard "Check") also downloads it, then installs when idle.
 */
export async function checkForUpdate(manual = false): Promise<Update | null> {
  if (!manual && !auto()) return null;
  const st = updateState.get().status;
  if (st === "checking" || st === "downloading" || st === "installing") return pending;
  if (st === "ready" && pending) {
    if (manual) maybeInstall(true);
    return pending;
  }
  updateState.set({ status: "checking", error: undefined });
  try {
    const update = await check();
    pending = update;
    downloaded = false;
    if (!update) {
      updateState.set({ status: "current", version: undefined, checkedAt: Date.now() });
      if (manual) toast({ kind: "success", title: "You're up to date", message: `BLOOD ${app.get().paths?.version ?? ""} is the latest version.` });
      return null;
    }
    updateState.set({ status: "available", version: update.version, checkedAt: Date.now() });
    if (auto() || manual) void download(update, manual);
    else
      toast(
        { kind: "info", title: `BLOOD ${update.version} is available`, message: "Automatic updates are off.", action: { label: "Update now", run: () => void installUpdate() } },
        0,
      );
    return update;
  } catch (e) {
    updateState.set({ status: "unavailable", checkedAt: Date.now(), error: e instanceof Error ? e.message : String(e) });
    // An unconfigured updater (development builds) is not an error worth showing.
    if (manual) reportError("Couldn't check for updates", e);
    else console.info("update check skipped:", e);
    return null;
  }
}

/** Download quietly in the background (no task card: nothing for the player to wait on). */
async function download(update: Update, manual: boolean) {
  updateState.set({ status: "downloading", version: update.version, progress: 0 });
  let total = 0;
  let done = 0;
  try {
    await update.download((event) => {
      if (event.event === "Started") total = event.data.contentLength ?? 0;
      else if (event.event === "Progress") {
        done += event.data.chunkLength;
        updateState.set({ progress: total ? done / total : undefined });
      }
    });
    downloaded = true;
    updateState.set({ status: "ready", progress: 1 });
    maybeInstall(manual);
  } catch (e) {
    pending = null;
    updateState.set({ status: "unavailable", error: e instanceof Error ? e.message : String(e) });
    if (manual) reportError("Couldn't download the update", e);
    else console.warn("update download failed; will retry on the next check", e);
  }
}

/** Starts the install countdown if an update is downloaded and nothing would be interrupted. */
function maybeInstall(now = false) {
  if (!pending || !downloaded || countdown || updateState.get().status !== "ready") return;
  if (!now && (!auto() || Date.now() < postponedUntil)) return;
  if (!idle()) return;
  const version = pending.version;
  let left = COUNTDOWN_S;
  const title = () => `Updating BLOOD to ${version} in ${left} s`;
  // Claim the countdown before showing the toast: toast() notifies store subscribers
  // (including this function) synchronously.
  const cd: { timer: ReturnType<typeof setInterval> | null; toastId: number } = { timer: null, toastId: -1 };
  countdown = cd as { timer: ReturnType<typeof setInterval>; toastId: number };
  cd.toastId = toast(
    {
      kind: "info",
      title: title(),
      message: "BLOOD restarts by itself. Your installations, mods and settings are kept.",
      action: {
        label: "Later",
        run: () => {
          // Postpone first: cancelling notifies subscribers, which would otherwise restart the countdown.
          postponedUntil = Date.now() + POSTPONE_MS;
          cancelCountdown();
          toast({ kind: "info", title: "Update postponed", message: `BLOOD ${version} installs in an hour, or the next time you start BLOOD.` });
        },
      },
    },
    0,
  );
  cd.timer = setInterval(() => {
    left--;
    if (!app.get().toasts.some((t) => t.id === cd.toastId)) {
      // Closing the notification counts as "Later".
      postponedUntil = Date.now() + POSTPONE_MS;
      cancelCountdown();
      return;
    }
    if (!idle()) {
      // A game or download started during the countdown: wait for the next quiet moment.
      cancelCountdown();
      return;
    }
    if (left <= 0) {
      updateState.set({ status: "installing" });
      cancelCountdown();
      void installUpdate();
      return;
    }
    updateToast(cd.toastId, { title: title() });
  }, 1000);
}

function cancelCountdown() {
  if (!countdown) return;
  const cd = countdown;
  countdown = null;
  if (cd.timer) clearInterval(cd.timer);
  if (cd.toastId >= 0) dismissToast(cd.toastId);
}

/** Install now (downloading first if needed) and restart BLOOD. Refuses while Minecraft is running. */
export async function installUpdate() {
  const update = pending ?? (await check().catch(() => null));
  if (!update) return;
  if (Object.keys(app.get().running).length > 0) {
    toast({ kind: "warning", title: "Close Minecraft first", message: "BLOOD will update once your game is closed." });
    return;
  }
  cancelCountdown();
  const id = "blood-update";
  startTask({ id, title: `Updating BLOOD to ${update.version}`, cancellable: false, stage: downloaded ? "Installing" : "Downloading" });
  updateState.set({ status: "installing", version: update.version });
  try {
    if (!downloaded) {
      let total = 0;
      let done = 0;
      await update.download((event) => {
        if (event.event === "Started") total = event.data.contentLength ?? 0;
        else if (event.event === "Progress") {
          done += event.data.chunkLength;
          updateTask(id, { progress: total ? done / total : null });
        }
      });
      downloaded = true;
    }
    updateTask(id, { stage: "Installing", progress: null });
    // On Windows the installer closes BLOOD, updates it and starts it again.
    await update.install();
    await relaunch();
  } catch (e) {
    endTask(id);
    updateState.set({ status: downloaded ? "ready" : "unavailable", error: e instanceof Error ? e.message : String(e) });
    reportError("Update failed", e);
  }
}
