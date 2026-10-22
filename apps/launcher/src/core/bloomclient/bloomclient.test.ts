import { test } from "node:test";
import assert from "node:assert/strict";
import { applyPatch, parseSettings, SettingsFileError } from "./settingsFile";
import { conflictsFor, readOption, writeOption, MENU_KEY_OPTION } from "./optionsTxt";
import { compareVersions, compatNotes, preferredVersion, support, versionFromFileName, type Bundle } from "./support";
import { pngSize, skyboxImageProblem } from "./png";

const bundle: Bundle = {
  version: "0.1.0",
  minecraft: ["1.21.1"],
  files: [
    { loader: "fabric", file: "bloomclient-fabric-1.21.1-0.1.0.jar", sha1: "a", size: 1 },
    { loader: "neoforge", file: "bloomclient-neoforge-1.21.1-0.1.0.jar", sha1: "b", size: 1 },
  ],
};

test("support: loaders and versions", () => {
  assert.deepEqual(support({ loader: "fabric", gameVersion: "1.21.1" }, bundle), { ok: true, loaderFile: "fabric", file: "bloomclient-fabric-1.21.1-0.1.0.jar" });
  assert.deepEqual(support({ loader: "quilt", gameVersion: "1.21.1" }, bundle), { ok: true, loaderFile: "fabric", file: "bloomclient-fabric-1.21.1-0.1.0.jar" });
  assert.deepEqual(support({ loader: "neoforge", gameVersion: "1.21.1" }, bundle), { ok: true, loaderFile: "neoforge", file: "bloomclient-neoforge-1.21.1-0.1.0.jar" });
  const v = support({ loader: "vanilla", gameVersion: "1.21.1" }, bundle);
  assert.equal(v.ok, false);
  assert.equal(!v.ok && v.reason, "vanilla");
  const f = support({ loader: "forge", gameVersion: "1.21.1" }, bundle);
  assert.equal(!f.ok && f.reason, "forge");
  const old = support({ loader: "fabric", gameVersion: "1.20.1" }, bundle);
  assert.equal(!old.ok && old.reason, "version");
  const none = support({ loader: "fabric", gameVersion: "1.21.1" }, null);
  assert.equal(!none.ok && none.reason, "not_bundled");
});

test("support: one bundle, several Minecraft versions", () => {
  const multi: Bundle = {
    version: "0.3.0",
    minecraft: ["1.21.1", "1.21.11"],
    files: [
      { loader: "fabric", minecraft: "1.21.1", file: "bloomclient-fabric-1.21.1-0.3.0.jar", sha1: "a", size: 1 },
      { loader: "neoforge", minecraft: "1.21.1", file: "bloomclient-neoforge-1.21.1-0.3.0.jar", sha1: "b", size: 1 },
      { loader: "fabric", minecraft: "1.21.11", file: "bloomclient-fabric-1.21.11-0.3.0.jar", sha1: "c", size: 1 },
    ],
  };
  const s = support({ loader: "fabric", gameVersion: "1.21.11" }, multi);
  assert.ok(s.ok && s.file === "bloomclient-fabric-1.21.11-0.3.0.jar");
  const neo = support({ loader: "neoforge", gameVersion: "1.21.11" }, multi);
  assert.equal(!neo.ok && neo.reason, "version");
  assert.equal(preferredVersion(multi), "1.21.11");
  assert.equal(preferredVersion(bundle), "1.21.1");
  assert.equal(versionFromFileName("bloomclient-fabric-1.21.11-0.3.0.jar"), "0.3.0");
});

test("support: versions in file names", () => {
  assert.equal(versionFromFileName("bloomclient-fabric-1.21.1-0.1.0.jar"), "0.1.0");
  assert.equal(versionFromFileName("bloomclient-neoforge-1.21.1-0.2.0-beta.1.jar.disabled"), "0.2.0-beta.1");
  assert.equal(versionFromFileName("sodium-fabric-0.6.5.jar"), null);
  assert.equal(compareVersions("0.10.0", "0.9.2"), 1);
  assert.equal(compareVersions("0.1.0", "0.1.0"), 0);
  assert.equal(compareVersions("0.1.0", "0.1.1"), -1);
});

test("compat notes", () => {
  const notes = compatNotes("fabric", new Set(["iris", "optifabric"]));
  assert.ok(notes.some((n) => n.level === "error" && /Fabric API/.test(n.title)));
  assert.ok(notes.some((n) => n.level === "warning" && /OptiFine/.test(n.title)));
  assert.ok(!compatNotes("fabric", new Set(["fabric-api"])).some((n) => n.level === "error"));
  assert.ok(!compatNotes("neoforge", new Set()).some((n) => n.level === "error"));
});

test("settings patch keeps unknown data and bumps revision", () => {
  const existing = JSON.stringify({ format: 1, revision: 7, updatedBy: "game", settings: { "sky.day": "galaxy", "future.thing": 3 }, hud: { activeLayout: "pvp" }, profiles: { active: "minimal", biomeRules: { snowy: "snowfall" } } });
  const out = JSON.parse(applyPatch(existing, { settings: { "petals.enabled": false }, profile: "fantasy" }, new Date(0)));
  assert.equal(out.revision, 8);
  assert.equal(out.updatedBy, "launcher");
  assert.equal(out.settings["sky.day"], "galaxy");
  assert.equal(out.settings["future.thing"], 3);
  assert.equal(out.settings["petals.enabled"], false);
  assert.equal(out.hud.activeLayout, "pvp");
  assert.deepEqual(out.profiles, { active: "fantasy", apply: true, biomeRules: { snowy: "snowfall" } });
  const fresh = JSON.parse(applyPatch(null, { settings: { "snow.enabled": true } }, new Date(0)));
  assert.equal(fresh.revision, 1);
  assert.throws(() => applyPatch("{ broken", { settings: {} }, new Date(0)), SettingsFileError);
  assert.throws(() => applyPatch(null, { settings: { "../x": 1 } }, new Date(0)), SettingsFileError);
  assert.throws(() => applyPatch(null, { profile: "../../evil" }, new Date(0)), SettingsFileError);
  assert.equal(parseSettings(existing).activeProfile, "minimal");
});

test("options.txt editing", () => {
  const text = "version:3955\r\nkey_key.inventory:key.keyboard.e\r\nkey_key.bloomclient.menu:key.keyboard.right.shift\r\n";
  assert.equal(readOption(text, MENU_KEY_OPTION), "key.keyboard.right.shift");
  const changed = writeOption(text, MENU_KEY_OPTION, "key.keyboard.m");
  assert.equal(readOption(changed, MENU_KEY_OPTION), "key.keyboard.m");
  assert.ok(changed.includes("\r\n"));
  assert.equal(changed.split("\r\n").length, text.split("\r\n").length);
  const added = writeOption("version:3955\n", MENU_KEY_OPTION, "key.keyboard.insert");
  assert.equal(added, "version:3955\nkey_key.bloomclient.menu:key.keyboard.insert\n");
  assert.deepEqual(conflictsFor(text, MENU_KEY_OPTION, "key.keyboard.e"), ["key.inventory"]);
  assert.throws(() => writeOption(text, MENU_KEY_OPTION, "x\nkey_evil:1"));
});

test("png header and skybox rules", () => {
  const header = new Uint8Array(33);
  header.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 73, 72, 68, 82]);
  const put = (o: number, v: number) => header.set([(v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255], o);
  put(16, 1536);
  put(20, 1024);
  assert.deepEqual(pngSize(header), { width: 1536, height: 1024 });
  assert.equal(skyboxImageProblem({ width: 1536, height: 1024 }), null);
  assert.equal(skyboxImageProblem({ width: 4096, height: 2048 }), null);
  assert.ok(skyboxImageProblem({ width: 1000, height: 1000 }));
  assert.equal(pngSize(new Uint8Array(10)), null);
});

// ---------------------------------------------------------------- MOTION Mods (HUD block)

import { activeLayout, HUD_WIDGETS, MOD_PRESETS, MODULES, patchHud, readHud, widgetOption, widgetVisible } from "./modules";

test("hud: defaults when the file has no hud block", () => {
  const view = parseSettings(JSON.stringify({ revision: 3, settings: {} }));
  assert.equal(view.hud.activeLayout, "default");
  const layout = activeLayout(view.hud);
  assert.equal(widgetVisible(layout, "fps"), true);
  assert.equal(widgetVisible(layout, "keystrokes"), false);
  assert.equal(widgetOption(layout, "fps", "label"), true);
  const pvp = view.hud.layouts.find((l) => l.id === "pvp")!;
  assert.equal(widgetVisible(pvp, "keystrokes"), true);
});

test("hud: patch merges into the active layout and keeps other data", () => {
  const existing = JSON.stringify({
    revision: 7,
    updatedBy: "game",
    settings: { "hud.enabled": true },
    hud: {
      activeLayout: "pvp",
      layouts: [
        { id: "default", name: "Default", widgets: { fps: { visible: true, x: 10 } } },
        { id: "pvp", name: "PvP", widgets: { armor: { visible: true, scale: 1.5, options: { percent: true } } } },
      ],
    },
    profiles: { active: "competitive" },
  });
  const next = JSON.parse(applyPatch(existing, { hud: { widgets: { armor: { options: { bars: true } }, clock: { visible: true } } } }, new Date(0)));
  assert.equal(next.revision, 8);
  assert.equal(next.updatedBy, "launcher");
  assert.deepEqual(next.profiles, { active: "competitive" });
  const pvp = next.hud.layouts.find((l: { id: string }) => l.id === "pvp");
  assert.deepEqual(pvp.widgets.armor, { visible: true, scale: 1.5, options: { percent: true, bars: true } });
  assert.deepEqual(pvp.widgets.clock, { visible: true });
  const def = next.hud.layouts.find((l: { id: string }) => l.id === "default");
  assert.deepEqual(def.widgets, { fps: { visible: true, x: 10 } });
});

test("hud: presets hide everything they don't mention", () => {
  const minimal = MOD_PRESETS.find((p) => p.id === "minimal")!;
  const hud = patchHud(readHud(null), minimal.hud);
  const l = activeLayout(hud);
  for (const w of HUD_WIDGETS) assert.equal(widgetVisible(l, w.id), w.id === "fps" || w.id === "coords", w.id);
  assert.equal(widgetOption(l, "coords", "compact"), true);
  const full = MOD_PRESETS.find((p) => p.id === "full")!;
  const lf = activeLayout(patchHud(readHud(null), full.hud));
  for (const w of HUD_WIDGETS) assert.equal(widgetVisible(lf, w.id), true, w.id);
});

test("hud: unknown widgets and bad blocks are rejected or repaired", () => {
  assert.throws(() => applyPatch(null, { hud: { widgets: { nope: { visible: true } } } }, new Date(0)), SettingsFileError);
  assert.equal(readHud({ layouts: [{ id: "../evil", widgets: {} }] }).layouts[0]!.id, "default");
  assert.equal(readHud({ activeLayout: "missing", layouts: [{ id: "a", widgets: {} }] }).activeLayout, "a");
});

test("modules: every HUD module points at a known widget, ids are unique", () => {
  const ids = new Set<string>();
  for (const m of MODULES) {
    assert.ok(!ids.has(m.id), m.id);
    ids.add(m.id);
    if (m.kind === "hud") assert.ok(HUD_WIDGETS.some((w) => w.id === m.widget));
    if (m.kind === "setting") assert.ok(m.setting && m.setting.keys.length > 0);
  }
});
