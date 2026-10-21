/**
 * Version comparison and range matching good enough for mod metadata:
 * Fabric/Quilt semver-style predicates (">=1.20 <1.21", "~1.20.1", "1.20.x")
 * and Maven ranges used by Forge/NeoForge ("[1.20,1.21)").
 * Anything we can't parse is treated as "unknown" rather than a mismatch, so we
 * never block a mod because of our own parsing limits.
 */

export function parseVersion(v: string): number[] | null {
  const core = v.trim().replace(/^v/i, "").split(/[-+]/)[0] ?? "";
  if (!/^\d+(\.\d+)*$/.test(core)) return null;
  return core.split(".").map(Number);
}

export function compareVersions(a: string, b: string): number {
  const pa = parseVersion(a);
  const pb = parseVersion(b);
  if (!pa || !pb) return a.localeCompare(b, undefined, { numeric: true });
  const n = Math.max(pa.length, pb.length);
  for (let i = 0; i < n; i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

function matchComparator(version: string, comp: string): boolean | null {
  const m = /^(>=|<=|>|<|=|~|\^)?\s*(.+)$/.exec(comp.trim());
  if (!m) return null;
  const op = m[1] ?? "=";
  const target = m[2]!.trim();
  if (target === "*" || target === "x") return true;

  // Wildcards: 1.20.x / 1.20.*
  if (/[.](x|X|\*)$/.test(target) || /[.](x|X|\*)[.]/.test(target)) {
    const prefix = target.split(/[.](?:x|X|\*)/)[0]!;
    const pv = parseVersion(version);
    const pp = parseVersion(prefix);
    if (!pv || !pp) return null;
    const prefixMatch = pp.every((n, i) => pv[i] === n);
    if (op === "=" || op === "~" || op === "^") return prefixMatch;
    return null;
  }

  const pv = parseVersion(version);
  const pt = parseVersion(target);
  if (!pv || !pt) return op === "=" ? version === target : null;
  const c = compareVersions(version, target);
  switch (op) {
    case ">=":
      return c >= 0;
    case "<=":
      return c <= 0;
    case ">":
      return c > 0;
    case "<":
      return c < 0;
    case "=":
      return c === 0;
    case "~": {
      // ~1.20.1 → >=1.20.1 <1.21.0 ; ~1.20 → >=1.20 <1.21
      const upper = pt.length >= 2 ? [pt[0]!, pt[1]! + 1] : [pt[0]! + 1];
      return c >= 0 && compareVersions(version, upper.join(".")) < 0;
    }
    case "^": {
      const major = pt[0] ?? 0;
      return c >= 0 && (pv[0] ?? 0) === major;
    }
  }
  return null;
}

/** Fabric/Quilt predicate (string or array = OR). Returns null when undecidable. */
export function matchesPredicate(version: string, predicate: string | string[]): boolean | null {
  const alternatives = Array.isArray(predicate) ? predicate : [predicate];
  let sawUnknown = false;
  for (const alt of alternatives) {
    const comps = alt.trim().split(/\s+/).filter(Boolean);
    if (comps.length === 0 || alt.trim() === "*") return true;
    let all: boolean | null = true;
    for (const comp of comps) {
      const r = matchComparator(version, comp);
      if (r === null) {
        all = null;
        break;
      }
      if (!r) {
        all = false;
        break;
      }
    }
    if (all === true) return true;
    if (all === null) sawUnknown = true;
  }
  return sawUnknown ? null : false;
}

/** Maven version range, e.g. "[1.20,1.21)", "[1.20.1]", "[1.19,1.20),[1.20.1,)". A bare version is a soft requirement (any). */
export function matchesMavenRange(version: string, range: string): boolean | null {
  const r = range.trim();
  if (!r || r === "*") return true;
  if (!/^[[(]/.test(r)) return true;
  const parts = r.match(/[[(][^\])]*[\])]/g);
  if (!parts) return null;
  if (!parseVersion(version)) return null;
  for (const part of parts) {
    const lowerInc = part.startsWith("[");
    const upperInc = part.endsWith("]");
    const inner = part.slice(1, -1);
    if (!inner.includes(",")) {
      if (compareVersions(version, inner.trim()) === 0) return true;
      continue;
    }
    const [lo, hi] = inner.split(",").map((s) => s.trim()) as [string, string];
    const okLo = !lo || (lowerInc ? compareVersions(version, lo) >= 0 : compareVersions(version, lo) > 0);
    const okHi = !hi || (upperInc ? compareVersions(version, hi) <= 0 : compareVersions(version, hi) < 0);
    if (okLo && okHi) return true;
  }
  return false;
}
