import { test } from "node:test";
import assert from "node:assert/strict";
import { prepareInstance, type InstallStage } from "./installer";
import { buildLaunchCommand } from "../mojang/args";
import { FakePlatform, json } from "../test/fakePlatform";
import { assetIndex17, fabricProfile, javaIndex, javaManifest, manifest, v1122, v1211 } from "../test/fixtures";
import { JAVA_RUNTIME_INDEX_URL, VERSION_MANIFEST_URL } from "../mojang/types";
import type { Instance } from "../instances/model";

function setup() {
  const p = new FakePlatform();
  p.route(VERSION_MANIFEST_URL, json(manifest));
  p.route(JAVA_RUNTIME_INDEX_URL, json(javaIndex));
  p.downloadContent.set(manifest.versions[0]!.url, JSON.stringify(v1211));
  p.downloadContent.set(manifest.versions[1]!.url, JSON.stringify(v1122));
  p.downloadContent.set(v1211.assetIndex!.url, JSON.stringify(assetIndex17));
  p.downloadContent.set(v1122.assetIndex!.url, JSON.stringify({ objects: {} }));
  p.downloadContent.set(javaIndex["windows-x64"]!["java-runtime-delta"]![0]!.manifest.url, JSON.stringify(javaManifest));
  p.route("https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.5/profile/json", json(fabricProfile));
  return p;
}

const fabricInst: Instance = {
  id: "survival",
  name: "Survival",
  gameVersion: "1.21.1",
  loader: "fabric",
  loaderVersion: "0.16.5",
  createdAt: 0,
};

test("prepares a Fabric installation end to end and builds a launch command", async () => {
  const p = setup();
  const stages: InstallStage[] = [];
  const prepared = await prepareInstance(p, fabricInst, {
    env: { os: "windows", arch: "x64" },
    progress: { stage: (s) => stages.push(s) },
    taskPrefix: "t1",
  });
  assert.deepEqual(stages, ["metadata", "loader", "java", "libraries", "assets"]);
  assert.equal(prepared.version.mainClass, "net.fabricmc.loader.impl.launch.knot.KnotClient");
  assert.equal(prepared.managedJava?.path, `${p.dataDir}\\runtimes\\java-runtime-delta\\bin\\javaw.exe`);
  assert.equal(prepared.managedJava?.major, 21);
  assert.equal(prepared.gameDir, `${p.dataDir}\\instances\\survival`);
  assert.equal(prepared.classpath[prepared.classpath.length - 1], `${p.dataDir}\\versions\\1.21.1\\1.21.1.jar`);
  assert.ok(prepared.classpath[0]!.endsWith("asm-9.7.1.jar"));
  assert.equal(prepared.loggingConfigPath, `${p.dataDir}\\assets\\log_configs\\client-1.12.xml`);

  const libBatch = p.downloads.find((d) => d.taskId === "t1:libs")!;
  assert.ok(libBatch.items.some((i) => i.url === v1211.downloads!.client!.url && i.sha1 === v1211.downloads!.client!.sha1));
  assert.ok(libBatch.items.every((i) => i.path.startsWith(p.dataDir)));
  const assetBatch = p.downloads.find((d) => d.taskId === "t1:assets" && d.items.length > 1)!;
  assert.equal(assetBatch.items.length, 2);
  assert.equal(p.files.get(`${p.dataDir}\\runtimes\\java-runtime-delta\\.bloom-complete`), "21.0.3");
  assert.ok(p.files.has(`${p.dataDir}\\versions\\fabric-loader-0.16.5-1.21.1\\fabric-loader-0.16.5-1.21.1.json`));

  const cmd = buildLaunchCommand(prepared.version, {
    env: { os: "windows", arch: "x64" },
    username: "Steve",
    uuid: "u",
    accessToken: "tok",
    xuid: "",
    clientId: "",
    userType: "msa",
    versionName: prepared.version.id,
    gameDir: prepared.gameDir,
    assetsRoot: prepared.assetsRoot,
    assetsIndexName: prepared.assetsIndexName,
    nativesDir: prepared.nativesDir,
    librariesDir: `${p.dataDir}\\libraries`,
    classpath: prepared.classpath,
    launcherName: "bloom",
    launcherVersion: "0.1.0",
    loggingConfigPath: prepared.loggingConfigPath,
    memory: { minMb: 512, maxMb: 4096 },
    extraJvmArgs: [],
    extraGameArgs: [],
  });
  assert.equal(cmd.mainClass, "net.fabricmc.loader.impl.launch.knot.KnotClient");
  assert.ok(cmd.jvmArgs.includes("-DFabricMcEmu= net.minecraft.client.main.Main "));
  assert.equal(cmd.gameArgs[cmd.gameArgs.indexOf("--version") + 1], "fabric-loader-0.16.5-1.21.1");

  // Second run: Java marker present → no runtime downloads; still verifies files.
  const before = p.downloads.length;
  await prepareInstance(p, fabricInst, { env: { os: "windows", arch: "x64" }, progress: { stage() {} }, taskPrefix: "t2" });
  assert.ok(!p.downloads.slice(before).some((d) => d.taskId === "t2:java"));
});

test("legacy versions extract natives; offline launches use cached metadata", async () => {
  const p = setup();
  const inst: Instance = { id: "old", name: "Old", gameVersion: "1.12.2", loader: "vanilla", createdAt: 0, javaPath: "C:\\java\\bin\\javaw.exe" };
  const prepared = await prepareInstance(p, inst, {
    env: { os: "windows", arch: "x64" },
    customJava: true,
    progress: { stage() {} },
    taskPrefix: "a",
  });
  assert.equal(prepared.managedJava, undefined);
  assert.equal(p.extracted.length, 2);
  assert.ok(p.extracted.every((e) => e.dest === `${p.dataDir}\\natives\\old`));

  p.offline = true;
  const again = await prepareInstance(p, inst, { env: { os: "windows", arch: "x64" }, customJava: true, progress: { stage() {} }, taskPrefix: "b" });
  assert.equal(again.version.id, "1.12.2");

  const never: Instance = { ...inst, id: "new", gameVersion: "1.21.1", loader: "vanilla" };
  const fresh = setup();
  fresh.offline = true;
  await assert.rejects(
    prepareInstance(fresh, never, { env: { os: "windows", arch: "x64" }, customJava: true, progress: { stage() {} }, taskPrefix: "c" }),
    (e: { code: string }) => e.code === "offline",
  );
});

test("a loader installation without a loader version fails with a clear message", async () => {
  const p = setup();
  await assert.rejects(
    prepareInstance(p, { ...fabricInst, loader: "forge", loaderVersion: undefined }, { env: { os: "windows", arch: "x64" }, progress: { stage() {} }, taskPrefix: "x" }),
    (e: { code: string }) => e.code === "loader_unavailable",
  );
});
