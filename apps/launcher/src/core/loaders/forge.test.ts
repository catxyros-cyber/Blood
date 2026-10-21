import { test } from "node:test";
import assert from "node:assert/strict";
import { forgeLoaderVersions, installerCoord, neoforgeGameVersion, parseMavenMetadata, resolveToken } from "./forge";
import { pickLoaderVersion } from "./loaders";
import { prepareInstance } from "../install/installer";
import { FakePlatform, json } from "../test/fakePlatform";
import { assetIndex17, javaIndex, javaManifest, manifest, v1122, v1211 } from "../test/fixtures";
import { JAVA_RUNTIME_INDEX_URL, VERSION_MANIFEST_URL, type VersionJson } from "../mojang/types";
import type { Instance } from "../instances/model";
import { coordPath } from "../util/maven";

const xml = (versions: string[]) =>
  `<?xml version="1.0"?><metadata><versioning><versions>${versions.map((v) => `<version>${v}</version>`).join("")}</versions></versioning></metadata>`;

test("maven metadata and NeoForge → Minecraft version mapping", () => {
  assert.deepEqual(parseMavenMetadata(xml(["1.0", " 2.0 "])), ["1.0", "2.0"]);
  assert.equal(neoforgeGameVersion("20.4.237"), "1.20.4");
  assert.equal(neoforgeGameVersion("21.0.167"), "1.21");
  assert.equal(neoforgeGameVersion("21.1.77"), "1.21.1");
  assert.equal(neoforgeGameVersion("20.2.3-beta"), "1.20.2");
  assert.equal(neoforgeGameVersion("26.1.0.12-beta"), "26.1");
  assert.equal(neoforgeGameVersion("26.1.1.3"), "26.1.1");
  assert.equal(neoforgeGameVersion("47.1.79"), null);
  assert.equal(
    installerCoord("forge", "1.20.1", "47.2.0").url,
    "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar",
  );
  assert.equal(
    installerCoord("neoforge", "1.21.1", "21.1.77").url,
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.77/neoforge-21.1.77-installer.jar",
  );
});

test("Forge versions are grouped by game version and the recommended build is preferred", async () => {
  const p = new FakePlatform();
  p.route("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml", {
    status: 200,
    headers: {},
    body: xml(["1.12.2-14.23.5.2859", "1.12.2-14.23.5.2860", "1.20.1-47.1.0", "1.20.1-47.2.0", "1.20.1-47.3.1"]),
  });
  p.route("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json", { promos: { "1.20.1-recommended": "47.2.0" } });
  const versions = await forgeLoaderVersions(p, "forge", "1.20.1");
  assert.deepEqual(versions.map((v) => v.version), ["47.3.1", "47.2.0", "47.1.0"]);
  assert.equal(pickLoaderVersion(versions).version, "47.2.0");
  await assert.rejects(forgeLoaderVersions(p, "forge", "1.8.9"), (e: { code: string }) => e.code === "loader_unavailable");
});

function baseSetup() {
  const p = new FakePlatform();
  const vanilla: VersionJson = {
    ...v1211,
    downloads: {
      ...v1211.downloads,
      client_mappings: { sha1: "9999000000000000000000000000000000000009", size: 9000000, url: "https://piston-data.mojang.com/v1/objects/9999/client.txt" },
    },
  };
  p.route(VERSION_MANIFEST_URL, json(manifest));
  const runtimes = javaIndex["windows-x64"]!;
  p.route(JAVA_RUNTIME_INDEX_URL, json({ ...javaIndex, "windows-x64": { ...runtimes, "jre-legacy": runtimes["java-runtime-delta"] } }));
  p.downloadContent.set(manifest.versions[0]!.url, JSON.stringify(vanilla));
  p.downloadContent.set(manifest.versions[1]!.url, JSON.stringify(v1122));
  p.downloadContent.set(v1211.assetIndex!.url, JSON.stringify(assetIndex17));
  p.downloadContent.set(v1122.assetIndex!.url, JSON.stringify({ objects: {} }));
  p.downloadContent.set(javaIndex["windows-x64"]!["java-runtime-delta"]![0]!.manifest.url, JSON.stringify(javaManifest));
  return p;
}

const neoProfile = {
  spec: 1,
  profile: "NeoForge",
  version: "neoforge-21.1.77",
  minecraft: "1.21.1",
  json: "/version.json",
  data: {
    MOJMAPS: { client: "[net.minecraft:client:1.21.1:mappings@txt]", server: "" },
    PATCHED: { client: "[net.neoforged:minecraft-client-patched:21.1.77]", server: "" },
    PATCHED_SHA: { client: "'abcabcabcabcabcabcabcabcabcabcabcabcabca'", server: "" },
    BINPATCH: { client: "/data/client.lzma", server: "/data/server.lzma" },
  },
  processors: [
    { sides: ["server"], jar: "net.neoforged.installertools:installertools:2.1.2", classpath: [], args: ["--task", "SERVER_ONLY"] },
    {
      sides: ["client"],
      jar: "net.neoforged.installertools:installertools:2.1.2",
      classpath: [],
      args: ["--task", "DOWNLOAD_MOJMAPS", "--version", "{MINECRAFT_VERSION}", "--side", "{SIDE}", "--output", "{MOJMAPS}"],
    },
    {
      jar: "net.neoforged.installertools:binarypatcher:2.1.2",
      classpath: ["net.neoforged.installertools:installertools:2.1.2", "net.sf.jopt-simple:jopt-simple:5.0.4"],
      args: ["--clean", "{MINECRAFT_JAR}", "--output", "{PATCHED}", "--apply", "{BINPATCH}"],
      outputs: { "{PATCHED}": "{PATCHED_SHA}" },
    },
  ],
  libraries: [
    {
      name: "net.neoforged.installertools:installertools:2.1.2",
      downloads: { artifact: { path: "net/neoforged/installertools/installertools/2.1.2/installertools-2.1.2.jar", url: "https://maven.neoforged.net/releases/net/neoforged/installertools/installertools/2.1.2/installertools-2.1.2.jar", sha1: "1111111111111111111111111111111111111111", size: 100 } },
    },
    {
      name: "net.neoforged.installertools:binarypatcher:2.1.2",
      downloads: { artifact: { path: "net/neoforged/installertools/binarypatcher/2.1.2/binarypatcher-2.1.2.jar", url: "https://maven.neoforged.net/releases/net/neoforged/installertools/binarypatcher/2.1.2/binarypatcher-2.1.2.jar", sha1: "2222222222222222222222222222222222222222", size: 100 } },
    },
    {
      name: "net.sf.jopt-simple:jopt-simple:5.0.4",
      downloads: { artifact: { path: "net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar", url: "https://maven.neoforged.net/releases/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar", sha1: "3333333333333333333333333333333333333333", size: 100 } },
    },
  ],
};

const neoVersion: VersionJson = {
  id: "neoforge-21.1.77",
  inheritsFrom: "1.21.1",
  type: "release",
  mainClass: "cpw.mods.bootstraplauncher.BootstrapLauncher",
  arguments: {
    game: ["--fml.neoForgeVersion", "21.1.77"],
    jvm: ["-DignoreList=client-extra,${version_name}.jar", "-DlibraryDirectory=${library_directory}"],
  },
  libraries: [
    {
      name: "net.neoforged:neoforge:21.1.77:universal",
      downloads: { artifact: { path: "net/neoforged/neoforge/21.1.77/neoforge-21.1.77-universal.jar", url: "", sha1: "4444444444444444444444444444444444444444", size: 100 } },
    },
    {
      name: "cpw.mods:bootstraplauncher:2.0.2",
      downloads: { artifact: { path: "cpw/mods/bootstraplauncher/2.0.2/bootstraplauncher-2.0.2.jar", url: "https://maven.neoforged.net/releases/cpw/mods/bootstraplauncher/2.0.2/bootstraplauncher-2.0.2.jar", sha1: "5555555555555555555555555555555555555555", size: 100 } },
    },
  ],
};

test("NeoForge: downloads the installer, runs client processors and launches through the patched profile", async () => {
  const p = baseSetup();
  const lib = (c: string) => `${p.dataDir}\\libraries\\${coordPath(c).split("/").join("\\")}`;
  const installerUrl = installerCoord("neoforge", "1.21.1", "21.1.77").url;
  const installerPath = lib("net.neoforged:neoforge:21.1.77:installer");
  p.route(`${installerUrl}.sha1`, { status: 200, headers: {}, body: "77777777777777777777777777777777777777aa\n" });
  p.zipEntries.set(installerPath, {
    "install_profile.json": JSON.stringify(neoProfile),
    "version.json": JSON.stringify(neoVersion),
    "data/client.lzma": "patches",
    "maven/net/neoforged/neoforge/21.1.77/neoforge-21.1.77-universal.jar": "universal",
  });
  const binpatcher = lib("net.neoforged.installertools:binarypatcher:2.1.2");
  p.zipEntries.set(binpatcher, { "META-INF/MANIFEST.MF": "Manifest-Version: 1.0\r\nMain-Class: net.neoforged.binarypatcher.ConsoleTool\r\n" });
  const patched = lib("net.neoforged:minecraft-client-patched:21.1.77");
  p.javaTool = (req) => {
    // The patcher writes its output; report the hash the installer expects.
    p.files.set(patched, "patched");
    p.hashes.set(patched, "abcabcabcabcabcabcabcabcabcabcabcabcabca");
    assert.equal(req.mainClass, "net.neoforged.binarypatcher.ConsoleTool");
    return { code: 0, output: "" };
  };

  const inst: Instance = { id: "neo", name: "Neo", gameVersion: "1.21.1", loader: "neoforge", loaderVersion: "21.1.77", createdAt: 0 };
  const stages: string[] = [];
  const prepared = await prepareInstance(p, inst, { env: { os: "windows", arch: "x64" }, progress: { stage: (_s, label) => stages.push(label) }, taskPrefix: "n" });

  // Installer download is verified against Maven's .sha1
  const installerDl = p.downloads.flatMap((d) => d.items).find((i) => i.url === installerUrl)!;
  assert.equal(installerDl.sha1, "77777777777777777777777777777777777777aa");

  // Only the client binpatch processor ran (server-only skipped; mappings downloaded by BLOOD).
  assert.equal(p.javaRuns.length, 1);
  const run = p.javaRuns[0]!;
  assert.deepEqual(run.classpath, [binpatcher, lib("net.neoforged.installertools:installertools:2.1.2"), lib("net.sf.jopt-simple:jopt-simple:5.0.4")]);
  assert.deepEqual(run.args, [
    "--clean",
    `${p.dataDir}\\versions\\1.21.1\\1.21.1.jar`,
    "--output",
    patched,
    "--apply",
    `${p.dataDir}\\tmp\\neoforge-neoforge-21.1.77\\data\\client.lzma`,
  ]);
  assert.equal(p.files.get(`${p.dataDir}\\tmp\\neoforge-neoforge-21.1.77\\data\\client.lzma`), undefined, "temporary files are cleaned up");
  const mappings = p.downloads.flatMap((d) => d.items).find((i) => i.url.endsWith("client.txt"))!;
  assert.equal(mappings.path, lib("net.minecraft:client:1.21.1:mappings@txt"));
  assert.equal(mappings.sha1, "9999000000000000000000000000000000000009");
  // Bundled maven/ artifacts were unpacked into libraries.
  assert.equal(p.files.get(lib("net.neoforged:neoforge:21.1.77:universal")), "universal");

  // Launch profile
  assert.equal(prepared.version.id, "neoforge-21.1.77");
  assert.equal(prepared.version.mainClass, "cpw.mods.bootstraplauncher.BootstrapLauncher");
  assert.equal(prepared.classpath[prepared.classpath.length - 1], `${p.dataDir}\\versions\\neoforge-21.1.77\\neoforge-21.1.77.jar`);
  assert.ok(p.files.has(`${p.dataDir}\\versions\\neoforge-21.1.77\\neoforge-21.1.77.jar`));
  assert.ok(prepared.classpath.includes(lib("net.neoforged:neoforge:21.1.77:universal")));
  assert.ok(stages.some((s) => s.startsWith("Patching Minecraft for NeoForge")));

  // Second launch: processors are not re-run.
  await prepareInstance(p, inst, { env: { os: "windows", arch: "x64" }, progress: { stage() {} }, taskPrefix: "n2" });
  assert.equal(p.javaRuns.length, 1);
});

test("NeoForge: a failing processor or a wrong output hash stops the install with a clear error", async () => {
  const p = baseSetup();
  const lib = (c: string) => `${p.dataDir}\\libraries\\${coordPath(c).split("/").join("\\")}`;
  const installerPath = lib("net.neoforged:neoforge:21.1.77:installer");
  p.zipEntries.set(installerPath, { "install_profile.json": JSON.stringify(neoProfile), "version.json": JSON.stringify(neoVersion) });
  p.zipEntries.set(lib("net.neoforged.installertools:binarypatcher:2.1.2"), { "META-INF/MANIFEST.MF": "Main-Class: x.Y\n" });
  const inst: Instance = { id: "neo", name: "Neo", gameVersion: "1.21.1", loader: "neoforge", loaderVersion: "21.1.77", createdAt: 0 };
  const opts = { env: { os: "windows", arch: "x64" } as const, progress: { stage() {} }, taskPrefix: "f" };

  p.javaTool = () => ({ code: 1, output: "line1\nException: boom" });
  await assert.rejects(prepareInstance(p, inst, opts), (e: { code: string; message: string }) => e.code === "forge_install_failed" && e.message.includes("boom"));

  p.javaTool = () => ({ code: 0, output: "" }); // succeeds but produces the wrong file
  await assert.rejects(prepareInstance(p, inst, opts), (e: { code: string }) => e.code === "hash_mismatch");
});

test("legacy Forge (1.12.2): universal jar from the installer, libraries from fixed-up Maven URLs", async () => {
  const p = baseSetup();
  const lib = (c: string) => `${p.dataDir}\\libraries\\${coordPath(c).split("/").join("\\")}`;
  const installerPath = lib("net.minecraftforge:forge:1.12.2-14.23.5.2860:installer");
  const legacy = {
    install: { path: "net.minecraftforge:forge:1.12.2-14.23.5.2860", filePath: "forge-1.12.2-14.23.5.2860-universal.jar", minecraft: "1.12.2" },
    versionInfo: {
      id: "1.12.2-forge-14.23.5.2860",
      inheritsFrom: "1.12.2",
      jar: "1.12.2",
      mainClass: "net.minecraft.launchwrapper.Launch",
      minecraftArguments: "--username ${auth_player_name} --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker",
      libraries: [
        { name: "net.minecraftforge:forge:1.12.2-14.23.5.2860", url: "http://files.minecraftforge.net/maven/" },
        { name: "net.minecraft:launchwrapper:1.12" },
        { name: "org.scala-lang:scala-library:2.11.1", url: "http://files.minecraftforge.net/maven/", clientreq: true, serverreq: true },
        { name: "com.example:server-only:1.0", clientreq: false, serverreq: true },
      ],
    },
  };
  p.zipEntries.set(installerPath, { "install_profile.json": JSON.stringify(legacy), "forge-1.12.2-14.23.5.2860-universal.jar": "universal" });
  const inst: Instance = { id: "old", name: "Old", gameVersion: "1.12.2", loader: "forge", loaderVersion: "14.23.5.2860", createdAt: 0 };
  const prepared = await prepareInstance(p, inst, { env: { os: "windows", arch: "x64" }, progress: { stage() {} }, taskPrefix: "l" });

  assert.equal(p.javaRuns.length, 0);
  assert.equal(p.files.get(lib("net.minecraftforge:forge:1.12.2-14.23.5.2860")), "universal");
  const urls = p.downloads.flatMap((d) => d.items).map((i) => i.url);
  assert.ok(urls.includes("https://maven.minecraftforge.net/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar"));
  assert.ok(urls.includes("https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar"));
  assert.ok(!urls.some((u) => u.includes("server-only")));
  assert.ok(!urls.some((u) => u.includes("forge-1.12.2-14.23.5.2860.jar")), "the universal jar is never downloaded");
  assert.equal(prepared.version.mainClass, "net.minecraft.launchwrapper.Launch");
  assert.equal(prepared.version.minecraftArguments, legacy.versionInfo.minecraftArguments);
  assert.equal(prepared.managedJava?.major, 8);
});

test("token resolution", () => {
  const ctx = { data: { A: "/x/a", SHA: "abc" }, libraryPath: (c: string) => `L:${c}`, extracted: (r: string) => `T:${r}` };
  assert.equal(resolveToken("{A}", ctx), "/x/a");
  assert.equal(resolveToken("[g:a:1]", ctx), "L:g:a:1");
  assert.equal(resolveToken("'lit'", ctx), "lit");
  assert.equal(resolveToken("--out={A}", ctx), "--out=/x/a");
  assert.equal(resolveToken("plain", ctx), "plain");
  assert.throws(() => resolveToken("{MISSING}", ctx));
});
