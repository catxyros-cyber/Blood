/**
 * BLOOD Client management: install/update/remove the bundled mod in an installation,
 * make sure Fabric API is present, and edit the mod's settings from the launcher.
 *
 * Files the launcher manages here are the BLOOD Client jar itself (tracked in the
 * installation's metadata) and BLOOD Client's own config. Removed or replaced jars go to
 * the Recycle Bin; other mods are never touched.
 */

import { BUILT_IN_PROFILES, type ProfileInfo } from "../core/bloomclient/catalog";
import { conflictsFor, MENU_KEY_OPTION, readOption, writeOption } from "../core/bloomclient/optionsTxt";
import { dataUrlBytes, pngSize, skyboxImageProblem } from "../core/bloomclient/png";
import { applyPatch, parseSettings, type SettingsPatch, type SettingsView } from "../core/bloomclient/settingsFile";
import { compareVersions, compatNotes, preferredVersion, support, versionFromFileName, type Bundle, type CompatNote, type SupportStatus } from "../core/bloomclient/support";
import type { Instance } from "../core/instances/model";
import { BloomError } from "../core/platform";
import { slugify } from "../core/util/path";
import { app, P, path, startTask, endTask, toast, updateTask } from "./app";
import { capeState, loadCapes } from "./capes";
import { applyInstallPlan, contentDir, listContent, modrinth, planProjectInstall, type ContentItem } from "./content";
import { getInstance, instanceGameDir, updateInstance } from "./instances";
import { native } from "./native";

let bundleCache: Promise<Bundle | null> | null = null;

/** The BLOOD Client build bundled with this launcher (null in builds without it). */
export function bundle(): Promise<Bundle | null> {
  if (!bundleCache) bundleCache = native.bloomClientBundle().catch(() => null);
  return bundleCache;
}

export interface ClientStatus {
  support: SupportStatus;
  bundle: Bundle | null;
  /** BLOOD Client jars currently in the mods folder. */
  installed: ContentItem[];
  installedVersion: string | null;
  enabled: boolean;
  updateAvailable: boolean;
  notes: CompatNote[];
}

function isBloomJar(item: ContentItem): boolean {
  return item.meta?.id === "bloomclient" || versionFromFileName(item.fileName) !== null;
}

export async function status(inst: Instance): Promise<ClientStatus> {
  const b = await bundle();
  const sup = support(inst, b);
  const mods = inst.loader === "vanilla" ? [] : await listContent(inst, "mods").catch(() => [] as ContentItem[]);
  const installed = mods.filter(isBloomJar);
  const active = installed.find((i) => i.enabled);
  const installedVersion = active ? (versionFromFileName(active.baseName) ?? active.meta?.version ?? null) : null;
  const ids = new Set(mods.filter((m) => m.enabled && m.meta?.id).map((m) => m.meta!.id));
  return {
    support: sup,
    bundle: b,
    installed,
    installedVersion,
    enabled: Boolean(active),
    // Newer version bundled, or the jar in the folder is for another Minecraft version.
    updateAvailable: Boolean(
      b && active && ((installedVersion && compareVersions(b.version, installedVersion) > 0) || (sup.ok && active.baseName !== sup.file && versionFromFileName(active.baseName) !== null)),
    ),
    notes: active ? compatNotes(inst.loader, ids) : [],
  };
}

/** Install (or update to) the bundled BLOOD Client, plus Fabric API when the loader needs it. */
export async function enable(inst: Instance): Promise<void> {
  const b = await bundle();
  const sup = support(inst, b);
  if (!sup.ok || !b) throw new BloomError("unsupported", sup.ok ? "BLOOD Client isn't bundled." : sup.message);
  const taskId = `bloomclient-${inst.id}`;
  startTask({ id: taskId, title: "Installing BLOOD Client", instanceId: inst.id, cancellable: false, stage: "Copying files" });
  try {
    const mods = await listContent(inst, "mods");
    const file = b.files.find((f) => f.file === sup.file)!;
    // Replace older BLOOD Client jars (ours) by moving them to the Recycle Bin.
    for (const old of mods.filter(isBloomJar)) {
      if (old.baseName === file.file && old.enabled) continue;
      await P().trash(old.path);
    }
    if (!mods.some((m) => m.baseName === file.file && m.enabled)) {
      await native.bloomClientInstall(sup.loaderFile, contentDir(inst, "mods"), file.file);
    }
    if (inst.loader === "fabric" || inst.loader === "quilt") {
      const ids = new Set(mods.filter((m) => m.enabled && m.meta?.id).map((m) => m.meta!.id));
      if (!ids.has("fabric-api") && !ids.has("quilted_fabric_api")) {
        updateTask(taskId, { stage: "Installing Fabric API" });
        const project = await modrinth().project("fabric-api");
        await applyInstallPlan(inst, project, await planProjectInstall(inst, project));
      }
    }
    await updateInstance(inst.id, { bloomClient: { enabled: true, version: b.version, file: file.file } });
    endTask(taskId);
    toast({ kind: "success", title: "BLOOD Client is ready", message: "Press Right Shift in game to open the BLOOD menu." });
  } catch (e) {
    endTask(taskId);
    throw e;
  }
}

/** Remove BLOOD Client from the installation (the jar goes to the Recycle Bin). Its settings stay. */
export async function disable(inst: Instance): Promise<void> {
  const mods = await listContent(inst, "mods");
  for (const m of mods.filter(isBloomJar)) await P().trash(m.path);
  await updateInstance(inst.id, { bloomClient: { enabled: false } });
}

/** Called before launching: keeps the managed jar on the bundled version. Never blocks a launch. */
export async function beforeLaunch(inst: Instance): Promise<void> {
  if (!inst.bloomClient?.enabled) {
    await addForCape(inst);
    return;
  }
  const s = await status(inst);
  if (!s.support.ok) return;
  if (!s.enabled || s.updateAvailable) {
    await enable(getInstance(inst.id) ?? inst);
  }
}

/**
 * BLOOD capes are drawn by BLOOD Client, so a cape does nothing in an installation without it.
 * If you've picked a cape and this installation can run BLOOD Client (and you never switched it
 * off here), add it before launching so your cape shows up in game.
 */
async function addForCape(inst: Instance): Promise<void> {
  if (inst.bloomClient?.enabled === false) return; // turned off on purpose
  if (inst.loader === "vanilla" || inst.loader === "forge") return;
  if (capeState.get().status === "idle") await loadCapes().catch(() => undefined);
  if (!capeState.get().capeId) return;
  const s = await status(inst);
  if (!s.support.ok || s.enabled) return;
  await enable(getInstance(inst.id) ?? inst);
  toast({ kind: "success", title: "Added BLOOD Client for your cape", message: "Your BLOOD cape is shown in game by BLOOD Client. Turn it off on the BLOOD Mods page if you don't want it." });
}

// ---------------------------------------------------------------- settings.json

function configDir(inst: Instance): string {
  return path(instanceGameDir(inst), "config", "bloomclient");
}

export async function readSettings(inst: Instance): Promise<SettingsView> {
  const text = await P().readText(path(configDir(inst), "settings.json")).catch(() => null);
  return parseSettings(text);
}

/** Read-modify-write right away, so the game's newer values are never overwritten. */
export async function writeSettings(inst: Instance, patch: SettingsPatch): Promise<SettingsView> {
  const file = path(configDir(inst), "settings.json");
  const text = await P().readText(file).catch(() => null);
  const next = applyPatch(text, patch, new Date());
  await P().writeText(file, next);
  return parseSettings(next);
}

export async function listProfiles(inst: Instance): Promise<ProfileInfo[]> {
  const out: ProfileInfo[] = [...BUILT_IN_PROFILES];
  const dir = path(configDir(inst), "profiles");
  for (const e of await P().list(dir).catch(() => [])) {
    if (e.isDir || !e.name.endsWith(".json")) continue;
    const id = e.name.slice(0, -5);
    if (!/^[a-z0-9][a-z0-9_-]{0,47}$/.test(id) || BUILT_IN_PROFILES.some((p) => p.id === id)) continue;
    try {
      const json = JSON.parse((await P().readText(e.path)) ?? "{}") as { name?: unknown; description?: unknown };
      out.push({ id, name: typeof json.name === "string" ? json.name.slice(0, 32) : id, description: typeof json.description === "string" ? json.description.slice(0, 160) : "", builtIn: false });
    } catch {
      /* unreadable profile: skip */
    }
  }
  return out;
}

// ---------------------------------------------------------------- key binding (options.txt)

function optionsPath(inst: Instance) {
  return path(instanceGameDir(inst), "options.txt");
}

export async function menuKey(inst: Instance): Promise<{ key: string | null; conflicts: string[] }> {
  const text = await P().readText(optionsPath(inst)).catch(() => null);
  const key = readOption(text, MENU_KEY_OPTION);
  return { key, conflicts: key ? conflictsFor(text, MENU_KEY_OPTION, key) : [] };
}

export async function setMenuKey(inst: Instance, key: string): Promise<string[]> {
  if (app.get().running[inst.id]) {
    throw new BloomError("running", "Close Minecraft first: the game rewrites its key bindings when it exits. You can also change the key in game (BLOOD menu → Keybinds).");
  }
  const file = optionsPath(inst);
  const text = await P().readText(file).catch(() => null);
  const next = writeOption(text, MENU_KEY_OPTION, key);
  await P().writeText(file, next);
  return conflictsFor(next, MENU_KEY_OPTION, key);
}

// ---------------------------------------------------------------- custom skyboxes

export interface CustomSkybox {
  id: string;
  name: string;
  path: string;
}

export async function listCustomSkyboxes(inst: Instance): Promise<CustomSkybox[]> {
  const dir = path(configDir(inst), "skyboxes");
  const out: CustomSkybox[] = [];
  for (const e of await P().list(dir).catch(() => [])) {
    if (e.isDir || !e.name.toLowerCase().endsWith(".png")) continue;
    const stem = e.name.slice(0, -4);
    if (!/^[a-z0-9][a-z0-9_-]{0,47}$/.test(stem)) continue;
    let name = stem.replace(/_/g, " ");
    try {
      const meta = JSON.parse((await P().readText(path(dir, stem + ".json"))) ?? "{}") as { name?: unknown };
      if (typeof meta.name === "string" && meta.name.trim()) name = meta.name.slice(0, 32);
    } catch {
      /* no metadata */
    }
    out.push({ id: "custom:" + stem, name, path: e.path });
  }
  return out;
}

/**
 * Copy a picked PNG into BLOOD Client's skybox folder under a new, safe name. The image is
 * checked first (3×2 cube layout or 2:1 panorama); the game converts panoramas on first use.
 */
export async function importSkybox(inst: Instance, source: string): Promise<CustomSkybox> {
  const head = await native.readDataUrl(source, 32 * 1024 * 1024);
  if (!head) throw new BloomError("not_found", "The image could not be read.");
  const size = pngSize(dataUrlBytes(head, 64));
  if (!size) throw new BloomError("invalid", "That file isn't a PNG image (or it's larger than 8192 pixels).");
  const problem = skyboxImageProblem(size);
  if (problem) throw new BloomError("invalid", problem);
  const dir = path(configDir(inst), "skyboxes");
  await P().mkdir(dir);
  const res = await native.importFiles([source], dir);
  const copied = res.copied[0];
  const original = source.split(/[\\/]/).pop() ?? "skybox.png";
  const baseName = original.replace(/\.png$/i, "");
  let stem = slugify(baseName).replace(/-/g, "_").slice(0, 40) || "skybox";
  const existing = new Set((await P().list(dir)).map((e) => e.name.toLowerCase()));
  let candidate = stem;
  for (let i = 2; existing.has(candidate + ".png") && (!copied || path(dir, candidate + ".png") !== copied); i++) candidate = `${stem}_${i}`;
  stem = candidate;
  const target = path(dir, stem + ".png");
  if (copied && copied !== target) await P().rename(copied, target);
  else if (!copied) throw new BloomError("exists", `A file named ${original} is already in the skybox folder.`);
  await P().writeText(path(dir, stem + ".json"), JSON.stringify({ format: 1, name: baseName.slice(0, 32), blend: "alpha", suggested: "any", origin: original.slice(0, 64) }, null, 2));
  return { id: "custom:" + stem, name: baseName.slice(0, 32), path: target };
}

/**
 * For installations BLOOD Client can't run on (vanilla, Forge, other versions): create a new
 * Fabric installation on a supported Minecraft version and add BLOOD Client to it. The
 * original installation is not changed.
 */
export async function createBloomInstallation(basedOn?: Instance): Promise<Instance> {
  const b = await bundle();
  if (!b) throw new BloomError("not_bundled", "This build of the launcher doesn't include BLOOD Client.");
  const gameVersion = preferredVersion(b);
  const { loaderVersions, pickLoaderVersion } = await import("../core/loaders/loaders");
  const { createInstance } = await import("./instances");
  const lv = pickLoaderVersion(await loaderVersions(P(), "fabric", gameVersion));
  const inst = await createInstance({
    name: `${basedOn ? basedOn.name : "BLOOD"} — BLOOD Client`.slice(0, 64),
    gameVersion,
    loader: "fabric",
    loaderVersion: lv.version,
    memory: basedOn?.memory,
  });
  await enable(inst);
  return getInstance(inst.id) ?? inst;
}
