import { useEffect, useState, type ReactNode } from "react";
import { BUILT_IN_PROFILES } from "../../core/bloomclient/catalog";
import type { SettingsView } from "../../core/bloomclient/settingsFile";
import { readSettings as readBloomSettings, status as bloomStatus, type ClientStatus } from "../../services/bloomClient";
import type { Instance } from "../../core/instances/model";
import { recommendMemory } from "../../core/perf/memory";
import { PRESETS } from "../../core/perf/presets";
import { activeAccount } from "../../services/accounts";
import { app, errorMessage, navigate, updateSettings } from "../../services/app";
import { compatibility, listContent } from "../../services/content";
import { formatPlaytime, isRunning, play, stopGame } from "../../services/game";
import { selectedInstance } from "../../services/instances";
import { patchNoteBody, patchNotes, type PatchNote } from "../../services/news";
import { useStore } from "../../services/store";
import { Badge, Button, Card, cx, formatBytes, gb, IconButton, Menu, Modal, ProgressBar, SectionTitle, Skeleton, Spinner, timeAgo } from "../components";
import { Icon } from "../icons";
import { InstanceIcon, loaderLabel, NewInstanceModal } from "../instanceParts";
import { Markdown } from "../markdown";
import { BloodMark } from "../icons";
import { native } from "../../services/native";
import { UpgradeCard } from "../premiumParts";
import { LicenseCard } from "../licenseCard";
import { TeamCard } from "../teamCard";
import { installSummary, type InstallSummary } from "../../services/dashboard";
import { installUpdate, updateState } from "../../services/updater";
import { perfStates } from "../../services/bloodMods";
import { activeLayout, HUD_WIDGETS, widgetVisible } from "../../core/bloomclient/modules";
import type { ContentItem } from "../../services/content";

export function PlayButton({ inst, size = "lg" }: { inst: Instance; size?: "lg" | "md" }) {
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const task = useStore(app, (s) => s.tasks.find((t) => t.id === `launch-${inst.id}`));
  const big = size === "lg";
  if (task) {
    return (
      <div className={cx("rounded-[10px] bg-raised border border-line-strong px-4 flex flex-col justify-center", big ? "h-12 w-64" : "h-10 w-48")}>
        <div className="flex items-center justify-between text-[13px] mb-2">
          <span className="font-semibold truncate">{task.stage}</span>
          <span className="text-muted tabular-nums">{task.progress !== null ? `${Math.round(task.progress * 100)}%` : ""}</span>
        </div>
        <ProgressBar value={task.progress} />
      </div>
    );
  }
  if (running) {
    return (
      <button
        type="button"
        onClick={() => void stopGame(inst.id)}
        className={cx(
          "group rounded-[10px] border border-line-strong bg-panel font-semibold flex items-center justify-center gap-2.5 transition-colors cursor-pointer hover:border-danger/50 hover:text-danger",
          big ? "h-12 w-44 text-[15px]" : "h-10 w-32 text-[14px]",
        )}
      >
        <span className="inline-flex rounded-full h-2 w-2 bg-accent group-hover:bg-danger" />
        <span className="group-hover:hidden">Playing</span>
        <span className="hidden group-hover:inline">Stop game</span>
      </button>
    );
  }
  return (
    <button
      type="button"
      onClick={() => void play(inst.id)}
      className={cx(
        "play-button font-semibold flex items-center justify-center transition-[filter] cursor-pointer",
        big ? "h-12 w-44 text-[16px] gap-2.5" : "h-10 w-32 text-[14px] gap-2",
      )}
    >
      <Icon.Play size={big ? 18 : 15} />
      Play
    </button>
  );
}

export function LiveStats({ instanceId }: { instanceId: string }) {
  const run = useStore(app, (s) => s.running[instanceId]);
  const [, tick] = useState(0);
  useEffect(() => {
    const t = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, []);
  if (!run) return null;
  const last = run.stats?.[run.stats.length - 1];
  const up = Math.floor((Date.now() - run.startedAt) / 1000);
  return (
    <div className="flex gap-5 text-[13px] text-muted mt-4">
      <span className="flex items-center gap-1.5">
        <Icon.Clock size={15} /> {Math.floor(up / 60)}:{String(up % 60).padStart(2, "0")}
      </span>
      {last && (
        <>
          <span className="flex items-center gap-1.5">
            <Icon.Memory size={15} /> {formatBytes(last.memoryBytes)} process memory
          </span>
          <span className="flex items-center gap-1.5">
            <Icon.Cpu size={15} /> {last.cpuPercent.toFixed(0)}% CPU
          </span>
        </>
      )}
      {run.startupMs !== undefined && <span>Loaded in {(run.startupMs / 1000).toFixed(1)} s</span>}
    </div>
  );
}

function Hero({ inst, onCreate }: { inst: Instance; onCreate: () => void }) {
  const instances = useStore(app, (s) => s.instances);
  const settings = useStore(app, (s) => s.settings);
  const [modCount, setModCount] = useState<number | null>(null);
  const [client, setClient] = useState<ClientStatus | null>(null);
  const [summary, setSummary] = useState<InstallSummary | null>(null);
  const system = useStore(app, (s) => s.system);
  useEffect(() => {
    setModCount(null);
    setClient(null);
    setSummary(null);
    installSummary(inst).then(setSummary).catch(() => setSummary(null));
    if (inst.loader === "vanilla") setModCount(0);
    else
      listContent(inst, "mods")
        .then((m) => setModCount(m.filter((x) => x.enabled).length))
        .catch(() => setModCount(null));
    bloomStatus(inst).then(setClient).catch(() => setClient(null));
  }, [inst]);
  const memory = inst.memory ?? settings.memory;
  const played = [inst.lastPlayedAt ? `Played ${timeAgo(inst.lastPlayedAt)}` : "Not played yet", inst.totalPlayMs ? formatPlaytime(inst.totalPlayMs) : null]
    .filter(Boolean)
    .join(" · ");
  return (
    <section className="relative px-10 pt-6 pb-10 min-h-[440px] flex flex-col">
      <HomeTopBar />
      <div className="flex-1 min-h-10" />
      <div className="flex items-end justify-between gap-10">
        <div className="min-w-0 max-w-[680px]">
          <div className="flex flex-wrap items-center gap-2 mb-3">
            <Menu
              align="left"
              trigger={
                <button type="button" className="chip rounded-md h-7 px-2.5 inline-flex items-center gap-1.5 text-[12.5px] font-medium cursor-pointer hover:border-white/25 transition-colors" title="Switch Minecraft version">
                  <Icon.Cube size={13} /> Minecraft {inst.gameVersion} <Icon.ChevronDown size={12} className="opacity-70" />
                </button>
              }
              items={[
                ...[...instances]
                  .sort((a, b) => b.gameVersion.localeCompare(a.gameVersion, undefined, { numeric: true }))
                  .map((i) => ({
                    label: `${i.gameVersion}  ·  ${loaderLabel(i)}  —  ${i.name}`,
                    icon: i.id === inst.id ? <Icon.Check size={15} /> : <span className="w-[15px] inline-block" />,
                    onClick: () => updateSettings({ selectedInstanceId: i.id }),
                  })),
                "divider" as const,
                { label: "Play another version…", icon: <Icon.Plus size={15} />, onClick: onCreate },
              ]}
            />
            <span className="chip rounded-md h-7 px-2.5 inline-flex items-center text-[12.5px] font-medium">{loaderLabel(inst)}</span>
            {inst.modpack && <span className="chip rounded-md h-7 px-2.5 inline-flex items-center text-[12.5px] font-medium">{inst.modpack.name}</span>}
            {client?.enabled && (
              <span className="chip rounded-md h-7 px-2.5 inline-flex items-center gap-1.5 text-[12.5px] font-medium">
                <Icon.Motion size={13} className="text-accent" /> MOTION Client {client.installedVersion}
              </span>
            )}
          </div>
          <Menu
            align="left"
            trigger={
              <button type="button" className="flex items-center gap-3 cursor-pointer group text-left">
                <h1 className="font-display text-[40px] leading-[1.1] font-bold tracking-tight truncate">{inst.name}</h1>
                <Icon.ChevronDown size={22} className="text-fg/60 group-hover:text-fg transition-colors shrink-0 mt-1" />
              </button>
            }
            items={[
              ...instances.map((i) => ({
                label: `${i.name}  ·  ${i.gameVersion} ${loaderLabel(i)}`,
                icon: i.id === inst.id ? <Icon.Check size={15} /> : <span className="w-[15px] inline-block" />,
                onClick: () => updateSettings({ selectedInstanceId: i.id }),
              })),
              "divider" as const,
              { label: "Manage installations", icon: <Icon.Library size={15} />, onClick: () => navigate({ page: "library" }) },
            ]}
          />
          <div className="text-[13.5px] text-fg/70 mt-1.5">{played}</div>
          <div className="flex flex-wrap items-center gap-2 mt-6">
            <PlayButton inst={inst} />
            <HeroAction icon={<Icon.Hud size={16} />} label="MOTION Mods" onClick={() => navigate({ page: "mods", instanceId: inst.id })} />
            <HeroAction icon={<Icon.Puzzle size={16} />} label="Mods" onClick={() => navigate({ page: "instance", id: inst.id, tab: "content" })} />
            <HeroAction icon={<Icon.Gauge size={16} />} label="Optimize" onClick={() => navigate({ page: "performance", instanceId: inst.id })} />
          </div>
          <JumpBackIn inst={inst} />
          <LiveStats instanceId={inst.id} />
        </div>
        <div className="rounded-xl border border-line bg-panel/90 p-4 w-[264px] shrink-0 hidden lg:block">
          <div className="flex items-center justify-between mb-3.5">
            <div className="text-[13px] font-semibold">This installation</div>
            <span className={cx("text-[11px] font-semibold inline-flex items-center gap-1", summary?.filesReady ? "text-accent" : "text-faint")} title={summary?.filesReady ? "Game files are downloaded" : "Game files download on first launch"}>
              <span className={cx("h-1.5 w-1.5 rounded-full", summary?.filesReady ? "bg-accent" : "bg-faint")} />
              {summary ? (summary.filesReady ? "Ready" : "Downloads on play") : "…"}
            </span>
          </div>
          <div className="grid grid-cols-2 gap-y-3 gap-x-4 text-[13px]">
            <QuickStat label="Mods" value={inst.loader === "vanilla" ? "Vanilla" : modCount === null ? "…" : `${modCount} enabled`} />
            <QuickStat
              label="Allocated RAM"
              value={memory ? `${gb(memory.maxMb)}${system ? ` of ${gb(Math.round(system.totalMemory / 1_048_576))}` : ""}` : "Auto (4 GB)"}
            />
            <QuickStat
              label="Java"
              value={summary ? (summary.java ? `${summary.java} · ${summary.javaSource === "custom" ? "custom" : "auto"}` : "Auto") : "…"}
              warn={summary?.javaError}
            />
            <QuickStat label="Preset" value={PRESETS.find((p) => p.id === inst.performancePreset)?.name ?? "None"} />
          </div>
        </div>
      </div>
    </section>
  );
}

/** One-click rejoin of the servers and worlds recently opened from MOTION. */
function JumpBackIn({ inst }: { inst: Instance }) {
  const busy = useStore(app, (s) => Boolean(s.running[inst.id]) || s.tasks.some((t) => t.id === `launch-${inst.id}`));
  const recent = (inst.recentPlays ?? []).slice(0, 4);
  if (!recent.length) return null;
  return (
    <div className="flex flex-wrap items-center gap-2 mt-4">
      <span className="text-[12.5px] text-fg/60 mr-1">Jump back in</span>
      {recent.map((r) => (
        <button
          key={`${r.kind}:${r.target}`}
          type="button"
          disabled={busy}
          title={r.kind === "server" ? `Join ${r.target}` : `Open the world ${r.name}`}
          onClick={() => void play(inst.id, { quickPlay: r.kind === "server" ? { kind: "server", address: r.target } : { kind: "world", folder: r.target }, quickPlayName: r.name })}
          className="chip rounded-md h-7 pl-2 pr-2.5 inline-flex items-center gap-1.5 text-[12.5px] font-medium cursor-pointer hover:border-white/25 transition-colors disabled:opacity-50 disabled:pointer-events-none max-w-[220px]"
        >
          {r.kind === "server" ? <Icon.Globe size={14} /> : <Icon.Map size={14} />}
          <span className="truncate">{r.name}</span>
        </button>
      ))}
    </div>
  );
}

function HeroAction({ icon, label, onClick }: { icon: ReactNode; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="h-12 px-4 rounded-[10px] inline-flex items-center gap-2 text-[13.5px] font-medium transition-colors cursor-pointer border border-white/10 bg-black/35 hover:bg-black/50 hover:border-white/20"
    >
      <span className="text-fg/70">{icon}</span>
      {label}
    </button>
  );
}

function QuickStat({ label, value, warn }: { label: string; value: string; warn?: string }) {
  return (
    <div className="min-w-0" title={warn ?? value}>
      <div className="text-faint text-[12px]">{label}</div>
      <div className={cx("font-medium truncate mt-0.5", warn && "text-warn")}>{value}</div>
    </div>
  );
}

/** Signed out: the one thing to do first. Signed in, the account lives in the sidebar. */
function HomeTopBar() {
  const account = activeAccount();
  if (account) return null;
  return (
    <div className="flex items-center justify-end">
      <Button variant="primary" icon={<Icon.User size={16} />} onClick={() => navigate({ page: "accounts" })}>
        Sign in with Microsoft
      </Button>
    </div>
  );
}

/** MOTION Client status, active HUD and quick links for the selected installation. */
function BloomClientCard({ inst }: { inst: Instance }) {
  const [st, setSt] = useState<ClientStatus | null>(null);
  const [view, setView] = useState<SettingsView | null>(null);
  useEffect(() => {
    setSt(null);
    bloomStatus(inst).then(setSt).catch(() => setSt(null));
    readBloomSettings(inst).then(setView).catch(() => setView(null));
  }, [inst]);
  const profile = BUILT_IN_PROFILES.find((p) => p.id === (view?.activeProfile ?? "blood_moon"));
  const layout = view ? activeLayout(view.hud) : null;
  const shown = layout ? HUD_WIDGETS.filter((w) => widgetVisible(layout, w.id)) : [];
  return (
    <Card className="p-4">
      <div className="flex items-center gap-2 mb-3">
        <BloodMark size={18} />
        <div className="font-semibold text-[14px]">MOTION Client</div>
        <span className="flex-1" />
        {st?.enabled && <Badge tone="accent">v{st.installedVersion}</Badge>}
      </div>
      <div>
        {!st ? (
          <Skeleton className="h-14 rounded-lg" />
        ) : st.enabled ? (
          <>
            <div className="text-[13px] text-muted leading-relaxed">
              Press <Kbd>Right Shift</Kbd> in game for the MOTION menu and HUD editor.
              {profile && (
                <>
                  {" "}Look: <span className="text-fg">{profile.name}</span>.
                </>
              )}
              {st.updateAvailable && <div className="text-accent mt-1">Update to {st.bundle?.version} installs on next launch.</div>}
            </div>
            {layout && (
              <div className="mt-3">
                <div className="text-[12px] text-faint mb-1.5">HUD · {layout.name}</div>
                <div className="flex flex-wrap gap-1">
                  {shown.length ? (
                    shown.map((w) => (
                      <span key={w.id} className="h-6 px-2 rounded-md bg-raised border border-line text-[11.5px] text-muted inline-flex items-center">
                        {w.name}
                      </span>
                    ))
                  ) : (
                    <span className="text-[12px] text-faint">No widgets shown</span>
                  )}
                </div>
              </div>
            )}
            <div className="grid grid-cols-2 gap-2 mt-3">
              <Button variant="primary" icon={<Icon.Hud size={15} />} onClick={() => navigate({ page: "mods", instanceId: inst.id })}>
                MOTION Mods
              </Button>
              <Button icon={<Icon.Paint size={15} />} onClick={() => navigate({ page: "customize", instanceId: inst.id })}>
                Visuals
              </Button>
            </div>
          </>
        ) : st.support.ok ? (
          <>
            <div className="text-[13px] text-muted leading-relaxed">Built-in HUD (FPS, coordinates, armor, potions, keystrokes and more), a custom crosshair and visual options. Client-side only.</div>
            <Button className="mt-3 w-full" variant="primary" icon={<Icon.Plus size={15} />} onClick={() => navigate({ page: "mods", instanceId: inst.id })}>
              Add to this installation
            </Button>
          </>
        ) : (
          <>
            <div className="text-[13px] text-muted leading-relaxed">{st.support.message}</div>
            <Button className="mt-3 w-full" icon={<Icon.Hud size={15} />} onClick={() => navigate({ page: "mods", instanceId: inst.id })}>
              Learn more
            </Button>
          </>
        )}
      </div>
    </Card>
  );
}

const CORE_PERF = ["sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling"];

/** Installed mods at a glance: counts, the core performance mods and problems. */
function ModsOverview({ inst }: { inst: Instance }) {
  const [mods, setMods] = useState<ContentItem[] | null>(null);
  useEffect(() => {
    setMods(null);
    if (inst.loader === "vanilla") return setMods([]);
    listContent(inst, "mods").then(setMods).catch(() => setMods([]));
  }, [inst]);
  if (inst.loader === "vanilla") {
    return (
      <Card className="p-4">
        <div className="text-[13px] text-muted leading-relaxed">This is a vanilla installation, so it has no mods. Create a Fabric installation to use performance mods and MOTION Client.</div>
        <Button size="sm" className="mt-3" onClick={() => navigate({ page: "performance", instanceId: inst.id })}>
          See options
        </Button>
      </Card>
    );
  }
  if (!mods) return <Skeleton className="h-[150px] rounded-2xl" />;
  const enabled = mods.filter((m) => m.enabled).length;
  const disabled = mods.length - enabled;
  const problems = compatibility(inst, mods).filter((i) => i.severity === "error").length;
  const perf = perfStates(mods, CORE_PERF);
  return (
    <Card className="p-4">
      <div className="flex items-baseline gap-4">
        <div>
          <span className="font-display text-[22px] font-semibold tabular-nums">{enabled}</span>
          <span className="text-[12.5px] text-muted ml-1.5">enabled</span>
        </div>
        <div className="text-[12.5px] text-faint">{disabled} disabled</div>
        <span className="flex-1" />
        {problems > 0 ? <Badge tone="danger">{problems} problem{problems === 1 ? "" : "s"}</Badge> : <Badge tone="accent">No conflicts</Badge>}
      </div>
      <div className="mt-3 space-y-1.5">
        {perf.map((p) => {
          const st = p.item ? (p.item.enabled ? "on" : "off") : "missing";
          return (
            <div key={p.info.slug} className="flex items-center gap-2 text-[12.5px]">
              <span className={cx("h-4 w-4 rounded grid place-items-center shrink-0", st === "on" ? "bg-accent text-accent-ink" : st === "off" ? "bg-line-strong text-muted" : "border border-line-strong")}>
                {st === "on" && <Icon.Check size={11} strokeWidth={3} />}
                {st === "off" && <Icon.Minus size={11} strokeWidth={3} />}
              </span>
              <span className={st === "missing" ? "text-faint" : ""}>{p.info.title}</span>
              <span className="flex-1" />
              <span className="text-faint text-[11.5px]">{st === "on" ? "Active" : st === "off" ? "Disabled" : "Not installed"}</span>
            </div>
          );
        })}
      </div>
      <div className="grid grid-cols-2 gap-2 mt-3">
        <Button size="sm" onClick={() => navigate({ page: "instance", id: inst.id, tab: "content" })}>
          Manage mods
        </Button>
        <Button size="sm" variant="outline" onClick={() => navigate({ page: "mods", instanceId: inst.id, category: "performance" })}>
          Performance mods
        </Button>
      </div>
    </Card>
  );
}

/** An update that's downloading or ready to install (Settings → Updates has the full controls). */
function LauncherStatus() {
  const version = useStore(app, (s) => s.paths?.version);
  const u = useStore(updateState, (x) => x);
  if (u.status !== "available" && u.status !== "downloading" && u.status !== "ready" && u.status !== "installing") return null;
  const label =
    u.status === "available"
      ? `Version ${u.version} found`
      : u.status === "downloading"
        ? `Downloading ${u.version}${u.progress !== undefined ? ` · ${Math.round(u.progress * 100)}%` : ""}`
        : u.status === "ready"
          ? `${u.version} is ready to install`
          : `Installing ${u.version}…`;
  return (
    <Card className="p-4 flex items-center gap-3">
      <div className="h-9 w-9 rounded-lg grid place-items-center shrink-0 bg-accent/12 text-accent">
        {u.status === "downloading" || u.status === "installing" ? <Spinner size={15} /> : <Icon.Sync size={17} />}
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-semibold">MOTION {version ?? ""}</div>
        <div className="text-[12px] truncate text-muted" title={label}>
          {label}
        </div>
      </div>
      {u.status === "ready" && (
        <Button size="sm" variant="primary" onClick={() => void installUpdate()}>
          Restart now
        </Button>
      )}
    </Card>
  );
}

function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="inline-flex items-center h-5 px-1.5 rounded border border-line-strong bg-raised text-[11.5px] font-mono text-fg">{children}</kbd>;
}

function Recommendations({ inst }: { inst: Instance }) {
  const system = useStore(app, (s) => s.system);
  const settings = useStore(app, (s) => s.settings);
  const [mods, setMods] = useState<string[] | null>(null);
  const [problems, setProblems] = useState(0);
  useEffect(() => {
    if (inst.loader === "vanilla") return setMods([]);
    listContent(inst, "mods")
      .then((m) => {
        setMods(m.filter((x) => x.enabled).map((x) => x.meta?.id ?? ""));
        setProblems(compatibility(inst, m).filter((i) => i.severity === "error").length);
      })
      .catch(() => setMods([]));
  }, [inst]);
  const tips: { title: string; body: string; action?: { label: string; run: () => void } }[] = [];
  if (problems > 0) {
    tips.push({
      title: `${problems} mod problem${problems === 1 ? "" : "s"} found`,
      body: "Missing dependencies, wrong loaders or conflicting mods can stop the game from starting.",
      action: { label: "Review mods", run: () => navigate({ page: "instance", id: inst.id, tab: "content" }) },
    });
  }
  if (inst.loader === "vanilla") {
    tips.push({
      title: "Use Fabric for performance mods",
      body: "Sodium, Lithium and friends need a mod loader. Create a Fabric installation of the same version.",
      action: { label: "Open Performance", run: () => navigate({ page: "performance", instanceId: inst.id }) },
    });
  } else if (mods && !mods.includes("sodium") && !mods.includes("embeddium")) {
    tips.push({
      title: "Sodium isn't installed",
      body: "A modern rendering engine is usually the single biggest frame-rate improvement. MOTION checks Modrinth for a compatible version.",
      action: { label: "See performance mods", run: () => navigate({ page: "performance", instanceId: inst.id }) },
    });
  }
  if (!inst.performancePreset) {
    tips.push({
      title: "Pick a performance preset",
      body: "Presets change video settings in one click and are backed up so you can undo them.",
      action: { label: "Choose preset", run: () => navigate({ page: "performance", instanceId: inst.id }) },
    });
  }
  if (system) {
    const advice = recommendMemory(system.totalMemory / 1_048_576, { modded: inst.loader !== "vanilla", modCount: mods?.length ?? 0 });
    const mem = inst.memory ?? settings.memory;
    if (mem && mem.maxMb > advice.maxSafeMb) {
      tips.push({ title: "Memory allocation is very high", body: `${gb(mem.maxMb)} leaves little for Windows on this PC. ${advice.reason}` });
    } else if (!mem) {
      tips.push({ title: `Suggested memory: ${gb(advice.recommendedMb)}`, body: advice.reason });
    }
  }
  if (tips.length === 0) tips.push({ title: "Looking good", body: "No obvious improvements for this installation. Real-world FPS still depends on your hardware, world and settings." });
  return (
    <Card className="divide-y divide-line">
      {tips.slice(0, 2).map((t) => (
        <div key={t.title} className="p-4">
          <div className="font-semibold text-[13.5px]">{t.title}</div>
          <div className="text-[13px] text-muted mt-1 leading-relaxed">{t.body}</div>
          {t.action && (
            <button type="button" onClick={t.action.run} className="text-[13px] font-medium text-accent mt-2 hover:underline cursor-pointer">
              {t.action.label}
            </button>
          )}
        </div>
      ))}
    </Card>
  );
}

function Announcements() {
  const items = useStore(app, (s) => s.announcements);
  const enabled = useStore(app, (s) => s.settings.announcements);
  if (!enabled || items.length === 0) return null;
  return (
    <section>
      <SectionTitle>From MOTION</SectionTitle>
      <div className="space-y-2">
        {items.slice(0, 3).map((a) => (
          <Card key={a.id} className="p-4">
            <div className="flex items-center gap-2">
              <div className="font-semibold">{a.title}</div>
              {a.premiumOnly && <Badge tone="warn">Early access</Badge>}
              <span className="flex-1" />
              <span className="text-[12px] text-faint">{timeAgo(a.date)}</span>
            </div>
            <p className="text-[13px] text-muted mt-1.5 leading-relaxed">{a.body}</p>
            {a.url && (
              <button type="button" className="text-[13px] font-medium text-accent mt-2 hover:underline cursor-pointer" onClick={() => void native.openUrl(a.url!)}>
                Read more
              </button>
            )}
          </Card>
        ))}
      </div>
    </section>
  );
}

function News() {
  const snapshots = useStore(app, (s) => s.settings.showSnapshots);
  const [notes, setNotes] = useState<PatchNote[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<PatchNote | null>(null);
  const [body, setBody] = useState<string | null>(null);
  useEffect(() => {
    patchNotes(4, snapshots).then(setNotes).catch((e) => setError(errorMessage(e)));
  }, [snapshots]);
  useEffect(() => {
    setBody(null);
    if (open) patchNoteBody(open).then(setBody).catch((e) => setBody(`<p>${errorMessage(e)}</p>`));
  }, [open]);
  if (error) return <div className="text-muted text-[13px]">News is unavailable right now ({error}).</div>;
  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        {(notes ?? Array.from({ length: 4 }, () => null)).map((n, i) =>
          n ? (
            <Card key={n.id} className="overflow-hidden group" onClick={() => setOpen(n)}>
              <div className="h-28 bg-raised overflow-hidden">
                {n.image && <img src={n.image} alt="" className="h-full w-full object-cover" />}
              </div>
              <div className="p-3.5">
                <div className="font-semibold truncate">{n.title}</div>
                <div className="text-[12px] text-faint mt-1 capitalize">
                  {n.type}
                  {n.date && ` · ${timeAgo(n.date)}`}
                </div>
              </div>
            </Card>
          ) : (
            <Skeleton key={i} className="h-[172px] rounded-xl" />
          ),
        )}
      </div>
      <Modal open={Boolean(open)} onClose={() => setOpen(null)} title={open?.title} width={760}>
        {body === null ? (
          <div className="py-10 grid place-items-center text-muted">
            <Spinner size={22} />
          </div>
        ) : (
          <Markdown html={body} base="https://launchercontent.mojang.com/" />
        )}
      </Modal>
    </>
  );
}

export function HomePage() {
  const instances = useStore(app, (s) => s.instances);
  const selectedId = useStore(app, (s) => s.settings.selectedInstanceId);
  const [creating, setCreating] = useState(false);
  const newsEnabled = useStore(app, (s) => s.settings.news);
  void selectedId;
  const inst = selectedInstance();
  const recent = [...instances].filter((i) => i.lastPlayedAt).sort((a, b) => (b.lastPlayedAt ?? 0) - (a.lastPlayedAt ?? 0)).slice(0, 4);

  return (
    <div className="animate-in">
      {inst ? (
        <Hero inst={inst} onCreate={() => setCreating(true)} />
      ) : (
        <section className="relative px-10 pt-6 pb-10 min-h-[440px] flex flex-col">
          <HomeTopBar />
          <div className="flex-1" />
          <h1 className="font-display text-[40px] font-bold tracking-tight leading-tight">Welcome to MOTION</h1>
          <p className="text-fg/75 mt-2 max-w-xl leading-relaxed">Create an installation to get started. Pick a Minecraft version and a loader (Fabric is recommended for MOTION Client and performance mods). MOTION downloads the game and the right Java for you.</p>
          <div className="mt-6">
            <Button variant="primary" size="lg" icon={<Icon.Plus size={18} />} onClick={() => setCreating(true)}>
              New installation
            </Button>
          </div>
        </section>
      )}

      <div className="px-10 pb-12 grid grid-cols-[1fr_320px] gap-8">
        <div className="space-y-10 min-w-0">
          {recent.length > 0 && (
            <section>
              <SectionTitle action={<Button size="sm" variant="ghost" onClick={() => navigate({ page: "library" })}>View all</Button>}>Recently played</SectionTitle>
              <div className="grid grid-cols-2 gap-3">
                {recent.map((i) => (
                  <Card key={i.id} className={cx("p-3 flex items-center gap-3", i.id === inst?.id && "border-line-strong bg-raised/60")} onClick={() => updateSettings({ selectedInstanceId: i.id })}>
                    <InstanceIcon inst={i} size={40} />
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-[13.5px] truncate">{i.name}</div>
                      <div className="text-[12.5px] text-muted truncate">
                        {i.gameVersion} · {loaderLabel(i)} · {isRunning(i.id) ? "Running now" : timeAgo(i.lastPlayedAt)}
                      </div>
                    </div>
                    <IconButton label="Open installation" onClick={(e) => { e.stopPropagation(); navigate({ page: "instance", id: i.id }); }}>
                      <Icon.ChevronDown size={16} className="-rotate-90" />
                    </IconButton>
                  </Card>
                ))}
              </div>
            </section>
          )}
          <Announcements />
          {newsEnabled && (
            <section>
              <SectionTitle>Minecraft news</SectionTitle>
              <News />
            </section>
          )}
        </div>
        <aside className="space-y-8">
          <LauncherStatus />
          <LicenseCard compact />
          <TeamCard />
          {inst && <BloomClientCard inst={inst} />}
          {inst && (
            <section>
              <SectionTitle action={<Button size="sm" variant="ghost" onClick={() => navigate({ page: "instance", id: inst.id, tab: "content" })}>Manage</Button>}>Installed mods</SectionTitle>
              <ModsOverview inst={inst} />
            </section>
          )}
          {inst && (
            <section>
              <SectionTitle action={<Button size="sm" variant="ghost" onClick={() => navigate({ page: "performance", instanceId: inst.id })}>Open</Button>}>Performance tips</SectionTitle>
              <Recommendations inst={inst} />
              <p className="text-[12px] text-faint mt-3 leading-relaxed">Real-world FPS depends on your hardware, settings, world and mods.</p>
            </section>
          )}
          <UpgradeCard />
        </aside>
      </div>
      <NewInstanceModal open={creating} onClose={() => setCreating(false)} />
    </div>
  );
}
