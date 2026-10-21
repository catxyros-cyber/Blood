import { coordKey } from "../util/maven";
import type { Library, VersionJson } from "./types";

/**
 * Merge a loader profile (Fabric/Quilt, `inheritsFrom`) over its parent
 * vanilla version. Loader libraries come first and win over vanilla ones with
 * the same group/artifact/classifier (e.g. Fabric's newer ASM).
 */
export function mergeInherited(child: VersionJson, parent: VersionJson): VersionJson {
  const seen = new Set<string>();
  const libraries: Library[] = [];
  for (const lib of [...child.libraries, ...parent.libraries]) {
    let key: string;
    try {
      key = coordKey(lib.name);
    } catch {
      key = lib.name;
    }
    // Natives libraries share a coordinate with their Java counterpart but differ by rules,
    // so only de-duplicate exact rule-less matches against the child's entries.
    const ruleKey = lib.rules ? JSON.stringify(lib.rules) : "";
    const fullKey = `${key}|${ruleKey}|${lib.natives ? "natives" : ""}`;
    if (seen.has(fullKey)) continue;
    seen.add(fullKey);
    libraries.push(lib);
  }

  const merged: VersionJson = {
    ...parent,
    id: child.id,
    type: child.type ?? parent.type,
    inheritsFrom: undefined,
    jar: child.jar ?? parent.jar ?? parent.id,
    mainClass: child.mainClass || parent.mainClass,
    libraries,
    javaVersion: child.javaVersion ?? parent.javaVersion,
    logging: child.logging ?? parent.logging,
    releaseTime: parent.releaseTime ?? child.releaseTime,
  };

  if (child.minecraftArguments) merged.minecraftArguments = child.minecraftArguments;
  if (child.arguments || parent.arguments) {
    merged.arguments = {
      game: [...(parent.arguments?.game ?? []), ...(child.arguments?.game ?? [])],
      jvm: [...(parent.arguments?.jvm ?? []), ...(child.arguments?.jvm ?? [])],
    };
  }
  return merged;
}

/** Mojang's Java runtime component for a version (Java 8 "jre-legacy" for old versions). */
export function javaComponentFor(v: VersionJson): { component: string; majorVersion: number } {
  return v.javaVersion ?? { component: "jre-legacy", majorVersion: 8 };
}

/** The id whose `.jar` provides the game client (the vanilla parent for loader profiles). */
export function clientJarId(v: VersionJson): string {
  return v.jar ?? v.id;
}

/** Compare by release time, falling back to id parsing. Returns <0 if a is older. */
export function compareReleaseTime(a: string | undefined, b: string | undefined): number {
  const ta = a ? Date.parse(a) : NaN;
  const tb = b ? Date.parse(b) : NaN;
  if (Number.isNaN(ta) || Number.isNaN(tb)) return 0;
  return ta - tb;
}

/** True if this version was released on or after the given ISO date. */
export function releasedOnOrAfter(v: Pick<VersionJson, "releaseTime">, isoDate: string): boolean {
  if (!v.releaseTime) return true;
  return Date.parse(v.releaseTime) >= Date.parse(isoDate);
}

/**
 * Estimate the Java major version a Minecraft release needs, for when its
 * metadata hasn't been downloaded yet. The real value always comes from the
 * version JSON (javaVersion.majorVersion) at launch.
 */
export function guessJavaMajor(gameVersion: string): number {
  const m = /^(\d+)\.(\d+)(?:\.(\d+))?$/.exec(gameVersion);
  if (!m) return 21; // snapshots and unknown ids: assume current
  const [a, b, c] = [Number(m[1]), Number(m[2]), Number(m[3] ?? 0)];
  if (a >= 26) return 25;
  if (a !== 1) return 21;
  if (b > 20 || (b === 20 && c >= 5)) return 21;
  if (b >= 18) return 17;
  if (b === 17) return 16;
  return 8;
}
