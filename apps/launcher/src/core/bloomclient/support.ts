/** Which installations MOTION Client can run on, and what to do about the others. */

import type { Instance } from "../instances/model";

export interface BundleFile {
  loader: "fabric" | "neoforge" | string;
  file: string;
  sha1: string;
  size: number;
  /** Minecraft version of this jar (older manifests: every file is for minecraft[0]). */
  minecraft?: string;
  /** Versions it installs on: min inclusive, below exclusive (older manifests: exactly `minecraft`). */
  supports?: { min: string; below: string };
  /** How players see that range, e.g. "26.1–26.1.2". */
  label?: string;
}

export interface Bundle {
  version: string;
  minecraft: string[];
  files: BundleFile[];
}

export type SupportStatus =
  | { ok: true; loaderFile: "fabric" | "neoforge"; file: string }
  | { ok: false; reason: "not_bundled" | "vanilla" | "forge" | "version" | "loader"; message: string; fix?: "create_fabric_copy" };

/** Quilt loads Fabric mods, so it uses the Fabric build. */
export function bundleLoaderFor(loader: Instance["loader"]): "fabric" | "neoforge" | null {
  if (loader === "fabric" || loader === "quilt") return "fabric";
  if (loader === "neoforge") return "neoforge";
  return null;
}

/** Minecraft version a bundled jar is for. */
export function fileMinecraft(bundle: Bundle, f: BundleFile): string {
  return f.minecraft ?? bundle.minecraft[0] ?? "";
}

/** Whether a bundled jar installs on this Minecraft version. */
export function fileSupports(bundle: Bundle, f: BundleFile, gameVersion: string): boolean {
  if (f.supports) return compareVersions(gameVersion, f.supports.min) >= 0 && compareVersions(gameVersion, f.supports.below) < 0;
  return fileMinecraft(bundle, f) === gameVersion;
}

/** The bundled jar for a loader and Minecraft version, if there is one (an exact build wins over a range). */
export function bundleFileFor(bundle: Bundle, loader: "fabric" | "neoforge", gameVersion: string): BundleFile | null {
  const mine = bundle.files.filter((f) => f.loader === loader);
  return mine.find((f) => fileMinecraft(bundle, f) === gameVersion) ?? mine.find((f) => fileSupports(bundle, f, gameVersion)) ?? null;
}

/** Every Minecraft version (or range) a loader has a jar for, as players read them, oldest first. */
export function supportedLabels(bundle: Bundle, loader?: "fabric" | "neoforge"): string[] {
  const files = bundle.files.filter((f) => !loader || f.loader === loader);
  const sorted = [...files].sort((a, b) => compareVersions(a.supports?.min ?? fileMinecraft(bundle, a), b.supports?.min ?? fileMinecraft(bundle, b)));
  return [...new Set(sorted.map((f) => f.label ?? fileMinecraft(bundle, f)))];
}

/** Newest Minecraft version with a Fabric build (what "Create Fabric installation" uses). */
export function preferredVersion(bundle: Bundle): string {
  const fabric = bundle.files.filter((f) => f.loader === "fabric").map((f) => fileMinecraft(bundle, f));
  const all = fabric.length ? fabric : bundle.minecraft;
  return [...all].sort((a, b) => compareVersions(b, a))[0] ?? bundle.minecraft[0] ?? "";
}

export function support(inst: Pick<Instance, "loader" | "gameVersion">, bundle: Bundle | null): SupportStatus {
  if (!bundle) {
    return { ok: false, reason: "not_bundled", message: "This build of the launcher doesn't include MOTION Client. Official MOTION releases do." };
  }
  const versions = supportedLabels(bundle).join(", ");
  if (inst.loader === "vanilla") {
    return {
      ok: false,
      reason: "vanilla",
      message: `MOTION Client is a mod, so it needs a mod loader. Create a Fabric installation for Minecraft ${versions} to use it.`,
      fix: "create_fabric_copy",
    };
  }
  if (inst.loader === "forge") {
    return {
      ok: false,
      reason: "forge",
      message: `MOTION Client isn't available for Forge. Use Fabric, Quilt or NeoForge on Minecraft ${versions}.`,
      fix: "create_fabric_copy",
    };
  }
  const lf = bundleLoaderFor(inst.loader);
  if (!lf || !bundle.files.some((f) => f.loader === lf)) {
    return { ok: false, reason: "loader", message: `MOTION Client isn't available for this mod loader.` };
  }
  const file = bundleFileFor(bundle, lf, inst.gameVersion);
  if (!file) {
    return {
      ok: false,
      reason: "version",
      message: `MOTION Client ${bundle.version} supports Minecraft ${supportedLabels(bundle, lf).join(", ")} on ${lf === "fabric" ? "Fabric and Quilt" : "NeoForge"}; this installation is ${inst.gameVersion}.`,
      fix: "create_fabric_copy",
    };
  }
  return { ok: true, loaderFile: lf, file: file.file };
}

/** "bloomclient-fabric-1.21.1-0.1.0.jar" → "0.1.0" (null for other files). Also "bloomclient-fabric-26.1-0.1.0.jar". */
export function versionFromFileName(name: string): string | null {
  const m = /^bloomclient-(fabric|neoforge)-[0-9.]+-(.+?)\.jar(\.disabled)?$/i.exec(name);
  return m ? m[2]! : null;
}

/** Compares dotted versions numerically ("0.10.0" > "0.9.2"); non-numeric parts compare as text. */
export function compareVersions(a: string, b: string): number {
  const pa = a.split(/[.+-]/), pb = b.split(/[.+-]/);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i] ?? "0", y = pb[i] ?? "0";
    const nx = Number(x), ny = Number(y);
    const c = Number.isFinite(nx) && Number.isFinite(ny) ? nx - ny : x.localeCompare(y);
    if (c !== 0) return Math.sign(c);
  }
  return 0;
}

export interface CompatNote {
  level: "ok" | "info" | "warning" | "error";
  title: string;
  message: string;
}

/** Known interactions with other mods in the installation (by mod id). */
export function compatNotes(loader: Instance["loader"], modIds: Set<string>): CompatNote[] {
  const out: CompatNote[] = [];
  const has = (...ids: string[]) => ids.some((i) => modIds.has(i));
  if ((loader === "fabric" || loader === "quilt") && !has("fabric-api", "fabric", "quilted_fabric_api")) {
    out.push({ level: "error", title: "Fabric API is missing", message: "MOTION Client needs Fabric API. MOTION installs it from Modrinth when you turn MOTION Client on." });
  }
  if (has("sodium", "embeddium")) out.push({ level: "ok", title: "Sodium", message: "Supported." });
  if (has("iris", "oculus")) {
    out.push({ level: "info", title: "Iris shaders", message: "While a shader pack is on, MOTION's skybox, fog and world particles pause unless you allow them in the in-game menu. The HUD keeps working." });
  }
  if (has("optifabric")) out.push({ level: "warning", title: "OptiFine (OptiFabric)", message: "Not supported together with MOTION Client. Use Sodium + Iris instead." });
  if (has("fabricskyboxes", "nuit")) {
    out.push({ level: "warning", title: "Another skybox mod", message: "FabricSkyBoxes/Nuit replaces MOTION's skybox whenever it has one active. Remove one to choose." });
  }
  if (has("particlerain")) out.push({ level: "warning", title: "Particle Rain", message: "Also adds rain and snow; turn off one of them to avoid doubled weather." });
  return out;
}
