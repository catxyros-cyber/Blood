/** Which installations BLOOD Client can run on, and what to do about the others. */

import type { Instance } from "../instances/model";

export interface BundleFile {
  loader: "fabric" | "neoforge" | string;
  file: string;
  sha1: string;
  size: number;
}

export interface Bundle {
  version: string;
  minecraft: string[];
  files: BundleFile[];
}

export type SupportStatus =
  | { ok: true; loaderFile: "fabric" | "neoforge" }
  | { ok: false; reason: "not_bundled" | "vanilla" | "forge" | "version" | "loader"; message: string; fix?: "create_fabric_copy" };

/** Quilt loads Fabric mods, so it uses the Fabric build. */
export function bundleLoaderFor(loader: Instance["loader"]): "fabric" | "neoforge" | null {
  if (loader === "fabric" || loader === "quilt") return "fabric";
  if (loader === "neoforge") return "neoforge";
  return null;
}

export function support(inst: Pick<Instance, "loader" | "gameVersion">, bundle: Bundle | null): SupportStatus {
  if (!bundle) {
    return { ok: false, reason: "not_bundled", message: "This build of the launcher doesn't include BLOOD Client. Official BLOOD releases do." };
  }
  const versions = bundle.minecraft.join(", ");
  if (inst.loader === "vanilla") {
    return {
      ok: false,
      reason: "vanilla",
      message: `BLOOD Client is a mod, so it needs a mod loader. Create a Fabric installation for Minecraft ${versions} to use it.`,
      fix: "create_fabric_copy",
    };
  }
  if (inst.loader === "forge") {
    return {
      ok: false,
      reason: "forge",
      message: `BLOOD Client isn't available for Forge. Use Fabric, Quilt or NeoForge on Minecraft ${versions}.`,
      fix: "create_fabric_copy",
    };
  }
  const lf = bundleLoaderFor(inst.loader);
  if (!lf || !bundle.files.some((f) => f.loader === lf)) {
    return { ok: false, reason: "loader", message: `BLOOD Client isn't available for this mod loader.` };
  }
  if (!bundle.minecraft.includes(inst.gameVersion)) {
    return {
      ok: false,
      reason: "version",
      message: `BLOOD Client ${bundle.version} supports Minecraft ${versions}; this installation is ${inst.gameVersion}.`,
      fix: "create_fabric_copy",
    };
  }
  return { ok: true, loaderFile: lf };
}

/** "bloomclient-fabric-1.21.1-0.1.0.jar" → "0.1.0" (null for other files). */
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
    out.push({ level: "error", title: "Fabric API is missing", message: "BLOOD Client needs Fabric API. BLOOD installs it from Modrinth when you turn BLOOD Client on." });
  }
  if (has("sodium", "embeddium")) out.push({ level: "ok", title: "Sodium", message: "Supported." });
  if (has("iris", "oculus")) {
    out.push({ level: "info", title: "Iris shaders", message: "While a shader pack is on, BLOOD's skybox, fog and world particles pause unless you allow them in the in-game menu. The HUD keeps working." });
  }
  if (has("optifabric")) out.push({ level: "warning", title: "OptiFine (OptiFabric)", message: "Not supported together with BLOOD Client. Use Sodium + Iris instead." });
  if (has("fabricskyboxes", "nuit")) {
    out.push({ level: "warning", title: "Another skybox mod", message: "FabricSkyBoxes/Nuit replaces BLOOD's skybox whenever it has one active. Remove one to choose." });
  }
  if (has("particlerain")) out.push({ level: "warning", title: "Particle Rain", message: "Also adds rain and snow; turn off one of them to avoid doubled weather." });
  return out;
}
