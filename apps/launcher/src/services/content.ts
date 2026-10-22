/**
 * Mods, resource packs, shader packs and worlds for an installation:
 * listing, metadata, enable/disable, import, removal (to the Recycle Bin),
 * Modrinth installs/updates and modpacks.
 */

import { instanceDir, type Instance } from "../core/instances/model";
import { loaderVersions, modrinthLoadersFor, pickLoaderVersion, LOADERS } from "../core/loaders/loaders";
import { combineMetadata, manifestVersion, METADATA_ENTRIES, type ModMeta } from "../core/mods/metadata";
import { analyzeMods, type CompatIssue, type InstalledMod } from "../core/mods/compat";
import { ModrinthClient } from "../core/modrinth/api";
import { parseMrpackIndex } from "../core/modrinth/mrpack";
import { planInstall, primaryFile, type InstallPlan } from "../core/modrinth/resolve";
import type { Project, ProjectType, Version } from "../core/modrinth/types";
import { BloomError, type DownloadItem } from "../core/platform";
import { app, endTask, L, P, path, reportError, startTask, toast, updateTask } from "./app";
import { createInstance, instanceGameDir, saveInstance } from "./instances";
import type { SyncedContent } from "../core/sync/sync";
import { native } from "./native";

export type ContentKind = "mods" | "resourcepacks" | "shaderpacks";

export interface IndexEntry {
  projectId: string;
  versionId?: string;
  title?: string;
  iconUrl?: string | null;
  clientSide?: string;
  sha1?: string;
  versionNumber?: string;
}

interface ContentIndex {
  files: Record<string, IndexEntry>;
  meta: Record<string, ModMeta | null>;
}

export interface ContentItem {
  fileName: string;
  baseName: string;
  path: string;
  enabled: boolean;
  isDir: boolean;
  size: number;
  modifiedMs: number;
  meta: ModMeta | null;
  modrinth?: IndexEntry;
}

let client: ModrinthClient | null = null;
export function modrinth(): ModrinthClient {
  if (!client) client = new ModrinthClient((req) => P().request(req));
  return client;
}

const DISABLED = ".disabled";

export function contentDir(inst: Instance, kind: ContentKind): string {
  return path(instanceGameDir(inst), kind);
}

function indexPath(inst: Instance): string {
  return path(instanceDir(L(), P().sep, inst.id), ".bloom", "content.json");
}

async function readIndex(inst: Instance): Promise<ContentIndex> {
  const text = await P().readText(indexPath(inst)).catch(() => null);
  if (!text) return { files: {}, meta: {} };
  try {
    const parsed = JSON.parse(text) as Partial<ContentIndex>;
    return { files: parsed.files ?? {}, meta: parsed.meta ?? {} };
  } catch {
    return { files: {}, meta: {} };
  }
}

async function writeIndex(inst: Instance, index: ContentIndex) {
  await P().writeText(indexPath(inst), JSON.stringify(index));
}

function accepts(kind: ContentKind, name: string, isDir: boolean): boolean {
  const base = name.endsWith(DISABLED) ? name.slice(0, -DISABLED.length) : name;
  if (kind === "mods") return !isDir && base.toLowerCase().endsWith(".jar");
  return isDir || base.toLowerCase().endsWith(".zip");
}

async function readModMeta(filePath: string): Promise<ModMeta | null> {
  const p = P();
  const entries = new Set(await p.zipList(filePath));
  const found: Partial<Record<(typeof METADATA_ENTRIES)[number], string>> = {};
  for (const e of METADATA_ENTRIES) {
    if (entries.has(e)) found[e] = (await p.zipReadText(filePath, e)) ?? undefined;
  }
  const meta = combineMetadata(found);
  if (meta && (!meta.version || meta.version.includes("${")) && entries.has("META-INF/MANIFEST.MF")) {
    const v = manifestVersion((await p.zipReadText(filePath, "META-INF/MANIFEST.MF")) ?? "");
    if (v) meta.version = v;
  }
  return meta;
}

/** Modrinth projects tracked in an installation (to show "Installed" in Browse). */
export async function installedProjects(inst: Instance): Promise<Set<string>> {
  const index = await readIndex(inst);
  return new Set(Object.values(index.files).map((f) => f.projectId));
}

export async function listContent(inst: Instance, kind: ContentKind): Promise<ContentItem[]> {
  const p = P();
  const dir = contentDir(inst, kind);
  const entries = (await p.list(dir)).filter((e) => accepts(kind, e.name, e.isDir));
  const index = await readIndex(inst);
  let dirty = false;
  const items: ContentItem[] = [];
  for (const e of entries) {
    const enabled = !e.name.endsWith(DISABLED);
    const baseName = enabled ? e.name : e.name.slice(0, -DISABLED.length);
    let meta: ModMeta | null = null;
    if (kind === "mods") {
      const key = `${baseName}:${e.size}:${e.modifiedMs}`;
      if (key in index.meta) meta = index.meta[key] ?? null;
      else {
        meta = await readModMeta(e.path).catch(() => null);
        index.meta[key] = meta;
        dirty = true;
      }
    }
    items.push({ fileName: e.name, baseName, path: e.path, enabled, isDir: e.isDir, size: e.size, modifiedMs: e.modifiedMs, meta, modrinth: index.files[baseName] });
  }
  if (dirty) {
    // Drop cache entries for files that no longer exist.
    const live = new Set(items.map((i) => `${i.baseName}:${i.size}:${i.modifiedMs}`));
    for (const k of Object.keys(index.meta)) if (!live.has(k)) delete index.meta[k];
    await writeIndex(inst, index).catch(() => undefined);
  }
  return items.sort((a, b) => (a.meta?.name ?? a.modrinth?.title ?? a.baseName).localeCompare(b.meta?.name ?? b.modrinth?.title ?? b.baseName));
}

const iconCache = new Map<string, string | null>();
export async function modIcon(item: ContentItem): Promise<string | null> {
  if (item.modrinth?.iconUrl) return item.modrinth.iconUrl;
  // Mods name their icon in metadata; resource packs ship pack.png at the root.
  const entry = item.meta?.icon?.replace(/^\//, "") ?? (!item.isDir && /\.zip$/i.test(item.baseName) ? "pack.png" : null);
  if (!entry) return null;
  const key = `${item.path}:${item.modifiedMs}`;
  if (iconCache.has(key)) return iconCache.get(key) ?? null;
  const url = await native.zipReadDataUrl(item.path, entry).catch(() => null);
  iconCache.set(key, url);
  return url;
}

export function compatibility(inst: Instance, mods: ContentItem[]): CompatIssue[] {
  const installed: InstalledMod[] = mods.map((m) => ({
    fileName: m.fileName,
    enabled: m.enabled,
    meta: m.meta,
    modrinth: m.modrinth ? { projectId: m.modrinth.projectId, title: m.modrinth.title, clientSide: m.modrinth.clientSide } : undefined,
  }));
  return analyzeMods(installed, { loader: inst.loader, gameVersion: inst.gameVersion });
}

export async function toggleContent(inst: Instance, kind: ContentKind, item: ContentItem) {
  const dir = contentDir(inst, kind);
  const to = item.enabled ? `${item.baseName}${DISABLED}` : item.baseName;
  await P().rename(item.path, path(dir, to));
}

/** Move files to the Recycle Bin. The UI confirms before calling this. */
export async function removeContent(inst: Instance, items: ContentItem[]) {
  const index = await readIndex(inst);
  for (const item of items) {
    await P().trash(item.path);
    delete index.files[item.baseName];
  }
  await writeIndex(inst, index);
}

export async function openContentFolder(inst: Instance, sub: ContentKind | "saves" | "config" | "logs" | "screenshots" | "") {
  await native.openPath(sub ? path(instanceGameDir(inst), sub) : instanceGameDir(inst));
}

const EXT: Record<ContentKind, string[]> = { mods: ["jar"], resourcepacks: ["zip"], shaderpacks: ["zip"] };

/** Copy user-chosen files into the installation. Never overwrites existing files. */
export async function importContent(inst: Instance, kind: ContentKind, sources: string[]) {
  const ok = sources.filter((s) => EXT[kind].some((e) => s.toLowerCase().endsWith(`.${e}`)));
  const rejected = sources.length - ok.length;
  if (ok.length === 0) {
    toast({ kind: "warning", title: "Nothing to import", message: `Only .${EXT[kind].join("/.")} files can be added here.` });
    return;
  }
  const result = await native.importFiles(ok, contentDir(inst, kind));
  const parts = [`Added ${result.copied.length} file${result.copied.length === 1 ? "" : "s"}.`];
  if (result.conflicts.length) parts.push(`Skipped ${result.conflicts.length} already present: ${result.conflicts.slice(0, 3).join(", ")}${result.conflicts.length > 3 ? "…" : ""}`);
  if (rejected) parts.push(`${rejected} file(s) had the wrong type.`);
  toast({ kind: result.copied.length ? "success" : "warning", title: "Import finished", message: parts.join(" ") });
}

export async function pickAndImport(inst: Instance, kind: ContentKind) {
  const files = await native.pickFiles({ title: "Add files", multiple: true, filters: [[kind === "mods" ? "Mods" : "Packs", EXT[kind]]] });
  if (files.length) await importContent(inst, kind, files);
}

export async function importFolder(inst: Instance) {
  const [folder] = await native.pickFiles({ title: "Import mods from a folder", directory: true });
  if (!folder) return;
  const entries = await native.listImportableFolder(folder);
  await importContent(inst, "mods", entries.filter((e) => !e.isDir).map((e) => e.path));
}

/** Look up unidentified files on Modrinth by their SHA-1 so they get names, icons and update checks. */
export async function identifyContent(inst: Instance, items: ContentItem[]): Promise<number> {
  const unknown = items.filter((i) => !i.isDir && !i.modrinth);
  if (unknown.length === 0) return 0;
  const p = P();
  const hashes = new Map<string, ContentItem>();
  for (const i of unknown) hashes.set(await p.hash(i.path, "sha1"), i);
  const found = await modrinth().versionsByHash([...hashes.keys()]);
  const projectIds = [...new Set(Object.values(found).map((v) => v.project_id))];
  const projects = new Map((await modrinth().projects(projectIds)).map((pr) => [pr.id, pr]));
  const index = await readIndex(inst);
  let n = 0;
  for (const [hash, v] of Object.entries(found)) {
    const item = hashes.get(hash);
    if (!item) continue;
    const pr = projects.get(v.project_id);
    index.files[item.baseName] = {
      projectId: v.project_id,
      versionId: v.id,
      versionNumber: v.version_number,
      title: pr?.title,
      iconUrl: pr?.icon_url,
      clientSide: pr?.client_side,
      sha1: hash,
    };
    n++;
  }
  await writeIndex(inst, index);
  return n;
}

export interface UpdateInfo {
  item: ContentItem;
  version: Version;
}

export async function checkUpdates(inst: Instance, items: ContentItem[]): Promise<UpdateInfo[]> {
  const tracked = items.filter((i) => i.modrinth?.sha1);
  if (tracked.length === 0) return [];
  const loaders = modrinthLoadersFor(inst.loader);
  const latest = await modrinth().latestByHash(
    tracked.map((i) => i.modrinth!.sha1!),
    loaders.length ? loaders : ["minecraft"],
    [inst.gameVersion],
  );
  const out: UpdateInfo[] = [];
  for (const item of tracked) {
    const v = latest[item.modrinth!.sha1!];
    if (v && v.id !== item.modrinth!.versionId) out.push({ item, version: v });
  }
  return out;
}

function kindForProject(t: ProjectType): ContentKind {
  if (t === "resourcepack") return "resourcepacks";
  if (t === "shader") return "shaderpacks";
  return "mods";
}

/** Download a planned install into the installation. Existing files are never replaced. */
export async function applyInstallPlan(inst: Instance, project: Project, plan: InstallPlan) {
  const taskId = `install-${inst.id}-${project.id}`;
  startTask({ id: taskId, title: `Installing ${project.title}`, instanceId: inst.id, cancellable: true, stage: "Downloading" });
  try {
    const p = P();
    const index = await readIndex(inst);
    const items: DownloadItem[] = [];
    const skipped: string[] = [];
    const kind = kindForProject(project.project_type);
    for (const pi of plan.installs) {
      const dir = contentDir(inst, pi.projectId === project.id ? kind : "mods");
      const dest = path(dir, pi.file.filename);
      if ((await p.exists(dest)) || (await p.exists(`${dest}${DISABLED}`))) {
        skipped.push(pi.file.filename);
      } else {
        items.push({ url: pi.file.url, path: dest, sha1: pi.file.hashes.sha1, sha512: pi.file.hashes.sha512, size: pi.file.size });
      }
      index.files[pi.file.filename] = {
        projectId: pi.projectId,
        versionId: pi.version.id,
        versionNumber: pi.version.version_number,
        title: pi.title,
        iconUrl: pi.projectId === project.id ? project.icon_url : undefined,
        clientSide: pi.projectId === project.id ? project.client_side : undefined,
        sha1: pi.file.hashes.sha1,
      };
    }
    await p.download(items, { taskId: `${taskId}:files`, verify: "hash" });
    await writeIndex(inst, index);
    endTask(taskId);
    const extra = plan.installs.length - 1;
    toast({
      kind: "success",
      title: `${project.title} installed`,
      message: [extra > 0 ? `Also installed ${extra} required ${extra === 1 ? "dependency" : "dependencies"}.` : "", skipped.length ? `Already present: ${skipped.join(", ")}.` : ""]
        .filter(Boolean)
        .join(" ") || undefined,
    });
  } catch (e) {
    endTask(taskId);
    throw e;
  }
}

export async function planProjectInstall(inst: Instance, project: Project, version?: Version): Promise<InstallPlan> {
  const index = await readIndex(inst);
  const installed = new Set(Object.values(index.files).map((f) => f.projectId));
  const loaders = project.project_type === "mod" ? modrinthLoadersFor(inst.loader) : [];
  if (project.project_type === "mod" && inst.loader === "vanilla") {
    throw new BloomError("vanilla_mods", "This installation is vanilla. Create or pick an installation with Fabric or Quilt to install mods.");
  }
  return planInstall(modrinth(), project, version, { target: { gameVersion: inst.gameVersion, loaders }, installedProjectIds: installed });
}

/** Replace a mod with a newer version: download the new file, then move the old one to the Recycle Bin. */
export async function updateContent(inst: Instance, kind: ContentKind, u: UpdateInfo) {
  const p = P();
  const file = primaryFile(u.version);
  const dir = contentDir(inst, kind);
  const dest = path(dir, file.filename);
  if (dest !== u.item.path && !(await p.exists(dest))) {
    await p.download([{ url: file.url, path: dest, sha1: file.hashes.sha1, sha512: file.hashes.sha512, size: file.size }], {
      taskId: `update-${inst.id}:file`,
      verify: "hash",
    });
    await p.trash(u.item.path);
    if (!u.item.enabled) await p.rename(dest, `${dest}${DISABLED}`);
  }
  const index = await readIndex(inst);
  const prev = index.files[u.item.baseName];
  delete index.files[u.item.baseName];
  index.files[file.filename] = { ...(prev ?? { projectId: u.version.project_id }), versionId: u.version.id, versionNumber: u.version.version_number, sha1: file.hashes.sha1 };
  await writeIndex(inst, index);
}

// ---------------------------------------------------------------------------
// Cloud sync helpers: Modrinth references in, files from Modrinth out
// ---------------------------------------------------------------------------

/** Modrinth-tracked files present in an installation, as portable references. */
export async function contentRefs(inst: Instance): Promise<SyncedContent[]> {
  const index = await readIndex(inst);
  const out: SyncedContent[] = [];
  for (const kind of ["mods", "resourcepacks", "shaderpacks"] as ContentKind[]) {
    const entries = await P().list(contentDir(inst, kind)).catch(() => []);
    for (const e of entries) {
      const enabled = !e.name.endsWith(DISABLED);
      const base = enabled ? e.name : e.name.slice(0, -DISABLED.length);
      const ref = index.files[base];
      if (ref?.versionId) out.push({ kind, projectId: ref.projectId, versionId: ref.versionId, fileName: base, enabled });
    }
  }
  return out;
}

/** Download referenced Modrinth versions into an installation. Existing files are kept as they are. */
export async function installRefs(inst: Instance, refs: SyncedContent[], taskId: string): Promise<number> {
  if (refs.length === 0) return 0;
  const p = P();
  const index = await readIndex(inst);
  const items: DownloadItem[] = [];
  const disable: string[] = [];
  for (const ref of refs) {
    let version: Version;
    try {
      version = await modrinth().version(ref.versionId);
    } catch {
      continue; // removed from Modrinth
    }
    const file = primaryFile(version);
    const dest = path(contentDir(inst, ref.kind), file.filename);
    if ((await p.exists(dest)) || (await p.exists(`${dest}${DISABLED}`))) continue;
    items.push({ url: file.url, path: dest, sha1: file.hashes.sha1, sha512: file.hashes.sha512, size: file.size });
    if (!ref.enabled) disable.push(dest);
    index.files[file.filename] = { projectId: version.project_id, versionId: version.id, versionNumber: version.version_number, sha1: file.hashes.sha1 };
  }
  if (items.length === 0) return 0;
  await p.download(items, { taskId, verify: "hash", concurrency: app.get().settings.downloadThreads });
  for (const d of disable) await p.rename(d, `${d}${DISABLED}`).catch(() => undefined);
  await writeIndex(inst, index);
  return items.length;
}

// ---------------------------------------------------------------------------
// Worlds
// ---------------------------------------------------------------------------

export interface WorldInfo {
  name: string;
  path: string;
  modifiedMs: number;
}

export async function listWorlds(inst: Instance): Promise<WorldInfo[]> {
  const entries = await P().list(path(instanceGameDir(inst), "saves"));
  return entries.filter((e) => e.isDir).map((e) => ({ name: e.name, path: e.path, modifiedMs: e.modifiedMs })).sort((a, b) => b.modifiedMs - a.modifiedMs);
}

// ---------------------------------------------------------------------------
// Modpacks (.mrpack)
// ---------------------------------------------------------------------------

async function installMrpackFile(mrpackPath: string, info: { icon?: string; projectId?: string; versionId?: string; version?: string }) {
  const p = P();
  const raw = await p.zipReadText(mrpackPath, "modrinth.index.json");
  if (!raw) throw new BloomError("invalid_modpack", "This file isn't a Modrinth modpack (no modrinth.index.json).");
  // Parse once to validate and learn the loader before creating anything.
  const probe = parseMrpackIndex(raw, "C:", p.sep);
  const loaderInfo = LOADERS.find((l) => l.id === probe.loader);
  if (!loaderInfo?.supported) {
    throw new BloomError("loader_unsupported", `${probe.name} uses ${loaderInfo?.name ?? probe.loader}, which this version of MOTION can't install yet.`);
  }
  let loaderVersion = probe.loaderVersion;
  if (probe.loader !== "vanilla" && !loaderVersion) loaderVersion = pickLoaderVersion(await loaderVersions(p, probe.loader, probe.gameVersion)).version;

  const inst = await createInstance({
    name: probe.name.slice(0, 64),
    gameVersion: probe.gameVersion,
    loader: probe.loader,
    loaderVersion,
    icon: info.icon,
    modpack: { projectId: info.projectId, versionId: info.versionId, name: probe.name, version: info.version ?? probe.versionId },
  });
  const taskId = `modpack-${inst.id}`;
  startTask({ id: taskId, title: `Installing ${probe.name}`, instanceId: inst.id, cancellable: true, stage: `Downloading ${probe.downloads.length} files` });
  try {
    const parsed = parseMrpackIndex(raw, instanceGameDir(inst), p.sep);
    await p.download(parsed.downloads, { taskId: `${taskId}:files`, verify: "hash", concurrency: app.get().settings.downloadThreads });
    updateTask(taskId, { stage: "Copying pack files", progress: null, detail: undefined });
    await p.zipExtract(mrpackPath, instanceGameDir(inst), { stripPrefix: "overrides/", overwrite: true });
    await p.zipExtract(mrpackPath, instanceGameDir(inst), { stripPrefix: "client-overrides/", overwrite: true });
    endTask(taskId);
    toast({ kind: "success", title: `${probe.name} is ready`, message: parsed.skipped.length ? `Skipped ${parsed.skipped.length} server-only files.` : undefined });
    await saveInstance(inst);
    return inst;
  } catch (e) {
    endTask(taskId);
    throw e;
  }
}

export async function installModpackVersion(project: Project, version: Version) {
  const p = P();
  const file = version.files.find((f) => f.filename.endsWith(".mrpack")) ?? primaryFile(version);
  const tmp = path(L().tmp, `${file.hashes.sha1}.mrpack`);
  const taskId = `mrpack-${project.id}`;
  startTask({ id: taskId, title: `Downloading ${project.title}`, cancellable: true, stage: "Downloading modpack" });
  try {
    await p.download([{ url: file.url, path: tmp, sha1: file.hashes.sha1, sha512: file.hashes.sha512, size: file.size }], { taskId: `${taskId}:pack`, verify: "hash" });
    endTask(taskId);
    return await installMrpackFile(tmp, { icon: project.icon_url ?? undefined, projectId: project.id, versionId: version.id, version: version.version_number });
  } catch (e) {
    endTask(taskId);
    throw e;
  }
}

export async function importModpackFile(sourcePath?: string) {
  let src = sourcePath;
  if (!src) {
    [src] = await native.pickFiles({ title: "Import a Modrinth modpack", filters: [["Modrinth modpack", ["mrpack"]]] });
  }
  if (!src) return;
  try {
    return await installMrpackFile(src, {});
  } catch (e) {
    reportError("Couldn't import the modpack", e);
  }
}
