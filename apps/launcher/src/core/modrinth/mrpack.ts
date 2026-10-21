import { BloomError, type DownloadItem } from "../platform";
import type { LoaderId } from "../loaders/loaders";
import { isSafeRelative, join } from "../util/path";
import type { MrpackIndex } from "./types";

/** Download hosts the .mrpack format allows. */
const ALLOWED_HOSTS = new Set(["cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com"]);

export interface ParsedMrpack {
  name: string;
  versionId: string;
  summary?: string;
  gameVersion: string;
  loader: LoaderId;
  loaderVersion?: string;
  downloads: DownloadItem[];
  /** Files skipped because they are server-only. */
  skipped: string[];
}

export function parseMrpackIndex(raw: string, gameDir: string, sep: string): ParsedMrpack {
  let index: MrpackIndex;
  try {
    index = JSON.parse(raw) as MrpackIndex;
  } catch {
    throw new BloomError("invalid_modpack", "This modpack's index is not valid JSON.");
  }
  if (index.formatVersion !== 1) throw new BloomError("invalid_modpack", `Unsupported modpack format version ${index.formatVersion}.`);
  if (index.game !== "minecraft") throw new BloomError("invalid_modpack", "This modpack is not for Minecraft.");

  const deps = index.dependencies ?? {};
  const gameVersion = deps["minecraft"];
  if (!gameVersion) throw new BloomError("invalid_modpack", "The modpack doesn't say which Minecraft version it needs.");

  let loader: LoaderId = "vanilla";
  let loaderVersion: string | undefined;
  if (deps["fabric-loader"]) [loader, loaderVersion] = ["fabric", deps["fabric-loader"]];
  else if (deps["quilt-loader"]) [loader, loaderVersion] = ["quilt", deps["quilt-loader"]];
  else if (deps["neoforge"]) [loader, loaderVersion] = ["neoforge", deps["neoforge"]];
  else if (deps["forge"]) [loader, loaderVersion] = ["forge", deps["forge"]];

  const downloads: DownloadItem[] = [];
  const skipped: string[] = [];
  for (const f of index.files ?? []) {
    if (!isSafeRelative(f.path)) {
      throw new BloomError("invalid_modpack", `The modpack contains an unsafe file path: ${f.path}`);
    }
    if (f.env?.client === "unsupported") {
      skipped.push(f.path);
      continue;
    }
    const url = f.downloads.find((u) => {
      try {
        const parsed = new URL(u);
        return parsed.protocol === "https:" && ALLOWED_HOSTS.has(parsed.hostname);
      } catch {
        return false;
      }
    });
    if (!url) throw new BloomError("invalid_modpack", `No allowed download source for ${f.path}.`);
    downloads.push({
      url,
      path: join(sep, gameDir, ...f.path.split(/[\\/]/)),
      sha1: f.hashes.sha1,
      sha512: f.hashes.sha512,
      size: f.fileSize,
    });
  }
  return { name: index.name, versionId: index.versionId, summary: index.summary, gameVersion, loader, loaderVersion, downloads, skipped };
}
