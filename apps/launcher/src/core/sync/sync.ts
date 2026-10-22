/**
 * Cloud sync (MOTION Premium): which settings and installation metadata travel
 * between PCs, and how a downloaded document is merged in. Only portable data
 * is synced — never tokens, Java paths, custom folders, worlds or mod files.
 * Mods tracked from Modrinth are synced as (project, version) references so
 * another PC can download the same files from Modrinth.
 */

import type { Instance } from "../instances/model";

export const SYNC_FORMAT = 1;

/** Settings that make sense on every PC. */
export const SYNCED_SETTINGS = [
  "theme",
  "accent",
  "accentFromTheme",
  "petals",
  "reduceMotion",
  "startPage",
  "onLaunch",
  "memory",
  "jvmPreset",
  "jvmArgs",
  "gameArgs",
  "resolution",
  "fullscreen",
  "showSnapshots",
  "modUpdateNotifications",
  "defaultPreset",
  "overlay",
] as const;

export type SyncedSettingKey = (typeof SYNCED_SETTINGS)[number];

const INSTANCE_FIELDS = [
  "id",
  "name",
  "icon",
  "gameVersion",
  "loader",
  "loaderVersion",
  "createdAt",
  "memory",
  "jvmPreset",
  "jvmArgs",
  "gameArgs",
  "resolution",
  "fullscreen",
  "performancePreset",
  "modpack",
] as const satisfies readonly (keyof Instance)[];

export type SyncedInstance = Pick<Instance, (typeof INSTANCE_FIELDS)[number]>;

export interface SyncedContent {
  kind: "mods" | "resourcepacks" | "shaderpacks";
  projectId: string;
  versionId: string;
  fileName: string;
  enabled: boolean;
}

export interface SyncData {
  format: number;
  settings: Partial<Record<SyncedSettingKey, unknown>>;
  instances: (SyncedInstance & { content: SyncedContent[] })[];
}

const MAX_ICON = 48 * 1024;

export function syncedInstance(inst: Instance): SyncedInstance {
  const out: Record<string, unknown> = {};
  for (const k of INSTANCE_FIELDS) {
    const v = inst[k];
    if (v === undefined) continue;
    // Large embedded icons stay local.
    if (k === "icon" && typeof v === "string" && v.startsWith("data:") && v.length > MAX_ICON) continue;
    out[k] = v;
  }
  return out as SyncedInstance;
}

export function buildSyncData(settings: Record<string, unknown>, instances: Instance[], content: Record<string, SyncedContent[]>): SyncData {
  const s: SyncData["settings"] = {};
  for (const k of SYNCED_SETTINGS) if (settings[k] !== undefined) s[k] = settings[k];
  return {
    format: SYNC_FORMAT,
    settings: s,
    instances: instances.map((i) => ({ ...syncedInstance(i), content: content[i.id] ?? [] })),
  };
}

export interface MergePlan {
  settingsPatch: Partial<Record<SyncedSettingKey, unknown>>;
  /** Installations that exist only in the cloud: create them. */
  create: (SyncedInstance & { content: SyncedContent[] })[];
  /** Existing installations whose synced fields differ. */
  update: { id: string; patch: Partial<SyncedInstance> }[];
  /** Local installations not in the cloud document (kept; never deleted). */
  localOnly: string[];
}

function same(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/** Decide how to apply a downloaded document. Nothing local is ever deleted. */
export function planMerge(local: { settings: Record<string, unknown>; instances: Instance[] }, remote: unknown): MergePlan {
  const plan: MergePlan = { settingsPatch: {}, create: [], update: [], localOnly: [] };
  if (!isSyncData(remote)) return plan;
  for (const k of SYNCED_SETTINGS) {
    if (k in remote.settings && !same(remote.settings[k], local.settings[k])) plan.settingsPatch[k] = remote.settings[k];
  }
  const byId = new Map(local.instances.map((i) => [i.id, i]));
  const remoteIds = new Set<string>();
  for (const r of remote.instances) {
    if (!r || typeof r.id !== "string" || !/^[a-z0-9-]{1,64}$/.test(r.id)) continue;
    remoteIds.add(r.id);
    const cur = byId.get(r.id);
    if (!cur) {
      plan.create.push(r);
      continue;
    }
    const patch: Partial<SyncedInstance> = {};
    for (const k of INSTANCE_FIELDS) {
      if (k === "id" || k === "createdAt") continue;
      if (!same(r[k], cur[k])) (patch as Record<string, unknown>)[k] = r[k];
    }
    if (Object.keys(patch).length) plan.update.push({ id: r.id, patch });
  }
  plan.localOnly = local.instances.filter((i) => !remoteIds.has(i.id)).map((i) => i.id);
  return plan;
}

export function isSyncData(x: unknown): x is SyncData {
  if (!x || typeof x !== "object") return false;
  const d = x as Partial<SyncData>;
  return d.format === SYNC_FORMAT && typeof d.settings === "object" && d.settings !== null && Array.isArray(d.instances);
}

/** Stable fingerprint to detect local changes since the last sync. */
export function fingerprint(data: SyncData): string {
  const json = JSON.stringify(data);
  let h = 2166136261;
  for (let i = 0; i < json.length; i++) {
    h ^= json.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0).toString(16);
}
