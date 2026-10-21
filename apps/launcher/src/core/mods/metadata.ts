import { parseToml, type TomlTable } from "../util/toml";

export type ModLoaderTag = "fabric" | "quilt" | "forge" | "neoforge";

export interface ModDependency {
  id: string;
  /** Fabric/Quilt predicate(s) or a Maven range (Forge/NeoForge). */
  range?: string | string[];
  rangeKind: "predicate" | "maven";
}

export interface ModMeta {
  id: string;
  name: string;
  version: string;
  description?: string;
  authors: string[];
  loaders: ModLoaderTag[];
  depends: ModDependency[];
  breaks: ModDependency[];
  provides: string[];
  /** "client" | "server" | "*" */
  environment: "client" | "server" | "*";
  /** Path of the icon inside the jar. */
  icon?: string;
  /** Number of jar-in-jar libraries (their ids are not visible without opening them). */
  nestedJars: number;
}

/** Files BLOOD reads from a jar to identify a mod, in priority order. */
export const METADATA_ENTRIES = [
  "fabric.mod.json",
  "quilt.mod.json",
  "META-INF/neoforge.mods.toml",
  "META-INF/mods.toml",
  "mcmod.info",
] as const;

/** fabric.mod.json allows raw control characters inside strings that strict JSON rejects. */
function lenientJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    // eslint-disable-next-line no-control-regex
    return JSON.parse(text.replace(/[\u0000-\u001f]+/g, " "));
  }
}

function pickIcon(icon: unknown): string | undefined {
  if (typeof icon === "string") return icon;
  if (icon && typeof icon === "object") {
    const entries = Object.entries(icon as Record<string, string>).sort((a, b) => Number(b[0]) - Number(a[0]));
    return entries[0]?.[1];
  }
  return undefined;
}

function fabricDeps(obj: unknown): ModDependency[] {
  if (!obj || typeof obj !== "object") return [];
  return Object.entries(obj as Record<string, string | string[]>).map(([id, range]) => ({ id, range, rangeKind: "predicate" }));
}

export function parseFabricModJson(text: string): ModMeta {
  const j = lenientJson(text) as Record<string, unknown>;
  const authors = Array.isArray(j.authors)
    ? (j.authors as unknown[]).map((a) => (typeof a === "string" ? a : String((a as { name?: string }).name ?? ""))).filter(Boolean)
    : [];
  const env = j.environment === "client" ? "client" : j.environment === "server" ? "server" : "*";
  return {
    id: String(j.id ?? ""),
    name: String(j.name ?? j.id ?? ""),
    version: String(j.version ?? ""),
    description: typeof j.description === "string" ? j.description : undefined,
    authors,
    loaders: ["fabric"],
    depends: fabricDeps(j.depends),
    breaks: fabricDeps(j.breaks),
    provides: Array.isArray(j.provides) ? (j.provides as string[]) : [],
    environment: env,
    icon: pickIcon(j.icon),
    nestedJars: Array.isArray(j.jars) ? j.jars.length : 0,
  };
}

export function parseQuiltModJson(text: string): ModMeta {
  const j = lenientJson(text) as Record<string, unknown>;
  const ql = (j.quilt_loader ?? {}) as Record<string, unknown>;
  const meta = (ql.metadata ?? {}) as Record<string, unknown>;
  const mc = (j.minecraft ?? {}) as Record<string, unknown>;
  const deps = (list: unknown): ModDependency[] =>
    Array.isArray(list)
      ? list
          .map((d): ModDependency | null => {
            if (typeof d === "string") return { id: d.split(":").pop()!, rangeKind: "predicate" };
            const o = d as { id?: string; versions?: string | string[]; optional?: boolean };
            if (!o.id || o.optional) return null;
            return { id: o.id.split(":").pop()!, range: o.versions, rangeKind: "predicate" };
          })
          .filter((d): d is ModDependency => d !== null)
      : [];
  const provides = Array.isArray(ql.provides)
    ? (ql.provides as unknown[]).map((p) => (typeof p === "string" ? p : String((p as { id?: string }).id ?? "")))
    : [];
  return {
    id: String(ql.id ?? ""),
    name: String(meta.name ?? ql.id ?? ""),
    version: String(ql.version ?? ""),
    description: typeof meta.description === "string" ? meta.description : undefined,
    authors: meta.contributors && typeof meta.contributors === "object" ? Object.keys(meta.contributors) : [],
    loaders: ["quilt"],
    depends: deps(ql.depends),
    breaks: deps(ql.breaks),
    provides,
    environment: mc.environment === "client" ? "client" : mc.environment === "dedicated_server" ? "server" : "*",
    icon: pickIcon(meta.icon),
    nestedJars: Array.isArray(ql.jars) ? ql.jars.length : 0,
  };
}

export function parseModsToml(text: string, file: "neoforge.mods.toml" | "mods.toml"): ModMeta[] {
  const t = parseToml(text);
  const mods = (Array.isArray(t.mods) ? t.mods : []) as TomlTable[];
  const depTables = (t.dependencies ?? {}) as Record<string, TomlTable[]>;
  return mods.map((m) => {
    const id = String(m.modId ?? "");
    const rawDeps = Array.isArray(depTables[id]) ? depTables[id] : [];
    const isNeo = file === "neoforge.mods.toml" || rawDeps.some((d) => d.modId === "neoforge");
    const depends: ModDependency[] = [];
    const breaks: ModDependency[] = [];
    for (const d of rawDeps) {
      const depId = String(d.modId ?? "");
      if (!depId) continue;
      const side = String(d.side ?? "BOTH").toUpperCase();
      if (side === "SERVER") continue;
      const type = typeof d.type === "string" ? d.type.toLowerCase() : d.mandatory === false ? "optional" : "required";
      const dep: ModDependency = { id: depId, range: typeof d.versionRange === "string" ? d.versionRange : undefined, rangeKind: "maven" };
      if (type === "required") depends.push(dep);
      else if (type === "incompatible") breaks.push(dep);
    }
    const authors = typeof m.authors === "string" ? m.authors.split(/,\s*/) : typeof t.authors === "string" ? [t.authors] : [];
    return {
      id,
      name: String(m.displayName ?? id),
      version: String(m.version ?? ""),
      description: typeof m.description === "string" ? m.description.trim() : undefined,
      authors,
      loaders: [isNeo ? "neoforge" : "forge"] as ModLoaderTag[],
      depends,
      breaks,
      provides: [],
      environment: "*" as const,
      icon: typeof m.logoFile === "string" ? m.logoFile : typeof t.logoFile === "string" ? t.logoFile : undefined,
      nestedJars: 0,
    };
  });
}

export function parseMcmodInfo(text: string): ModMeta[] {
  const j = lenientJson(text);
  const list = (Array.isArray(j) ? j : ((j as { modList?: unknown[] }).modList ?? [])) as Record<string, unknown>[];
  return list.map((m) => ({
    id: String(m.modid ?? ""),
    name: String(m.name ?? m.modid ?? ""),
    version: String(m.version ?? ""),
    description: typeof m.description === "string" ? m.description : undefined,
    authors: Array.isArray(m.authorList) ? (m.authorList as string[]) : [],
    loaders: ["forge"] as ModLoaderTag[],
    depends: Array.isArray(m.requiredMods)
      ? (m.requiredMods as string[]).map((id) => ({ id: id.split("@")[0]!, rangeKind: "maven" as const }))
      : [],
    breaks: [],
    provides: [],
    environment: "*" as const,
    icon: typeof m.logoFile === "string" ? m.logoFile : undefined,
    nestedJars: 0,
  }));
}

/**
 * Combine the metadata files found in one jar. Multi-loader jars (e.g. a jar
 * with both fabric.mod.json and neoforge.mods.toml) report every loader.
 */
export function combineMetadata(found: Partial<Record<(typeof METADATA_ENTRIES)[number], string>>): ModMeta | null {
  const metas: ModMeta[] = [];
  const tryParse = (fn: () => ModMeta | ModMeta[]) => {
    try {
      const r = fn();
      metas.push(...(Array.isArray(r) ? r : [r]));
    } catch {
      /* unreadable metadata is reported as "unknown mod" by the caller */
    }
  };
  if (found["fabric.mod.json"]) tryParse(() => parseFabricModJson(found["fabric.mod.json"]!));
  if (found["quilt.mod.json"]) tryParse(() => parseQuiltModJson(found["quilt.mod.json"]!));
  if (found["META-INF/neoforge.mods.toml"]) tryParse(() => parseModsToml(found["META-INF/neoforge.mods.toml"]!, "neoforge.mods.toml"));
  if (found["META-INF/mods.toml"]) tryParse(() => parseModsToml(found["META-INF/mods.toml"]!, "mods.toml"));
  if (found["mcmod.info"]) tryParse(() => parseMcmodInfo(found["mcmod.info"]!));
  const primary = metas.find((m) => m.id);
  if (!primary) return null;
  const loaders = new Set<ModLoaderTag>();
  metas.forEach((m) => m.loaders.forEach((l) => loaders.add(l)));
  return { ...primary, loaders: [...loaders] };
}

/** Read `Implementation-Version` from a MANIFEST.MF (Forge mods often use ${file.jarVersion}). */
export function manifestVersion(manifest: string): string | undefined {
  const m = /^Implementation-Version:\s*(.+)$/m.exec(manifest);
  return m?.[1]?.trim();
}
