import type { LoaderId } from "../loaders/loaders";
import { KNOWN_CONFLICTS, looksLikeOptifine } from "../perf/knownMods";
import { matchesMavenRange, matchesPredicate } from "../util/versions";
import type { ModMeta } from "./metadata";

export interface InstalledMod {
  fileName: string;
  enabled: boolean;
  meta: ModMeta | null;
  /** From Modrinth, when the file was identified there. */
  modrinth?: { projectId: string; title?: string; clientSide?: string };
}

export type IssueKind =
  | "wrong_loader"
  | "duplicate"
  | "missing_dependency"
  | "breaks"
  | "game_version"
  | "server_only"
  | "known_conflict"
  | "unknown_file";

export interface CompatIssue {
  kind: IssueKind;
  severity: "error" | "warning" | "info";
  files: string[];
  message: string;
  fix?: string;
}

/** Ids provided by the loader/game itself — never reported as missing. */
const BUILTIN_IDS = new Set([
  "minecraft",
  "java",
  "fabricloader",
  "fabric-loader",
  "quilt_loader",
  "quilt_base",
  "forge",
  "neoforge",
  "javafml",
  "lowcodefml",
  "mixinextras",
]);

export function isFabricApiModule(id: string): boolean {
  return id === "fabric" || id === "fabric-api" || id === "fabric-api-base" || /^fabric-[a-z0-9-]+-v\d+$/.test(id);
}

function modLabel(m: InstalledMod): string {
  return m.meta?.name || m.modrinth?.title || m.fileName;
}

function loaderAccepts(instance: LoaderId, mod: ModMeta): boolean {
  if (instance === "vanilla") return false;
  if (mod.loaders.includes(instance as never)) return true;
  // Quilt loads Fabric mods.
  if (instance === "quilt" && mod.loaders.includes("fabric")) return true;
  return false;
}

/**
 * Static compatibility analysis of an installation's mods folder. This catches
 * the common causes of crash-on-launch before the game is started; it cannot
 * prove two mods work together.
 */
export function analyzeMods(mods: InstalledMod[], instance: { loader: LoaderId; gameVersion: string }): CompatIssue[] {
  const issues: CompatIssue[] = [];
  const enabled = mods.filter((m) => m.enabled);

  if (instance.loader === "vanilla" && enabled.length > 0) {
    issues.push({
      kind: "wrong_loader",
      severity: "warning",
      files: enabled.map((m) => m.fileName),
      message: "This is a vanilla installation, so mods in its mods folder will not load.",
      fix: "Edit the installation and choose a mod loader such as Fabric.",
    });
    return issues;
  }

  // Which ids are present (including `provides`)
  const provided = new Map<string, InstalledMod[]>();
  for (const m of enabled) {
    if (!m.meta) continue;
    for (const id of [m.meta.id, ...m.meta.provides]) {
      if (!id) continue;
      provided.set(id, [...(provided.get(id) ?? []), m]);
    }
  }
  const optifine = enabled.filter((m) => looksLikeOptifine(m.fileName));
  if (optifine.length) provided.set("optifine", optifine);

  const hasFabricApi = provided.has("fabric-api") || provided.has("fabric") || provided.has("quilted_fabric_api");

  for (const m of enabled) {
    if (!m.meta) {
      if (!looksLikeOptifine(m.fileName)) {
        issues.push({
          kind: "unknown_file",
          severity: "info",
          files: [m.fileName],
          message: `${m.fileName} has no mod metadata MOTION recognises. It may be a library or a mod for another loader.`,
        });
      }
      continue;
    }
    const meta = m.meta;

    if (!loaderAccepts(instance.loader, meta)) {
      issues.push({
        kind: "wrong_loader",
        severity: "error",
        files: [m.fileName],
        message: `${modLabel(m)} is a ${meta.loaders.join("/")} mod, but this installation uses ${instance.loader}.`,
        fix: "Remove it or install the version built for this loader.",
      });
      continue;
    }

    if (meta.environment === "server" || m.modrinth?.clientSide === "unsupported") {
      issues.push({
        kind: "server_only",
        severity: "warning",
        files: [m.fileName],
        message: `${modLabel(m)} is a server-side mod and won't do anything (or may crash) on the client.`,
        fix: "Disable it in this installation.",
      });
    }

    for (const dep of meta.depends) {
      if (dep.id === "minecraft" && dep.range) {
        const ok =
          dep.rangeKind === "maven" ? matchesMavenRange(instance.gameVersion, String(dep.range)) : matchesPredicate(instance.gameVersion, dep.range);
        if (ok === false) {
          issues.push({
            kind: "game_version",
            severity: "error",
            files: [m.fileName],
            message: `${modLabel(m)} requires Minecraft ${Array.isArray(dep.range) ? dep.range.join(" or ") : dep.range}, but this installation is ${instance.gameVersion}.`,
            fix: "Install the version of this mod made for your Minecraft version.",
          });
        }
        continue;
      }
      if (BUILTIN_IDS.has(dep.id)) continue;
      if (provided.has(dep.id)) continue;
      // Fabric API is split into many modules shipped inside one jar ("fabric-…-v1", "fabric-api-base").
      // Other "fabric-" ids, like fabric-language-kotlin, are separate mods.
      if (isFabricApiModule(dep.id) && hasFabricApi) continue;
      // Some deps are bundled inside the mod itself (jar-in-jar); we can't see those ids.
      if (meta.nestedJars > 0 && !["fabric-api", "fabric"].includes(dep.id)) continue;
      issues.push({
        kind: "missing_dependency",
        severity: "error",
        files: [m.fileName],
        message: `${modLabel(m)} needs "${dep.id}", which isn't installed.`,
        fix: isFabricApiModule(dep.id) ? "Install Fabric API from Browse." : `Search Browse for "${dep.id}" and install it.`,
      });
    }

    for (const br of meta.breaks) {
      const other = provided.get(br.id);
      if (other && other.some((o) => o !== m)) {
        issues.push({
          kind: "breaks",
          severity: "error",
          files: [m.fileName, ...other.map((o) => o.fileName)],
          message: `${modLabel(m)} declares itself incompatible with ${other.map(modLabel).join(", ")}.`,
          fix: "Disable one of them.",
        });
      }
    }
  }

  // Duplicates: the same mod id (or Modrinth project) twice.
  const byKey = new Map<string, InstalledMod[]>();
  for (const m of enabled) {
    const key = m.meta?.id ? `id:${m.meta.id}` : m.modrinth?.projectId ? `mr:${m.modrinth.projectId}` : "";
    if (!key) continue;
    byKey.set(key, [...(byKey.get(key) ?? []), m]);
  }
  for (const group of byKey.values()) {
    if (group.length > 1) {
      issues.push({
        kind: "duplicate",
        severity: "error",
        files: group.map((g) => g.fileName),
        message: `${modLabel(group[0]!)} is installed ${group.length} times.`,
        fix: "Keep only the newest file.",
      });
    }
  }

  // Known rendering/optimization conflicts.
  for (const rule of KNOWN_CONFLICTS) {
    const left = rule.a.flatMap((id) => provided.get(id) ?? []);
    const right = rule.b.flatMap((id) => provided.get(id) ?? []);
    if (left.length && right.length) {
      issues.push({
        kind: "known_conflict",
        severity: rule.severity,
        files: [...new Set([...left, ...right].map((m) => m.fileName))],
        message: rule.message,
        fix: rule.fix,
      });
    }
  }
  if (optifine.length && (instance.loader === "fabric" || instance.loader === "quilt")) {
    issues.push({
      kind: "known_conflict",
      severity: "warning",
      files: optifine.map((m) => m.fileName),
      message: "OptiFine doesn't support Fabric/Quilt directly and needs an unofficial bridge that often breaks other mods.",
      fix: "Use Sodium (and Iris for shaders) instead.",
    });
  }

  const order = { error: 0, warning: 1, info: 2 };
  return issues.sort((a, b) => order[a.severity] - order[b.severity]);
}
