/**
 * MOTION Mods: reads and changes the real switches behind each module (see
 * core/bloomclient/modules.ts). Changes are written straight away with read-modify-write,
 * so values the game saved in the meantime are never overwritten; a running game picks
 * them up within a few seconds (MOTION Client watches its settings file).
 *
 * Presets and restores are "major changes": MOTION Client's settings file is backed up first.
 */

import { CROSSHAIR, MOD_PRESETS, type ModPreset, type SettingToggle, type WidgetJson } from "../core/bloomclient/modules";
import { readOption, writeOption } from "../core/bloomclient/optionsTxt";
import { instanceDir, type Instance } from "../core/instances/model";
import { modrinthLoadersFor } from "../core/loaders/loaders";
import { compatibleVersions } from "../core/modrinth/resolve";
import type { Version } from "../core/modrinth/types";
import { PERF_MODS, type PerfModInfo } from "../core/perf/knownMods";
import { BloomError } from "../core/platform";
import { app, L, P, path, startTask, endTask, updateTask } from "./app";
import { readSettings, writeSettings } from "./bloomClient";
import { applyInstallPlan, listContent, modrinth, planProjectInstall, toggleContent, type ContentItem } from "./content";
import { instanceGameDir } from "./instances";

// ---------------------------------------------------------------- MOTION Client settings

function clientConfigDir(inst: Instance) {
  return path(instanceGameDir(inst), "config", "bloomclient");
}

export async function setWidget(inst: Instance, widget: string, change: WidgetJson) {
  return writeSettings(inst, { hud: { widgets: { [widget]: change } } });
}

export async function setSettings(inst: Instance, values: Record<string, string | number | boolean | string[]>) {
  return writeSettings(inst, { settings: values });
}

export function settingOn(view: Record<string, unknown>, t: SettingToggle): boolean {
  const v = view[t.keys[0]!];
  const value = v === undefined ? t.default : v;
  return value === t.on || (typeof t.on === "number" && typeof value === "number" && value > 0);
}

export function toggleValues(t: SettingToggle, on: boolean): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {};
  for (const k of t.keys) out[k] = on ? t.on : t.off;
  return out;
}

/** The crosshair's current values with defaults filled in. */
export function crosshairValues(view: Record<string, unknown>) {
  const num = (k: string, d: number) => (typeof view[k] === "number" ? (view[k] as number) : d);
  const str = (k: string, d: string) => (typeof view[k] === "string" ? (view[k] as string) : d);
  const bool = (k: string, d: boolean) => (typeof view[k] === "boolean" ? (view[k] as boolean) : d);
  return {
    enabled: bool(CROSSHAIR.enabled.key, CROSSHAIR.enabled.default),
    style: str(CROSSHAIR.style.key, CROSSHAIR.style.default),
    size: num(CROSSHAIR.size.key, CROSSHAIR.size.default),
    gap: num(CROSSHAIR.gap.key, CROSSHAIR.gap.default),
    thickness: num(CROSSHAIR.thickness.key, CROSSHAIR.thickness.default),
    color: str(CROSSHAIR.color.key, CROSSHAIR.color.default),
    opacity: num(CROSSHAIR.opacity.key, CROSSHAIR.opacity.default),
    outline: bool(CROSSHAIR.outline.key, CROSSHAIR.outline.default),
  };
}

// ---------------------------------------------------------------- backups of MOTION Client's settings

export interface ClientBackup {
  id: string;
  at: number;
  reason: string;
}

const MAX_CLIENT_BACKUPS = 15;

function backupDir(inst: Instance) {
  return path(instanceDir(L(), P().sep, inst.id), ".bloom", "client-backups");
}

export async function listClientBackups(inst: Instance): Promise<ClientBackup[]> {
  const out: ClientBackup[] = [];
  for (const e of await P().list(backupDir(inst)).catch(() => [])) {
    if (e.isDir || !e.name.endsWith(".meta.json")) continue;
    try {
      const m = JSON.parse((await P().readText(e.path)) ?? "") as ClientBackup;
      if (typeof m.id === "string" && typeof m.at === "number") out.push(m);
    } catch {
      /* skip unreadable */
    }
  }
  return out.sort((a, b) => b.at - a.at);
}

/** Copies settings.json (if it exists yet) before a major change. Old backups beyond the limit go to the Recycle Bin. */
export async function backupClientSettings(inst: Instance, reason: string): Promise<ClientBackup | null> {
  const text = await P().readText(path(clientConfigDir(inst), "settings.json")).catch(() => null);
  if (!text) return null;
  const id = new Date().toISOString().replace(/[:.]/g, "-");
  const b: ClientBackup = { id, at: Date.now(), reason: reason.slice(0, 120) };
  await P().writeText(path(backupDir(inst), `${id}.json`), text);
  await P().writeText(path(backupDir(inst), `${id}.meta.json`), JSON.stringify(b, null, 2));
  const all = await listClientBackups(inst);
  for (const old of all.slice(MAX_CLIENT_BACKUPS)) {
    await P().trash(path(backupDir(inst), `${old.id}.json`)).catch(() => undefined);
    await P().trash(path(backupDir(inst), `${old.id}.meta.json`)).catch(() => undefined);
  }
  return b;
}

/**
 * Puts a backup back. The restored file gets a new revision and is marked as coming from
 * the launcher, so a running game applies it instead of ignoring it as "older".
 */
export async function restoreClientBackup(inst: Instance, b: ClientBackup): Promise<void> {
  const saved = await P().readText(path(backupDir(inst), `${b.id}.json`));
  if (!saved) throw new BloomError("not_found", "That backup no longer exists.");
  let root: Record<string, unknown>;
  try {
    root = JSON.parse(saved) as Record<string, unknown>;
  } catch {
    throw new BloomError("invalid", "That backup is damaged and can't be restored.");
  }
  await backupClientSettings(inst, `Before restoring the backup from ${new Date(b.at).toLocaleString()}`);
  const current = await readSettings(inst);
  root.revision = Math.max(current.revision, typeof root.revision === "number" ? root.revision : 0) + 1;
  root.updatedBy = "launcher";
  root.updatedAt = new Date().toISOString();
  await P().writeText(path(clientConfigDir(inst), "settings.json"), JSON.stringify(root, null, 2));
}

// ---------------------------------------------------------------- vanilla options (options.txt)

function optionsPath(inst: Instance) {
  return path(instanceGameDir(inst), "options.txt");
}

export async function readVanillaOptions(inst: Instance, names: string[]): Promise<Record<string, boolean>> {
  const text = await P().readText(optionsPath(inst)).catch(() => null);
  const out: Record<string, boolean> = {};
  for (const n of names) out[n] = readOption(text, n) === "true";
  return out;
}

export async function setVanillaOption(inst: Instance, name: string, on: boolean): Promise<void> {
  if (app.get().running[inst.id]) {
    throw new BloomError("running", "Close Minecraft first: it rewrites options.txt when it exits. You can also change this in Minecraft's Controls settings.");
  }
  const text = await P().readText(optionsPath(inst)).catch(() => null);
  await P().writeText(optionsPath(inst), writeOption(text, name, on ? "true" : "false"));
}

// ---------------------------------------------------------------- performance mods

export interface PerfModState {
  info: PerfModInfo;
  /** The mod's file in this installation (enabled or disabled), if any. */
  item?: ContentItem;
}

export function perfInfo(slug: string): PerfModInfo | undefined {
  return PERF_MODS.find((m) => m.slug === slug);
}

export function perfStates(mods: ContentItem[], slugs: string[]): PerfModState[] {
  return slugs.flatMap((slug) => {
    const info = perfInfo(slug);
    if (!info) return [];
    const matches = mods.filter((m) => (m.meta && info.modIds.some((id) => m.meta!.id === id || m.meta!.provides.includes(id))) || m.modrinth?.title === info.title);
    // Prefer the enabled copy when both exist.
    return [{ info, item: matches.find((m) => m.enabled) ?? matches[0] }];
  });
}

/** Best Modrinth version of a performance mod for this installation, or null if there is none. */
export async function compatiblePerfVersion(inst: Instance, slug: string): Promise<Version | null> {
  const loaders = modrinthLoadersFor(inst.loader);
  const versions = await modrinth().versions(slug, { loaders, gameVersions: [inst.gameVersion] });
  return compatibleVersions(versions, { gameVersion: inst.gameVersion, loaders })[0] ?? null;
}

/**
 * Turn a performance mod on or off. Off disables the file (renamed to .disabled, so it can
 * be turned back on without downloading). On re-enables a disabled copy, or installs the best
 * compatible version from Modrinth together with its required dependencies.
 */
export async function setPerfMod(inst: Instance, state: PerfModState, on: boolean): Promise<"enabled" | "disabled" | "installed" | "unchanged"> {
  if (inst.loader === "vanilla") throw new BloomError("vanilla", "Performance mods need a mod loader such as Fabric.");
  if (app.get().running[inst.id]) throw new BloomError("running", "Close Minecraft first: mods are loaded when the game starts.");
  if (state.item) {
    if (state.item.enabled === on) return "unchanged";
    await toggleContent(inst, "mods", state.item);
    return on ? "enabled" : "disabled";
  }
  if (!on) return "unchanged";
  const version = await compatiblePerfVersion(inst, state.info.slug);
  if (!version) throw new BloomError("unavailable", `${state.info.title} has no version for Minecraft ${inst.gameVersion} on this loader yet.`);
  const project = await modrinth().project(version.project_id);
  const plan = await planProjectInstall(inst, project, version);
  if (plan.conflicts.length) {
    throw new BloomError("conflict", plan.conflicts.map((c) => `${c.title} is marked incompatible with ${c.with}.`).join(" "));
  }
  if (plan.unresolved.length) {
    throw new BloomError("unresolved", `${state.info.title} needs ${plan.unresolved.map((u) => u.title).join(", ")}, which has no compatible version here.`);
  }
  await applyInstallPlan(inst, project, plan);
  return "installed";
}

// ---------------------------------------------------------------- presets

export interface PresetResult {
  backup: ClientBackup | null;
  installed: string[];
  skipped: { title: string; reason: string }[];
}

/**
 * Apply a MOTION Mods preset: back up MOTION Client's settings, change the HUD layout and
 * settings in one write, and (for Performance) install or re-enable the core performance mods.
 */
export async function applyModPreset(inst: Instance, presetId: ModPreset["id"]): Promise<PresetResult> {
  const preset = MOD_PRESETS.find((p) => p.id === presetId);
  if (!preset) throw new BloomError("invalid", "Unknown preset");
  const taskId = `modpreset-${inst.id}`;
  startTask({ id: taskId, title: `Applying ${preset.name}`, instanceId: inst.id, cancellable: false, stage: "Backing up settings" });
  const result: PresetResult = { backup: null, installed: [], skipped: [] };
  try {
    result.backup = await backupClientSettings(inst, `Before the ${preset.name} preset`);
    updateTask(taskId, { stage: "Updating the HUD" });
    await writeSettings(inst, { settings: preset.settings, hud: preset.hud, ...(preset.profile ? { profile: preset.profile } : {}) });
    if (preset.perfMods?.length && inst.loader !== "vanilla") {
      const mods = await listContent(inst, "mods");
      const states = perfStates(mods, preset.perfMods);
      let n = 0;
      for (const s of states) {
        updateTask(taskId, { stage: `Checking ${s.info.title}`, progress: n++ / states.length });
        try {
          const r = await setPerfMod(inst, s, true);
          if (r === "installed" || r === "enabled") result.installed.push(s.info.title);
        } catch (e) {
          result.skipped.push({ title: s.info.title, reason: e instanceof Error ? e.message : String(e) });
        }
      }
    }
    return result;
  } finally {
    endTask(taskId);
  }
}
