import { BloomError, type HttpRequest, type HttpResponse } from "../platform";
import type {
  CategoryTag,
  GameVersionTag,
  Project,
  ProjectType,
  SearchResult,
  TeamMember,
  Version,
} from "./types";

const BASE = "https://api.modrinth.com/v2";

export type SearchIndex = "relevance" | "downloads" | "follows" | "newest" | "updated";

export interface SearchParams {
  query?: string;
  projectType: ProjectType;
  gameVersion?: string;
  /** Modrinth loader tags, e.g. ["fabric"] or ["quilt","fabric"]. Ignored for shaders/resource packs. */
  loaders?: string[];
  categories?: string[];
  /** Only projects that work on the client. */
  clientOnly?: boolean;
  index?: SearchIndex;
  offset?: number;
  limit?: number;
}

/** Build Modrinth's facet array: outer array = AND, inner arrays = OR. */
export function buildFacets(p: SearchParams): string[][] {
  const facets: string[][] = [[`project_type:${p.projectType}`]];
  if (p.gameVersion) facets.push([`versions:${p.gameVersion}`]);
  if (p.loaders && p.loaders.length > 0) facets.push(p.loaders.map((l) => `categories:${l}`));
  for (const c of p.categories ?? []) facets.push([`categories:${c}`]);
  if (p.clientOnly) facets.push(["client_side:required", "client_side:optional"]);
  return facets;
}

/**
 * Thin, rate-limit-aware Modrinth client. Modrinth allows 300 requests per
 * minute per IP and reports the budget in `X-Ratelimit-*` headers; we pause
 * when the budget runs out and retry once on HTTP 429.
 */
export class ModrinthClient {
  private remaining = 300;
  private resetAt = 0;
  private cache = new Map<string, { at: number; value: unknown }>();

  constructor(
    private http: (req: HttpRequest) => Promise<HttpResponse>,
    private cacheMs = 60_000,
  ) {}

  private async wait(ms: number) {
    await new Promise((r) => setTimeout(r, ms));
  }

  private async call<T>(method: "GET" | "POST", path: string, json?: unknown, attempt = 0): Promise<T> {
    const cacheKey = method === "GET" ? path : "";
    if (cacheKey) {
      const hit = this.cache.get(cacheKey);
      if (hit && Date.now() - hit.at < this.cacheMs) return hit.value as T;
    }
    if (this.remaining <= 1 && Date.now() < this.resetAt) await this.wait(this.resetAt - Date.now());

    const res = await this.http({ method, url: `${BASE}${path}`, json, timeoutMs: 20_000 });
    const remaining = Number(res.headers["x-ratelimit-remaining"]);
    const reset = Number(res.headers["x-ratelimit-reset"]);
    if (Number.isFinite(remaining)) this.remaining = remaining;
    if (Number.isFinite(reset)) this.resetAt = Date.now() + reset * 1000;

    if (res.status === 429 && attempt < 2) {
      await this.wait(Math.max(1000, (Number.isFinite(reset) ? reset : 5) * 1000));
      return this.call<T>(method, path, json, attempt + 1);
    }
    if (res.status === 404) throw new BloomError("http_404", "Not found on Modrinth");
    if (res.status >= 500) throw new BloomError("modrinth_down", "Modrinth is having problems right now. Try again shortly.");
    if (res.status < 200 || res.status >= 300) throw new BloomError("http_error", `Modrinth returned HTTP ${res.status}`);
    let value: T;
    try {
      value = JSON.parse(res.body) as T;
    } catch {
      throw new BloomError("invalid_json", "Modrinth returned an invalid response");
    }
    if (cacheKey) this.cache.set(cacheKey, { at: Date.now(), value });
    return value;
  }

  search(p: SearchParams): Promise<SearchResult> {
    const qs = new URLSearchParams();
    if (p.query) qs.set("query", p.query);
    qs.set("facets", JSON.stringify(buildFacets(p)));
    qs.set("index", p.index ?? "relevance");
    qs.set("offset", String(p.offset ?? 0));
    qs.set("limit", String(Math.min(p.limit ?? 20, 100)));
    return this.call("GET", `/search?${qs.toString()}`);
  }

  project(idOrSlug: string): Promise<Project> {
    return this.call("GET", `/project/${encodeURIComponent(idOrSlug)}`);
  }

  projects(ids: string[]): Promise<Project[]> {
    if (ids.length === 0) return Promise.resolve([]);
    return this.call("GET", `/projects?ids=${encodeURIComponent(JSON.stringify(ids))}`);
  }

  members(idOrSlug: string): Promise<TeamMember[]> {
    return this.call("GET", `/project/${encodeURIComponent(idOrSlug)}/members`);
  }

  versions(idOrSlug: string, filter?: { loaders?: string[]; gameVersions?: string[] }): Promise<Version[]> {
    const qs = new URLSearchParams();
    if (filter?.loaders?.length) qs.set("loaders", JSON.stringify(filter.loaders));
    if (filter?.gameVersions?.length) qs.set("game_versions", JSON.stringify(filter.gameVersions));
    const q = qs.toString();
    return this.call("GET", `/project/${encodeURIComponent(idOrSlug)}/version${q ? `?${q}` : ""}`);
  }

  version(id: string): Promise<Version> {
    return this.call("GET", `/version/${encodeURIComponent(id)}`);
  }

  /** Identify files by hash. Returns a map of hash → version for files Modrinth knows. */
  versionsByHash(hashes: string[], algorithm: "sha1" | "sha512" = "sha1"): Promise<Record<string, Version>> {
    if (hashes.length === 0) return Promise.resolve({});
    return this.call("POST", "/version_files", { hashes, algorithm });
  }

  /** Latest compatible version for each installed file (by hash). */
  latestByHash(
    hashes: string[],
    loaders: string[],
    gameVersions: string[],
    algorithm: "sha1" | "sha512" = "sha1",
  ): Promise<Record<string, Version>> {
    if (hashes.length === 0) return Promise.resolve({});
    return this.call("POST", "/version_files/update", { hashes, algorithm, loaders, game_versions: gameVersions });
  }

  categories(): Promise<CategoryTag[]> {
    return this.call("GET", "/tag/category");
  }

  gameVersions(): Promise<GameVersionTag[]> {
    return this.call("GET", "/tag/game_version");
  }
}

export function projectUrl(projectType: ProjectType, slug: string): string {
  return `https://modrinth.com/${projectType}/${slug}`;
}
