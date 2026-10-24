import type { LoaderId } from "../loaders/loaders";
import type { PresetId } from "../perf/presets";
import type { JvmPreset } from "../perf/memory";
import { join, slugify } from "../util/path";

export interface Instance {
  id: string;
  name: string;
  /** Data URL or https URL (e.g. a modpack icon). */
  icon?: string;
  gameVersion: string;
  loader: LoaderId;
  loaderVersion?: string;
  createdAt: number;
  lastPlayedAt?: number;
  totalPlayMs?: number;
  /** Custom game directory (absolute). Default: <data>/instances/<id>. */
  gameDir?: string;
  /** Custom Java executable. Default: the Mojang runtime Minecraft asks for. */
  javaPath?: string;
  /** Overrides the global memory setting when set. */
  memory?: { minMb: number; maxMb: number };
  jvmPreset?: JvmPreset["id"];
  jvmArgs?: string[];
  gameArgs?: string[];
  resolution?: { width: number; height: number };
  fullscreen?: boolean;
  performancePreset?: PresetId;
  modpack?: { projectId?: string; versionId?: string; name: string; version?: string };
  /** MOTION Client (the in-game mod) managed by the launcher for this installation. */
  bloomClient?: { enabled: boolean; version?: string; file?: string };
  /** Servers and worlds recently joined from MOTION, most recent first ("Jump back in"). */
  recentPlays?: { kind: "server" | "world"; target: string; name: string; at: number }[];
  /** Last measured launch timings, from the game's own log. */
  lastLaunch?: { prepareMs: number; startupMs?: number; at: number };
}

export interface Layout {
  root: string;
  versions: string;
  libraries: string;
  assets: string;
  runtimes: string;
  instances: string;
  natives: string;
  meta: string;
  logs: string;
  tmp: string;
}

export function layout(dataDir: string, sep: string): Layout {
  const j = (...p: string[]) => join(sep, dataDir, ...p);
  return {
    root: dataDir,
    versions: j("versions"),
    libraries: j("libraries"),
    assets: j("assets"),
    runtimes: j("runtimes"),
    instances: j("instances"),
    natives: j("natives"),
    meta: j("meta"),
    logs: j("logs"),
    tmp: j("tmp"),
  };
}

export function instanceDir(l: Layout, sep: string, id: string): string {
  return join(sep, l.instances, id);
}

export function gameDirOf(l: Layout, sep: string, inst: Instance): string {
  return inst.gameDir || instanceDir(l, sep, inst.id);
}

/** MOTION's own metadata lives in a hidden folder inside the instance folder. */
export function instanceMetaPath(l: Layout, sep: string, id: string): string {
  return join(sep, l.instances, id, ".bloom", "instance.json");
}

export function newInstanceId(name: string, existing: Set<string>): string {
  const base = slugify(name);
  if (!existing.has(base)) return base;
  for (let i = 2; i < 1000; i++) {
    const id = `${base}-${i}`;
    if (!existing.has(id)) return id;
  }
  return `${base}-${Date.now().toString(36)}`;
}

export function validateInstance(inst: Partial<Instance>): string[] {
  const errors: string[] = [];
  if (!inst.name?.trim()) errors.push("Give the installation a name.");
  if (inst.name && inst.name.length > 64) errors.push("Names can be at most 64 characters.");
  if (!inst.gameVersion) errors.push("Choose a Minecraft version.");
  if (inst.memory) {
    if (inst.memory.maxMb < 1024) errors.push("Allocate at least 1 GB of memory.");
    if (inst.memory.minMb > inst.memory.maxMb) errors.push("Minimum memory can't be higher than maximum memory.");
  }
  if (inst.resolution && (inst.resolution.width < 320 || inst.resolution.height < 240)) {
    errors.push("Window size must be at least 320×240.");
  }
  return errors;
}

/** Subfolders each installation keeps separately. */
export const CONTENT_FOLDERS = {
  mods: "mods",
  resourcepacks: "resourcepacks",
  shaderpacks: "shaderpacks",
  saves: "saves",
  config: "config",
  screenshots: "screenshots",
} as const;
