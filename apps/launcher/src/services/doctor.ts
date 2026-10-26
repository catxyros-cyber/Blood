/**
 * Gathers what the performance doctor needs for one installation, and applies the fix for a
 * finding when the player clicks it. Nothing changes without that click.
 */

import type { Instance } from "../core/instances/model";
import { BUILT_IN_PROFILES, DEFAULT_PROFILE } from "../core/bloomclient/catalog";
import { diagnose, MOTION_EFFECTS, parseFpsSessions, type DoctorMod, type Finding, type FpsSession } from "../core/perf/doctor";
import { recommendMemory } from "../core/perf/memory";
import { PRESETS, readVideoSettings, type VideoSettings } from "../core/perf/presets";
import { app, P, path } from "./app";
import { readSettings, status as clientStatus, writeSettings } from "./bloomClient";
import { listContent, toggleContent, type ContentItem } from "./content";
import { instanceGameDir, updateInstance } from "./instances";
import { applyPreset } from "./performance";

export interface DoctorReport {
  findings: Finding[];
  sessions: FpsSession[];
  video: VideoSettings | null;
  mods: ContentItem[];
}

/** Iris (and Oculus on Forge) keep "enableShaders" and the chosen pack in config/<name>.properties. */
const SHADER_CONFIGS = ["iris.properties", "oculus.properties"];

async function shadersOn(inst: Instance): Promise<boolean> {
  for (const f of SHADER_CONFIGS) {
    const text = await P().readText(path(instanceGameDir(inst), "config", f)).catch(() => null);
    if (!text) continue;
    const on = /^\s*enableShaders\s*=\s*true\s*$/m.test(text);
    const pack = /^\s*shaderPack\s*=\s*(.+?)\s*$/m.exec(text)?.[1];
    if (on && pack && pack !== "(off)" && pack !== "(internal)") return true;
  }
  return false;
}

/** Looks MOTION Client has that the launcher's picker doesn't list. */
const GAME_ONLY_LOOKS: Record<string, string> = { blood_moon: "Blood Red", cherry_blossom: "Cherry Blossom" };

/** MOTION Client's current look and its costly effects that are on (undefined when it's off). */
async function motionLook(inst: Instance): Promise<{ profileName: string | null; effects: string[] } | undefined> {
  if (!(await clientStatus(inst)).enabled) return undefined;
  const view = await readSettings(inst);
  // Before the first launch the file has no values yet; there's nothing measured to suggest.
  if (!Object.keys(view.settings).length) return undefined;
  const effects = MOTION_EFFECTS.filter(([key]) => view.settings[key] === true).map(([, label]) => label);
  const id = view.activeProfile ?? DEFAULT_PROFILE;
  const name = BUILT_IN_PROFILES.find((p) => p.id === id)?.name ?? GAME_ONLY_LOOKS[id] ?? null;
  return { profileName: name, effects };
}

export async function runDoctor(inst: Instance): Promise<DoctorReport> {
  const p = P();
  const dir = instanceGameDir(inst);
  const [mods, optionsText, shaders, sessionsText, motion] = await Promise.all([
    inst.loader === "vanilla" ? Promise.resolve([] as ContentItem[]) : listContent(inst, "mods").catch(() => [] as ContentItem[]),
    p.readText(path(dir, "options.txt")).catch(() => null),
    shadersOn(inst).catch(() => false),
    p.readText(path(dir, ".bloom", "fps-sessions.json")).catch(() => null),
    motionLook(inst).catch(() => undefined),
  ]);
  const video = optionsText ? readVideoSettings(optionsText, PRESETS[1]!.video) : null;
  const system = app.get().system;
  const settings = app.get().settings;
  const mem = inst.memory ?? settings.memory;
  const advice = system ? recommendMemory(system.totalMemory / 1_048_576, { modded: inst.loader !== "vanilla", modCount: mods.length }) : null;
  const doctorMods: DoctorMod[] = mods
    .filter((m) => m.meta)
    .map((m) => ({ id: m.meta!.id, name: m.meta!.name || m.meta!.id, version: m.meta!.version, fileName: m.fileName, enabled: m.enabled, modifiedMs: m.modifiedMs }));
  const findings = diagnose({
    mods: doctorMods,
    video,
    shadersOn: shaders,
    memory: advice ? { maxMb: mem?.maxMb ?? null, recommendedMb: advice.recommendedMb, maxSafeMb: advice.maxSafeMb } : undefined,
    motion,
  });
  return { findings, sessions: parseFpsSessions(sessionsText), video, mods };
}

/** Applies one finding's fix (or several settings at once with `patch`). */
export async function applyFinding(inst: Instance, report: DoctorReport, finding: Finding, patch?: Partial<VideoSettings>): Promise<void> {
  if (app.get().running[inst.id]) throw new Error("Close Minecraft first: it rewrites its settings and keeps mod files open while it runs.");
  const a = patch ? ({ kind: "settings", patch } as const) : finding.action;
  if (!a) return;
  switch (a.kind) {
    case "disable": {
      for (const name of a.fileNames) {
        const item = report.mods.find((m) => m.fileName === name && m.enabled);
        if (item) await toggleContent(inst, "mods", item);
      }
      return;
    }
    case "settings": {
      if (!report.video) throw new Error("Play once first: Minecraft creates its settings file on the first launch.");
      await applyPreset(inst, "custom", { ...report.video, ...a.patch });
      return;
    }
    case "shaders-off": {
      for (const f of SHADER_CONFIGS) {
        const file = path(instanceGameDir(inst), "config", f);
        const text = await P().readText(file).catch(() => null);
        if (text && /^\s*enableShaders\s*=/m.test(text)) await P().writeText(file, text.replace(/^(\s*enableShaders\s*=\s*)true\s*$/m, "$1false"));
      }
      return;
    }
    case "memory": {
      const cur = inst.memory ?? app.get().settings.memory;
      await updateInstance(inst.id, { memory: { minMb: Math.min(cur?.minMb ?? 1024, a.maxMb), maxMb: a.maxMb } });
      return;
    }
    case "motion-look": {
      await writeSettings(inst, { profile: a.profile });
      return;
    }
    case "install":
      return;
  }
}
