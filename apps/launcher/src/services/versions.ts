import { loaderGameVersions, loaderVersions, type LoaderId, type LoaderVersion } from "../core/loaders/loaders";
import { VERSION_MANIFEST_URL, type ManifestVersion, type VersionManifest } from "../core/mojang/types";
import { getJson } from "../core/platform";
import { P, path, L } from "./app";

let manifest: VersionManifest | null = null;

/** Mojang's version list (cached on disk so the picker works offline). */
export async function minecraftVersions(): Promise<ManifestVersion[]> {
  if (manifest) return manifest.versions;
  const cache = path(L().meta, "version_manifest_v2.json");
  try {
    manifest = await getJson<VersionManifest>(P(), VERSION_MANIFEST_URL);
    await P().writeText(cache, JSON.stringify(manifest));
  } catch (e) {
    const text = await P().readText(cache);
    if (!text) throw e;
    manifest = JSON.parse(text) as VersionManifest;
  }
  return manifest.versions;
}

export async function latestRelease(): Promise<string | undefined> {
  await minecraftVersions();
  return manifest?.latest.release;
}

const supported = new Map<LoaderId, Set<string>>();
export async function loaderSupports(loader: LoaderId, gameVersion: string): Promise<boolean> {
  if (loader === "vanilla") return true;
  if (!supported.has(loader)) supported.set(loader, await loaderGameVersions(P(), loader));
  return supported.get(loader)!.has(gameVersion);
}

export async function availableLoaderVersions(loader: LoaderId, gameVersion: string): Promise<LoaderVersion[]> {
  return loaderVersions(P(), loader, gameVersion);
}
