/**
 * Premium license keys.
 *
 * A key (MOTION-XXXXX-XXXXX-XXXXX-XXXXX) is redeemed on the dashboard. The server binds it to the
 * first PC that redeems it, using a SHA-256 of this PC's machine id (the id itself never leaves
 * the PC). Only that PC gets Premium. The owner can move the key to another PC once every
 * 24 hours, so sharing a key doesn't work.
 *
 * The key is kept in Windows Credential Manager. The launcher re-checks it at start and every
 * few hours; when the server can't be reached, the last good answer is used for up to 7 days.
 */

import { formatKey, normalizeKey, type LicenseStatus } from "@bloom/shared";
import { BloomError, toBloomError } from "../core/platform";
import { app, errorMessage, P, path, toast, type LicenseState } from "./app";
import { native } from "./native";
import { API_URL, apiCall, refreshPremium, setLicenseHeaders } from "./premium";

const SECRET = "blood-license-key";
const RECHECK_MS = 6 * 60 * 60 * 1000;
const OFFLINE_GRACE_MS = 7 * 86_400_000;

interface CacheFile {
  hint: string;
  hwid: string;
  status: LicenseStatus;
  checkedAt: number;
}

const cachePath = () => path(P().dataDir, "license.json");

function setLicense(next: LicenseState) {
  app.set((s) => ({ premium: { ...s.premium, license: next } }));
}

let hwidCache: string | null = null;
async function hwid(): Promise<string> {
  hwidCache ??= await native.machineId();
  return hwidCache;
}

function stateFrom(st: LicenseStatus, checkedAt: number, cached = false): LicenseState {
  const expired = st.expiresAt !== null && st.expiresAt <= Date.now();
  const status: LicenseState["status"] = expired ? "expired" : st.valid ? "active" : st.boundElsewhere ? "elsewhere" : "expired";
  return { status, hint: st.hint, expiresAt: st.expiresAt, nextMoveAt: st.nextMoveAt, checkedAt, cached };
}

async function writeCache(hint: string, status: LicenseStatus) {
  const data: CacheFile = { hint, hwid: await hwid(), status, checkedAt: Date.now() };
  await P().writeText(cachePath(), JSON.stringify(data)).catch(() => undefined);
}

async function readCache(): Promise<CacheFile | null> {
  try {
    const raw = await P().readText(cachePath());
    if (!raw) return null;
    const c = JSON.parse(raw) as CacheFile;
    if (!c.status || c.hwid !== (await hwid())) return null;
    if (Date.now() - c.checkedAt > OFFLINE_GRACE_MS) return null;
    return c;
  } catch {
    return null;
  }
}

async function call(route: "/v1/license/activate" | "/v1/license/move", key: string): Promise<LicenseStatus> {
  const { body } = await apiCall<LicenseStatus>("POST", route, { json: { key, hwid: await hwid() } });
  return body;
}

/** Turn a server error about the key itself into a final state (key gone, disabled…). */
function keyProblem(code: string): LicenseState["status"] | null {
  if (code === "key_revoked") return "revoked";
  if (code === "key_expired") return "expired";
  if (code === "invalid_key") return "none";
  return null;
}

/** Redeem a key on this PC. Throws with a readable message if it can't be used. */
export async function redeemKey(input: string): Promise<LicenseState> {
  if (!API_URL) throw new BloomError("api_unconfigured", "This build of MOTION isn't connected to MOTION's server, so keys can't be checked.");
  const norm = normalizeKey(input);
  if (!norm) throw new BloomError("invalid_key", "That doesn't look like a MOTION key. Keys look like MOTION-XXXXX-XXXXX-XXXXX-XXXXX.");
  const key = formatKey(norm);
  const st = await call("/v1/license/activate", key);
  // Keep the key even when it's active elsewhere, so the player can move it here later.
  await native.secretSet(SECRET, key);
  await writeCache(st.hint, st);
  const next = stateFrom(st, Date.now());
  setLicense(next);
  if (next.status === "active") toast({ kind: "success", title: "Premium unlocked", message: "Your key is now tied to this PC." });
  return next;
}

/** Move the saved key to this PC (allowed once every 24 hours). */
export async function moveKeyHere(): Promise<LicenseState> {
  const key = await native.secretGet(SECRET).catch(() => null);
  if (!key) throw new BloomError("no_key", "There's no key saved on this PC.");
  const st = await call("/v1/license/move", key);
  await writeCache(st.hint, st);
  const next = stateFrom(st, Date.now());
  setLicense(next);
  if (next.status === "active") toast({ kind: "success", title: "Key moved to this PC", message: "Premium is active here. Your other PC no longer has it." });
  return next;
}

/** Re-check the saved key with the server (startup and every few hours). Never throws. */
export async function checkLicense(): Promise<void> {
  const key = await native.secretGet(SECRET).catch(() => null);
  if (!key) {
    setLicense({ status: "none" });
    return;
  }
  const prev = app.get().premium.license;
  if (!prev || prev.status === "none") setLicense({ status: "checking", hint: normalizeKey(key)?.slice(-5) });
  if (!API_URL) {
    setLicense({ status: "error", error: "This build isn't connected to MOTION's server." });
    return;
  }
  try {
    const st = await call("/v1/license/activate", key);
    await writeCache(st.hint, st);
    const next = stateFrom(st, Date.now());
    if (prev?.status === "active" && next.status === "elsewhere") {
      toast({ kind: "warning", title: "Premium moved to another PC", message: "Your key was activated on a different computer. You can move it back from the dashboard." }, 0);
    }
    setLicense(next);
  } catch (e) {
    const err = toBloomError(e);
    const final = keyProblem(err.code);
    if (final) {
      await P().writeText(cachePath(), "{}").catch(() => undefined);
      setLicense({ status: final, hint: normalizeKey(key)?.slice(-5), error: errorMessage(err), checkedAt: Date.now() });
      return;
    }
    // Offline or server trouble: fall back on the last good answer for a while.
    const cached = await readCache();
    if (cached) setLicense({ ...stateFrom(cached.status, cached.checkedAt, true), error: errorMessage(err) });
    else setLicense({ status: "error", hint: normalizeKey(key)?.slice(-5), error: errorMessage(err) });
  }
}

/** Forget the key on this PC. (It stays bound to this PC on the server until it's moved.) */
export async function removeKey(): Promise<void> {
  await native.secretDelete(SECRET).catch(() => undefined);
  await P().writeText(cachePath(), "{}").catch(() => undefined);
  setLicense({ status: "none" });
}

// Signed-in requests carry the key so the server can count it for cloud sync on this PC.
setLicenseHeaders(async (): Promise<Record<string, string>> => {
  if (app.get().premium.license?.status !== "active") return {};
  const key = await native.secretGet(SECRET).catch(() => null);
  return key ? { "X-Blood-License": key, "X-Blood-Hwid": await hwid() } : {};
});

/** After the key's state changes, re-read the account's Premium so cloud sync follows the key. */
function afterChange(before: LicenseState["status"] | undefined) {
  const now = app.get().premium.license?.status;
  if ((before === "active") !== (now === "active") && app.get().premium.user) void refreshPremium({ quiet: true });
}

let started = false;
/** Call once after startup. */
export function startLicenseChecks() {
  if (started) return;
  started = true;
  let last = app.get().premium.license?.status;
  app.subscribe(() => {
    const cur = app.get().premium.license?.status;
    if (cur === last) return;
    const before = last;
    last = cur;
    afterChange(before);
  });
  void checkLicense();
  setInterval(() => void checkLicense(), RECHECK_MS);
}
