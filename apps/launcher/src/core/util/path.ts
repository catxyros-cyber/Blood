/** Minimal path helpers that work with either separator (the core never touches `node:path`). */

export function join(sep: string, ...parts: string[]): string {
  const cleaned: string[] = [];
  parts.forEach((part, i) => {
    if (!part) return;
    let p = part.replace(/[\\/]+/g, sep);
    if (i > 0) p = p.replace(new RegExp(`^${escape(sep)}+`), "");
    if (i < parts.length - 1) p = p.replace(new RegExp(`${escape(sep)}+$`), "");
    if (p) cleaned.push(p);
  });
  return cleaned.join(sep);
}

function escape(s: string): string {
  return s.replace(/[\\^$.*+?()[\]{}|]/g, "\\$&");
}

export function basename(p: string): string {
  const parts = p.split(/[\\/]/);
  return parts[parts.length - 1] ?? "";
}

export function dirname(p: string): string {
  const idx = Math.max(p.lastIndexOf("/"), p.lastIndexOf("\\"));
  return idx <= 0 ? p : p.slice(0, idx);
}

/** A relative path from an untrusted source (mrpack, zip) must stay inside its root. */
export function isSafeRelative(p: string): boolean {
  if (!p || p.startsWith("/") || p.startsWith("\\") || /^[a-zA-Z]:/.test(p)) return false;
  return !p.split(/[\\/]/).some((seg) => seg === ".." || seg === "");
}

/** Turn an arbitrary name into a filesystem-safe folder name. */
export function slugify(name: string): string {
  const s = name
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40);
  return s || "installation";
}
