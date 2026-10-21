/** In-memory Platform used by unit tests. Routes HTTP by URL and records downloads. */

import type { DirEntry, DownloadItem, HttpRequest, HttpResponse, Platform } from "../platform";
import { BloomError } from "../platform";

export type Route = (req: HttpRequest) => HttpResponse | Promise<HttpResponse>;

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): HttpResponse {
  return { status, headers, body: JSON.stringify(body) };
}

export class FakePlatform implements Platform {
  os: Platform["os"] = "windows";
  arch: Platform["arch"] = "x64";
  sep = "\\";
  dataDir = "C:\\Users\\steve\\AppData\\Roaming\\bloom";
  files = new Map<string, string>();
  routes = new Map<string, Route>();
  requests: HttpRequest[] = [];
  downloads: { taskId: string; items: DownloadItem[]; verify?: string }[] = [];
  extracted: { path: string; dest: string; exclude?: string[] }[] = [];
  offline = false;
  /** Content to place at a download's path, keyed by URL (for JSON files fetched via download). */
  downloadContent = new Map<string, string>();

  /** Register a handler, a ready-made response, or a value to serve as JSON. */
  route(url: string, r: Route): void;
  route(url: string, r: HttpResponse | object | unknown[]): void;
  route(url: string, r: unknown) {
    if (typeof r === "function") this.routes.set(url, r as Route);
    else if (r && typeof r === "object" && "status" in r && "body" in r) this.routes.set(url, () => r as HttpResponse);
    else this.routes.set(url, () => json(r));
  }

  async request(req: HttpRequest): Promise<HttpResponse> {
    this.requests.push(req);
    if (this.offline) throw new BloomError("offline", "offline");
    const base = req.url.split("?")[0]!;
    const r = this.routes.get(req.url) ?? this.routes.get(base);
    if (!r) return { status: 404, headers: {}, body: "not found" };
    return r(req);
  }

  async download(items: DownloadItem[], opts: { taskId: string; verify?: string }) {
    if (this.offline && items.some((i) => !this.files.has(i.path))) throw new BloomError("offline", "offline");
    this.downloads.push({ taskId: opts.taskId, items, verify: opts.verify });
    let downloaded = 0;
    for (const i of items) {
      if (this.files.has(i.path)) continue;
      this.files.set(i.path, this.downloadContent.get(i.url) ?? `<binary ${i.url}>`);
      downloaded++;
    }
    return { downloaded, skipped: items.length - downloaded };
  }

  async readText(path: string) {
    return this.files.get(path) ?? null;
  }
  async writeText(path: string, contents: string) {
    this.files.set(path, contents);
  }
  async exists(path: string) {
    return this.files.has(path) || [...this.files.keys()].some((k) => k.startsWith(path + this.sep));
  }
  async list(path: string): Promise<DirEntry[]> {
    const prefix = path + this.sep;
    const names = new Set<string>();
    for (const k of this.files.keys()) if (k.startsWith(prefix)) names.add(k.slice(prefix.length).split(this.sep)[0]!);
    return [...names].map((name) => ({
      name,
      path: prefix + name,
      isDir: !this.files.has(prefix + name),
      size: this.files.get(prefix + name)?.length ?? 0,
      modifiedMs: 0,
    }));
  }
  async mkdir() {}
  async trash(path: string) {
    this.files.delete(path);
  }
  async removeInternal(path: string) {
    for (const k of [...this.files.keys()]) if (k === path || k.startsWith(path + this.sep)) this.files.delete(k);
  }
  async rename(from: string, to: string) {
    if (this.files.has(to)) throw new BloomError("already_exists", to);
    const v = this.files.get(from);
    if (v === undefined) throw new BloomError("not_found", from);
    this.files.delete(from);
    this.files.set(to, v);
  }
  async copy(from: string, to: string) {
    this.files.set(to, this.files.get(from) ?? "");
  }
  /** Hashes to report for specific paths (default: forty zeros). */
  hashes = new Map<string, string>();
  async hash(path: string) {
    return this.hashes.get(path) ?? "0".repeat(40);
  }
  zipEntries = new Map<string, Record<string, string>>();
  async zipReadText(path: string, entry: string) {
    return this.zipEntries.get(path)?.[entry] ?? null;
  }
  async zipList(path: string) {
    return Object.keys(this.zipEntries.get(path) ?? {});
  }
  /** Records the call and, for archives registered in `zipEntries`, materialises matching entries. */
  async zipExtract(path: string, dest: string, opts?: { stripPrefix?: string; exclude?: string[]; overwrite?: boolean }) {
    this.extracted.push({ path, dest, exclude: opts?.exclude });
    const entries = this.zipEntries.get(path);
    if (!entries) return { extracted: 1, skippedExisting: [] };
    let extracted = 0;
    const skippedExisting: string[] = [];
    for (const [name, content] of Object.entries(entries)) {
      if (opts?.exclude?.some((e) => name.startsWith(e))) continue;
      let rel = name;
      if (opts?.stripPrefix) {
        if (!name.startsWith(opts.stripPrefix) || name.length === opts.stripPrefix.length) continue;
        rel = name.slice(opts.stripPrefix.length);
      }
      if (rel.endsWith("/")) continue;
      const target = `${dest}${this.sep}${rel.split("/").join(this.sep)}`;
      if (this.files.has(target) && !opts?.overwrite) {
        skippedExisting.push(rel);
        continue;
      }
      this.files.set(target, content);
      extracted++;
    }
    return { extracted, skippedExisting };
  }

  javaRuns: import("../platform").JavaToolRequest[] = [];
  /** Simulates a Java tool run; by default succeeds. Tests can replace it. */
  javaTool: (req: import("../platform").JavaToolRequest) => import("../platform").JavaToolResult = () => ({ code: 0, output: "" });
  async runJavaTool(req: import("../platform").JavaToolRequest) {
    this.javaRuns.push(req);
    return this.javaTool(req);
  }
}
