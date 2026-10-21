/**
 * Editing BLOOD Client's settings.json (<game dir>/config/bloomclient/settings.json) from
 * the launcher. Only the keys the launcher changes are touched; everything else (including
 * keys from newer mod versions) is preserved. The revision is bumped and `updatedBy` is set
 * to "launcher" so a running game picks up the change and merges it per key.
 */

import { patchHud, readHud, type HudBlock, type HudPatch } from "./modules";

export interface SettingsPatch {
  settings?: Record<string, string | number | boolean | string[]>;
  /** Select a visual profile; the game applies it on next start (or within seconds if running). */
  profile?: string;
  /** Change widgets in the active HUD layout (the "hud" block). */
  hud?: HudPatch;
}

export interface SettingsView {
  revision: number;
  settings: Record<string, unknown>;
  activeProfile: string | null;
  /** HUD layouts; the game's defaults when the file has none yet. */
  hud: HudBlock;
}

export class SettingsFileError extends Error {}

export function parseSettings(text: string | null): SettingsView {
  if (!text) return { revision: 0, settings: {}, activeProfile: null, hud: readHud(null) };
  let root: Record<string, unknown>;
  try {
    const v = JSON.parse(text) as unknown;
    if (!v || typeof v !== "object" || Array.isArray(v)) throw new Error("not an object");
    root = v as Record<string, unknown>;
  } catch {
    throw new SettingsFileError("BLOOD Client's settings file is damaged. Start the game once to repair it.");
  }
  const settings = root.settings && typeof root.settings === "object" && !Array.isArray(root.settings) ? (root.settings as Record<string, unknown>) : {};
  const profiles = root.profiles as { active?: unknown } | undefined;
  return {
    revision: typeof root.revision === "number" ? root.revision : 0,
    settings,
    activeProfile: profiles && typeof profiles.active === "string" ? profiles.active : null,
    hud: readHud(root.hud),
  };
}

const KEY = /^[a-z][a-zA-Z0-9]*(\.[a-zA-Z0-9]+)+$/;
const PROFILE_ID = /^[a-z0-9][a-z0-9_-]{0,47}$/;

/** Returns the new file contents. Throws SettingsFileError if the existing file is unreadable. */
export function applyPatch(existing: string | null, patch: SettingsPatch, now: Date): string {
  let root: Record<string, unknown> = {};
  if (existing) {
    parseSettings(existing); // validates
    root = JSON.parse(existing) as Record<string, unknown>;
  }
  const settings = { ...((root.settings as Record<string, unknown>) ?? {}) };
  for (const [k, v] of Object.entries(patch.settings ?? {})) {
    if (!KEY.test(k)) throw new SettingsFileError(`Invalid setting key ${k}`);
    settings[k] = v;
  }
  const out: Record<string, unknown> = { ...root };
  out.format = typeof root.format === "number" ? root.format : 1;
  out.revision = (typeof root.revision === "number" ? root.revision : 0) + 1;
  out.updatedAt = now.toISOString();
  out.updatedBy = "launcher";
  out.settings = settings;
  if (patch.hud) {
    try {
      out.hud = patchHud(readHud(root.hud), patch.hud);
    } catch (e) {
      throw new SettingsFileError(e instanceof Error ? e.message : String(e));
    }
  }
  if (patch.profile !== undefined) {
    if (!PROFILE_ID.test(patch.profile)) throw new SettingsFileError("Invalid profile id");
    const prev = (root.profiles && typeof root.profiles === "object" ? root.profiles : {}) as Record<string, unknown>;
    out.profiles = { ...prev, active: patch.profile, apply: true };
  }
  return JSON.stringify(out, null, 2);
}
