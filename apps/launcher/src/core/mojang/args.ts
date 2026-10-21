import type { OsName } from "../platform";
import { rulesAllow, type RuleEnv } from "./rules";
import type { ArgumentValue, VersionJson } from "./types";

export interface LaunchContext {
  env: RuleEnv;
  username: string;
  uuid: string;
  accessToken: string;
  xuid: string;
  clientId: string;
  userType: "msa" | "legacy";
  versionName: string;
  gameDir: string;
  assetsRoot: string;
  assetsIndexName: string;
  /** For legacy virtual asset indexes. */
  gameAssets?: string;
  nativesDir: string;
  librariesDir: string;
  classpath: string[];
  launcherName: string;
  launcherVersion: string;
  /** Path to the downloaded log4j config, if the version has one. */
  loggingConfigPath?: string;
  memory: { minMb: number; maxMb: number };
  extraJvmArgs: string[];
  extraGameArgs: string[];
  resolution?: { width: number; height: number };
  fullscreen?: boolean;
}

export interface LaunchCommand {
  jvmArgs: string[];
  mainClass: string;
  gameArgs: string[];
}

function classpathSeparator(os: OsName): string {
  return os === "windows" ? ";" : ":";
}

function substitute(arg: string, vars: Record<string, string>): string {
  return arg.replace(/\$\{([a-zA-Z0-9_]+)\}/g, (_, key: string) => vars[key] ?? "");
}

function expand(values: ArgumentValue[] | undefined, env: RuleEnv): string[] {
  const out: string[] = [];
  for (const v of values ?? []) {
    if (typeof v === "string") out.push(v);
    else if (rulesAllow(v.rules, env)) out.push(...(Array.isArray(v.value) ? v.value : [v.value]));
  }
  return out;
}

/** Strip the `-Xmx`/`-Xms` and classpath flags BLOOD manages itself out of user-provided args. */
function sanitizeUserJvmArgs(args: string[]): string[] {
  return args.filter((a) => !/^-Xm[sx]/i.test(a) && a !== "-cp" && a !== "-classpath");
}

export function buildLaunchCommand(version: VersionJson, ctx: LaunchContext): LaunchCommand {
  const features = {
    is_demo_user: false,
    has_custom_resolution: Boolean(ctx.resolution),
    has_quick_plays_support: false,
    is_quick_play_singleplayer: false,
    is_quick_play_multiplayer: false,
    is_quick_play_realms: false,
  };
  const env: RuleEnv = { ...ctx.env, features };
  const vars: Record<string, string> = {
    auth_player_name: ctx.username,
    version_name: ctx.versionName,
    game_directory: ctx.gameDir,
    assets_root: ctx.assetsRoot,
    game_assets: ctx.gameAssets ?? ctx.assetsRoot,
    assets_index_name: ctx.assetsIndexName,
    auth_uuid: ctx.uuid,
    auth_access_token: ctx.accessToken,
    auth_session: `token:${ctx.accessToken}:${ctx.uuid}`,
    auth_xuid: ctx.xuid,
    clientid: ctx.clientId,
    user_type: ctx.userType,
    version_type: String(version.type ?? "release"),
    user_properties: "{}",
    natives_directory: ctx.nativesDir,
    launcher_name: ctx.launcherName,
    launcher_version: ctx.launcherVersion,
    classpath: ctx.classpath.join(classpathSeparator(ctx.env.os)),
    classpath_separator: classpathSeparator(ctx.env.os),
    library_directory: ctx.librariesDir,
    resolution_width: String(ctx.resolution?.width ?? ""),
    resolution_height: String(ctx.resolution?.height ?? ""),
  };

  let jvmTemplate: string[];
  let gameTemplate: string[];
  if (version.arguments) {
    jvmTemplate = expand(version.arguments.jvm, env);
    gameTemplate = expand(version.arguments.game, env);
  } else {
    // Legacy versions (≤1.12) only ship `minecraftArguments`.
    jvmTemplate = ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"];
    if (ctx.env.os === "windows") {
      // Mojang's launcher passes this so Intel drivers apply their Minecraft profile.
      jvmTemplate.unshift("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
    }
    gameTemplate = (version.minecraftArguments ?? "").split(" ").filter(Boolean);
    if (ctx.resolution) gameTemplate.push("--width", "${resolution_width}", "--height", "${resolution_height}");
  }

  const jvmArgs: string[] = [
    `-Xms${ctx.memory.minMb}M`,
    `-Xmx${ctx.memory.maxMb}M`,
    ...sanitizeUserJvmArgs(ctx.extraJvmArgs),
    "-Dlog4j2.formatMsgNoLookups=true",
  ];
  if (version.logging?.client && ctx.loggingConfigPath) {
    jvmArgs.push(version.logging.client.argument.replace("${path}", ctx.loggingConfigPath));
  }
  jvmArgs.push(...jvmTemplate.map((a) => substitute(a, vars)));

  const gameArgs = gameTemplate.map((a) => substitute(a, vars));
  if (ctx.fullscreen) gameArgs.push("--fullscreen");
  gameArgs.push(...ctx.extraGameArgs);

  return { jvmArgs, mainClass: version.mainClass, gameArgs };
}

/** Render a command for display/logging with the access token masked. */
export function redactCommand(cmd: LaunchCommand, token: string): string {
  const all = [...cmd.jvmArgs, cmd.mainClass, ...cmd.gameArgs];
  return all.map((a) => (token && a.includes(token) ? a.split(token).join("[redacted]") : a)).join(" ");
}
