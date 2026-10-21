import { BloomError } from "../platform";
import type { ModrinthClient } from "./api";
import type { Project, Version, VersionFile } from "./types";

export interface InstallTarget {
  gameVersion: string;
  /** Modrinth loader tags accepted by the installation (empty for vanilla content). */
  loaders: string[];
}

const TYPE_RANK: Record<Version["version_type"], number> = { release: 0, beta: 1, alpha: 2 };

/** Versions compatible with the target, best first: release > beta > alpha, then newest. */
export function compatibleVersions(versions: Version[], target: InstallTarget): Version[] {
  return versions
    .filter((v) => v.game_versions.includes(target.gameVersion))
    .filter((v) => target.loaders.length === 0 || v.loaders.some((l) => target.loaders.includes(l)))
    .sort((a, b) => {
      const t = TYPE_RANK[a.version_type] - TYPE_RANK[b.version_type];
      if (t !== 0) return t;
      return Date.parse(b.date_published) - Date.parse(a.date_published);
    });
}

export function primaryFile(v: Version): VersionFile {
  const f = v.files.find((x) => x.primary) ?? v.files[0];
  if (!f) throw new BloomError("no_files", `${v.name} has no downloadable files`);
  return f;
}

export interface PlannedInstall {
  projectId: string;
  title: string;
  version: Version;
  file: VersionFile;
  /** Why this is being installed: the user's choice or a required dependency of another mod. */
  reason: "requested" | "dependency";
  requiredBy?: string;
}

export interface InstallPlan {
  installs: PlannedInstall[];
  optional: { projectId: string; title: string }[];
  /** Installed projects this plan declares itself incompatible with. */
  conflicts: { projectId: string; title: string; with: string }[];
  /** Required dependencies with no compatible version — installing anyway will likely crash. */
  unresolved: { projectId: string; title: string; requiredBy: string }[];
  warnings: string[];
}

export interface ResolveOptions {
  target: InstallTarget;
  /** Modrinth project ids already present in the installation. */
  installedProjectIds: Set<string>;
  maxDepth?: number;
}

/**
 * Resolve a project version plus its required dependencies (recursively) for
 * an installation. Never silently picks an incompatible version: if a required
 * dependency has nothing compatible, it is reported in `unresolved`.
 */
export async function planInstall(
  api: ModrinthClient,
  project: Project,
  chosen: Version | undefined,
  opts: ResolveOptions,
): Promise<InstallPlan> {
  const plan: InstallPlan = { installs: [], optional: [], conflicts: [], unresolved: [], warnings: [] };
  const visited = new Set<string>(opts.installedProjectIds);
  const titles = new Map<string, string>([[project.id, project.title]]);

  if (project.client_side === "unsupported") {
    plan.warnings.push(`${project.title} is a server-side project; it won't do anything in a client installation.`);
  }

  const root =
    chosen ?? compatibleVersions(await api.versions(project.id, { loaders: opts.target.loaders, gameVersions: [opts.target.gameVersion] }), opts.target)[0];
  if (!root) {
    throw new BloomError(
      "no_compatible_version",
      `${project.title} has no version for Minecraft ${opts.target.gameVersion}${opts.target.loaders.length ? ` (${opts.target.loaders.join("/")})` : ""}.`,
    );
  }
  if (chosen && !compatibleVersions([chosen], opts.target).length) {
    plan.warnings.push(`${chosen.name} isn't marked compatible with Minecraft ${opts.target.gameVersion} and this loader.`);
  }

  const queue: { version: Version; title: string; depth: number; reason: PlannedInstall["reason"]; requiredBy?: string }[] = [
    { version: root, title: project.title, depth: 0, reason: "requested" },
  ];
  visited.add(project.id);
  const maxDepth = opts.maxDepth ?? 8;

  async function titleOf(id: string): Promise<string> {
    if (!titles.has(id)) {
      try {
        titles.set(id, (await api.project(id)).title);
      } catch {
        titles.set(id, id);
      }
    }
    return titles.get(id)!;
  }

  while (queue.length > 0) {
    const item = queue.shift()!;
    plan.installs.push({
      projectId: item.version.project_id,
      title: item.title,
      version: item.version,
      file: primaryFile(item.version),
      reason: item.reason,
      requiredBy: item.requiredBy,
    });
    if (item.depth >= maxDepth) continue;

    for (const dep of item.version.dependencies) {
      if (dep.dependency_type === "embedded") continue;
      let depVersion: Version | undefined;
      let depProject = dep.project_id;
      if (dep.version_id) {
        try {
          depVersion = await api.version(dep.version_id);
          depProject = depVersion.project_id;
        } catch {
          depVersion = undefined;
        }
      }
      if (!depProject) continue;

      if (dep.dependency_type === "incompatible") {
        if (opts.installedProjectIds.has(depProject)) {
          plan.conflicts.push({ projectId: depProject, title: await titleOf(depProject), with: item.title });
        }
        continue;
      }
      if (visited.has(depProject)) continue;

      if (dep.dependency_type === "optional") {
        plan.optional.push({ projectId: depProject, title: await titleOf(depProject) });
        continue;
      }

      // required
      visited.add(depProject);
      if (depVersion && !compatibleVersions([depVersion], opts.target).length) depVersion = undefined;
      if (!depVersion) {
        try {
          depVersion = compatibleVersions(
            await api.versions(depProject, { loaders: opts.target.loaders, gameVersions: [opts.target.gameVersion] }),
            opts.target,
          )[0];
        } catch {
          depVersion = undefined;
        }
      }
      const depTitle = await titleOf(depProject);
      if (!depVersion) {
        plan.unresolved.push({ projectId: depProject, title: depTitle, requiredBy: item.title });
        continue;
      }
      queue.push({ version: depVersion, title: depTitle, depth: item.depth + 1, reason: "dependency", requiredBy: item.title });
    }
  }
  return plan;
}
