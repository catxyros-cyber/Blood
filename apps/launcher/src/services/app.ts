/**
 * Application state: settings, accounts, installations, background tasks,
 * running games, notifications and navigation.
 */

import type { Instance } from "../core/instances/model";
import { layout, type Layout } from "../core/instances/model";
import type { Platform } from "../core/platform";
import { toBloomError } from "../core/platform";
import type { PresetId } from "../core/perf/presets";
import type { JvmPreset } from "../core/perf/memory";
import type { GcStats } from "../core/perf/gclog";
import type { Announcement, Entitlement, PlansResponse, PublicUser } from "@bloom/shared";
import { join } from "../core/util/path";
import { createPlatform, events, native, type AppPaths, type SystemInfo } from "./native";
import { createStore } from "./store";

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

export type OverlayCorner = "TOP_LEFT" | "TOP_RIGHT" | "BOTTOM_LEFT" | "BOTTOM_RIGHT";

export type ThemeId = "crimson" | "obsidian" | "ash" | "ember" | "mono";
export const THEMES: { id: ThemeId; name: string; accent: string; description: string }[] = [
  { id: "crimson", name: "Crimson", accent: "#e3192f", description: "BLOOD's signature: deep crimson on warm black." },
  { id: "obsidian", name: "Obsidian", accent: "#ff1f3d", description: "Pure black surfaces for OLED screens, with a hotter red." },
  { id: "ash", name: "Ash", accent: "#dc2f3c", description: "Neutral charcoal and grey with crimson highlights." },
  { id: "ember", name: "Ember", accent: "#ff4a1f", description: "Warmer, red-orange accents on smoky black." },
  { id: "mono", name: "Monochrome", accent: "#ececec", description: "Black, white and grey. No colour at all." },
];

export interface BackgroundSettings {
  /** "theme": the theme's built-in art; "custom": the user's image; "none": plain colour. */
  mode: "theme" | "custom" | "none";
  /** Path of the user's image, copied into BLOOD's data folder. */
  custom: string | null;
  /** 0..0.9 — how much the art is darkened behind pages other than Home. */
  dim: number;
  /** Blur in px applied to the art behind pages other than Home. */
  blur: number;
}

export interface Settings {
  theme: ThemeId;
  /** Used only when accentFromTheme is false. */
  accent: string;
  accentFromTheme: boolean;
  /** 0..100: how saturated the accent is and how strong glows are (0 = flat, 100 = vivid). */
  accentIntensity: number;
  background: BackgroundSettings;
  /** Rising embers on the Home screen. */
  petals: boolean;
  reduceMotion: boolean;
  startPage: "home" | "library";
  /** What the launcher window does when a game starts. */
  onLaunch: "keep" | "minimize";
  memory: { minMb: number; maxMb: number } | null;
  javaPath: string | null;
  jvmPreset: JvmPreset["id"];
  jvmArgs: string[];
  gameArgs: string[];
  resolution: { width: number; height: number } | null;
  fullscreen: boolean;
  showSnapshots: boolean;
  modUpdateNotifications: boolean;
  autoUpdate: boolean;
  downloadThreads: number;
  defaultPreset: PresetId;
  overlay: { enabled: boolean; corner: OverlayCorner; extended: boolean; contrast: "NONE" | "BACKGROUND" | "SHADOW" };
  diagnostics: { verboseLogs: boolean };
  /** Advanced: override the Azure application (client) id baked into this build. */
  msClientId: string;
  /** Show Mojang's patch notes on Home (contacts launchercontent.mojang.com). */
  news: boolean;
  /** Show BLOOD announcements (contacts the BLOOD API). */
  announcements: boolean;
  /** Premium: keep settings and installation profiles in sync across PCs. */
  cloudSync: boolean;
  /** Where new installations' game folders go (null = inside BLOOD's data folder). */
  defaultGameRoot: string | null;
  selectedInstanceId: string | null;
  activeAccountId: string | null;
}

export const DEFAULT_SETTINGS: Settings = {
  theme: "crimson",
  accent: "#e3192f",
  accentFromTheme: true,
  accentIntensity: 70,
  background: { mode: "theme", custom: null, dim: 0.78, blur: 12 },
  petals: true,
  reduceMotion: false,
  startPage: "home",
  onLaunch: "minimize",
  memory: null,
  javaPath: null,
  jvmPreset: "mojang",
  jvmArgs: [],
  gameArgs: [],
  resolution: null,
  fullscreen: false,
  showSnapshots: false,
  modUpdateNotifications: true,
  autoUpdate: true,
  downloadThreads: 16,
  defaultPreset: "balanced",
  overlay: { enabled: false, corner: "TOP_LEFT", extended: true, contrast: "BACKGROUND" },
  diagnostics: { verboseLogs: false },
  msClientId: "",
  news: true,
  announcements: true,
  cloudSync: true,
  defaultGameRoot: null,
  selectedInstanceId: null,
  activeAccountId: null,
};

// ---------------------------------------------------------------------------
// Accounts (no tokens here — those live in the OS credential store)
// ---------------------------------------------------------------------------

export interface Account {
  id: string; // Minecraft profile UUID
  username: string;
  xuid: string;
  skinUrl?: string;
  addedAt: number;
  lastVerifiedAt: number;
}

// ---------------------------------------------------------------------------
// Tasks, toasts, games, routes
// ---------------------------------------------------------------------------

export interface Task {
  id: string;
  title: string;
  stage: string;
  /** 0..1, or null when indeterminate. */
  progress: number | null;
  detail?: string;
  instanceId?: string;
  cancellable: boolean;
}

export interface Toast {
  id: number;
  kind: "success" | "error" | "info" | "warning";
  title: string;
  message?: string;
  action?: { label: string; run: () => void };
}

export interface LogLine {
  t: number;
  level: string;
  text: string;
}

export interface RunningGame {
  instanceId: string;
  pid: number;
  startedAt: number;
  prepareMs: number;
  startupMs?: number;
  stats?: { memoryBytes: number; cpuPercent: number; at: number }[];
  /** Measured from the JVM's GC log (Java 9+). */
  gc?: GcStats;
  /** Heap limit passed to the JVM (-Xmx). */
  maxHeapMb?: number;
  javaMajor?: number;
}

/** What was measured during the most recent session of an installation. */
export interface SessionSummary {
  instanceId: string;
  startedAt: number;
  endedAt: number;
  prepareMs: number;
  startupMs?: number;
  peakMemoryBytes?: number;
  avgCpuPercent?: number;
  gc?: GcStats;
  maxHeapMb?: number;
  exitCode: number | null;
}

export interface PremiumState {
  /** False when this build has no BLOOD API URL (online features unavailable). */
  configured: boolean;
  status: "signed_out" | "connecting" | "ready" | "error";
  user?: PublicUser;
  entitlement?: Entitlement;
  /** Shown from the local cache because the server couldn't be reached. */
  cached?: boolean;
  error?: string;
  plans?: PlansResponse;
  /** Waiting for a checkout started from this PC to complete. */
  awaitingPayment?: boolean;
  /** Show the "Welcome to Premium" message. */
  welcome?: boolean;
  sync: { state: "idle" | "syncing" | "conflict" | "error"; lastSyncAt?: number; error?: string };
  /** Premium from a license key redeemed on this PC (separate from the Minecraft-account purchase). */
  license?: LicenseState;
}

export interface LicenseState {
  /**
   * none: no key on this PC · checking · active · elsewhere: the key is in use on another PC ·
   * expired · revoked · error: the server couldn't be reached and there's no recent check to fall back on.
   */
  status: "none" | "checking" | "active" | "elsewhere" | "expired" | "revoked" | "error";
  /** Last 5 characters of the key. */
  hint?: string;
  expiresAt?: number | null;
  /** When the key may next be moved to another PC (null/undefined = now). */
  nextMoveAt?: number | null;
  checkedAt?: number;
  /** Shown from the last successful check because the server couldn't be reached. */
  cached?: boolean;
  error?: string;
}

export type Route =
  | { page: "home" }
  | { page: "library" }
  | { page: "instance"; id: string; tab?: "content" | "resourcepacks" | "shaderpacks" | "worlds" | "logs" | "settings" }
  | { page: "browse"; type?: "mod" | "modpack" | "resourcepack" | "shader"; instanceId?: string }
  | { page: "project"; id: string; instanceId?: string }
  | { page: "performance"; instanceId?: string }
  | { page: "accounts" }
  | { page: "premium" }
  | { page: "settings"; section?: string }
  | { page: "customize"; instanceId?: string }
  | { page: "mods"; instanceId?: string; category?: ModCategory };

export type ModCategory = "all" | "hud" | "performance" | "visuals" | "utility";

export interface AppState {
  ready: boolean;
  fatal: string | null;
  paths: AppPaths | null;
  system: SystemInfo | null;
  settings: Settings;
  accounts: Account[];
  instances: Instance[];
  tasks: Task[];
  toasts: Toast[];
  running: Record<string, RunningGame>;
  sessions: Record<string, SessionSummary>;
  logs: Record<string, LogLine[]>;
  route: Route;
  dragActive: boolean;
  premium: PremiumState;
  announcements: Announcement[];
}

export const app = createStore<AppState>({
  ready: false,
  fatal: null,
  paths: null,
  system: null,
  settings: DEFAULT_SETTINGS,
  accounts: [],
  instances: [],
  tasks: [],
  toasts: [],
  running: {},
  sessions: {},
  logs: {},
  route: { page: "home" },
  dragActive: false,
  premium: { configured: false, status: "signed_out", sync: { state: "idle" } },
  announcements: [],
});

let platform: Platform | null = null;
export function P(): Platform {
  if (!platform) throw new Error("BLOOD is still starting");
  return platform;
}
export function L(): Layout {
  const p = P();
  return layout(p.dataDir, p.sep);
}
export function path(...parts: string[]): string {
  return join(P().sep, ...parts);
}

export function navigate(route: Route) {
  app.set({ route });
}

// ---------------------------------------------------------------------------
// Toasts
// ---------------------------------------------------------------------------

let toastSeq = 0;
export function toast(t: Omit<Toast, "id">, ms = 6000): number {
  const id = ++toastSeq;
  app.set((s) => ({ toasts: [...s.toasts, { ...t, id }].slice(-5) }));
  if (ms > 0) setTimeout(() => dismissToast(id), ms);
  return id;
}
/** Change a visible toast in place (e.g. a countdown). */
export function updateToast(id: number, patch: Partial<Omit<Toast, "id">>) {
  app.set((s) => ({ toasts: s.toasts.map((t) => (t.id === id ? { ...t, ...patch } : t)) }));
}

export function dismissToast(id: number) {
  app.set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
}

const FRIENDLY: Record<string, string> = {
  offline: "You appear to be offline. Check your internet connection.",
  timeout: "The server took too long to respond. Try again.",
  disk_full: "Your disk is full. Free up some space and try again.",
  hash_mismatch: "A downloaded file was corrupted. Try again — BLOOD will re-download it.",
  java_missing: "Java could not be found. Check the Java setting for this installation.",
  permission_denied: "Windows denied access to a file. Close Minecraft and any program using the folder, then try again.",
  host_not_allowed: "BLOOD blocked a download from an unexpected website.",
};

export function reportError(title: string, e: unknown) {
  const err = toBloomError(e);
  if (err.code === "cancelled") return;
  console.error(title, err);
  toast({ kind: "error", title, message: errorMessage(err) }, 10000);
}

/** A message for the user: our friendly wording for well-known failures, otherwise the specific error. */
export function errorMessage(e: unknown): string {
  const err = toBloomError(e);
  return FRIENDLY[err.code] ?? (err.message || "Something went wrong.");
}

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

export function startTask(t: Omit<Task, "progress" | "stage"> & { stage?: string }): Task {
  const task: Task = { progress: null, stage: t.stage ?? "Starting", ...t };
  app.set((s) => ({ tasks: [...s.tasks.filter((x) => x.id !== t.id), task] }));
  return task;
}
export function updateTask(id: string, patch: Partial<Task>) {
  app.set((s) => ({ tasks: s.tasks.map((t) => (t.id === id ? { ...t, ...patch } : t)) }));
}
export function endTask(id: string) {
  app.set((s) => ({ tasks: s.tasks.filter((t) => t.id !== id) }));
}

/** Map download progress events (task ids look like "<taskId>:<stage>") onto tasks. */
function wireDownloadProgress() {
  void events.onDownloadProgress((e) => {
    const base = e.taskId.split(":")[0]!;
    const task = app.get().tasks.find((t) => t.id === base);
    if (!task || e.total === 0) return;
    const frac = e.totalBytes > 0 ? Math.min(1, e.bytes / e.totalBytes) : e.done / e.total;
    const mb = (n: number) => (n / 1_048_576).toFixed(n > 100 * 1_048_576 ? 0 : 1);
    updateTask(base, {
      progress: frac,
      detail: e.totalBytes > 0 ? `${mb(e.bytes)} / ${mb(e.totalBytes)} MB` : `${e.done} / ${e.total} files`,
    });
  });
}

// ---------------------------------------------------------------------------
// Persistence
// ---------------------------------------------------------------------------

function settingsPath() {
  return path(P().dataDir, "settings.json");
}
function accountsPath() {
  return path(P().dataDir, "accounts.json");
}

let saveTimer: ReturnType<typeof setTimeout> | null = null;
export function updateSettings(patch: Partial<Settings>) {
  app.set((s) => ({ settings: { ...s.settings, ...patch } }));
  applyTheme(app.get().settings);
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    P()
      .writeText(settingsPath(), JSON.stringify(app.get().settings, null, 2))
      .catch((e) => reportError("Couldn't save settings", e));
  }, 300);
}

export async function saveAccounts() {
  await P().writeText(accountsPath(), JSON.stringify(app.get().accounts, null, 2));
}

export function applyTheme(s: Settings) {
  const root = document.documentElement;
  root.dataset.theme = s.theme;
  root.dataset.motion = s.reduceMotion ? "reduce" : "full";
  if (s.accentFromTheme) root.style.removeProperty("--accent-base");
  else root.style.setProperty("--accent-base", s.accent);
  const intensity = Math.max(0, Math.min(100, Number.isFinite(s.accentIntensity) ? s.accentIntensity : 70)) / 100;
  root.style.setProperty("--intensity", intensity.toFixed(2));
  try {
    // Lets the splash screen show the right colours before settings are read.
    localStorage.setItem("blood.theme", s.theme);
    // The loading screen shows your own background too (read before settings load).
    if (s.background.mode === "custom" && s.background.custom) localStorage.setItem("blood.splashBg", s.background.custom);
    else localStorage.removeItem("blood.splashBg");
  } catch {
    /* storage unavailable */
  }
}

/** The accent currently in effect (theme accent unless a custom one is chosen). */
export function effectiveAccent(s: Settings): string {
  return s.accentFromTheme ? (THEMES.find((t) => t.id === s.theme)?.accent ?? "#e3192f") : s.accent;
}

/** Settings from older builds (Bloom's cherry/forest/lavender/ocean themes, graphite/midnight/oled before that). */
export function migrateSettings(stored: Partial<Settings> & { theme?: string }): Partial<Settings> {
  const out = { ...stored } as Partial<Settings>;
  const legacy: Record<string, ThemeId> = { oled: "obsidian", midnight: "obsidian", graphite: "ash", forest: "ash", ocean: "obsidian" };
  if (stored.theme && !THEMES.some((t) => t.id === stored.theme)) out.theme = legacy[stored.theme] ?? "crimson";
  if (stored.accentFromTheme === undefined && stored.accent) {
    // Keep a deliberately chosen accent; the old default green follows the new theme instead.
    out.accentFromTheme = stored.accent.toLowerCase() === "#4ade80";
  }
  return out;
}

async function readJson<T>(p: string, fallback: T): Promise<T> {
  try {
    const text = await P().readText(p);
    return text ? (JSON.parse(text) as T) : fallback;
  } catch {
    return fallback;
  }
}

export async function boot(loadInstances: () => Promise<Instance[]>) {
  try {
    const paths = await native.appPaths();
    platform = createPlatform(paths);
    const stored = migrateSettings(await readJson<Partial<Settings>>(settingsPath(), {}));
    const settings: Settings = {
      ...DEFAULT_SETTINGS,
      ...stored,
      overlay: { ...DEFAULT_SETTINGS.overlay, ...(stored.overlay ?? {}) },
      background: { ...DEFAULT_SETTINGS.background, ...(stored.background ?? {}) },
    };
    applyTheme(settings);
    const accounts = await readJson<Account[]>(accountsPath(), []);
    const instances = await loadInstances();
    app.set({ paths, settings, accounts, instances, ready: true, route: { page: settings.startPage } });
    wireDownloadProgress();
    void events.onDrag((e) => app.set({ dragActive: e.kind === "enter" }));
    native
      .systemInfo()
      .then((system) => app.set({ system }))
      .catch(() => undefined);
  } catch (e) {
    app.set({ fatal: errorMessage(e), ready: false });
  }
}

export const MS_CLIENT_ID_BUILD: string = (import.meta as unknown as { env?: Record<string, string> }).env?.VITE_MS_CLIENT_ID ?? "";
export function msClientId(): string {
  return app.get().settings.msClientId.trim() || MS_CLIENT_ID_BUILD;
}
