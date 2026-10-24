import { test } from "node:test";
import assert from "node:assert/strict";
import { diagnoseCrash, modrinthSlugFor } from "./crash";

const lines = (s: string) => s.split("\n");
const ids = (s: string, ctx = {}) => diagnoseCrash(lines(s), { exitCode: 1, ...ctx }).map((d) => d.id);

test("crash: Fabric missing dependency names the mod and offers to install it", () => {
  const log = `[main/ERROR]: Incompatible mods found!
net.fabricmc.loader.impl.FormattedException: Some of your mods are incompatible with the game or each other!
A potential solution has been determined, this may resolve your problem:
	 - Install fabric-api, any version.
More details:
	 - Mod 'Sodium Extra' (sodium-extra) 0.5.1 requires any version of fabric-api, which is missing!
	 - Mod 'Mod Menu' (modmenu) 11.0.1 requires version 0.92.0 or later of mod 'Fabric API' (fabric-api), which is missing!`;
  const [d] = diagnoseCrash(lines(log), { exitCode: 1 });
  assert.equal(d?.id, "missing-dependency");
  assert.deepEqual(d?.mods, ["fabric-api"]);
  assert.match(d?.detail ?? "", /Sodium Extra and Mod Menu need fabric-api/);
  assert.deepEqual(d?.fix, { kind: "install-mods", modIds: ["fabric-api"] });
});

test("crash: Forge/NeoForge missing mandatory dependency", () => {
  const log = `Missing or unsupported mandatory dependencies:
	Mod ID: 'architectury', Requested by: 'rei', Expected range: '[9.1,)', Actual version: '[MISSING]'`;
  const [d] = diagnoseCrash(lines(log), { exitCode: 1 });
  assert.equal(d?.id, "missing-dependency");
  assert.deepEqual(d?.mods, ["architectury"]);
});

test("crash: mod built for another Minecraft version is turned off", () => {
  const log = `A potential solution has been determined, this may resolve your problem:
	 - Replace mod 'Sodium' (sodium) 0.5.8 with any version that is compatible with minecraft 1.21.1
More details:
	 - Mod 'Sodium' (sodium) 0.5.8 requires version 1.20.4 of minecraft, but only the wrong version is present: 1.21.1!`;
  const ds = diagnoseCrash(lines(log), { exitCode: 1 });
  assert.equal(ds[0]?.id, "wrong-game-version");
  assert.deepEqual(ds[0]?.fix, { kind: "disable-mods", modIds: ["sodium"] });
  assert.ok(!ds.some((d) => d.id === "missing-dependency"), "minecraft is never reported as a missing mod");
});

test("crash: incompatible mod pair", () => {
  const log = `	 - Remove mod 'OptiFabric' (optifabric).
	 - Mod 'Sodium' (sodium) 0.5.8 is incompatible with any version of mod 'OptiFabric' (optifabric), but a matching version is present: 1.13.0!`;
  const [d] = diagnoseCrash(lines(log), { exitCode: 1 });
  assert.equal(d?.id, "incompatible-mod");
  assert.deepEqual(d?.mods, ["optifabric"]);
});

test("crash: mixin failure points at the mod", () => {
  const log = `Caused by: org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered
Caused by: org.spongepowered.asm.mixin.throwables.MixinApplyError: Mixin [coolmod.mixins.json:WorldRendererMixin] from phase [DEFAULT] in config [coolmod.mixins.json] FAILED during APPLY
Mixin apply for mod coolmod failed coolmod.mixins.json:WorldRendererMixin from mod coolmod -> net.minecraft.class_761`;
  const [d] = diagnoseCrash(lines(log), { exitCode: 1 });
  assert.equal(d?.id, "mixin-failure");
  assert.deepEqual(d?.mods, ["coolmod"]);
});

test("crash: out of memory suggests a bigger heap, capped by what the PC can spare", () => {
  const log = `Exception in thread "Render thread" java.lang.OutOfMemoryError: Java heap space`;
  const [d] = diagnoseCrash(lines(log), { exitCode: -1, maxHeapMb: 4096, maxSafeMb: 12288 });
  assert.equal(d?.id, "out-of-memory");
  assert.deepEqual(d?.fix, { kind: "raise-memory", toMb: 6144 });

  const [capped] = diagnoseCrash(lines(log), { exitCode: -1, maxHeapMb: 6144, maxSafeMb: 6144 });
  assert.equal(capped?.fix, undefined, "no fix when the heap is already at the safe maximum");
  assert.match(capped?.detail ?? "", /most this PC can safely spare/);
});

test("crash: Java version from the class file version", () => {
  const log = `Error: LinkageError occurred while loading main class
java.lang.UnsupportedClassVersionError: dev/cool/Mod has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0`;
  const [d] = diagnoseCrash(lines(log), { exitCode: 1, javaMajor: 17 });
  assert.equal(d?.id, "java-version");
  assert.equal(d?.title, "This needs Java 21");
  assert.deepEqual(d?.fix, { kind: "reset-java" });
});

test("crash: corrupt jar → repair", () => {
  assert.deepEqual(ids("java.util.zip.ZipException: zip END header not found"), ["corrupt-files"]);
});

test("crash: graphics driver", () => {
  assert.deepEqual(ids("[Render thread/ERROR]: GLFW error 65542: WGL: The driver does not appear to support OpenGL"), ["graphics-driver"]);
  assert.deepEqual(ids("# Problematic frame:\n# C  [atio6axx.dll+0x1a2b3c]"), ["graphics-driver"]);
});

test("crash: suspected mods from the crash report, without repeating known culprits", () => {
  const log = `Suspected Mods:
	Lithium (lithium), Version: 0.12.1
	Minecraft (minecraft), Version: 1.21.1`;
  const [d] = diagnoseCrash(lines(log), { exitCode: 255 });
  assert.equal(d?.id, "suspected-mod");
  assert.deepEqual(d?.mods, ["lithium"]);
  assert.deepEqual(ids("Suspected Mods: NONE"), []);
});

test("crash: native JVM crash only when nothing better was found", () => {
  assert.deepEqual(ids("# A fatal error has been detected by the Java Runtime Environment:\n#  EXCEPTION_ACCESS_VIOLATION"), ["native-crash"]);
  assert.deepEqual(ids("", { exitCode: -1073741819 }), ["native-crash"]);
});

test("crash: an ordinary log gives no diagnosis", () => {
  assert.deepEqual(ids("[Render thread/INFO]: Stopping!\n[Render thread/INFO]: Sound engine started"), []);
});

test("crash: Modrinth slugs for common ids", () => {
  assert.equal(modrinthSlugFor("architectury"), "architectury-api");
  assert.equal(modrinthSlugFor("fabric-api"), "fabric-api");
  assert.equal(modrinthSlugFor("some_mod"), "some-mod");
});
