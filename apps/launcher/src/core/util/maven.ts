/** Maven coordinates: `group:artifact:version[:classifier][@ext]`. */

export interface MavenCoord {
  group: string;
  artifact: string;
  version: string;
  classifier?: string;
  ext: string;
}

export function parseCoord(name: string): MavenCoord {
  let ext = "jar";
  let base = name;
  const at = name.indexOf("@");
  if (at >= 0) {
    ext = name.slice(at + 1);
    base = name.slice(0, at);
  }
  const parts = base.split(":");
  if (parts.length < 3) throw new Error(`Invalid Maven coordinate: ${name}`);
  const [group, artifact, version, classifier] = parts as [string, string, string, string | undefined];
  return { group, artifact, version, classifier, ext };
}

/** Repository-relative path, always with forward slashes (as used in URLs). */
export function coordPath(c: MavenCoord | string): string {
  const m = typeof c === "string" ? parseCoord(c) : c;
  const file = `${m.artifact}-${m.version}${m.classifier ? `-${m.classifier}` : ""}.${m.ext}`;
  return `${m.group.replace(/\./g, "/")}/${m.artifact}/${m.version}/${file}`;
}

/** Identity used to de-duplicate libraries: the same artifact at a different version is a conflict. */
export function coordKey(c: MavenCoord | string): string {
  const m = typeof c === "string" ? parseCoord(c) : c;
  return `${m.group}:${m.artifact}${m.classifier ? `:${m.classifier}` : ""}`;
}
