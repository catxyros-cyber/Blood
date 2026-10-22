/**
 * Forge and NeoForge.
 *
 * Both loaders ship an installer jar whose `install_profile.json` describes
 * libraries to download and "processors" — small Java programs from the
 * loader's own Maven that patch the vanilla client (binary patches, mappings).
 * MOTION performs the same steps as the official installer: it downloads the
 * installer and libraries with checksum verification, runs the processors in a
 * separate Java process (never inside the launcher) and checks their outputs
 * against the hashes the installer declares.
 *
 * Supported formats:
 *   - modern installers (spec 0/1 with processors): Forge 1.13+ and all NeoForge
 *   - legacy installers (`versionInfo`): Forge 1.6–1.12.2
 */

import { mergeInherited } from "../mojang/version";
import { planLibraries } from "../mojang/libraries";
import type { RuleEnv } from "../mojang/rules";
import type { Library, VersionJson } from "../mojang/types";
import { BloomError, toBloomError, type DownloadItem, type Platform } from "../platform";
import { coordPath, parseCoord } from "../util/maven";
import { join } from "../util/path";
import { layout } from "../instances/model";
import type { LoaderVersion } from "./loaders";

export type ForgeLike = "forge" | "neoforge";

const FORGE_MAVEN = "https://maven.minecraftforge.net";
const NEOFORGE_MAVEN = "https://maven.neoforged.net/releases";
const FORGE_PROMOTIONS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

// ---------------------------------------------------------------------------
// Version lists
// ---------------------------------------------------------------------------

/** All `<version>` entries of a maven-metadata.xml, in file order (oldest first). */
export function parseMavenMetadata(xml: string): string[] {
  const out: string[] = [];
  const re = /<version>\s*([^<\s]+)\s*<\/version>/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(xml))) out.push(m[1]!);
  return out;
}

/**
 * The Minecraft version a NeoForge version targets. NeoForge numbers releases
 * after the game: 20.4.x → 1.20.4, 21.0.x → 1.21, 21.1.x → 1.21.1. From the
 * year-based Minecraft versions onwards (26.1 …) the Minecraft version is the
 * leading part of the NeoForge version (26.1.0.x → 26.1, 26.1.1.x → 26.1.1).
 */
export function neoforgeGameVersion(neo: string): string | null {
  const parts = neo.split(/[-+]/)[0]!.split(".");
  const major = Number(parts[0]);
  const minor = Number(parts[1]);
  if (!Number.isInteger(major) || !Number.isInteger(minor)) return null;
  if (major >= 25 && major < 40) {
    const patch = Number(parts[2] ?? 0);
    if (parts.length >= 4 && Number.isInteger(patch) && patch > 0) return `${major}.${minor}.${patch}`;
    return `${major}.${minor}`;
  }
  // 47.x is the 1.20.1 "neoforged/forge" fork (installed as Forge); nothing else predates 20.x.
  if (major < 20 || major >= 25) return null;
  return minor === 0 ? `1.${major}` : `1.${major}.${minor}`;
}

async function getText(p: Platform, url: string): Promise<string> {
  const res = await p.request({ method: "GET", url, timeoutMs: 30_000 });
  if (res.status < 200 || res.status >= 300) throw new BloomError(res.status === 404 ? "http_404" : "http_error", `${new URL(url).host} returned HTTP ${res.status}`);
  return res.body;
}

interface ForgeIndex {
  /** game version → loader versions, newest first */
  byGame: Map<string, LoaderVersion[]>;
}

const indexCache = new Map<ForgeLike, { at: number; index: ForgeIndex }>();

export async function forgeIndex(p: Platform, loader: ForgeLike): Promise<ForgeIndex> {
  try {
    return await loadForgeIndex(p, loader);
  } catch (e) {
    const err = toBloomError(e);
    if (["offline", "timeout", "network"].includes(err.code)) throw err;
    const name = loader === "forge" ? "Forge" : "NeoForge";
    throw new BloomError("loader_unavailable", `Couldn't load the list of ${name} versions (${err.message}). Try again in a moment.`);
  }
}

async function loadForgeIndex(p: Platform, loader: ForgeLike): Promise<ForgeIndex> {
  const hit = indexCache.get(loader);
  if (hit && Date.now() - hit.at < 10 * 60_000) return hit.index;
  const byGame = new Map<string, LoaderVersion[]>();
  const add = (game: string, v: LoaderVersion) => {
    const list = byGame.get(game) ?? [];
    list.push(v);
    byGame.set(game, list);
  };

  if (loader === "forge") {
    const xml = await getText(p, `${FORGE_MAVEN}/net/minecraftforge/forge/maven-metadata.xml`);
    let recommended: Record<string, string> = {};
    try {
      const promos = JSON.parse(await getText(p, FORGE_PROMOTIONS)) as { promos?: Record<string, string> };
      recommended = promos.promos ?? {};
    } catch {
      /* promotions are optional */
    }
    for (const full of parseMavenMetadata(xml).reverse()) {
      const dash = full.indexOf("-");
      if (dash < 0) continue;
      const game = full.slice(0, dash);
      const version = full.slice(dash + 1);
      add(game, {
        version,
        stable: true,
        recommended: recommended[`${game}-recommended`] === version.split("-")[0],
      });
    }
  } else {
    const xml = await getText(p, `${NEOFORGE_MAVEN}/net/neoforged/neoforge/maven-metadata.xml`);
    for (const version of parseMavenMetadata(xml).reverse()) {
      const game = neoforgeGameVersion(version);
      if (!game) continue;
      add(game, { version, stable: !/(alpha|beta|pre|rc)/i.test(version) });
    }
  }
  const index = { byGame };
  indexCache.set(loader, { at: Date.now(), index });
  return index;
}

export async function forgeGameVersions(p: Platform, loader: ForgeLike): Promise<Set<string>> {
  return new Set((await forgeIndex(p, loader)).byGame.keys());
}

export async function forgeLoaderVersions(p: Platform, loader: ForgeLike, gameVersion: string): Promise<LoaderVersion[]> {
  const list = (await forgeIndex(p, loader)).byGame.get(gameVersion) ?? [];
  if (list.length === 0) {
    throw new BloomError("loader_unavailable", `No ${loader === "forge" ? "Forge" : "NeoForge"} versions exist for Minecraft ${gameVersion}`);
  }
  return list;
}

// ---------------------------------------------------------------------------
// Installer
// ---------------------------------------------------------------------------

export function installerCoord(loader: ForgeLike, gameVersion: string, loaderVersion: string): { coord: string; url: string } {
  if (loader === "forge") {
    const coord = `net.minecraftforge:forge:${gameVersion}-${loaderVersion}:installer`;
    return { coord, url: `${FORGE_MAVEN}/${coordPath(coord)}` };
  }
  const coord = `net.neoforged:neoforge:${loaderVersion}:installer`;
  return { coord, url: `${NEOFORGE_MAVEN}/${coordPath(coord)}` };
}

interface Processor {
  sides?: string[];
  jar: string;
  classpath: string[];
  args: string[];
  outputs?: Record<string, string>;
}

interface ModernProfile {
  spec?: number;
  version: string;
  json: string;
  minecraft: string;
  data: Record<string, { client: string; server: string }>;
  processors: Processor[];
  libraries: Library[];
}

interface LegacyProfile {
  install: { path: string; filePath: string; minecraft: string; target?: string };
  versionInfo: Omit<VersionJson, "libraries"> & { libraries: (Library & { clientreq?: boolean; serverreq?: boolean })[] };
}

export interface ForgeInstallOptions {
  loader: ForgeLike;
  gameVersion: string;
  loaderVersion: string;
  vanilla: VersionJson;
  /** Path of the verified vanilla client jar (versions/<mc>/<mc>.jar). */
  vanillaJar: string;
  /** Java used to run installer processors. */
  java: string;
  env: RuleEnv;
  taskId: string;
  repair?: boolean;
  onStage?: (label: string) => void;
}

export interface ForgeInstallResult {
  /** The loader's own version JSON (inheritsFrom the vanilla version). */
  profile: VersionJson;
  /** Merged over vanilla, ready to launch. */
  merged: VersionJson;
}

/** Old Forge repositories used plain http and a since-retired host. */
function fixLegacyUrl(url: string): string {
  return url
    .replace(/^http:\/\//, "https://")
    .replace("https://files.minecraftforge.net/maven", FORGE_MAVEN)
    .replace("https://maven.minecraftforge.net/maven", FORGE_MAVEN);
}

async function mavenSha1(p: Platform, url: string): Promise<string | undefined> {
  try {
    const text = (await getText(p, `${url}.sha1`)).trim().split(/\s+/)[0];
    return text && /^[0-9a-f]{40}$/i.test(text) ? text.toLowerCase() : undefined;
  } catch {
    return undefined;
  }
}

function mainClassFromManifest(manifest: string | null): string | null {
  if (!manifest) return null;
  const m = /^Main-Class:\s*(.+?)\s*$/m.exec(manifest.replace(/\r/g, ""));
  return m ? m[1]! : null;
}

/** Resolve a value from install_profile `data` or a processor argument. */
export function resolveToken(
  value: string,
  ctx: { data: Record<string, string>; libraryPath: (coord: string) => string; extracted: (rel: string) => string },
): string {
  if (value.startsWith("[") && value.endsWith("]")) return ctx.libraryPath(value.slice(1, -1));
  if (value.startsWith("'") && value.endsWith("'")) return value.slice(1, -1);
  if (value.startsWith("{") && value.endsWith("}")) {
    const key = value.slice(1, -1);
    const v = ctx.data[key];
    if (v === undefined) throw new BloomError("forge_install_failed", `The installer refers to unknown data "${key}".`);
    return v;
  }
  // Embedded references like "--output={MAPPINGS}" are rare but handled.
  return value.replace(/\{([A-Z0-9_]+)\}/g, (all, key: string) => ctx.data[key] ?? all);
}

export async function installForgeLike(p: Platform, o: ForgeInstallOptions): Promise<ForgeInstallResult> {
  const L = layout(p.dataDir, p.sep);
  const libPath = (coord: string) => join(p.sep, L.libraries, coordPath(coord));
  const name = o.loader === "forge" ? "Forge" : "NeoForge";

  // 1. Installer jar (verified against the Maven .sha1 when available).
  o.onStage?.(`Downloading the ${name} installer`);
  const inst = installerCoord(o.loader, o.gameVersion, o.loaderVersion);
  const installerPath = libPath(inst.coord);
  const sha1 = await mavenSha1(p, inst.url);
  await p.download([{ url: inst.url, path: installerPath, sha1 }], { taskId: `${o.taskId}`, verify: o.repair ? "hash" : "size" });

  const rawProfile = await p.zipReadText(installerPath, "install_profile.json");
  if (!rawProfile) throw new BloomError("forge_install_failed", `The ${name} installer is damaged (no install_profile.json). Use Repair to download it again.`);
  let parsed: ModernProfile | LegacyProfile;
  try {
    parsed = JSON.parse(rawProfile) as ModernProfile | LegacyProfile;
  } catch {
    throw new BloomError("forge_install_failed", `The ${name} installer's profile couldn't be read.`);
  }

  if ("versionInfo" in parsed) return installLegacy(p, o, parsed, installerPath);
  return installModern(p, o, parsed, installerPath, libPath);
}

async function installLegacy(p: Platform, o: ForgeInstallOptions, profile: LegacyProfile, installerPath: string): Promise<ForgeInstallResult> {
  const L = layout(p.dataDir, p.sep);
  const info = profile.versionInfo;
  const universalCoord = parseCoord(profile.install.path);
  const isUniversal = (name: string) => {
    try {
      const c = parseCoord(name);
      return c.group === universalCoord.group && c.artifact === universalCoord.artifact;
    } catch {
      return false;
    }
  };
  const version: VersionJson = { ...info, libraries: [] };
  version.libraries = info.libraries
    // Like the official launcher, keep everything except libraries explicitly marked as not needed on the client.
    .filter((l) => l.clientreq !== false)
    .map((l) => {
      const out: Library = { name: l.name, rules: l.rules, natives: l.natives, extract: l.extract, downloads: l.downloads };
      // The universal jar comes from the installer: an empty URL marks it as locally provided.
      if (isUniversal(l.name)) out.downloads = { artifact: { path: coordPath(profile.install.path), url: "" } };
      else if (l.url) out.url = fixLegacyUrl(l.url);
      return out;
    });

  // The universal jar ships inside the installer.
  o.onStage?.("Unpacking Forge");
  const tmp = join(p.sep, L.tmp, `forge-${o.gameVersion}-${o.loaderVersion}`);
  await p.removeInternal(tmp);
  await p.zipExtract(installerPath, tmp, { exclude: ["maven/", "META-INF/"], overwrite: true });
  const universalSrc = join(p.sep, tmp, profile.install.filePath);
  const universalDest = join(p.sep, L.libraries, coordPath(profile.install.path));
  await p.copy(universalSrc, universalDest, true);
  await p.removeInternal(tmp).catch(() => undefined);

  o.onStage?.("Downloading Forge libraries");
  const plan = planLibraries(version, L.libraries, p.sep, o.env);
  await p.download(plan.downloads, { taskId: o.taskId, verify: o.repair ? "hash" : "size", concurrency: 12 });

  const id = version.id;
  await p.writeText(join(p.sep, L.versions, id, `${id}.json`), JSON.stringify(version));
  return finish(p, o, version);
}

async function installModern(
  p: Platform,
  o: ForgeInstallOptions,
  profile: ModernProfile,
  installerPath: string,
  libPath: (coord: string) => string,
): Promise<ForgeInstallResult> {
  const L = layout(p.dataDir, p.sep);
  const name = o.loader === "forge" ? "Forge" : "NeoForge";

  const versionRaw = await p.zipReadText(installerPath, profile.json.replace(/^\//, ""));
  if (!versionRaw) throw new BloomError("forge_install_failed", `The ${name} installer is missing its version file.`);
  const version = JSON.parse(versionRaw) as VersionJson;
  const id = version.id;
  const versionDir = join(p.sep, L.versions, id);
  const marker = join(p.sep, versionDir, ".bloom-installed");
  const installerSha = await p.hash(installerPath, "sha1");

  // Artifacts bundled in the installer's maven/ folder (never overwrite verified files).
  await p.zipExtract(installerPath, L.libraries, { stripPrefix: "maven/", overwrite: false });

  // Libraries for both the processors and the game.
  o.onStage?.(`Downloading ${name} libraries`);
  const libs = planLibraries({ id, mainClass: "", libraries: [...profile.libraries, ...version.libraries] }, L.libraries, p.sep, o.env);
  await p.download(libs.downloads, { taskId: o.taskId, verify: o.repair ? "hash" : "size", concurrency: 12 });

  const alreadyInstalled = !o.repair && (await p.readText(marker))?.trim() === installerSha;
  if (!alreadyInstalled) {
    // Files referenced as "/data/…" live in the installer itself.
    const tmp = join(p.sep, L.tmp, `${o.loader}-${id}`);
    await p.removeInternal(tmp);
    await p.zipExtract(installerPath, tmp, { exclude: ["maven/"], overwrite: true });

    const data: Record<string, string> = {
      SIDE: "client",
      MINECRAFT_JAR: o.vanillaJar,
      MINECRAFT_VERSION: o.gameVersion,
      ROOT: p.dataDir,
      INSTALLER: installerPath,
      LIBRARY_DIR: L.libraries,
    };
    const extracted = (rel: string) => join(p.sep, tmp, rel.replace(/^\//, ""));
    const baseCtx = { data, libraryPath: libPath, extracted };
    for (const [key, value] of Object.entries(profile.data ?? {})) {
      const v = value.client;
      data[key] = v.startsWith("/") ? extracted(v) : resolveToken(v, baseCtx);
    }

    const processors = (profile.processors ?? []).filter((pr) => !pr.sides || pr.sides.includes("client"));
    let n = 0;
    for (const pr of processors) {
      n++;
      const args = pr.args.map((a) => resolveToken(a, baseCtx));

      // MOTION downloads the official mappings itself (verified), instead of letting the tool fetch them.
      const task = args[args.indexOf("--task") + 1];
      if (args.includes("--task") && task === "DOWNLOAD_MOJMAPS") {
        const mappings = o.vanilla.downloads?.client_mappings;
        const out = args[args.indexOf("--output") + 1];
        if (mappings && out) {
          o.onStage?.(`Downloading Mojang mappings (${n}/${processors.length})`);
          await p.download([{ url: mappings.url, path: out, sha1: mappings.sha1, size: mappings.size }], { taskId: o.taskId, verify: "hash" });
          continue;
        }
      }

      // Skip a processor whose declared outputs are already present and correct.
      const outputs = Object.entries(pr.outputs ?? {}).map(([k, v]) => ({ path: resolveToken(k, baseCtx), sha1: resolveToken(v, baseCtx) }));
      if (outputs.length > 0) {
        let ok = true;
        for (const out of outputs) {
          if (!(await p.exists(out.path)) || (await p.hash(out.path, "sha1")).toLowerCase() !== out.sha1.toLowerCase()) {
            ok = false;
            break;
          }
        }
        if (ok) continue;
      }

      const jar = libPath(pr.jar);
      const mainClass = mainClassFromManifest(await p.zipReadText(jar, "META-INF/MANIFEST.MF"));
      if (!mainClass) throw new BloomError("forge_install_failed", `${pr.jar} has no Main-Class; the ${name} installer may be damaged.`);
      o.onStage?.(`Patching Minecraft for ${name} (${n}/${processors.length})`);
      const result = await p.runJavaTool({
        java: o.java,
        classpath: [jar, ...pr.classpath.map(libPath)],
        mainClass,
        args,
        cwd: p.dataDir,
        taskId: `${o.taskId}:proc`,
      });
      if (result.code !== 0) {
        throw new BloomError(
          "forge_install_failed",
          `${name} installation step ${n} of ${processors.length} failed (exit code ${result.code ?? "?"}).${result.output ? `\n${result.output.split("\n").slice(-8).join("\n")}` : ""}`,
        );
      }
      for (const out of outputs) {
        const actual = (await p.hash(out.path, "sha1")).toLowerCase();
        if (actual !== out.sha1.toLowerCase()) {
          throw new BloomError("hash_mismatch", `${name} produced an unexpected file (${out.path}). Try Repair; if it keeps happening, this ${name} version may not support your setup.`);
        }
      }
    }
    await p.removeInternal(tmp).catch(() => undefined);
    await p.writeText(marker, installerSha);
  }

  await p.writeText(join(p.sep, versionDir, `${id}.json`), JSON.stringify(version));
  return finish(p, o, version);
}

/**
 * The official launcher runs an inherited version from versions/<id>/<id>.jar
 * (a copy of the vanilla jar). Forge 1.17+ relies on this name to keep the
 * vanilla jar off its module layer, so MOTION does the same.
 */
async function finish(p: Platform, o: ForgeInstallOptions, profile: VersionJson): Promise<ForgeInstallResult> {
  const L = layout(p.dataDir, p.sep);
  const id = profile.id;
  const jarCopy = join(p.sep, L.versions, id, `${id}.jar`);
  if (o.repair || !(await p.exists(jarCopy))) await p.copy(o.vanillaJar, jarCopy, true);
  const merged = mergeInherited(profile, o.vanilla);
  merged.jar = id;
  return { profile, merged };
}

/** Plan a verified download of the vanilla client jar (needed before processors run). */
export function vanillaJarDownload(vanilla: VersionJson, path: string): DownloadItem {
  const client = vanilla.downloads?.client;
  if (!client) throw new BloomError("invalid_json", `Minecraft ${vanilla.id} has no client download.`);
  return { url: client.url, path, sha1: client.sha1, size: client.size };
}
