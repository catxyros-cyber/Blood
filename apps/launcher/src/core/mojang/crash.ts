/**
 * Crash Doctor: reads the end of a game's output after it exits with an error and explains, in
 * plain words, what most likely went wrong, with a fix MOTION can apply where there is one.
 *
 * Only facts found in the log are reported. When nothing is recognised the result is empty and
 * the UI falls back to "check the log".
 */

export type CrashFix =
  | { kind: "raise-memory"; toMb: number }
  | { kind: "disable-mods"; modIds: string[] }
  | { kind: "install-mods"; modIds: string[] }
  | { kind: "repair" }
  | { kind: "reset-java" }
  | { kind: "open-shaders" };

export interface CrashDiagnosis {
  /** Stable id for de-duplication and tests. */
  id:
    | "missing-dependency"
    | "incompatible-mod"
    | "wrong-game-version"
    | "duplicate-mod"
    | "mixin-failure"
    | "out-of-memory"
    | "java-version"
    | "corrupt-files"
    | "graphics-driver"
    | "shader-pack"
    | "suspected-mod"
    | "native-crash";
  title: string;
  detail: string;
  /** Mod ids named in the log, when there are any. */
  mods?: string[];
  fix?: CrashFix;
}

export interface CrashContext {
  exitCode: number | null;
  /** -Xmx the game ran with. */
  maxHeapMb?: number;
  /** Highest heap MOTION considers safe on this PC. */
  maxSafeMb?: number;
  /** Java major version the game ran on. */
  javaMajor?: number;
}

const uniq = <T,>(xs: T[]) => [...new Set(xs)];

/** Ids Fabric and Forge print that are Minecraft/loader internals, not mods a player can act on. */
const NOT_A_MOD = new Set(["minecraft", "java", "fabricloader", "fabric-loader", "forge", "neoforge", "quilt_loader", "mixinextras"]);

function modsIn(ids: string[]): string[] {
  return uniq(ids.map((s) => s.trim().toLowerCase()).filter((s) => s && !NOT_A_MOD.has(s)));
}

const list = (xs: string[]) => (xs.length <= 1 ? (xs[0] ?? "") : `${xs.slice(0, -1).join(", ")} and ${xs[xs.length - 1]}`);

/** Java class file version → Java release (class 65 = Java 21). */
function javaFromClassVersion(v: number): number {
  return Math.round(v) - 44;
}

/** Round up to a whole 512 MB step. */
const roundUp = (mb: number) => Math.ceil(mb / 512) * 512;

export function diagnoseCrash(lines: readonly string[], ctx: CrashContext): CrashDiagnosis[] {
  // Only the end of the output matters, and the scan must stay cheap on a 5000-line log.
  const tail = lines.slice(-3000);
  const text = tail.join("\n");
  const out: CrashDiagnosis[] = [];
  const add = (d: CrashDiagnosis) => {
    if (!out.some((x) => x.id === d.id)) out.push(d);
  };

  // ------------------------------------------------------------------ missing dependencies
  // Fabric / Quilt:  Mod 'Sodium Extra' (sodium-extra) 0.5.1 requires any version of fabric-api, which is missing!
  //                  Mod 'X' (x) 1.0 requires version 2.0 or later of mod 'Fabric API' (fabric-api), which is missing!
  //                  - Install fabric-api, version 0.92.0 or later.
  const missing: string[] = [];
  const needers: string[] = [];
  for (const m of text.matchAll(/Mod '([^']+)' \(([^)]+)\)[^\n]*? requires [^\n]*? of (?:mod '[^']*' \(([^)]+)\)|([\w.\-]+)), which is missing/g)) {
    needers.push(m[1] ?? m[2] ?? "");
    missing.push(m[3] ?? m[4] ?? "");
  }
  for (const m of text.matchAll(/^\s*-\s*Install ([\w.\-]+),/gm)) missing.push(m[1] ?? "");
  // Forge / NeoForge:  Mod ID: 'architectury', Requested by: 'rei', Expected range: '[9.1,)', Actual version: '[MISSING]'
  for (const m of text.matchAll(/Mod ID: '([^']+)', Requested by: '([^']+)'[^\n]*Actual version: '\[MISSING\]'/g)) {
    missing.push(m[1] ?? "");
    needers.push(m[2] ?? "");
  }
  // NeoForge 20.4+: "Mod sodiumextra requires architectury 9.1 or above\nCurrently, architectury is not installed"
  for (const m of text.matchAll(/Mod ([\w.\-]+) requires ([\w.\-]+)[^\n]*\n[^\n]*Currently, \2 is not installed/g)) {
    needers.push(m[1] ?? "");
    missing.push(m[2] ?? "");
  }
  const missingMods = modsIn(missing);
  if (missingMods.length) {
    const who = uniq(needers.filter(Boolean));
    add({
      id: "missing-dependency",
      title: missingMods.length === 1 ? `A required mod is missing: ${missingMods[0]}` : `${missingMods.length} required mods are missing`,
      detail: `${who.length ? `${list(who.slice(0, 3))} need${who.length === 1 ? "s" : ""}` : "Your mods need"} ${list(missingMods)}, which ${missingMods.length === 1 ? "isn't" : "aren't"} installed. Install ${missingMods.length === 1 ? "it" : "them"} from Browse, or remove the mod that needs ${missingMods.length === 1 ? "it" : "them"}.`,
      mods: missingMods,
      fix: { kind: "install-mods", modIds: missingMods },
    });
  }

  // ------------------------------------------------------------------ wrong Minecraft version / incompatible
  // Fabric: - Replace mod 'Sodium' (sodium) 0.5.8 with any version that is compatible with minecraft 1.21.1
  //         Mod 'Sodium' (sodium) 0.5.8 requires version 1.20.4 of minecraft, but only the wrong version is present: 1.21.1!
  const wrongMc: string[] = [];
  for (const m of text.matchAll(/Replace mod '[^']+' \(([^)]+)\)[^\n]*compatible with minecraft/g)) wrongMc.push(m[1] ?? "");
  for (const m of text.matchAll(/Mod '[^']+' \(([^)]+)\)[^\n]*requires [^\n]*of (?:mod '[^']*' \()?minecraft\)?, but only the wrong version/g)) wrongMc.push(m[1] ?? "");
  const wrongMcMods = modsIn(wrongMc);
  if (wrongMcMods.length) {
    add({
      id: "wrong-game-version",
      title: wrongMcMods.length === 1 ? `${wrongMcMods[0]} is for a different Minecraft version` : `${wrongMcMods.length} mods are for a different Minecraft version`,
      detail: `${list(wrongMcMods)} ${wrongMcMods.length === 1 ? "was" : "were"} built for another version of Minecraft. Update ${wrongMcMods.length === 1 ? "it" : "them"} from the Mods tab, or turn ${wrongMcMods.length === 1 ? "it" : "them"} off.`,
      mods: wrongMcMods,
      fix: { kind: "disable-mods", modIds: wrongMcMods },
    });
  }

  // Fabric: Mod 'X' (x) 1.0 is incompatible with any version of mod 'Optifabric' (optifabric), but a matching version is present: 1.13!
  //         - Remove mod 'Optifabric' (optifabric).
  const breaking: string[] = [];
  for (const m of text.matchAll(/Mod '[^']+' \(([^)]+)\)[^\n]*is incompatible with [^\n]*mod '[^']*' \(([^)]+)\)/g)) breaking.push(m[2] ?? "");
  for (const m of text.matchAll(/^\s*-\s*Remove mod '[^']+' \(([^)]+)\)/gm)) breaking.push(m[1] ?? "");
  const breakingMods = modsIn(breaking).filter((x) => !wrongMcMods.includes(x));
  if (breakingMods.length) {
    add({
      id: "incompatible-mod",
      title: breakingMods.length === 1 ? `${breakingMods[0]} conflicts with your other mods` : `${breakingMods.length} mods conflict with each other`,
      detail: `The mod loader refused to start because ${list(breakingMods)} ${breakingMods.length === 1 ? "is" : "are"} known not to work with the rest of this installation. Turn ${breakingMods.length === 1 ? "it" : "them"} off to play.`,
      mods: breakingMods,
      fix: { kind: "disable-mods", modIds: breakingMods },
    });
  }

  // ------------------------------------------------------------------ duplicates
  const dupes = modsIn([
    ...[...text.matchAll(/(?:Duplicate mod(?: ID)?|Found duplicate mods?)[:\s]+'?([\w.\-]+)/gi)].map((m) => m[1] ?? ""),
    ...[...text.matchAll(/Mod ID:? '?([\w.\-]+)'? (?:was found|from mod files?)[^\n]*(?:multiple|duplicate)/gi)].map((m) => m[1] ?? ""),
  ]);
  if (dupes.length || /Found \d+ duplicate mods?|Duplicate mods found/i.test(text)) {
    add({
      id: "duplicate-mod",
      title: dupes.length ? `${list(dupes)} is installed twice` : "A mod is installed twice",
      detail: "Two files contain the same mod (often an old and a new version). Keep the newest one and remove the other from the Mods tab.",
      mods: dupes,
    });
  }

  // ------------------------------------------------------------------ mixin failures
  const mixinMods = modsIn([
    ...[...text.matchAll(/Mixin apply for mod ([\w.\-]+) failed/g)].map((m) => m[1] ?? ""),
    ...[...text.matchAll(/MixinTransformerError[\s\S]{0,400}?\[from mod ([\w.\-]+)\]/g)].map((m) => m[1] ?? ""),
    ...[...text.matchAll(/InvalidInjectionException[^\n]*\[[^\]]*from mod ([\w.\-]+)\]/g)].map((m) => m[1] ?? ""),
  ]);
  if (mixinMods.length) {
    add({
      id: "mixin-failure",
      title: mixinMods.length === 1 ? `${mixinMods[0]} broke while loading` : `${list(mixinMods)} broke while loading`,
      detail: `${list(mixinMods)} couldn't patch the game. This usually means ${mixinMods.length === 1 ? "it's" : "they're"} outdated or ${mixinMods.length === 1 ? "clashes" : "clash"} with another mod that changes the same code. Update or turn off ${mixinMods.length === 1 ? "it" : "them"}.`,
      mods: mixinMods,
      fix: { kind: "disable-mods", modIds: mixinMods },
    });
  }

  // ------------------------------------------------------------------ memory
  if (/java\.lang\.OutOfMemoryError: (?:Java heap space|GC overhead limit exceeded)/.test(text)) {
    const cur = ctx.maxHeapMb ?? 4096;
    const want = roundUp(Math.max(cur + 1024, cur * 1.5));
    const toMb = ctx.maxSafeMb ? Math.min(want, ctx.maxSafeMb) : want;
    const canRaise = toMb > cur;
    add({
      id: "out-of-memory",
      title: "Minecraft ran out of memory",
      detail: canRaise
        ? `The game used all ${(cur / 1024).toFixed(1)} GB it was given. Raising it to ${(toMb / 1024).toFixed(1)} GB should fix this.`
        : `The game used all ${(cur / 1024).toFixed(1)} GB it was given, and that's already the most this PC can safely spare. Remove some mods, or use a lighter resource pack.`,
      fix: canRaise ? { kind: "raise-memory", toMb } : undefined,
    });
  } else if (/java\.lang\.OutOfMemoryError: (?:Metaspace|Direct buffer memory|unable to create native thread)/.test(text)) {
    add({
      id: "out-of-memory",
      title: "Your PC ran out of memory",
      detail: "Minecraft couldn't get memory outside its heap. Close other programs (browsers especially) and try again. If it keeps happening, lower the RAM given to Minecraft a little.",
    });
  }

  // ------------------------------------------------------------------ Java version
  const cv = /UnsupportedClassVersionError[^\n]*class file version (\d+(?:\.\d+)?)/.exec(text);
  const fabricJava = /(?:requires|needs) (?:at least )?Java (\d+)/.exec(text);
  if (cv || (fabricJava && ctx.javaMajor && Number(fabricJava[1]) > ctx.javaMajor)) {
    const need = cv ? javaFromClassVersion(Number(cv[1])) : Number(fabricJava?.[1]);
    add({
      id: "java-version",
      title: `This needs Java ${need}`,
      detail: `Something in this installation was built for Java ${need}${ctx.javaMajor ? `, but the game ran on Java ${ctx.javaMajor}` : ""}. Let MOTION pick the right Java (remove any custom Java path), or remove the mod that needs the newer Java.`,
      fix: { kind: "reset-java" },
    });
  }

  // ------------------------------------------------------------------ corrupt files
  if (
    /java\.util\.zip\.ZipException|zip END header not found|Invalid or corrupt jarfile|Could not find or load main class net\.minecraft|ClassNotFoundException: net\.minecraft\.client\.main\.Main|error in opening zip file/i.test(
      text,
    )
  ) {
    add({
      id: "corrupt-files",
      title: "Some game files are damaged",
      detail: "A game or mod file couldn't be read (an interrupted download does this). Repair re-checks every file against its published hash and downloads the broken ones again.",
      fix: { kind: "repair" },
    });
  }

  // ------------------------------------------------------------------ graphics
  const gpu =
    /GLFW error 6554[23]|WGL: The driver does not appear to support OpenGL|Pixel format not accelerated|OpenGL [\d.]+ (?:is not supported|or higher is required)|Failed to create (?:the )?(?:OpenGL )?(?:context|window)|No OpenGL context|couldn't set pixel format/i.test(
      text,
    ) || /(?:atio6axx|nvoglv64|ig\d+icd64|igxelpicd64|amdxx64)\.dll/i.test(text);
  if (gpu) {
    add({
      id: "graphics-driver",
      title: "Your graphics driver stopped Minecraft",
      detail: "The crash happened inside the graphics driver or OpenGL couldn't start. Update your GPU driver from NVIDIA, AMD or Intel's website. On laptops, make sure Minecraft runs on the dedicated GPU (Windows Settings → Display → Graphics).",
    });
  }

  // ------------------------------------------------------------------ shaders
  if (/(?:net\.irisshaders|net\.coderbot\.iris|oculus)[\w.$]*Exception|Failed to (?:load|compile) shader ?pack|ShaderCompileException|Iris[^\n]*(?:crash|failed to)/i.test(text)) {
    add({
      id: "shader-pack",
      title: "The shader pack failed",
      detail: "Iris/Oculus hit an error while running your shader pack. Try another shader pack or turn shaders off, then play again.",
      fix: { kind: "open-shaders" },
    });
  }

  // ------------------------------------------------------------------ crash-report "Suspected Mods"
  // Fabric:   Suspected Mods: \n\tSodium (sodium), Version: 0.5.8
  // NeoForge: Suspected Mods: Sodium (sodium), Version: 0.5.8; Iris (iris), ...
  const susp = /Suspected Mods?:[ \t]*([^\n]*(?:\n[ \t]+[^\n]+)*)/.exec(text);
  if (susp && !/Suspected Mods?:[ \t]*(?:NONE|Unknown)/i.test(susp[0])) {
    const ids = modsIn([...(susp[1] ?? "").matchAll(/\(([\w.\-]+)\)/g)].map((m) => m[1] ?? ""));
    const known = new Set(out.flatMap((d) => d.mods ?? []));
    const fresh = ids.filter((x) => !known.has(x));
    if (fresh.length) {
      add({
        id: "suspected-mod",
        title: fresh.length === 1 ? `${fresh[0]} is the likely cause` : `Likely cause: ${list(fresh.slice(0, 3))}`,
        detail: `The game's crash report points at ${list(fresh.slice(0, 3))}. Update ${fresh.length === 1 ? "it" : "them"}, or turn ${fresh.length === 1 ? "it" : "them"} off and play again to confirm.`,
        mods: fresh,
        fix: { kind: "disable-mods", modIds: fresh },
      });
    }
  }

  // ------------------------------------------------------------------ JVM native crash
  if (!out.length && (/A fatal error has been detected by the Java Runtime Environment/.test(text) || ctx.exitCode === -1073741819 || ctx.exitCode === 3221225477)) {
    add({
      id: "native-crash",
      title: "Java crashed outside the game",
      detail: "The Java runtime itself crashed (an access violation). This is usually the graphics driver, an overlay (Discord, MSI Afterburner, RivaTuner) or antivirus. Update your GPU driver and turn off overlays, then try again.",
    });
  }

  return out;
}

/** Common mod ids whose Modrinth slug differs from the id. */
const SLUGS: Record<string, string> = {
  fabric: "fabric-api",
  "fabric-api-base": "fabric-api",
  architectury: "architectury-api",
  cloth_config: "cloth-config",
  "cloth-config2": "cloth-config",
  yet_another_config_lib_v3: "yacl",
  owo: "owo-lib",
  forgeconfigapiport: "forge-config-api-port",
  "cardinal-components": "cardinal-components-api",
  puzzleslib: "puzzles-lib",
  resourcefullib: "resourceful-lib",
  geckolib: "geckolib",
  fabric_language_kotlin: "fabric-language-kotlin",
  kotlinforforge: "kotlin-for-forge",
  player_animator: "playeranimator",
  playeranimator: "playeranimator",
  creativecore: "creativecore",
  balm: "balm",
  collective: "collective",
};

/** Best-guess Modrinth project slug for a mod id. */
export function modrinthSlugFor(modId: string): string {
  const id = modId.toLowerCase();
  return SLUGS[id] ?? id.replace(/_/g, "-");
}
