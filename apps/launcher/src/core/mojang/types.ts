/** Types for Mojang's launcher metadata (piston-meta). Only fields BLOOD uses are declared. */

export const VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
export const JAVA_RUNTIME_INDEX_URL =
  "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
export const RESOURCES_URL = "https://resources.download.minecraft.net";
export const LIBRARIES_URL = "https://libraries.minecraft.net/";

export type VersionType = "release" | "snapshot" | "old_beta" | "old_alpha";

export interface ManifestVersion {
  id: string;
  type: VersionType;
  url: string;
  time: string;
  releaseTime: string;
  sha1: string;
  complianceLevel?: number;
}

export interface VersionManifest {
  latest: { release: string; snapshot: string };
  versions: ManifestVersion[];
}

export interface Rule {
  action: "allow" | "disallow";
  os?: { name?: string; arch?: string; version?: string };
  features?: Record<string, boolean>;
}

export interface Artifact {
  path?: string;
  sha1?: string;
  size?: number;
  url: string;
}

export interface Library {
  name: string;
  downloads?: {
    artifact?: Artifact;
    classifiers?: Record<string, Artifact>;
  };
  /** Base repository URL for libraries without `downloads` (Fabric/Quilt style). */
  url?: string;
  sha1?: string;
  size?: number;
  rules?: Rule[];
  natives?: Partial<Record<"windows" | "linux" | "osx", string>>;
  extract?: { exclude?: string[] };
}

export type ArgumentValue = string | { rules?: Rule[]; value: string | string[] };

export interface LoggingConfig {
  argument: string;
  file: { id: string; sha1: string; size: number; url: string };
  type: string;
}

export interface VersionJson {
  id: string;
  type?: VersionType | string;
  inheritsFrom?: string;
  /** Some loader profiles name the jar to use explicitly. */
  jar?: string;
  mainClass: string;
  minecraftArguments?: string;
  arguments?: { game?: ArgumentValue[]; jvm?: ArgumentValue[] };
  assetIndex?: { id: string; sha1: string; size: number; totalSize?: number; url: string };
  assets?: string;
  downloads?: { client?: Artifact; client_mappings?: Artifact; server?: Artifact };
  libraries: Library[];
  javaVersion?: { component: string; majorVersion: number };
  logging?: { client?: LoggingConfig };
  releaseTime?: string;
  time?: string;
  complianceLevel?: number;
}

export interface AssetIndex {
  objects: Record<string, { hash: string; size: number }>;
  virtual?: boolean;
  map_to_resources?: boolean;
}

export interface JavaRuntimeEntry {
  availability: { group: number; progress: number };
  manifest: { sha1: string; size: number; url: string };
  version: { name: string; released: string };
}

export type JavaRuntimeIndex = Record<string, Record<string, JavaRuntimeEntry[]>>;

export interface JavaManifest {
  files: Record<
    string,
    | { type: "directory" }
    | { type: "link"; target: string }
    | {
        type: "file";
        executable?: boolean;
        downloads: { raw: { sha1: string; size: number; url: string }; lzma?: { sha1: string; size: number; url: string } };
      }
  >;
}
