import { test } from "node:test";
import assert from "node:assert/strict";
import { rulesAllow, type RuleEnv } from "./rules";
import { coordKey, coordPath, parseCoord } from "../util/maven";
import { planLibraries } from "./libraries";
import { mergeInherited, clientJarId, javaComponentFor } from "./version";
import { buildLaunchCommand, redactCommand, type LaunchContext } from "./args";
import { planAssets } from "./assets";
import { pickRuntime, planRuntime, runtimePlatformKey, javaExecutable } from "./java";
import { LogParser, detectMilestone } from "./logparse";
import { fabricProfile, javaIndex, javaManifest, legacyIndex, v1122, v1211, assetIndex17 } from "../test/fixtures";

const win: RuleEnv = { os: "windows", arch: "x64" };
const LIB = "C:\\bloom\\libraries";

test("rules: last matching rule wins; no rules means allowed", () => {
  assert.equal(rulesAllow(undefined, win), true);
  assert.equal(rulesAllow([{ action: "allow", os: { name: "osx" } }], win), false);
  assert.equal(rulesAllow([{ action: "allow" }, { action: "disallow", os: { name: "osx" } }], win), true);
  assert.equal(rulesAllow([{ action: "allow" }, { action: "disallow", os: { name: "windows" } }], win), false);
  assert.equal(rulesAllow([{ action: "allow", os: { arch: "x86" } }], win), false);
  assert.equal(rulesAllow([{ action: "allow", os: { arch: "x86" } }], { os: "windows", arch: "x86" }), true);
  assert.equal(rulesAllow([{ action: "allow", features: { has_custom_resolution: true } }], { ...win, features: {} }), false);
  assert.equal(
    rulesAllow([{ action: "allow", features: { has_custom_resolution: true } }], { ...win, features: { has_custom_resolution: true } }),
    true,
  );
  assert.equal(rulesAllow([{ action: "allow", os: { name: "windows", version: "^10\\." } }], { ...win, osVersion: "10.0" }), true);
});

test("maven coordinates", () => {
  assert.equal(coordPath("net.fabricmc:fabric-loader:0.16.5"), "net/fabricmc/fabric-loader/0.16.5/fabric-loader-0.16.5.jar");
  assert.equal(coordPath("org.lwjgl:lwjgl:3.3.3:natives-windows"), "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar");
  assert.equal(coordPath("de.oceanlabs.mcp:mcp_config:1.20.1@zip"), "de/oceanlabs/mcp/mcp_config/1.20.1/mcp_config-1.20.1.zip");
  assert.equal(coordKey("org.ow2.asm:asm:9.3"), "org.ow2.asm:asm");
  assert.equal(parseCoord("a:b:1:c").classifier, "c");
});

test("libraries (1.19+): keeps natives for this OS and arch only", () => {
  const plan = planLibraries(v1211, LIB, "\\", win);
  const names = plan.classpath.map((p) => p.split("\\").pop());
  assert.deepEqual(names, ["blocklist-1.0.10.jar", "asm-9.3.jar", "lwjgl-3.3.3.jar", "lwjgl-3.3.3-natives-windows.jar"]);
  assert.equal(plan.natives.length, 0);
  assert.equal(plan.downloads.length, 4);
  assert.ok(plan.downloads.every((d) => d.sha1 && d.size));
  assert.equal(plan.classpath[0], "C:\\bloom\\libraries\\com\\mojang\\blocklist\\1.0.10\\blocklist-1.0.10.jar");

  const arm = planLibraries(v1211, LIB, "\\", { os: "windows", arch: "arm64" });
  assert.ok(arm.classpath.some((p) => p.endsWith("natives-windows-arm64.jar")));
  assert.ok(!arm.classpath.some((p) => p.endsWith("natives-windows.jar")));
});

test("libraries (legacy): natives are extracted, ${arch} is substituted, osx-only rules apply", () => {
  const plan = planLibraries(v1122, LIB, "\\", win);
  assert.equal(plan.natives.length, 2);
  assert.ok(plan.natives[0]!.path.endsWith("lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar"));
  assert.deepEqual(plan.natives[0]!.exclude, ["META-INF/"]);
  assert.ok(plan.natives[1]!.path.endsWith("twitch-platform-6.5-natives-windows-64.jar"));
  // lwjgl-platform has no main artifact, so it isn't on the classpath
  assert.equal(plan.classpath.length, 1);
  assert.ok(plan.classpath[0]!.endsWith("lwjgl-2.9.4-nightly-20150209.jar"));
  // disallowed on osx
  assert.equal(planLibraries(v1122, LIB, "/", { os: "osx", arch: "x64" }).classpath.length, 0);
});

test("fabric profile merges over vanilla: loader libs first and override duplicates", () => {
  const merged = mergeInherited(fabricProfile, v1211);
  assert.equal(merged.id, "fabric-loader-0.16.5-1.21.1");
  assert.equal(merged.mainClass, "net.fabricmc.loader.impl.launch.knot.KnotClient");
  assert.equal(clientJarId(merged), "1.21.1");
  assert.equal(merged.inheritsFrom, undefined);
  assert.equal(javaComponentFor(merged).majorVersion, 21);
  const asm = merged.libraries.filter((l) => l.name.startsWith("org.ow2.asm:asm:"));
  assert.deepEqual(asm.map((l) => l.name), ["org.ow2.asm:asm:9.7.1"]);
  assert.equal(merged.libraries[0]!.name, "org.ow2.asm:asm:9.7.1");
  assert.ok(merged.arguments?.jvm?.includes("-DFabricMcEmu= net.minecraft.client.main.Main "));
  assert.ok(merged.arguments?.game?.includes("--username"));

  const plan = planLibraries(merged, LIB, "\\", win);
  const loader = plan.downloads.find((d) => d.url.includes("fabric-loader"));
  assert.equal(loader?.url, "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.5/fabric-loader-0.16.5.jar");
  const asmDl = plan.downloads.find((d) => d.url.includes("asm-9.7.1"));
  assert.equal(asmDl?.sha1, "f0ed132a49244b042cd0e15702ab9f2ce3cc8436");
});

function ctx(over: Partial<LaunchContext> = {}): LaunchContext {
  return {
    env: win,
    username: "Steve",
    uuid: "069a79f444e94726a5befca90e38aaf5",
    accessToken: "eyJ.secret.token",
    xuid: "2535",
    clientId: "client",
    userType: "msa",
    versionName: "1.21.1",
    gameDir: "C:\\bloom\\instances\\survival",
    assetsRoot: "C:\\bloom\\assets",
    assetsIndexName: "17",
    nativesDir: "C:\\bloom\\natives\\survival",
    librariesDir: LIB,
    classpath: ["C:\\a.jar", "C:\\b.jar"],
    launcherName: "bloom",
    launcherVersion: "0.1.0",
    loggingConfigPath: "C:\\bloom\\assets\\log_configs\\client-1.12.xml",
    memory: { minMb: 512, maxMb: 4096 },
    extraJvmArgs: ["-Xmx16G", "-XX:+UseG1GC"],
    extraGameArgs: [],
    ...over,
  };
}

test("launch args (modern): rules, substitution, classpath separator, memory", () => {
  const cmd = buildLaunchCommand(v1211, ctx());
  assert.equal(cmd.mainClass, "net.minecraft.client.main.Main");
  assert.ok(cmd.jvmArgs.includes("-Xmx4096M"));
  assert.ok(!cmd.jvmArgs.includes("-Xmx16G"), "user -Xmx is ignored in favour of the slider");
  assert.ok(cmd.jvmArgs.includes("-XX:+UseG1GC"));
  assert.ok(cmd.jvmArgs.includes("-Dlog4j.configurationFile=C:\\bloom\\assets\\log_configs\\client-1.12.xml"));
  assert.ok(cmd.jvmArgs.includes("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump"));
  assert.ok(!cmd.jvmArgs.includes("-XstartOnFirstThread"));
  assert.ok(!cmd.jvmArgs.includes("-Xss1M"));
  const cp = cmd.jvmArgs[cmd.jvmArgs.indexOf("-cp") + 1];
  assert.equal(cp, "C:\\a.jar;C:\\b.jar");
  assert.ok(cmd.jvmArgs.includes("-Djava.library.path=C:\\bloom\\natives\\survival"));
  const g = cmd.gameArgs;
  assert.equal(g[g.indexOf("--username") + 1], "Steve");
  assert.equal(g[g.indexOf("--accessToken") + 1], "eyJ.secret.token");
  assert.equal(g[g.indexOf("--userType") + 1], "msa");
  assert.ok(!g.includes("--demo"));
  assert.ok(!g.includes("--width"), "no resolution feature unless set");
  assert.ok(!g.includes("--quickPlaySingleplayer"));
  assert.ok(!redactCommand(cmd, "eyJ.secret.token").includes("eyJ.secret.token"));

  const sized = buildLaunchCommand(v1211, ctx({ resolution: { width: 1280, height: 720 }, fullscreen: true }));
  assert.equal(sized.gameArgs[sized.gameArgs.indexOf("--width") + 1], "1280");
  assert.ok(sized.gameArgs.includes("--fullscreen"));
});

test("launch args (legacy minecraftArguments)", () => {
  const cmd = buildLaunchCommand(v1122, ctx({ versionName: "1.12.2", assetsIndexName: "1.12", resolution: { width: 854, height: 480 } }));
  assert.ok(cmd.jvmArgs.includes("-Djava.library.path=C:\\bloom\\natives\\survival"));
  assert.ok(cmd.jvmArgs.some((a) => a.startsWith("-XX:HeapDumpPath=MojangTricks")));
  assert.equal(cmd.gameArgs[cmd.gameArgs.indexOf("--assetIndex") + 1], "1.12");
  assert.equal(cmd.gameArgs[cmd.gameArgs.indexOf("--width") + 1], "854");
  assert.ok(!cmd.gameArgs.some((a) => a.includes("${")));
});

test("assets: content-addressed objects, de-duplicated; legacy virtual copies", () => {
  const plan = planAssets(assetIndex17, "17", "C:\\bloom\\assets", "C:\\game", "\\");
  assert.equal(plan.downloads.length, 2);
  assert.equal(plan.downloads[0]!.url, "https://resources.download.minecraft.net/bd/bdf48ef6b5d0d23bbb02e17d04865216179f510a");
  assert.equal(plan.downloads[0]!.path, "C:\\bloom\\assets\\objects\\bd\\bdf48ef6b5d0d23bbb02e17d04865216179f510a");
  const legacy = planAssets(legacyIndex, "legacy", "C:\\bloom\\assets", "C:\\game", "\\");
  assert.equal(legacy.virtualDir, "C:\\bloom\\assets\\virtual\\legacy");
  assert.ok(legacy.downloads.some((d) => d.path === "C:\\bloom\\assets\\virtual\\legacy\\sounds\\step\\grass1.ogg"));
});

test("java runtimes: platform key, selection, file plan", () => {
  assert.equal(runtimePlatformKey("windows", "x64"), "windows-x64");
  assert.equal(runtimePlatformKey("windows", "arm64"), "windows-arm64");
  const entry = pickRuntime(javaIndex, "windows", "x64", "java-runtime-delta");
  assert.equal(entry.version.name, "21.0.3");
  assert.throws(() => pickRuntime(javaIndex, "windows", "x64", "jre-legacy"), /custom Java/);
  const items = planRuntime(javaManifest, "C:\\bloom\\runtimes\\java-runtime-delta", "\\");
  assert.equal(items.length, 2);
  assert.equal(items[0]!.path, "C:\\bloom\\runtimes\\java-runtime-delta\\bin\\javaw.exe");
  assert.equal(items[0]!.executable, true);
  assert.equal(javaExecutable("C:\\r", "windows", "\\"), "C:\\r\\bin\\javaw.exe");
});

test("log parser: log4j XML events, throwables and plain lines", () => {
  const p = new LogParser();
  assert.deepEqual(p.push('<log4j:Event logger="net.minecraft.client.Minecraft" timestamp="1700000000000" level="INFO" thread="Render thread">'), []);
  assert.deepEqual(p.push("  <log4j:Message><![CDATA[Backend library: LWJGL version 3.3.3+5]]></log4j:Message>"), []);
  const done = p.push("</log4j:Event>");
  assert.equal(done.length, 1);
  assert.equal(done[0]!.level, "INFO");
  assert.equal(done[0]!.thread, "Render thread");
  assert.equal(done[0]!.time, 1700000000000);
  assert.equal(detectMilestone(done[0]!.message), "window");

  const err = [
    '<log4j:Event logger="x" timestamp="1" level="ERROR" thread="main">',
    "  <log4j:Message><![CDATA[Crashed]]></log4j:Message>",
    "  <log4j:Throwable><![CDATA[java.lang.RuntimeException: boom",
    "\tat a.b(C.java:1)",
    "]]></log4j:Throwable>",
    "</log4j:Event>",
  ].flatMap((l) => p.push(l));
  assert.equal(err.length, 1);
  assert.equal(err[0]!.level, "ERROR");
  assert.match(err[0]!.message, /Crashed\njava.lang.RuntimeException: boom/);

  const plain = p.push("[12:00:01] [Render thread/WARN]: Missing sound");
  assert.equal(plain[0]!.level, "WARN");
  assert.equal(plain[0]!.message, "Missing sound");
  assert.equal(p.push("Exception in thread main", "stderr")[0]!.level, "WARN");
  assert.equal(detectMilestone("Sound engine started"), "ready");
});

test("Java version estimates for versions not downloaded yet", async () => {
  const { guessJavaMajor } = await import("./version");
  assert.equal(guessJavaMajor("1.8.9"), 8);
  assert.equal(guessJavaMajor("1.16.5"), 8);
  assert.equal(guessJavaMajor("1.17.1"), 16);
  assert.equal(guessJavaMajor("1.20.4"), 17);
  assert.equal(guessJavaMajor("1.20.5"), 21);
  assert.equal(guessJavaMajor("1.21.1"), 21);
  assert.equal(guessJavaMajor("26.1"), 25);
  assert.equal(guessJavaMajor("25w03a"), 21);
});
