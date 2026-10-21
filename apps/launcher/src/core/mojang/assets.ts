import type { DownloadItem } from "../platform";
import { join } from "../util/path";
import { RESOURCES_URL, type AssetIndex } from "./types";

export interface AssetPlan {
  downloads: DownloadItem[];
  /** Directory passed as ${game_assets} for legacy "virtual" indexes. */
  virtualDir?: string;
  totalSize: number;
}

/**
 * Asset objects are content-addressed under assets/objects/<hh>/<hash>.
 * Legacy indexes additionally need a by-name copy: "virtual" (1.6–1.7.2) under
 * assets/virtual/<index>, and "map_to_resources" (pre-1.6) inside the game
 * directory's resources/ folder. Those copies are just extra download targets.
 */
export function planAssets(
  index: AssetIndex,
  indexId: string,
  assetsDir: string,
  gameDir: string,
  sep: string,
): AssetPlan {
  const downloads: DownloadItem[] = [];
  let totalSize = 0;
  const seen = new Set<string>();
  const virtualDir = index.virtual ? join(sep, assetsDir, "virtual", indexId) : undefined;
  const resourcesDir = index.map_to_resources ? join(sep, gameDir, "resources") : undefined;

  for (const [name, obj] of Object.entries(index.objects)) {
    const hash = obj.hash.toLowerCase();
    const prefix = hash.slice(0, 2);
    const url = `${RESOURCES_URL}/${prefix}/${hash}`;
    if (!seen.has(hash)) {
      seen.add(hash);
      totalSize += obj.size;
      downloads.push({ url, path: join(sep, assetsDir, "objects", prefix, hash), sha1: hash, size: obj.size });
    }
    if (virtualDir) downloads.push({ url, path: join(sep, virtualDir, name), sha1: hash, size: obj.size });
    if (resourcesDir) downloads.push({ url, path: join(sep, resourcesDir, name), sha1: hash, size: obj.size });
  }
  return { downloads, virtualDir, totalSize };
}
