/**
 * BLOOD Performance: reversible presets (with automatic backups), performance
 * mod recommendations checked live against Modrinth, and the optional
 * in-game FPS overlay (via the Sodium Extra mod).
 */

import { instanceDir, type Instance } from "../core/instances/model";
import { modrinthLoadersFor } from "../core/loaders/loaders";
import { compatibleVersions } from "../core/modrinth/resolve";
import type { Version } from "../core/modrinth/types";
import { PERF_MODS, type PerfModInfo } from "../core/perf/knownMods";
import { applyVideoSettings, optionsFormatFor, PRESETS, type AppliedChange, type PresetId, type VideoSettings } from "../core/perf/presets";
import { app, L, P, path } from "./app";
import { installedProjects, listContent, modrinth } from "./content";
import { instanceGameDir, saveInstance, getInstance } from "./instances";

interface PendingPreset {
  presetId: PresetId;
  video: VideoSettings;
}

export interface BackupInfo {
  id: string;
  at: number;
  reason: string;
  presetBefore?: PresetId;
}

function bloomDir(inst: Instance) {
  return path(instanceDir(L(), P().sep, inst.id), ".bloom");
}
function optionsPath(inst: Instance) {
  return path(instanceGameDir(inst), "options.txt");
}

async function releaseTime(inst: Instance): Promise<string | undefined> {
  const text = await P().readText(path(L().versions, inst.gameVersion, `${inst.gameVersion}.json`)).catch(() => null);
  if (!text) return undefined;
  try {
    return (JSON.parse(text) as { releaseTime?: string }).releaseTime;
  } catch {
    return undefined;
  }
}

export async function listBackups(inst: Instance): Promise<BackupInfo[]> {
  const dir = path(bloomDir(inst), "backups");
  const out: BackupInfo[] = [];
  for (const e of await P().list(dir)) {
    if (!e.isDir) continue;
    const info = await P().readText(path(e.path, "backup.json")).catch(() => null);
    if (info) {
      try {
        out.push(JSON.parse(info) as BackupInfo);
      } catch {
        /* ignore */
      }
    }
  }
  return out.sort((a, b) => b.at - a.at);
}

/** Save options.txt and BLOOD's per-installation settings so any change can be undone. */
export async function backupSettings(inst: Instance, reason: string): Promise<BackupInfo | null> {
  const p = P();
  const options = await p.readText(optionsPath(inst));
  const id = new Date().toISOString().replace(/[:.]/g, "-");
  const dir = path(bloomDir(inst), "backups", id);
  const info: BackupInfo = { id, at: Date.now(), reason, presetBefore: inst.performancePreset };
  if (options !== null) await p.writeText(path(dir, "options.txt"), options);
  await p.writeText(path(dir, "instance.json"), JSON.stringify(inst, null, 2));
  await p.writeText(path(dir, "backup.json"), JSON.stringify(info, null, 2));
  return info;
}

export async function restoreBackup(inst: Instance, backup: BackupInfo): Promise<void> {
  const p = P();
  const dir = path(bloomDir(inst), "backups", backup.id);
  await backupSettings(inst, `Before restoring the backup from ${new Date(backup.at).toLocaleString()}`);
  const options = await p.readText(path(dir, "options.txt"));
  if (options !== null) await p.writeText(optionsPath(inst), options);
  const savedInst = await p.readText(path(dir, "instance.json"));
  if (savedInst) {
    const old = JSON.parse(savedInst) as Instance;
    // Restore performance-related settings only; keep name, playtime etc. current.
    const current = getInstance(inst.id) ?? inst;
    await saveInstance({
      ...current,
      performancePreset: old.performancePreset,
      memory: old.memory,
      jvmPreset: old.jvmPreset,
      jvmArgs: old.jvmArgs,
    });
  }
  await p.trash(path(bloomDir(inst), "pending-preset.json")).catch(() => undefined);
}

export interface ApplyResult {
  applied: boolean;
  changes: AppliedChange[];
  pending: boolean;
}

/**
 * Apply a preset's video settings. Always backs up first. If Minecraft hasn't
 * created options.txt yet (never launched), the preset is stored and applied
 * automatically after the first launch, so Minecraft's own defaults and data
 * version are written first.
 */
export async function applyPreset(inst: Instance, presetId: PresetId, video?: VideoSettings): Promise<ApplyResult> {
  const p = P();
  const settings = video ?? PRESETS.find((x) => x.id === presetId)?.video;
  if (!settings) throw new Error(`Unknown preset ${presetId}`);
  const current = await p.readText(optionsPath(inst));
  await backupSettings(inst, `Before applying ${presetId === "custom" ? "custom settings" : PRESETS.find((x) => x.id === presetId)!.name}`);
  if (current === null) {
    await p.writeText(path(bloomDir(inst), "pending-preset.json"), JSON.stringify({ presetId, video: settings } satisfies PendingPreset));
    await saveInstance({ ...(getInstance(inst.id) ?? inst), performancePreset: presetId });
    return { applied: false, changes: [], pending: true };
  }
  const { text, changes } = applyVideoSettings(current, settings, optionsFormatFor(await releaseTime(inst)));
  await p.writeText(optionsPath(inst), text);
  await saveInstance({ ...(getInstance(inst.id) ?? inst), performancePreset: presetId });
  return { applied: true, changes, pending: false };
}

export async function applyPendingPreset(inst: Instance): Promise<void> {
  const p = P();
  const raw = await p.readText(path(bloomDir(inst), "pending-preset.json"));
  if (!raw) return;
  const current = await p.readText(optionsPath(inst));
  if (current === null) return;
  const pending = JSON.parse(raw) as PendingPreset;
  const { text } = applyVideoSettings(current, pending.video, optionsFormatFor(await releaseTime(inst)));
  await p.writeText(optionsPath(inst), text);
  await p.trash(path(bloomDir(inst), "pending-preset.json"));
}

// ---------------------------------------------------------------------------
// Recommendations
// ---------------------------------------------------------------------------

export interface PerfModStatus {
  mod: PerfModInfo;
  installed: boolean;
  /** Best compatible version on Modrinth, if any. */
  version?: Version;
  projectId?: string;
}

/** For each known performance mod: is it installed, and does Modrinth have a compatible version? */
export async function perfModStatus(inst: Instance): Promise<PerfModStatus[]> {
  if (inst.loader === "vanilla") return PERF_MODS.map((mod) => ({ mod, installed: false }));
  const mods = await listContent(inst, "mods");
  const ids = new Set(mods.filter((m) => m.enabled).flatMap((m) => (m.meta ? [m.meta.id, ...m.meta.provides] : [])));
  const tracked = await installedProjects(inst).catch(() => new Set<string>());
  const loaders = modrinthLoadersFor(inst.loader);
  return Promise.all(
    PERF_MODS.map(async (mod) => {
      let installed = mod.modIds.some((id) => ids.has(id)) || mods.some((m) => m.modrinth?.title === mod.title);
      try {
        const versions = await modrinth().versions(mod.slug, { loaders, gameVersions: [inst.gameVersion] });
        const best = compatibleVersions(versions, { gameVersion: inst.gameVersion, loaders })[0];
        // Also count files BLOOD installed from Modrinth (by project id), even before their metadata is read.
        if (best && tracked.has(best.project_id)) installed = true;
        return { mod, installed, version: best, projectId: best?.project_id };
      } catch {
        return { mod, installed };
      }
    }),
  );
}

// ---------------------------------------------------------------------------
// In-game overlay (Sodium Extra)
// ---------------------------------------------------------------------------

/**
 * BLOOD doesn't inject anything into the game. The FPS overlay is provided by
 * the Sodium Extra mod; BLOOD writes its overlay options before launch. Only
 * the overlay keys are touched — the rest of the file is preserved.
 */
export async function writeOverlayConfig(inst: Instance): Promise<void> {
  if (inst.loader === "vanilla") return;
  const p = P();
  const mods = await listContent(inst, "mods");
  const hasSodiumExtra = mods.some((m) => m.enabled && m.meta?.id === "sodium-extra");
  if (!hasSodiumExtra) return;
  const cfgPath = path(instanceGameDir(inst), "config", "sodium-extra-options.json");
  const overlay = app.get().settings.overlay;
  let cfg: Record<string, unknown> = {};
  const raw = await p.readText(cfgPath);
  if (raw) {
    try {
      cfg = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return; // never clobber a file we can't parse
    }
  }
  const extra = (cfg.extra_settings && typeof cfg.extra_settings === "object" ? cfg.extra_settings : {}) as Record<string, unknown>;
  cfg.extra_settings = {
    ...extra,
    show_fps: overlay.enabled,
    show_f_p_s_extended: overlay.extended,
    overlay_corner: overlay.corner,
    text_contrast: overlay.contrast,
  };
  await p.writeText(cfgPath, JSON.stringify(cfg, null, 2));
}
