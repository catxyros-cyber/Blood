/**
 * Typed bridge to BLOOD's Rust commands (src-tauri/src). The Platform object
 * built here is what the launcher core runs against in the app.
 */

import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import {
  toBloomError,
  type Arch,
  type DirEntry,
  type DownloadItem,
  type DownloadOptions,
  type ExtractOptions,
  type HttpRequest,
  type HttpResponse,
  type JavaToolRequest,
  type JavaToolResult,
  type OsName,
  type Platform,
} from "../core/platform";
import type { Bundle } from "../core/bloomclient/support";

async function call<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  try {
    return await invoke<T>(cmd, args);
  } catch (e) {
    throw toBloomError(e);
  }
}

export interface AppPaths {
  dataDir: string;
  sep: string;
  platform: string;
  arch: string;
  version: string;
}

export interface SystemInfo {
  totalMemory: number;
  availableMemory: number;
  cpuBrand: string;
  logicalCores: number;
  physicalCores: number | null;
  os: string;
  gpus: string[];
}

export interface JavaInfo {
  path: string;
  version: string;
  major: number;
  is64bit: boolean;
}

export interface ProcessStats {
  memoryBytes: number;
  cpuPercent: number;
}

export const native = {
  appPaths: () => call<AppPaths>("app_paths"),
  systemInfo: () => call<SystemInfo>("system_info"),
  javaProbe: (path: string) => call<JavaInfo>("java_probe", { path }),
  findJavas: () => call<string[]>("find_javas"),
  processStats: (pid: number) => call<ProcessStats | null>("process_stats", { pid }),
  runningGames: () => call<{ instanceId: string; pid: number }[]>("running_games"),
  launchGame: (req: { instanceId: string; java: string; args: string[]; cwd: string; env?: Record<string, string>; logFile: string }) =>
    call<number>("launch_game", { req }),
  killGame: (instanceId: string) => call<void>("kill_game", { instanceId }),
  cancelTask: (taskId: string) => call<void>("cancel_task", { taskId }),
  openPath: (path: string) => call<void>("open_path", { path }),
  openUrl: (url: string) => call<void>("open_url", { url }),
  pickFiles: (options: { title?: string; filters?: [string, string[]][]; multiple?: boolean; directory?: boolean }) =>
    call<string[]>("pick_files", { options: { filters: [], multiple: false, directory: false, ...options } }),
  pickGameDir: () => call<string | null>("pick_game_dir"),
  listImportableFolder: (path: string) => call<DirEntry[]>("list_importable_folder", { path }),
  importFiles: (sources: string[], destDir: string) => call<{ copied: string[]; conflicts: string[] }>("import_files", { sources, destDir }),
  copyDir: (from: string, to: string, skip?: string[]) => call<void>("fs_copy_dir", { from, to, skip }),
  stat: (path: string) => call<DirEntry | null>("fs_stat", { path }),
  readTail: (path: string, maxBytes: number) => call<string | null>("fs_read_tail", { path, maxBytes }),
  zipReadDataUrl: (path: string, entry: string) => call<string | null>("zip_read_data_url", { path, entry }),
  autostartGet: () => call<boolean>("autostart_get"),
  autostartSet: (enabled: boolean) => call<void>("autostart_set", { enabled }),
  openLogDir: () => call<void>("open_log_dir"),
  /** SHA-256 (hex) of this PC's machine id, for binding license keys. */
  machineId: () => call<string>("machine_id"),
  secretGet: (key: string) => call<string | null>("secret_get", { key }),
  secretSet: (key: string, value: string) => call<void>("secret_set", { key, value }),
  secretDelete: (key: string) => call<void>("secret_delete", { key }),
  bloomClientBundle: () => call<Bundle | null>("bloom_client_bundle"),
  bloomClientInstall: (loader: string, modsDir: string) => call<string>("bloom_client_install", { loader, modsDir }),
  readDataUrl: (path: string, maxBytes: number) => call<string | null>("fs_read_data_url", { path, maxBytes }),
  /** Opens Microsoft's sign-in page in a window; resolves with the redirect URL, or null if closed. */
  msLoginWindow: (authorizeUrl: string) => call<string | null>("ms_login_window", { authorizeUrl }),
};

function osName(p: string): OsName {
  if (p === "windows") return "windows";
  if (p === "macos") return "osx";
  return "linux";
}

function archName(a: string): Arch {
  if (a === "aarch64") return "arm64";
  if (a === "x86") return "x86";
  return "x64";
}

export function createPlatform(paths: AppPaths): Platform {
  return {
    os: osName(paths.platform),
    arch: archName(paths.arch),
    sep: paths.sep,
    dataDir: paths.dataDir,
    request: (req: HttpRequest) => call<HttpResponse>("http_request", { req }),
    download: (items: DownloadItem[], opts: DownloadOptions) =>
      call("download_batch", { batch: { taskId: opts.taskId, items, concurrency: opts.concurrency, verify: opts.verify } }),
    readText: (path) => call("fs_read_text", { path }),
    writeText: (path, contents) => call("fs_write_text", { path, contents }),
    exists: (path) => call("fs_exists", { path }),
    list: (path) => call("fs_list", { path }),
    mkdir: (path) => call("fs_mkdir", { path }),
    trash: (path) => call("fs_trash", { path }),
    removeInternal: (path) => call("fs_remove_internal", { path }),
    rename: (from, to) => call("fs_rename", { from, to }),
    copy: (from, to, overwrite) => call("fs_copy", { from, to, overwrite }),
    hash: (path, algo) => call("fs_hash", { path, algo }),
    zipReadText: (path, entry) => call("zip_read_text", { path, entry }),
    zipList: (path) => call("zip_list", { path }),
    zipExtract: (path, dest, options?: ExtractOptions) => call("zip_extract", { path, dest, options }),
    runJavaTool: (req: JavaToolRequest) => call<JavaToolResult>("run_java_tool", { req }),
  };
}

export interface DownloadProgressEvent {
  taskId: string;
  done: number;
  total: number;
  bytes: number;
  totalBytes: number;
}

export const events = {
  onDownloadProgress: (cb: (e: DownloadProgressEvent) => void): Promise<UnlistenFn> =>
    listen<DownloadProgressEvent>("download://progress", (e) => cb(e.payload)),
  onGameLog: (cb: (e: { instanceId: string; lines: { stream: "stdout" | "stderr"; line: string }[] }) => void) =>
    listen<{ instanceId: string; lines: { stream: "stdout" | "stderr"; line: string }[] }>("game://log", (e) => cb(e.payload)),
  onGameExit: (cb: (e: { instanceId: string; code: number | null; killed: boolean }) => void) =>
    listen<{ instanceId: string; code: number | null; killed: boolean }>("game://exit", (e) => cb(e.payload)),
  onDrag: (cb: (e: { kind: "enter" | "drop" | "leave"; paths: string[] }) => void) =>
    listen<{ kind: "enter" | "drop" | "leave"; paths: string[] }>("bloom://drag", (e) => cb(e.payload)),
};
