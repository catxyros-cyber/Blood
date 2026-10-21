/**
 * Ensures everything an installation needs to start is present and verified:
 * version metadata, the loader profile, a Java runtime, the client jar,
 * libraries, the log4j config, assets and natives. Safe to call before every
 * launch — files that already exist are skipped (or re-hashed with `repair`).
 */

import { isForgeLike, loaderProfile, loaderProfileId, type LoaderId } from "../loaders/loaders";
import { installForgeLike, vanillaJarDownload } from "../loaders/forge";
import { planAssets } from "../mojang/assets";
import { javaExecutable, pickRuntime, planRuntime } from "../mojang/java";
import { planLibraries } from "../mojang/libraries";
import type { RuleEnv } from "../mojang/rules";
import {
  JAVA_RUNTIME_INDEX_URL,
  VERSION_MANIFEST_URL,
  type AssetIndex,
  type JavaManifest,
  type JavaRuntimeIndex,
  type VersionJson,
  type VersionManifest,
} from "../mojang/types";
import { clientJarId, javaComponentFor, mergeInherited } from "../mojang/version";
import { BloomError, getJson, toBloomError, type DownloadItem, type Platform } from "../platform";
import { join } from "../util/path";
import { gameDirOf, layout, type Instance } from "../instances/model";

export type InstallStage = "metadata" | "loader" | "java" | "libraries" | "assets" | "natives";

export interface InstallProgress {
  stage(stage: InstallStage, label: string, taskId?: string): void;
}

export interface PreparedLaunch {
  version: VersionJson;
  classpath: string[];
  nativesDir: string;
  gameDir: string;
  assetsRoot: string;
  assetsIndexName: string;
  virtualAssetsDir?: string;
  loggingConfigPath?: string;
  /** Java from Mojang's runtime (when no custom Java is set). */
  managedJava?: { path: string; major: number };
}

function isOfflineError(e: unknown): boolean {
  const code = toBloomError(e).code;
  return code === "offline" || code === "timeout" || code === "network" || code === "modrinth_down";
}

/** Fetch JSON, cache it on disk, and fall back to the cache when offline. */
async function cachedJson<T>(p: Platform, url: string, cachePath: string): Promise<T> {
  try {
    const value = await getJson<T>(p, url);
    await p.writeText(cachePath, JSON.stringify(value));
    return value;
  } catch (e) {
    if (!isOfflineError(e)) throw e;
    const cached = await p.readText(cachePath);
    if (cached) return JSON.parse(cached) as T;
    throw new BloomError("offline", "You're offline and this hasn't been downloaded before. Connect to the internet and try again.");
  }
}

/** Download a JSON file with a known hash (or reuse a verified copy) and parse it. */
async function verifiedJson<T>(p: Platform, item: DownloadItem, taskId: string, repair: boolean): Promise<T> {
  try {
    await p.download([item], { taskId, verify: repair ? "hash" : "size" });
  } catch (e) {
    if (!isOfflineError(e) || !(await p.exists(item.path))) throw e;
  }
  const text = await p.readText(item.path);
  if (!text) throw new BloomError("corrupt_file", `Missing ${item.path}`);
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new BloomError("corrupt_file", `${item.path} is corrupted. Use Repair to download it again.`);
  }
}

export async function resolveVersionJson(
  p: Platform,
  gameVersion: string,
  loader: LoaderId,
  loaderVersion: string | undefined,
  opts: { repair?: boolean; taskId: string },
): Promise<VersionJson> {
  const L = layout(p.dataDir, p.sep);
  const manifest = await cachedJson<VersionManifest>(p, VERSION_MANIFEST_URL, join(p.sep, L.meta, "version_manifest_v2.json"));
  const entry = manifest.versions.find((v) => v.id === gameVersion);
  if (!entry) throw new BloomError("unknown_version", `Minecraft ${gameVersion} isn't in Mojang's version list.`);

  const vanilla = await verifiedJson<VersionJson>(
    p,
    { url: entry.url, path: join(p.sep, L.versions, gameVersion, `${gameVersion}.json`), sha1: entry.sha1 },
    opts.taskId,
    Boolean(opts.repair),
  );
  if (loader === "vanilla") return vanilla;
  if (loader !== "fabric" && loader !== "quilt") {
    // Forge/NeoForge need a Java runtime and the client jar first; see prepareInstance.
    throw new BloomError("loader_unsupported", `${loader} profiles are produced by its installer, not fetched.`);
  }
  if (!loaderVersion) throw new BloomError("loader_unavailable", "No loader version selected for this installation.");

  const profileId = loaderProfileId(loader, gameVersion, loaderVersion);
  const profilePath = join(p.sep, L.versions, profileId, `${profileId}.json`);
  let profile: VersionJson;
  const cached = opts.repair ? null : await p.readText(profilePath);
  if (cached) {
    profile = JSON.parse(cached) as VersionJson;
  } else {
    try {
      profile = await loaderProfile(p, loader, gameVersion, loaderVersion);
      await p.writeText(profilePath, JSON.stringify(profile));
    } catch (e) {
      const fallback = await p.readText(profilePath);
      if (!fallback || !isOfflineError(e)) throw e;
      profile = JSON.parse(fallback) as VersionJson;
    }
  }
  return mergeInherited(profile, vanilla);
}

export interface PrepareOptions {
  env: RuleEnv;
  repair?: boolean;
  /** Skip the managed Java runtime (the instance uses a custom Java path). */
  customJava?: boolean;
  progress: InstallProgress;
  /** Unique prefix for download task ids (used to cancel). */
  taskPrefix: string;
}

export async function ensureJavaRuntime(
  p: Platform,
  component: string,
  opts: { repair?: boolean; taskId: string; env: RuleEnv },
): Promise<string> {
  const L = layout(p.dataDir, p.sep);
  const dir = join(p.sep, L.runtimes, component);
  const marker = join(p.sep, dir, ".bloom-complete");
  const exe = javaExecutable(dir, p.os, p.sep);
  if (!opts.repair && (await p.exists(marker)) && (await p.exists(exe))) return exe;

  const index = await cachedJson<JavaRuntimeIndex>(p, JAVA_RUNTIME_INDEX_URL, join(p.sep, L.meta, "java-runtimes.json"));
  const runtime = pickRuntime(index, opts.env.os, opts.env.arch, component);
  const manifest = await verifiedJson<JavaManifest>(
    p,
    { url: runtime.manifest.url, path: join(p.sep, L.meta, "java", `${component}-${runtime.manifest.sha1}.json`), sha1: runtime.manifest.sha1, size: runtime.manifest.size },
    opts.taskId,
    Boolean(opts.repair),
  );
  await p.download(planRuntime(manifest, dir, p.sep), { taskId: opts.taskId, verify: opts.repair ? "hash" : "size", concurrency: 12 });
  await p.writeText(marker, runtime.version.name);
  return exe;
}

export async function prepareInstance(p: Platform, inst: Instance, opts: PrepareOptions): Promise<PreparedLaunch> {
  const L = layout(p.dataDir, p.sep);
  const { progress, env } = opts;
  const t = (s: string) => `${opts.taskPrefix}:${s}`;

  progress.stage("metadata", `Fetching Minecraft ${inst.gameVersion}`, t("meta"));
  let version: VersionJson;
  let managedJava: PreparedLaunch["managedJava"];

  if (isForgeLike(inst.loader)) {
    if (!inst.loaderVersion) throw new BloomError("loader_unavailable", "No loader version selected for this installation.");
    const vanilla = await resolveVersionJson(p, inst.gameVersion, "vanilla", undefined, { repair: opts.repair, taskId: t("meta") });
    // The installer's processors need Java and the vanilla client jar before anything else.
    const { component, majorVersion } = javaComponentFor(vanilla);
    progress.stage("java", `Installing Java ${majorVersion}`, t("java"));
    const javaPath = await ensureJavaRuntime(p, component, { repair: opts.repair, taskId: t("java"), env });
    if (!opts.customJava) managedJava = { path: javaPath, major: majorVersion };
    const vanillaJar = join(p.sep, L.versions, inst.gameVersion, `${inst.gameVersion}.jar`);
    await p.download([vanillaJarDownload(vanilla, vanillaJar)], { taskId: t("libs"), verify: opts.repair ? "hash" : "size" });
    const label = inst.loader === "forge" ? "Forge" : "NeoForge";
    progress.stage("loader", `Installing ${label} ${inst.loaderVersion}`, t("loader"));
    const result = await installForgeLike(p, {
      loader: inst.loader,
      gameVersion: inst.gameVersion,
      loaderVersion: inst.loaderVersion,
      vanilla,
      vanillaJar,
      java: javaPath,
      env,
      taskId: t("loader"),
      repair: opts.repair,
      onStage: (l) => progress.stage("loader", l, t("loader")),
    });
    version = result.merged;
  } else {
    if (inst.loader !== "vanilla") progress.stage("loader", `Preparing ${inst.loader} ${inst.loaderVersion ?? ""}`, t("meta"));
    version = await resolveVersionJson(p, inst.gameVersion, inst.loader, inst.loaderVersion, {
      repair: opts.repair,
      taskId: t("meta"),
    });
    if (!opts.customJava) {
      const { component, majorVersion } = javaComponentFor(version);
      progress.stage("java", `Installing Java ${majorVersion}`, t("java"));
      const path = await ensureJavaRuntime(p, component, { repair: opts.repair, taskId: t("java"), env });
      managedJava = { path, major: majorVersion };
    }
  }

  // Client jar, libraries and log config in one verified batch.
  progress.stage("libraries", "Downloading game files", t("libs"));
  const libs = planLibraries(version, L.libraries, p.sep, env);
  const jarId = clientJarId(version);
  const client = version.downloads?.client;
  if (!client) throw new BloomError("invalid_json", `Minecraft ${inst.gameVersion} has no client download.`);
  const clientJar = join(p.sep, L.versions, jarId, `${jarId}.jar`);
  const batch: DownloadItem[] = [{ url: client.url, path: clientJar, sha1: client.sha1, size: client.size }, ...libs.downloads];
  let loggingConfigPath: string | undefined;
  const logging = version.logging?.client;
  if (logging) {
    loggingConfigPath = join(p.sep, L.assets, "log_configs", logging.file.id);
    batch.push({ url: logging.file.url, path: loggingConfigPath, sha1: logging.file.sha1, size: logging.file.size });
  }
  await p.download(batch, { taskId: t("libs"), verify: opts.repair ? "hash" : "size", concurrency: 16 });

  // Assets
  const gameDir = gameDirOf(L, p.sep, inst);
  const assetIndexRef = version.assetIndex;
  if (!assetIndexRef) throw new BloomError("invalid_json", "This version has no asset index.");
  progress.stage("assets", "Downloading assets", t("assets"));
  const index = await verifiedJson<AssetIndex>(
    p,
    {
      url: assetIndexRef.url,
      path: join(p.sep, L.assets, "indexes", `${assetIndexRef.id}.json`),
      sha1: assetIndexRef.sha1,
      size: assetIndexRef.size,
    },
    t("assets"),
    Boolean(opts.repair),
  );
  const assets = planAssets(index, assetIndexRef.id, L.assets, gameDir, p.sep);
  await p.download(assets.downloads, { taskId: t("assets"), verify: opts.repair ? "hash" : "size", concurrency: 24 });

  // Natives (old-style jars only; newer LWJGL loads them from the classpath)
  const nativesDir = join(p.sep, L.natives, inst.id);
  if (libs.natives.length > 0) {
    progress.stage("natives", "Extracting native libraries", t("natives"));
    await p.removeInternal(nativesDir);
    await p.mkdir(nativesDir);
    for (const n of libs.natives) {
      await p.zipExtract(n.path, nativesDir, { exclude: n.exclude, overwrite: true });
    }
  } else {
    await p.mkdir(nativesDir);
  }
  await p.mkdir(gameDir);

  return {
    version,
    classpath: [...libs.classpath, clientJar],
    nativesDir,
    gameDir,
    assetsRoot: L.assets,
    assetsIndexName: assetIndexRef.id,
    virtualAssetsDir: assets.virtualDir,
    loggingConfigPath,
    managedJava,
  };
}
