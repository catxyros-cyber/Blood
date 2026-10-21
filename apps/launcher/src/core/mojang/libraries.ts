import type { DownloadItem } from "../platform";
import { coordPath, parseCoord } from "../util/maven";
import { join } from "../util/path";
import { rulesAllow, type RuleEnv } from "./rules";
import { LIBRARIES_URL, type Library, type VersionJson } from "./types";

export interface LibraryPlan {
  downloads: DownloadItem[];
  /** Absolute jar paths in classpath order (client jar not included). */
  classpath: string[];
  /** Old-style native jars to extract into the natives directory. */
  natives: { path: string; exclude: string[] }[];
}

const ARCH_SUFFIX: Record<RuleEnv["arch"], string> = { x64: "", x86: "-x86", arm64: "-arm64" };

/**
 * New-style (1.19+) natives are ordinary classpath libraries with a
 * `natives-<os>[-arch]` classifier. Keep only the one matching our arch.
 */
function nativeClassifierMatches(classifier: string | undefined, env: RuleEnv): boolean {
  if (!classifier || !classifier.startsWith("natives-")) return true;
  const rest = classifier.slice("natives-".length);
  const osNames = env.os === "osx" ? ["macos", "osx"] : [env.os];
  for (const os of osNames) {
    if (rest === os || rest.startsWith(`${os}-`)) {
      const suffix = rest.slice(os.length);
      return suffix === ARCH_SUFFIX[env.arch] || (env.arch === "x64" && (suffix === "-x86_64" || suffix === "-x64"));
    }
  }
  // A natives classifier for another OS.
  return false;
}

function artifactFor(lib: Library, path: string): { url: string; sha1?: string; size?: number } | null {
  const a = lib.downloads?.artifact;
  if (a) {
    // An empty URL means the file is generated locally (e.g. by a loader installer).
    if (!a.url) return null;
    return { url: a.url, sha1: a.sha1, size: a.size };
  }
  const base = lib.url ?? LIBRARIES_URL;
  return { url: `${base.endsWith("/") ? base : `${base}/`}${path}`, sha1: lib.sha1, size: lib.size };
}

export function planLibraries(version: VersionJson, librariesDir: string, sep: string, env: RuleEnv): LibraryPlan {
  const downloads: DownloadItem[] = [];
  const classpath: string[] = [];
  const natives: LibraryPlan["natives"] = [];
  const seenPaths = new Set<string>();

  for (const lib of version.libraries) {
    if (!rulesAllow(lib.rules, env)) continue;
    const coord = parseCoord(lib.name);

    // Old-style natives (pre-1.19): a classifier chosen per OS, extracted before launch.
    if (lib.natives) {
      const key = lib.natives[env.os];
      if (key) {
        const classifier = key.replace("${arch}", env.arch === "x86" ? "32" : "64");
        const art = lib.downloads?.classifiers?.[classifier];
        const rel = art?.path ?? coordPath({ ...coord, classifier });
        const abs = join(sep, librariesDir, rel);
        if (art?.url) downloads.push({ url: art.url, path: abs, sha1: art.sha1, size: art.size });
        else if (!art) {
          const base = lib.url ?? LIBRARIES_URL;
          downloads.push({ url: `${base.endsWith("/") ? base : `${base}/`}${rel}` , path: abs });
        }
        natives.push({ path: abs, exclude: lib.extract?.exclude ?? ["META-INF/"] });
      }
      // Libraries with `natives` sometimes also carry a normal artifact (e.g. lwjgl-platform has none).
      if (!lib.downloads?.artifact) continue;
    }

    if (!nativeClassifierMatches(coord.classifier, env)) continue;

    const rel = lib.downloads?.artifact?.path ?? coordPath(coord);
    const abs = join(sep, librariesDir, rel);
    if (seenPaths.has(abs)) continue;
    seenPaths.add(abs);
    const art = artifactFor(lib, rel);
    if (art) downloads.push({ url: art.url, path: abs, sha1: art.sha1, size: art.size });
    classpath.push(abs);
  }
  return { downloads, classpath, natives };
}
