import { test } from "node:test";
import assert from "node:assert/strict";
import { OptionsFile } from "./options";
import { PRESETS, applyVideoSettings, optionsFormatFor, readVideoSettings } from "./presets";
import { recommendMemory, splitArgs, validateJvmArgs } from "./memory";
import { gcLogArg, gcStats, parseGcLog } from "./gclog";

const modernOptions = `version:3955
ao:true
renderDistance:12
simulationDistance:12
graphicsMode:1
renderClouds:"true"
key_key.attack:key.mouse.left
maxFps:120
`;

test("options.txt round-trips unknown keys and keybinds", () => {
  const f = new OptionsFile(modernOptions);
  assert.equal(f.get("key_key.attack"), "key.mouse.left");
  f.set("renderDistance", "8");
  const out = f.toString();
  assert.ok(out.startsWith("version:3955\nao:true\nrenderDistance:8\n"));
  assert.ok(out.includes("key_key.attack:key.mouse.left"));
});

test("presets write the right format for each Minecraft era", () => {
  const potato = PRESETS.find((p) => p.id === "potato")!;
  const modern = applyVideoSettings(modernOptions, potato.video, optionsFormatFor("2024-08-08T00:00:00Z"));
  const f = new OptionsFile(modern.text);
  assert.equal(f.get("ao"), "false");
  assert.equal(f.get("graphicsMode"), "0");
  assert.equal(f.get("renderClouds"), '"false"');
  assert.equal(f.get("simulationDistance"), "5");
  assert.equal(f.get("entityDistanceScaling"), "0.5");
  assert.ok(modern.changes.some((c) => c.key === "renderDistance" && c.from === "12" && c.to === "5"));
  assert.equal(f.get("key_key.attack"), "key.mouse.left");

  const legacy = new OptionsFile(applyVideoSettings(null, potato.video, optionsFormatFor("2017-09-18T00:00:00Z")).text);
  assert.equal(legacy.get("fancyGraphics"), "false");
  assert.equal(legacy.get("ao"), "0");
  assert.equal(legacy.get("simulationDistance"), undefined);
  assert.equal(legacy.get("renderClouds"), "false");

  // Re-applying is a no-op
  const again = applyVideoSettings(modern.text, potato.video, optionsFormatFor("2024-08-08T00:00:00Z"));
  assert.equal(again.changes.length, 0);

  const back = readVideoSettings(modern.text, PRESETS[1]!.video);
  assert.deepEqual(back, potato.video);
});

test("memory recommendations stay within safe limits", () => {
  const vanilla16 = recommendMemory(16384, { modded: false, modCount: 0 });
  assert.equal(vanilla16.recommendedMb, 3072);
  const big8 = recommendMemory(8192, { modded: true, modCount: 200 });
  assert.ok(big8.recommendedMb <= 8192 - 3072);
  assert.match(big8.reason, /Limited/);
  const tiny = recommendMemory(4096, { modded: true, modCount: 10 });
  assert.ok(tiny.recommendedMb >= 1024 && tiny.recommendedMb <= tiny.maxSafeMb);
});

test("JVM argument validation", () => {
  const issues = validateJvmArgs(["-Xmx8G", "-XX:+UseConcMarkSweepGC", "-XX:+UseG1GC", "nope"], 21);
  assert.equal(issues.filter((i) => i.severity === "error").length, 3);
  assert.equal(validateJvmArgs(["-XX:+UseZGC", "-XX:+ZGenerational"], 17).length, 1);
  assert.equal(validateJvmArgs(["-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"], 21).length, 0);
  assert.deepEqual(splitArgs('-Dfoo="a b" -XX:+UseG1GC'), ["-Dfoo=a b", "-XX:+UseG1GC"]);
});


test("GC log: G1 pauses, heap use and long-pause detection", () => {
  const log = [
    "[0.008s][info][gc] Using G1",
    "[10.000s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 512M->128M(1024M) 4.000ms",
    "[20.000s][info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 600M->140M(1024M) 6.000ms",
    "[25.000s][info][gc] GC(2) Pause Young (Concurrent Start) (G1 Humongous Allocation) 700M->300M(1024M) 8.500ms",
    "[25.100s][info][gc] GC(2) Concurrent Mark Cycle 50.123ms",
    "[26.000s][info][gc] GC(3) Pause Remark 320M->310M(1024M) 1.500ms",
    "[70.000s][info][gc] GC(4) Pause Full (G1 Compaction Pause) 1000M->400M(1536M) 0.120s",
    "garbage line",
  ].join("\n");
  const { events, collector } = parseGcLog(log);
  assert.equal(collector, "G1");
  assert.equal(events.length, 5);
  assert.equal(events[4]!.pauseMs, 120);
  const s = gcStats(log);
  assert.equal(s.pauses, 5);
  assert.equal(s.maxPauseMs, 120);
  assert.equal(s.longPauses, 1);
  assert.equal(s.heapUsedMb, 400);
  assert.equal(s.heapCommittedMb, 1536);
  assert.equal(s.spanSec, 60);
  assert.ok(Math.abs(s.perMinute - 4) < 1e-9);
  // allocated between collections: (600-128)+(700-140)+(320-300)+(1000-310) = 1742 MB over 60 s
  assert.ok(Math.abs((s.allocMbPerSec ?? 0) - 1742 / 60) < 1e-9);
});

test("GC log: generational ZGC collections are not counted as pauses", () => {
  const log = [
    "[0.010s][info][gc,init] Using The Z Garbage Collector",
    "[5.000s][info][gc] GC(0) Minor Collection (Allocation Rate) 2010M(25%)->1234M(15%) 0.045s",
    "[9.000s][info][gc] GC(1) Major Collection (Proactive) 1.5G(18%)->512M(6%) 0.300s",
  ].join("\n");
  const s = gcStats(log);
  assert.equal(s.collector, "The Z Garbage Collector");
  assert.equal(s.events, 2);
  assert.equal(s.pauses, 0);
  assert.equal(s.heapUsedMb, 512);
  assert.equal(gcStats("").events, 0);
});

test("GC log flag quotes Windows paths and needs Java 9+", () => {
  assert.equal(gcLogArg("C:\\Users\\a b\\gc.log", 21), '-Xlog:gc:file="C:\\Users\\a b\\gc.log":uptime,level,tags:filecount=0');
  assert.equal(gcLogArg("C:\\x.log", 8), null);
});
