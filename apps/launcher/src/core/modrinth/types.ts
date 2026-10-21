/** Modrinth API v2 types (https://docs.modrinth.com/api/). Only fields BLOOD uses. */

export type ProjectType = "mod" | "modpack" | "resourcepack" | "shader" | "datapack" | "plugin";
export type SideSupport = "required" | "optional" | "unsupported" | "unknown";

export interface SearchHit {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  categories: string[];
  display_categories?: string[];
  client_side: SideSupport;
  server_side: SideSupport;
  project_type: ProjectType;
  downloads: number;
  follows: number;
  icon_url: string | null;
  date_modified: string;
  date_created: string;
  latest_version?: string;
  versions: string[];
  gallery?: string[];
  featured_gallery?: string | null;
  color?: number | null;
}

export interface SearchResult {
  hits: SearchHit[];
  offset: number;
  limit: number;
  total_hits: number;
}

export interface GalleryImage {
  url: string;
  featured: boolean;
  title?: string | null;
  description?: string | null;
}

export interface Project {
  id: string;
  slug: string;
  title: string;
  description: string;
  body: string;
  categories: string[];
  additional_categories?: string[];
  client_side: SideSupport;
  server_side: SideSupport;
  project_type: ProjectType;
  downloads: number;
  followers: number;
  icon_url: string | null;
  gallery: GalleryImage[];
  game_versions: string[];
  loaders: string[];
  license?: { id: string; name: string; url?: string | null };
  source_url?: string | null;
  issues_url?: string | null;
  wiki_url?: string | null;
  discord_url?: string | null;
  team: string;
  published: string;
  updated: string;
  versions: string[];
}

export type DependencyType = "required" | "optional" | "incompatible" | "embedded";

export interface Dependency {
  version_id: string | null;
  project_id: string | null;
  file_name: string | null;
  dependency_type: DependencyType;
}

export interface VersionFile {
  hashes: { sha1: string; sha512: string };
  url: string;
  filename: string;
  primary: boolean;
  size: number;
  file_type?: string | null;
}

export interface Version {
  id: string;
  project_id: string;
  name: string;
  version_number: string;
  version_type: "release" | "beta" | "alpha";
  game_versions: string[];
  loaders: string[];
  featured: boolean;
  date_published: string;
  downloads: number;
  files: VersionFile[];
  dependencies: Dependency[];
  changelog?: string | null;
}

export interface TeamMember {
  role: string;
  user: { username: string; avatar_url?: string | null };
}

export interface CategoryTag {
  icon: string;
  name: string;
  project_type: ProjectType;
  header: string;
}

export interface GameVersionTag {
  version: string;
  version_type: "release" | "snapshot" | "alpha" | "beta";
  date: string;
  major: boolean;
}

/** The .mrpack index (https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack). */
export interface MrpackIndex {
  formatVersion: number;
  game: string;
  versionId: string;
  name: string;
  summary?: string;
  files: {
    path: string;
    hashes: { sha1: string; sha512: string };
    env?: { client: SideSupport; server: SideSupport };
    downloads: string[];
    fileSize: number;
  }[];
  dependencies: Record<string, string>;
}
