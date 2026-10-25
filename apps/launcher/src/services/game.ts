/** Play button: prepare → launch → track the running game. */
import { API_URL } from "./premium";

import { beforeLaunch as bloomClientBeforeLaunch } from "./bloomClient";
import { prepareInstance } from "../core/install/installer";
import { buildLaunchCommand, type QuickPlay } from "../core/mojang/args";
import { LogParser, detectMilestone } from "../core/mojang/logparse";
import { diagnoseCrash, type CrashDiagnosis } from "../core/mojang/crash";
import { JVM_PRESETS, recommendMemory } from "../core/perf/memory";
import { javaComponentFor } from "../core/mojang/version";
import { gcLogArg, gcStats, type GcStats } from "../core/perf/gclog";
import { BloomError } from "../core/platform";
import type { Instance } from "../core/instances/model";
import { activeAccount, launchCredentials } from "./accounts";
import { app, endTask, errorMessage, L, P, path, reportError, startTask, toast, updateTask, type LogLine, type SessionSummary } from "./app";
import { getInstance, saveInstance } from "./instances";
import { native, events } from "./native";
import { applyPendingPreset, writeOverlayConfig } from "./performance";
import { compatibility, listContent } from "./content";

const MAX_LOG_LINES = 5000;
const parsers = new Map<string, LogParser>();

export function isRunning(id: string): boolean {
  return Boolean(app.get().running[id]);
}

export function isPreparing(id: string): boolean {
  return app.get().tasks.some((t) => t.id === `launch-${id}`);
}

function pushLogs(instanceId: string, lines: LogLine[]) {
  app.set((s) => {
    const cur = s.logs[instanceId] ?? [];
    const next = cur.concat(lines);
    return { logs: { ...s.logs, [instanceId]: next.length > MAX_LOG_LINES ? next.slice(-MAX_LOG_LINES) : next } };
  });
}

async function resolveJava(inst: Instance, required: number, managed?: { path: string; major: number }): Promise<{ path: string; major: number }> {
  const custom = inst.javaPath || app.get().settings.javaPath;
  if (!custom) {
    if (!managed) throw new BloomError("java_missing", "No Java runtime is available.");
    return managed;
  }
  const info = await native.javaProbe(custom);
  if (info.major < required) {
    throw new BloomError(
      "java_too_old",
      `Minecraft ${inst.gameVersion} needs Java ${required}, but ${custom} is Java ${info.major}. Remove the custom Java path to let MOTION use the right version.`,
    );
  }
  if (!info.is64bit) toast({ kind: "warning", title: "32-bit Java", message: "This Java is 32-bit and can only use about 1.5 GB of RAM." });
  return { path: custom, major: info.major };
}

/** Remember the server/world for "Jump back in" (most recent first, at most 6). */
function rememberPlay(inst: Instance, opts: PlayOptions): Instance["recentPlays"] {
  const qp = opts.quickPlay;
  if (!qp) return inst.recentPlays;
  const target = qp.kind === "server" ? qp.address : qp.folder;
  const entry = { kind: qp.kind, target, name: opts.quickPlayName || target, at: Date.now() };
  return [entry, ...(inst.recentPlays ?? []).filter((r) => !(r.kind === qp.kind && r.target === target))].slice(0, 6);
}

export function gcLogPath(instanceId: string): string {
  return path(L().logs, "gc", `${instanceId}.log`);
}

export interface PlayOptions {
  repair?: boolean;
  ignoreModProblems?: boolean;
  /** Join this server or open this world once the game has loaded. */
  quickPlay?: QuickPlay;
  /** Display name for the quick-play target (server or world name), remembered for "Jump back in". */
  quickPlayName?: string;
}

export async function play(instanceId: string, opts: PlayOptions = {}) {
  const inst = getInstance(instanceId);
  if (!inst) return;
  if (isRunning(instanceId) || isPreparing(instanceId)) return;
  const account = activeAccount();
  if (!account) {
    toast({ kind: "info", title: "Sign in to play", message: "Add your Microsoft account to launch Minecraft." });
    app.set({ route: { page: "accounts" } });
    return;
  }

  // Catch mod problems that would crash the game (missing dependencies, wrong loader or
  // Minecraft version, known conflicts) before spending time on the launch.
  if (!opts.ignoreModProblems && inst.loader !== "vanilla") {
    const mods = await listContent(inst, "mods").catch(() => null);
    const errors = mods ? compatibility(inst, mods).filter((i) => i.severity === "error") : [];
    if (errors.length > 0) {
      toast(
        {
          kind: "error",
          title: `${errors.length} mod problem${errors.length === 1 ? "" : "s"} in ${inst.name}`,
          message: `${errors.slice(0, 2).map((e) => e.message).join(" ")} Review them in the installation's Mods tab, or launch anyway.`,
          action: { label: "Launch anyway", run: () => void play(instanceId, { ...opts, ignoreModProblems: true }) },
        },
        15000,
      );
      return;
    }
  }

  const taskId = `launch-${instanceId}`;
  startTask({ id: taskId, title: opts.repair ? `Repairing ${inst.name}` : `Launching ${inst.name}`, instanceId, cancellable: true, stage: "Signing in" });
  const t0 = performance.now();
  try {
    const creds = await launchCredentials(account);
    if (creds.offline) toast({ kind: "warning", title: "Playing offline", message: "Couldn't reach Microsoft. Singleplayer works; multiplayer needs an online session." });

    const p = P();
    const customJava = Boolean(inst.javaPath || app.get().settings.javaPath);
    const prepared = await prepareInstance(p, inst, {
      env: { os: p.os, arch: p.arch },
      repair: opts.repair,
      customJava,
      taskPrefix: taskId,
      progress: {
        stage: (_stage, label) => updateTask(taskId, { stage: label, progress: null, detail: undefined }),
      },
    });
    updateTask(taskId, { stage: "Starting Minecraft", progress: null, detail: undefined });
    const required = javaComponentFor(prepared.version).majorVersion;
    const java = await resolveJava(inst, required, prepared.managedJava);

    await applyPendingPreset(inst).catch((e) => console.warn("preset", e));
    // Keep MOTION Client on the version bundled with this launcher (never blocks the launch).
    updateTask(taskId, { stage: "Checking MOTION Client", progress: null, detail: undefined });
    await bloomClientBeforeLaunch(inst).catch((e) =>
      toast({ kind: "warning", title: "MOTION Client wasn't updated", message: errorMessage(e) }),
    );
    await writeOverlayConfig(inst).catch((e) => console.warn("overlay", e));

    const s = app.get().settings;
    const memory = inst.memory ?? s.memory ?? { minMb: 512, maxMb: 4096 };
    // Measured heap/GC statistics come from the JVM's own log (Java 9+).
    const gcArg = gcLogArg(gcLogPath(instanceId), java.major);
    if (gcArg) await P().mkdir(path(L().logs, "gc")).catch(() => undefined);
    const presetArgs = JVM_PRESETS.find((j) => j.id === (inst.jvmPreset ?? s.jvmPreset))?.args ?? [];
    const cmd = buildLaunchCommand(prepared.version, {
      env: { os: p.os, arch: p.arch },
      username: creds.username,
      uuid: creds.uuid,
      accessToken: creds.accessToken,
      xuid: creds.xuid,
      clientId: "",
      userType: "msa",
      versionName: prepared.version.id,
      gameDir: prepared.gameDir,
      assetsRoot: prepared.assetsRoot,
      assetsIndexName: prepared.assetsIndexName,
      gameAssets: prepared.virtualAssetsDir,
      nativesDir: prepared.nativesDir,
      librariesDir: L().libraries,
      classpath: prepared.classpath,
      launcherName: "bloom",
      launcherVersion: app.get().paths?.version ?? "0",
      loggingConfigPath: prepared.loggingConfigPath,
      memory: { minMb: Math.min(memory.minMb, memory.maxMb), maxMb: memory.maxMb },
      // MOTION Client asks this server which capes players wear.
      systemProps: API_URL ? { "bloodclient.api": API_URL } : undefined,
      extraJvmArgs: [...presetArgs, ...s.jvmArgs, ...(inst.jvmArgs ?? []), ...(gcArg ? [gcArg] : [])],
      extraGameArgs: [...s.gameArgs, ...(inst.gameArgs ?? [])],
      resolution: inst.resolution ?? s.resolution ?? undefined,
      fullscreen: inst.fullscreen ?? s.fullscreen,
      quickPlay: opts.quickPlay,
    });

    parsers.set(instanceId, new LogParser());
    app.set((st) => ({ logs: { ...st.logs, [instanceId]: [] } }));
    const pid = await native.launchGame({
      instanceId,
      java: java.path,
      args: [...cmd.jvmArgs, cmd.mainClass, ...cmd.gameArgs],
      cwd: prepared.gameDir,
      logFile: path(L().logs, "game", `${instanceId}.log`),
    });
    const prepareMs = Math.round(performance.now() - t0);
    app.set((st) => ({
      running: { ...st.running, [instanceId]: { instanceId, pid, startedAt: Date.now(), prepareMs, maxHeapMb: memory.maxMb, javaMajor: java.major } },
    }));
    await saveInstance({ ...(getInstance(instanceId) ?? inst), lastPlayedAt: Date.now(), recentPlays: rememberPlay(inst, opts) });
    endTask(taskId);
    if (s.onLaunch === "minimize") {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      void getCurrentWindow().minimize();
    }
  } catch (e) {
    endTask(taskId);
    reportError(`Couldn't launch ${inst.name}`, e);
  }
}

export function cancelLaunch(instanceId: string) {
  const base = `launch-${instanceId}`;
  for (const stage of ["meta", "java", "libs", "loader", "loader:proc", "assets", "natives"]) void native.cancelTask(`${base}:${stage}`);
}

export async function stopGame(instanceId: string) {
  await native.killGame(instanceId);
}

/** Wire game log/exit events and the stats poller. Call once at startup. */
export function initGameEvents() {
  void events.onGameLog((e) => {
    let parser = parsers.get(e.instanceId);
    if (!parser) {
      parser = new LogParser();
      parsers.set(e.instanceId, parser);
    }
    const out: LogLine[] = [];
    for (const l of e.lines) {
      for (const entry of parser.push(l.line, l.stream)) {
        out.push({ t: entry.time, level: entry.level, text: entry.thread ? `[${entry.thread}] ${entry.message}` : entry.message });
        if (detectMilestone(entry.message) === "ready") {
          const run = app.get().running[e.instanceId];
          if (run && run.startupMs === undefined) {
            const startupMs = Date.now() - run.startedAt;
            app.set((st) => ({ running: { ...st.running, [e.instanceId]: { ...run, startupMs } } }));
          }
        }
      }
    }
    if (out.length) pushLogs(e.instanceId, out);
  });

  void events.onGameExit(async (e) => {
    const run = app.get().running[e.instanceId];
    // A half-read XML log event must not leak into the next launch's output.
    parsers.delete(e.instanceId);
    const crashed = !e.killed && e.code !== 0 && e.code !== null;
    const crash = crashed ? diagnose(e.instanceId, e.code, run) : undefined;
    app.set((st) => {
      const running = { ...st.running };
      delete running[e.instanceId];
      return { running };
    });
    const inst = getInstance(e.instanceId);
    if (run) {
      // Final read of the GC log so the summary covers the whole session.
      const gc = await readGc(e.instanceId).catch(() => undefined);
      const samples = run.stats ?? [];
      const summary: SessionSummary = {
        instanceId: e.instanceId,
        startedAt: run.startedAt,
        endedAt: Date.now(),
        prepareMs: run.prepareMs,
        startupMs: run.startupMs,
        peakMemoryBytes: samples.length ? Math.max(...samples.map((x) => x.memoryBytes)) : undefined,
        avgCpuPercent: samples.length ? samples.reduce((a, x) => a + x.cpuPercent, 0) / samples.length : undefined,
        gc: gc ?? run.gc,
        maxHeapMb: run.maxHeapMb,
        exitCode: e.code,
        crash,
      };
      app.set((st) => ({ sessions: { ...st.sessions, [e.instanceId]: summary } }));
    }
    if (inst && run) {
      const played = Date.now() - run.startedAt;
      await saveInstance({
        ...inst,
        totalPlayMs: (inst.totalPlayMs ?? 0) + played,
        lastLaunch: { prepareMs: run.prepareMs, startupMs: run.startupMs, at: run.startedAt },
      }).catch(() => undefined);
      // Presets saved before options.txt existed can now be applied.
      await applyPendingPreset(inst).catch(() => undefined);
    }
    if (crashed && inst) {
      if (!run) {
        // Re-attached game (no session yet): keep the diagnosis for the Logs tab anyway.
        app.set((st) => ({ sessions: { ...st.sessions, [e.instanceId]: { instanceId: e.instanceId, startedAt: Date.now(), endedAt: Date.now(), prepareMs: 0, exitCode: e.code, crash } } }));
      }
      const top = crash?.[0];
      const viewLog = () => app.set({ route: { page: "instance", id: inst.id, tab: "logs" } });
      toast(
        {
          kind: "error",
          title: top ? `${inst.name} crashed: ${top.title}` : `${inst.name} crashed (exit code ${e.code})`,
          message: top ? top.detail : "MOTION couldn't recognise the cause. Check the log; recently added mods are the most common culprit.",
          action: top?.fix
            ? { label: "Fix it", run: () => void import("./crashFix").then((m) => (top.fix ? m.applyCrashFix(inst.id, top.fix) : undefined)) }
            : { label: "View log", run: viewLog },
        },
        0,
      );
    }
  });

  // Process stats every 2 s while games run; GC log every 6 s.
  let ticks = 0;
  setInterval(async () => {
    ticks++;
    const running = Object.values(app.get().running);
    for (const r of running) {
      const stats = await native.processStats(r.pid).catch(() => null);
      const gc = ticks % 3 === 0 ? await readGc(r.instanceId).catch(() => undefined) : undefined;
      const cur = app.get().running[r.instanceId];
      if (!cur) continue;
      const history = stats ? [...(cur.stats ?? []), { ...stats, at: Date.now() }].slice(-150) : cur.stats;
      app.set((st) => ({ running: { ...st.running, [r.instanceId]: { ...cur, stats: history, gc: gc ?? cur.gc } } }));
    }
  }, 2000);

  // Re-attach to games that were running when the launcher window was reloaded.
  native
    .runningGames()
    .then((games) => {
      for (const g of games) {
        app.set((st) => ({ running: { ...st.running, [g.instanceId]: { instanceId: g.instanceId, pid: g.pid, startedAt: Date.now(), prepareMs: 0 } } }));
      }
    })
    .catch(() => undefined);
}

/** Crash Doctor: explain the crash from the end of the game's output. */
function diagnose(instanceId: string, code: number | null, run: { maxHeapMb?: number; javaMajor?: number } | undefined): CrashDiagnosis[] {
  try {
    const lines = (app.get().logs[instanceId] ?? []).map((l) => l.text);
    const system = app.get().system;
    const inst = getInstance(instanceId);
    const maxSafeMb = system ? recommendMemory(system.totalMemory / 1_048_576, { modded: inst?.loader !== "vanilla", modCount: 0 }).maxSafeMb : undefined;
    return diagnoseCrash(lines, { exitCode: code, maxHeapMb: run?.maxHeapMb, maxSafeMb, javaMajor: run?.javaMajor });
  } catch (err) {
    console.warn("crash diagnosis failed", err);
    return [];
  }
}

async function readGc(instanceId: string): Promise<GcStats | undefined> {
  const text = await native.readTail(gcLogPath(instanceId), 2 * 1024 * 1024);
  if (!text) return undefined;
  const stats = gcStats(text);
  return stats.events > 0 ? stats : undefined;
}

export function formatPlaytime(ms: number | undefined): string {
  if (!ms) return "Never played";
  const h = Math.floor(ms / 3_600_000);
  const m = Math.floor((ms % 3_600_000) / 60_000);
  return h > 0 ? `${h}h ${m}m played` : `${m}m played`;
}
