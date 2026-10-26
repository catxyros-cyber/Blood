import { test } from "node:test";
import assert from "node:assert/strict";
import { beforeAfter, capped, diagnose, parseFpsSessions, settingsPatch, type DoctorMod, type FpsSession } from "./doctor";
import { PRESETS } from "./presets";

const mod = (id: string, version: string, fileName: string, extra: Partial<DoctorMod> = {}): DoctorMod => ({ id, name: id, version, fileName, enabled: true, modifiedMs: 0, ...extra });

test("doctor: duplicate copies keep the newest version", () => {
  const f = diagnose({ mods: [mod("voicechat", "1.21.11-2.6.25", "voicechat-2.6.25.jar"), mod("voicechat", "1.21.11-2.6.26", "voicechat-2.6.26.jar")], video: null, shadersOn: false });
  assert.equal(f.length, 1);
  assert.equal(f[0]!.impact, "high");
  assert.deepEqual(f[0]!.action, { kind: "disable", fileNames: ["voicechat-2.6.25.jar"] });
});

test("doctor: costly mods, shaders and settings, most important first", () => {
  const video = { ...PRESETS.find((p) => p.id === "balanced")!.video, vsync: true, renderDistance: 20, entityShadows: true, maxFps: 260 };
  const f = diagnose({
    mods: [mod("flashback", "0.39.10", "Flashback.jar"), mod("essential-container", "1.0.0", "Essential.jar"), mod("sodium", "0.8.15", "sodium.jar"), mod("naturalmotionblur", "1.4.5", "blur.jar", { enabled: false })],
    video,
    shadersOn: true,
  });
  const ids = f.map((x) => x.id);
  assert.ok(ids.includes("mod:flashback"));
  assert.ok(ids.includes("mod:essential"));
  assert.ok(!ids.includes("mod:naturalmotionblur"), "disabled mods aren't reported");
  assert.ok(ids.includes("shaders") && ids.includes("vsync") && ids.includes("render") && ids.includes("shadows"));
  // Sorted: every high before any medium before any low.
  const order = { high: 0, medium: 1, low: 2 } as const;
  for (let i = 1; i < f.length; i++) assert.ok(order[f[i - 1]!.impact] <= order[f[i]!.impact]);
  assert.deepEqual(settingsPatch(f), { vsync: false, renderDistance: 12, entityShadows: false, ...(video.graphics === "fancy" ? { graphics: "fast" } : {}), ...(video.clouds === "fancy" ? { clouds: "off" } : {}), ...(video.particles === "all" ? { particles: "decreased" } : {}), ...(video.simulationDistance > 10 ? { simulationDistance: 8 } : {}), ...(video.biomeBlend > 2 ? { biomeBlend: 1 } : {}) });
});

test("doctor: an optimized setup has nothing to fix", () => {
  const video = { ...PRESETS.find((p) => p.id === "extreme")!.video };
  const f = diagnose({ mods: [mod("sodium", "0.8.15", "sodium.jar"), mod("lithium", "0.21.4", "lithium.jar")], video, shadersOn: false, memory: { maxMb: 4096, recommendedMb: 4096, maxSafeMb: 8192 } });
  assert.deepEqual(f.map((x) => x.id), []);
});

test("doctor: memory outside the safe range", () => {
  assert.equal(diagnose({ mods: [], video: null, shadersOn: false, memory: { maxMb: 14336, recommendedMb: 4096, maxSafeMb: 10240 } })[0]!.id, "memory-high");
  assert.equal(diagnose({ mods: [], video: null, shadersOn: false, memory: { maxMb: 2048, recommendedMb: 6144, maxSafeMb: 10240 } })[0]!.id, "memory-low");
});

const session = (s: Partial<FpsSession>): FpsSession => ({ startedAt: 0, endedAt: 0, seconds: 120, avgFps: 100, low1Fps: 50, resolution: "1920x1080", multiplayer: false, ...s });

test("fps sessions: before/after finds the last change and names it", () => {
  const sessions = parseFpsSessions(
    JSON.stringify({
      version: 1,
      sessions: [
        session({ startedAt: 1, avgFps: 140, renderDistance: 16, vsync: false, fpsLimit: 260, mods: ["flashback 1", "sodium 1"] }),
        session({ startedAt: 2, avgFps: 150, renderDistance: 16, vsync: false, fpsLimit: 260, mods: ["flashback 1", "sodium 1"], resolution: "1280x720" }),
        session({ startedAt: 3, avgFps: 310, renderDistance: 12, vsync: false, fpsLimit: 260, mods: ["sodium 1"] }),
      ],
    }),
  );
  const ba = beforeAfter(sessions)!;
  assert.equal(ba.latest.avgFps, 310);
  assert.equal(ba.before?.avgFps, 140, "the 1280×720 session isn't comparable");
  assert.deepEqual(ba.changes, ["Disabled flashback", "Render distance 16 → 12"]);
});

test("fps sessions: capped and damaged logs", () => {
  assert.equal(capped(session({ vsync: true })), true);
  assert.equal(capped(session({ fpsLimit: 120, avgFps: 119 })), true);
  assert.equal(capped(session({ fpsLimit: 260, avgFps: 240 })), false);
  assert.deepEqual(parseFpsSessions("not json"), []);
  assert.deepEqual(parseFpsSessions(null), []);
});
