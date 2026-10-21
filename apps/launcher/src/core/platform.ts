/**
 * The I/O surface the launcher core needs. In the app it is implemented by the
 * Rust layer (see services/native.ts); in tests by an in-memory fake. Keeping
 * core logic behind this interface is what makes it unit-testable.
 */

export type OsName = "windows" | "linux" | "osx";
export type Arch = "x64" | "x86" | "arm64";

export interface HttpRequest {
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  url: string;
  headers?: Record<string, string>;
  body?: string;
  json?: unknown;
  form?: Record<string, string>;
  timeoutMs?: number;
}

export interface HttpResponse {
  status: number;
  headers: Record<string, string>;
  body: string;
}

export interface DownloadItem {
  url: string;
  /** Absolute path inside BLOOD's data dir (or an allowed game directory). */
  path: string;
  sha1?: string;
  sha512?: string;
  size?: number;
  executable?: boolean;
}

export interface DownloadOptions {
  taskId: string;
  concurrency?: number;
  /** "size" (fast, default) or "hash" (re-verify everything, used by Repair). */
  verify?: "size" | "hash";
}

export interface DirEntry {
  name: string;
  path: string;
  isDir: boolean;
  size: number;
  modifiedMs: number;
}

export interface ExtractOptions {
  stripPrefix?: string;
  exclude?: string[];
  overwrite?: boolean;
}

/** A short-lived Java program (Forge/NeoForge installer processors). */
export interface JavaToolRequest {
  java: string;
  classpath: string[];
  mainClass: string;
  args: string[];
  cwd: string;
  /** Used to cancel. */
  taskId: string;
}

export interface JavaToolResult {
  code: number | null;
  /** Last lines of combined stdout/stderr, for error reports. */
  output: string;
}

export interface Platform {
  os: OsName;
  arch: Arch;
  sep: string;
  dataDir: string;

  request(req: HttpRequest): Promise<HttpResponse>;
  download(items: DownloadItem[], opts: DownloadOptions): Promise<{ downloaded: number; skipped: number }>;

  readText(path: string): Promise<string | null>;
  writeText(path: string, contents: string): Promise<void>;
  exists(path: string): Promise<boolean>;
  list(path: string): Promise<DirEntry[]>;
  mkdir(path: string): Promise<void>;
  /** Move to the Recycle Bin. */
  trash(path: string): Promise<void>;
  /** Permanently delete a launcher cache path. */
  removeInternal(path: string): Promise<void>;
  rename(from: string, to: string): Promise<void>;
  copy(from: string, to: string, overwrite?: boolean): Promise<void>;
  hash(path: string, algo: "sha1" | "sha512"): Promise<string>;

  zipReadText(path: string, entry: string): Promise<string | null>;
  zipList(path: string): Promise<string[]>;
  zipExtract(path: string, dest: string, opts?: ExtractOptions): Promise<{ extracted: number; skippedExisting: string[] }>;

  /** Run a Java program to completion (never the game itself). */
  runJavaTool(req: JavaToolRequest): Promise<JavaToolResult>;
}

export class BloomError extends Error {
  constructor(
    public code: string,
    message: string,
  ) {
    super(message);
    this.name = "BloomError";
  }
}

export function isBloomError(e: unknown): e is BloomError {
  return e instanceof BloomError || (typeof e === "object" && e !== null && "code" in e && "message" in e);
}

/** Normalise anything thrown (including `{code, message}` objects from Rust) into a BloomError. */
export function toBloomError(e: unknown): BloomError {
  if (e instanceof BloomError) return e;
  if (typeof e === "object" && e !== null && "code" in e && "message" in e) {
    const o = e as { code: unknown; message: unknown };
    return new BloomError(String(o.code), String(o.message));
  }
  if (e instanceof Error) return new BloomError("unknown", e.message);
  return new BloomError("unknown", String(e));
}

export async function getJson<T>(p: Platform, url: string, headers?: Record<string, string>): Promise<T> {
  const res = await p.request({ method: "GET", url, headers });
  if (res.status === 404) throw new BloomError("http_404", `Not found: ${url}`);
  if (res.status < 200 || res.status >= 300) {
    throw new BloomError("http_error", `${new URL(url).host} returned HTTP ${res.status}`);
  }
  try {
    return JSON.parse(res.body) as T;
  } catch {
    throw new BloomError("invalid_json", `Invalid response from ${new URL(url).host}`);
  }
}
