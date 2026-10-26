/**
 * The performance doctor: looks at one installation (its mods, video settings, shader state and
 * memory) and lists what costs frame rate, most important first, each with one concrete fix.
 * It only suggests; the player applies each fix (and every fix can be undone). Impact is a
 * category from how these mods and settings behave, never a promised number: the real effect
 * is measured in game (see fpsSessions) and shown as before/after.
 */

import type { VideoSettings } from "./presets";
import { UNLIMITED_FPS } from "./presets";

export type Impact = "high" | "medium" | "low";

export interface DoctorMod {
  id: string;
  name: string;
  version: string;
  fileName: string;
  enabled: boolean;
  modifiedMs: number;
}

export type DoctorAction =
  | { kind: "disable"; fileNames: string[] }
  | { kind: "settings"; patch: Partial<VideoSettings> }
  | { kind: "shaders-off" }
  | { kind: "memory"; maxMb: number }
  | { kind: "motion-look"; profile: string }
  | { kind: "install"; slug: string };

export interface Finding {
  id: string;
  impact: Impact;
  title: string;
  detail: string;
  action?: DoctorAction;
  /** Button text for the action. */
  actionLabel?: string;
}

/**
 * Mods that cost frame rate or memory by design: they do more work every frame, not because
 * they're badly made. Disabling one is a suggestion; the player knows if they need it.
 */
export const COSTLY_MODS: { ids: string[]; title: string; impact: Impact; why: string }[] = [
  { ids: ["flashback"], title: "Flashback", impact: "high", why: "Records everything you do for replays, all the time, which costs CPU, memory and disk while you play. Turn it on only when you want to record." },
  { ids: ["replaymod"], title: "Replay Mod", impact: "high", why: "Records the game for replays while you play, which costs CPU, memory and disk. Turn it on only when you want to record." },
  { ids: ["distanthorizons"], title: "Distant Horizons", impact: "high", why: "Draws terrain far past your render distance and generates it in the background." },
  { ids: ["physicsmod"], title: "Physics Mod", impact: "high", why: "Simulates physics for blocks and mobs on the CPU." },
  { ids: ["naturalmotionblur", "motionblur", "motion_blur", "motionblurmod"], title: "Motion blur", impact: "medium", why: "Adds a full-screen blur pass to every frame." },
  { ids: ["essential", "essential-container", "essential-dependencies"], title: "Essential", impact: "medium", why: "Runs cosmetics, friends and its own networking in the background, which costs memory and some frame rate." },
  { ids: ["immersive_portals", "imm_ptl_core"], title: "Immersive Portals", impact: "medium", why: "Renders the world again through every visible portal." },
  { ids: ["sound_physics_remastered"], title: "Sound Physics Remastered", impact: "medium", why: "Traces sound reflections through the world on the CPU." },
  { ids: ["wakes"], title: "Wakes", impact: "low", why: "Simulates water wakes behind boats and swimmers." },
  { ids: ["lambdynlights", "dynamiclights"], title: "Dynamic lights", impact: "low", why: "Recalculates lighting around moving light sources." },
  { ids: ["entity_model_features", "entity_texture_features"], title: "Entity Model/Texture Features", impact: "low", why: "Custom entity models and textures add rendering work in busy areas." },
  { ids: ["visuality"], title: "Visuality", impact: "low", why: "Adds extra particles around blocks and mobs." },
  { ids: ["particlerain"], title: "Particle Rain", impact: "low", why: "Draws rain and snow with many more particles." },
];

const IMPACT_ORDER: Record<Impact, number> = { high: 0, medium: 1, low: 2 };

export interface DoctorInput {
  mods: DoctorMod[];
  /** Current video settings (null before the first launch). */
  video: VideoSettings | null;
  /** A shader pack is switched on (Iris/Oculus). */
  shadersOn: boolean;
  /** Allocated maximum heap and what MOTION recommends for this PC (MiB). */
  memory?: { maxMb: number | null; recommendedMb: number; maxSafeMb: number };
  /** MOTION Client, when it's on: its current look and which of its costly effects are on. */
  motion?: { profileName: string | null; effects: string[] };
}

/** MOTION Client settings that cost frame rate every frame, and how to name them. */
export const MOTION_EFFECTS: [key: string, label: string][] = [
  ["post.enabled", "shader effects"],
  ["petals.enabled", "petals"],
  ["leaves.enabled", "falling leaves"],
  ["fireflies.enabled", "fireflies"],
  ["dust.enabled", "floating motes"],
];

/** Version-ish comparison: "2.6.26" > "2.6.25", "0.8.15-beta" < "0.8.15". */
function newer(a: string, b: string): number {
  const pa = a.split(/[^0-9]+/).filter(Boolean).map(Number), pb = b.split(/[^0-9]+/).filter(Boolean).map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d) return d;
  }
  return 0;
}

export function diagnose(input: DoctorInput): Finding[] {
  const out: Finding[] = [];
  const enabled = input.mods.filter((m) => m.enabled && m.id);

  // Two copies of one mod: the game loads one at best, and slows down or crashes at worst.
  const byId = new Map<string, DoctorMod[]>();
  for (const m of enabled) byId.set(m.id, [...(byId.get(m.id) ?? []), m]);
  for (const [id, copies] of byId) {
    if (copies.length < 2) continue;
    const sorted = [...copies].sort((a, b) => newer(b.version, a.version) || b.modifiedMs - a.modifiedMs);
    const keep = sorted[0]!;
    const extra = sorted.slice(1);
    out.push({
      id: `duplicate:${id}`,
      impact: "high",
      title: `Two copies of ${keep.name}`,
      detail: `Versions ${copies.map((c) => c.version).join(" and ")} are both enabled, and Minecraft can only use one. Keep ${keep.version}.`,
      action: { kind: "disable", fileNames: extra.map((e) => e.fileName) },
      actionLabel: extra.length > 1 ? `Disable ${extra.length} older` : "Disable older copy",
    });
  }

  if (input.shadersOn) {
    out.push({
      id: "shaders",
      impact: "high",
      title: "A shader pack is on",
      detail: "Shaders are usually the biggest frame-rate cost there is: the graphics card redraws lighting and shadows for every frame. Turn them off to play fast, back on for screenshots.",
      action: { kind: "shaders-off" },
      actionLabel: "Turn shaders off",
    });
  }

  for (const c of COSTLY_MODS) {
    const found = enabled.filter((m) => c.ids.includes(m.id));
    if (!found.length) continue;
    out.push({
      id: `mod:${c.ids[0]}`,
      impact: c.impact,
      title: `${c.title} is on`,
      detail: c.why,
      action: { kind: "disable", fileNames: found.map((m) => m.fileName) },
      actionLabel: "Disable",
    });
  }

  const motion = input.motion;
  if (motion && motion.effects.length) {
    const list = motion.effects.length === 1 ? motion.effects[0]! : `${motion.effects.slice(0, -1).join(", ")} and ${motion.effects[motion.effects.length - 1]}`;
    out.push({
      id: "motion-look",
      impact: motion.effects.includes("shader effects") ? "medium" : "low",
      title: `MOTION's ${motion.profileName ?? "current"} look has ${list} on`,
      detail: "They're extra work every frame. The Clean PvP look turns them off and keeps your HUD, capes, wings and crosshair.",
      action: { kind: "motion-look", profile: "competitive" },
      actionLabel: "Use Clean PvP",
    });
  }

  const v = input.video;
  if (v) {
    if (v.vsync) {
      out.push({ id: "vsync", impact: "high", title: "VSync is on", detail: "It holds the frame rate at your monitor's refresh rate (often 60 or 144), however fast your PC is.", action: { kind: "settings", patch: { vsync: false } }, actionLabel: "Turn off" });
    }
    if (v.maxFps < UNLIMITED_FPS) {
      out.push({ id: "maxfps", impact: v.maxFps <= 144 ? "high" : "medium", title: `Frame rate limited to ${v.maxFps}`, detail: "Minecraft won't go above this limit. Set it to Unlimited to see what your PC can do.", action: { kind: "settings", patch: { maxFps: UNLIMITED_FPS } }, actionLabel: "Unlimited" });
    }
    if (v.renderDistance > 12) {
      out.push({ id: "render", impact: v.renderDistance > 16 ? "high" : "medium", title: `Render distance ${v.renderDistance} chunks`, detail: "Every extra chunk of distance adds a ring of terrain to draw. 12 still looks far in most worlds.", action: { kind: "settings", patch: { renderDistance: 12 } }, actionLabel: "Set to 12" });
    }
    if (v.simulationDistance > 10) {
      out.push({ id: "simulation", impact: "low", title: `Simulation distance ${v.simulationDistance} chunks`, detail: "In singleplayer, every chunk in this range keeps its mobs, crops and redstone running.", action: { kind: "settings", patch: { simulationDistance: 8 } }, actionLabel: "Set to 8" });
    }
    if (v.graphics === "fancy") {
      out.push({ id: "graphics", impact: "medium", title: "Fancy graphics", detail: "Fast graphics draws leaves and transparent blocks more cheaply.", action: { kind: "settings", patch: { graphics: "fast" } }, actionLabel: "Use Fast" });
    }
    if (v.clouds === "fancy") {
      out.push({ id: "clouds", impact: "low", title: "3D clouds", detail: "Fancy clouds are full 3D geometry across the sky.", action: { kind: "settings", patch: { clouds: "off" } }, actionLabel: "Turn off" });
    }
    if (v.entityShadows) {
      out.push({ id: "shadows", impact: "low", title: "Entity shadows", detail: "Each mob and item draws a shadow underneath.", action: { kind: "settings", patch: { entityShadows: false } }, actionLabel: "Turn off" });
    }
    if (v.particles === "all") {
      out.push({ id: "particles", impact: "low", title: "All particles", detail: "Explosions, rain and potion effects make a lot of particles at the highest setting.", action: { kind: "settings", patch: { particles: "decreased" } }, actionLabel: "Decrease" });
    }
    if (v.biomeBlend > 2) {
      out.push({ id: "biomeblend", impact: "low", title: `Biome blend ${v.biomeBlend * 2 + 1}×${v.biomeBlend * 2 + 1}`, detail: "Wide biome blending makes chunks slower to build when you move.", action: { kind: "settings", patch: { biomeBlend: 1 } }, actionLabel: "Set to 3×3" });
    }
  }

  const mem = input.memory;
  if (mem?.maxMb) {
    if (mem.maxMb > mem.maxSafeMb) {
      out.push({ id: "memory-high", impact: "medium", title: `${(mem.maxMb / 1024).toFixed(1)} GB of memory is too much for this PC`, detail: "Windows starts swapping to disk, which causes long freezes. More memory doesn't add frame rate.", action: { kind: "memory", maxMb: mem.recommendedMb }, actionLabel: `Use ${(mem.recommendedMb / 1024).toFixed(1)} GB` });
    } else if (mem.maxMb < mem.recommendedMb * 0.6) {
      out.push({ id: "memory-low", impact: "medium", title: `Only ${(mem.maxMb / 1024).toFixed(1)} GB of memory`, detail: "With this many mods Java has to clean up memory constantly, which shows as stutter.", action: { kind: "memory", maxMb: mem.recommendedMb }, actionLabel: `Use ${(mem.recommendedMb / 1024).toFixed(1)} GB` });
    }
  }

  return out.sort((a, b) => IMPACT_ORDER[a.impact] - IMPACT_ORDER[b.impact]);
}

/** All the settings fixes in a list, as one patch. */
export function settingsPatch(findings: Finding[]): Partial<VideoSettings> {
  const patch: Partial<VideoSettings> = {};
  for (const f of findings) if (f.action?.kind === "settings") Object.assign(patch, f.action.patch);
  return patch;
}

// ---------------------------------------------------------------------------------------------
// Measured FPS (written by MOTION Client to <game dir>/.bloom/fps-sessions.json)

export interface FpsSession {
  startedAt: number;
  endedAt: number;
  seconds: number;
  avgFps: number;
  low1Fps: number;
  low01Fps?: number;
  renderDistance?: number;
  simulationDistance?: number;
  fpsLimit?: number;
  vsync?: boolean;
  shaders?: boolean;
  resolution?: string;
  multiplayer?: boolean;
  modCount?: number;
  mods?: string[];
  motion?: string;
}

export function parseFpsSessions(text: string | null): FpsSession[] {
  if (!text) return [];
  try {
    const root = JSON.parse(text) as { sessions?: unknown };
    if (!Array.isArray(root.sessions)) return [];
    return root.sessions.filter((s): s is FpsSession => !!s && typeof s === "object" && typeof (s as FpsSession).avgFps === "number" && typeof (s as FpsSession).startedAt === "number");
  } catch {
    return [];
  }
}

/** True when a limit held this session's frame rate down, so it isn't the PC's real maximum. */
export function capped(s: FpsSession): boolean {
  return Boolean(s.vsync) || (s.fpsLimit !== undefined && s.fpsLimit < UNLIMITED_FPS && s.avgFps >= s.fpsLimit * 0.9);
}

const modName = (m: string) => m.split(" ")[0]!;

/** What differs between two sessions, in words ("Disabled flashback", "Render distance 16 → 12"). */
export function changesBetween(before: FpsSession, after: FpsSession): string[] {
  const out: string[] = [];
  const b = new Set((before.mods ?? []).map(modName)), a = new Set((after.mods ?? []).map(modName));
  const removed = [...b].filter((m) => !a.has(m)), added = [...a].filter((m) => !b.has(m));
  if (removed.length) out.push(`Disabled ${removed.slice(0, 4).join(", ")}${removed.length > 4 ? ` and ${removed.length - 4} more` : ""}`);
  if (added.length) out.push(`Added ${added.slice(0, 4).join(", ")}${added.length > 4 ? ` and ${added.length - 4} more` : ""}`);
  const num = (label: string, x?: number, y?: number, fmt = (n: number) => String(n)) => {
    if (x !== undefined && y !== undefined && x !== y) out.push(`${label} ${fmt(x)} → ${fmt(y)}`);
  };
  num("Render distance", before.renderDistance, after.renderDistance);
  num("Simulation distance", before.simulationDistance, after.simulationDistance);
  num("FPS limit", before.fpsLimit, after.fpsLimit, (n) => (n >= UNLIMITED_FPS ? "unlimited" : String(n)));
  if (before.vsync !== undefined && after.vsync !== undefined && before.vsync !== after.vsync) out.push(after.vsync ? "VSync on" : "VSync off");
  if (before.shaders !== undefined && after.shaders !== undefined && before.shaders !== after.shaders) out.push(after.shaders ? "Shaders on" : "Shaders off");
  return out;
}

/**
 * The latest session, and the one before the most recent change to compare it with: same
 * window size and same kind of play (singleplayer or a server), but different mods or settings.
 */
export function beforeAfter(sessions: FpsSession[]): { latest: FpsSession; before?: FpsSession; changes: string[] } | null {
  if (!sessions.length) return null;
  const sorted = [...sessions].sort((x, y) => x.startedAt - y.startedAt);
  const latest = sorted[sorted.length - 1]!;
  for (let i = sorted.length - 2; i >= 0; i--) {
    const s = sorted[i]!;
    if (s.resolution !== latest.resolution || Boolean(s.multiplayer) !== Boolean(latest.multiplayer)) continue;
    const changes = changesBetween(s, latest);
    if (changes.length) return { latest, before: s, changes };
  }
  return { latest, changes: [] };
}
