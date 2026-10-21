import { BloomError, getJson, type Platform } from "../platform";
import type { VersionJson } from "../mojang/types";
import { forgeGameVersions, forgeLoaderVersions } from "./forge";

export type LoaderId = "vanilla" | "fabric" | "quilt" | "neoforge" | "forge";

export interface LoaderInfo {
  id: LoaderId;
  name: string;
  /** Whether BLOOD can install this loader in the current release. */
  supported: boolean;
  note?: string;
}

export const LOADERS: LoaderInfo[] = [
  { id: "vanilla", name: "Vanilla", supported: true },
  { id: "fabric", name: "Fabric", supported: true },
  { id: "quilt", name: "Quilt", supported: true },
  {
    id: "neoforge",
    name: "NeoForge",
    supported: true,
    note: "The first launch runs NeoForge's official installer steps, which takes a minute.",
  },
  {
    id: "forge",
    name: "Forge",
    supported: true,
    note: "Forge 1.6.4 and newer. The first launch runs Forge's official installer steps, which takes a minute.",
  },
];

export interface LoaderVersion {
  version: string;
  stable: boolean;
  /** Forge marks one build per Minecraft version as "recommended". */
  recommended?: boolean;
}

export function isForgeLike(loader: LoaderId): loader is "forge" | "neoforge" {
  return loader === "forge" || loader === "neoforge";
}

const FABRIC_META = "https://meta.fabricmc.net/v2";
const QUILT_META = "https://meta.quiltmc.org/v3";

function metaBase(loader: LoaderId): string {
  if (loader === "fabric") return FABRIC_META;
  if (loader === "quilt") return QUILT_META;
  throw new BloomError("loader_unsupported", `${loader} is not supported yet`);
}

/** Game versions the loader supports. */
export async function loaderGameVersions(p: Platform, loader: LoaderId): Promise<Set<string>> {
  if (isForgeLike(loader)) return forgeGameVersions(p, loader);
  const list = await getJson<{ version: string }[]>(p, `${metaBase(loader)}/versions/game`);
  return new Set(list.map((v) => v.version));
}

/** Loader versions available for a game version, newest first. */
export async function loaderVersions(p: Platform, loader: LoaderId, gameVersion: string): Promise<LoaderVersion[]> {
  if (isForgeLike(loader)) return forgeLoaderVersions(p, loader, gameVersion);
  const url = `${metaBase(loader)}/versions/loader/${encodeURIComponent(gameVersion)}`;
  const list = await getJson<{ loader: { version: string; stable?: boolean } }[]>(p, url);
  const versions = list.map((e) => ({
    version: e.loader.version,
    // Quilt's meta has no `stable` flag; pre-releases are tagged in the version string.
    stable: e.loader.stable ?? !/-(beta|alpha|pre|rc)/i.test(e.loader.version),
  }));
  if (versions.length === 0) {
    throw new BloomError("loader_unavailable", `No ${loader} versions exist for Minecraft ${gameVersion}`);
  }
  return versions;
}

/** The default choice: Forge's recommended build, else the newest stable, else the newest. */
export function pickLoaderVersion(versions: LoaderVersion[]): LoaderVersion {
  return versions.find((v) => v.recommended) ?? versions.find((v) => v.stable) ?? versions[0]!;
}

export async function loaderProfile(
  p: Platform,
  loader: LoaderId,
  gameVersion: string,
  loaderVersion: string,
): Promise<VersionJson> {
  const url = `${metaBase(loader)}/versions/loader/${encodeURIComponent(gameVersion)}/${encodeURIComponent(loaderVersion)}/profile/json`;
  const profile = await getJson<VersionJson>(p, url);
  if (!profile.mainClass || !Array.isArray(profile.libraries)) {
    throw new BloomError("invalid_json", `Unexpected ${loader} profile format`);
  }
  return profile;
}

/** Version id BLOOD stores a loader profile under. */
export function loaderProfileId(loader: LoaderId, gameVersion: string, loaderVersion: string): string {
  return `${loader}-loader-${loaderVersion}-${gameVersion}`;
}

/** Modrinth loader tags that are compatible with an installation's loader. */
export function modrinthLoadersFor(loader: LoaderId): string[] {
  switch (loader) {
    case "quilt":
      return ["quilt", "fabric"]; // Quilt runs most Fabric mods
    case "fabric":
      return ["fabric"];
    case "neoforge":
      return ["neoforge"];
    case "forge":
      return ["forge"];
    default:
      return [];
  }
}
