import { test } from "node:test";
import assert from "node:assert/strict";
import { buildSyncData, fingerprint, planMerge } from "./sync";
import type { Instance } from "../instances/model";

const inst = (over: Partial<Instance> = {}): Instance => ({
  id: "survival",
  name: "Survival",
  gameVersion: "1.21.1",
  loader: "fabric",
  loaderVersion: "0.16.5",
  createdAt: 1,
  gameDir: "D:\\Games\\survival",
  javaPath: "C:\\java\\javaw.exe",
  lastPlayedAt: 5,
  memory: { minMb: 512, maxMb: 4096 },
  ...over,
});

test("only portable data is synced", () => {
  const data = buildSyncData(
    { theme: "midnight", accent: "#fff", javaPath: "C:\\x", msClientId: "secret-ish", activeAccountId: "u" },
    [inst()],
    { survival: [{ kind: "mods", projectId: "AANobbMI", versionId: "v1", fileName: "sodium.jar", enabled: true }] },
  );
  assert.deepEqual(data.settings, { theme: "midnight", accent: "#fff" });
  const i = data.instances[0]!;
  assert.equal((i as Record<string, unknown>).gameDir, undefined, "custom folders stay local");
  assert.equal((i as Record<string, unknown>).javaPath, undefined, "Java paths stay local");
  assert.equal((i as Record<string, unknown>).lastPlayedAt, undefined);
  assert.equal(i.content.length, 1);
  const big = buildSyncData({}, [inst({ icon: `data:image/png;base64,${"A".repeat(60_000)}` })], {});
  assert.equal(big.instances[0]!.icon, undefined, "large embedded icons stay local");
  assert.equal(fingerprint(data), fingerprint(buildSyncData({ theme: "midnight", accent: "#fff" }, [inst()], { survival: data.instances[0]!.content })));
});

test("merging creates and updates but never deletes", () => {
  const remote = buildSyncData({ theme: "oled", accent: "#4ade80" }, [inst({ name: "Survival SMP", memory: { minMb: 512, maxMb: 6144 } }), inst({ id: "creative", name: "Creative" })], {});
  const plan = planMerge({ settings: { theme: "graphite", accent: "#4ade80" }, instances: [inst(), inst({ id: "local-only", name: "Mine" })] }, remote);
  assert.deepEqual(plan.settingsPatch, { theme: "oled" });
  assert.deepEqual(plan.create.map((c) => c.id), ["creative"]);
  assert.deepEqual(plan.update, [{ id: "survival", patch: { name: "Survival SMP", memory: { minMb: 512, maxMb: 6144 } } }]);
  assert.deepEqual(plan.localOnly, ["local-only"]);

  // Garbage or unsafe ids are ignored.
  const evil = { format: 1, settings: {}, instances: [{ ...inst(), id: "..\\..\\Windows" }] };
  assert.deepEqual(planMerge({ settings: {}, instances: [] }, evil).create, []);
  assert.deepEqual(planMerge({ settings: {}, instances: [] }, { nope: true }).create, []);
});
