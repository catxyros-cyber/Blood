import { useEffect, useMemo, useState } from "react";
import type { Instance } from "../../core/instances/model";
import { pickLoaderVersion } from "../../core/loaders/loaders";
import { beforeAfter, capped, settingsPatch, type Finding, type FpsSession, type Impact } from "../../core/perf/doctor";
import { JVM_PRESETS } from "../../core/perf/memory";
import { PRESETS, readVideoSettings, type AppliedChange, type PresetId, type VideoSettings } from "../../core/perf/presets";
import { app, navigate, P, path, reportError, toast, updateSettings, type OverlayCorner } from "../../services/app";
import { createInstance, instanceGameDir, updateInstance } from "../../services/instances";
import { applyFinding, runDoctor, type DoctorReport } from "../../services/doctor";
import { applyPreset, backupSettings, listBackups, perfModStatus, restoreBackup, type BackupInfo, type PerfModStatus } from "../../services/performance";
import { useStore } from "../../services/store";
import { availableLoaderVersions } from "../../services/versions";
import { Badge, Button, Callout, Card, confirm, cx, EmptyState, formatBytes, gb, Modal, PageHeader, SectionTitle, Segmented, Select, Skeleton, Slider, Spinner, timeAgo, Toggle } from "../components";
import { Metric, Sparkline } from "../charts";
import { useContent } from "../contentEvents";
import { Icon } from "../icons";
import { installProject, useInstalling } from "../installFlow";
import { loaderLabel } from "../instanceParts";
import { PremiumBadge, PremiumLock, usePremium } from "../premiumParts";
import { MemoryControl } from "./Instance";

export function PerformancePage({ instanceId }: { instanceId?: string }) {
  const instances = useStore(app, (s) => s.instances);
  const selectedId = useStore(app, (s) => s.settings.selectedInstanceId);
  const inst = instances.find((i) => i.id === (instanceId ?? selectedId)) ?? instances[0];

  if (!inst) {
    return (
      <div className="animate-in">
        <PageHeader title="MOTION Performance" subtitle="Reversible optimizations, measured statistics." />
        <Card>
          <EmptyState icon={<Icon.Gauge size={24} />} title="Create an installation first" action={<Button onClick={() => navigate({ page: "library" })}>Go to Library</Button>}>
            Performance tools work per installation.
          </EmptyState>
        </Card>
      </div>
    );
  }

  return (
    <div className="animate-in">
      <PageHeader
        title="Performance"
        subtitle="What's costing frame rate in this installation, and your measured FPS."
        actions={
          <Select
            value={inst.id}
            onChange={(id) => navigate({ page: "performance", instanceId: id })}
            options={instances.map((i) => ({ value: i.id, label: `${i.name} — ${i.gameVersion} ${loaderLabel(i)}` }))}
            className="w-80"
          />
        }
      />
      <div className="grid grid-cols-[1fr_340px] gap-8 items-start">
        <div className="space-y-10 min-w-0">
          <Doctor inst={inst} />
          <Presets inst={inst} />
          <ModRecommendations inst={inst} />
          <Monitor inst={inst} />
          <Overlay inst={inst} />
        </div>
        <div className="space-y-6">
          <MeasuredFps inst={inst} />
          <MemoryJava inst={inst} />
          <Backups inst={inst} />
        </div>
      </div>
    </div>
  );
}


// ---------------------------------------------------------------------------
// Doctor: what costs frame rate here, each with one fix
// ---------------------------------------------------------------------------

const IMPACT_LABEL: Record<Impact, string> = { high: "High", medium: "Medium", low: "Low" };
const IMPACT_TONE: Record<Impact, string> = { high: "bg-danger", medium: "bg-warn", low: "bg-faint" };

function useDoctor(inst: Instance) {
  const { items } = useContent(inst, "mods");
  const [report, setReport] = useState<DoctorReport | null>(null);
  const [tick, setTick] = useState(0);
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  useEffect(() => {
    let live = true;
    runDoctor(inst)
      .then((r) => live && setReport(r))
      .catch(() => live && setReport({ findings: [], sessions: [], video: null, mods: [] }));
    return () => {
      live = false;
    };
  }, [inst.id, inst.memory?.maxMb, inst.performancePreset, items?.length, items?.filter((m) => m.enabled).length, tick, running]);
  return { report, refresh: () => setTick((t) => t + 1) };
}

function Doctor({ inst }: { inst: Instance }) {
  const { report, refresh } = useDoctor(inst);
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const [busy, setBusy] = useState<string | null>(null);
  const findings = report?.findings ?? [];
  const patch = settingsPatch(findings);
  const settingsCount = findings.filter((f) => f.action?.kind === "settings").length;

  async function fix(f: Finding | null, all?: boolean) {
    if (!report) return;
    setBusy(all ? "all" : f!.id);
    try {
      await applyFinding(inst, report, f ?? findings[0]!, all ? patch : undefined);
      toast({ kind: "success", title: all ? "Settings changed" : f!.actionLabel ?? "Done", message: all || f?.action?.kind === "settings" ? "A backup of your old settings was saved (Backups, on this page)." : undefined });
      refresh();
    } catch (e) {
      reportError("Couldn't apply that", e);
    } finally {
      setBusy(null);
    }
  }

  return (
    <section>
      <SectionTitle
        action={
          settingsCount > 1 ? (
            <Button size="sm" variant="primary" loading={busy === "all"} disabled={running || busy !== null} onClick={() => void fix(null, true)}>
              Apply all {settingsCount} settings
            </Button>
          ) : undefined
        }
      >
        Fixes
      </SectionTitle>
      {running && <p className="text-[13px] text-warn mb-3">Close Minecraft to apply fixes. It rewrites its settings and keeps mod files open while it runs.</p>}
      {!report ? (
        <Skeleton className="h-40 rounded-xl" />
      ) : findings.length === 0 ? (
        <div className="rounded-xl border border-line px-4 py-5 text-[14px]">
          Nothing to fix. This installation already runs with fast settings and no heavy mods.
        </div>
      ) : (
        <div className="rounded-xl border border-line divide-y divide-line overflow-hidden">
          {findings.map((f) => (
            <div key={f.id} className="grid grid-cols-[76px_1fr_auto] items-start gap-4 px-4 py-3.5">
              <div className="flex items-center gap-2 pt-0.5 text-[12px] text-muted">
                <span className={cx("h-1.5 w-1.5 rounded-full", IMPACT_TONE[f.impact])} />
                {IMPACT_LABEL[f.impact]}
              </div>
              <div className="min-w-0">
                <div className="text-[14px] font-medium">{f.title}</div>
                <div className="text-[13px] text-muted leading-snug mt-0.5">{f.detail}</div>
              </div>
              {f.action && f.action.kind !== "install" && (
                <Button size="sm" loading={busy === f.id} disabled={running || busy !== null} onClick={() => void fix(f)}>
                  {f.actionLabel ?? "Fix"}
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
      <p className="text-[12px] text-faint mt-2.5">
        Impact is how much these usually cost, not a promise. Your real numbers are under Measured FPS.
      </p>
    </section>
  );
}

function fps(n: number): string {
  return n >= 100 ? String(Math.round(n)) : n.toFixed(1).replace(/\.0$/, "");
}

function MeasuredFps({ inst }: { inst: Instance }) {
  const { report } = useDoctor(inst);
  const ba = report ? beforeAfter(report.sessions) : null;
  const row = (label: string, s: FpsSession) => (
    <div className="grid grid-cols-[64px_1fr_1fr] items-baseline gap-2">
      <span className="text-[12px] text-faint">{label}</span>
      <span>
        <span className="text-[20px] font-semibold tabular-nums">{fps(s.avgFps)}</span>
        <span className="text-[12px] text-muted ml-1">avg</span>
      </span>
      <span>
        <span className="text-[16px] font-medium tabular-nums">{fps(s.low1Fps)}</span>
        <span className="text-[12px] text-muted ml-1">1% low</span>
      </span>
    </div>
  );
  const detail = (s: FpsSession) =>
    [`${Math.round(s.seconds / 60) || "<1"} min`, s.resolution, s.multiplayer ? "server" : "singleplayer", s.renderDistance !== undefined ? `${s.renderDistance} chunks` : null, s.shaders ? "shaders" : null].filter(Boolean).join(" · ");
  const change = ba?.before ? (ba.latest.avgFps - ba.before.avgFps) / ba.before.avgFps : null;
  return (
    <Card className="p-5">
      <h3 className="font-semibold mb-3">Measured FPS</h3>
      {!report ? (
        <Skeleton className="h-20" />
      ) : !ba ? (
        <p className="text-[13px] text-muted leading-relaxed">
          Play for a few minutes with MOTION Client on and your real frame rate shows up here, so you can see what each fix did. Only gameplay counts: not menus, pauses or loading.
        </p>
      ) : (
        <div className="space-y-3">
          {row("Latest", ba.latest)}
          <div className="text-[12px] text-faint -mt-1.5 pl-[72px]">{detail(ba.latest)} · {timeAgo(ba.latest.endedAt)}</div>
          {ba.before && (
            <>
              {row("Before", ba.before)}
              <div className="pl-[72px] text-[13px]">
                <span className={cx("font-semibold tabular-nums", change! >= 0 ? "text-accent" : "text-danger")}>
                  {change! >= 0 ? "+" : ""}
                  {Math.round(change! * 100)}%
                </span>
                <span className="text-muted"> after: {ba.changes.join(", ")}</span>
              </div>
            </>
          )}
          {capped(ba.latest) && <p className="text-[12px] text-warn leading-snug">VSync or an FPS limit was on, so this is that limit, not what your PC can reach.</p>}
        </div>
      )}
    </Card>
  );
}


// ---------------------------------------------------------------------------
// Presets
// ---------------------------------------------------------------------------

function summary(v: VideoSettings): string {
  return [`${v.renderDistance} chunks`, v.graphics === "fast" ? "Fast graphics" : "Fancy graphics", `particles ${v.particles}`, v.clouds === "off" ? "no clouds" : `${v.clouds} clouds`].join(" · ");
}

function Presets({ inst }: { inst: Instance }) {
  const premium = usePremium();
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const [busy, setBusy] = useState<PresetId | null>(null);
  const [result, setResult] = useState<{ name: string; changes: AppliedChange[]; pending: boolean } | null>(null);
  const [custom, setCustom] = useState(false);

  async function apply(id: PresetId, video?: VideoSettings) {
    setBusy(id);
    try {
      const r = await applyPreset(inst, id, video);
      setResult({ name: id === "custom" ? "Custom" : PRESETS.find((p) => p.id === id)!.name, changes: r.changes, pending: r.pending });
    } catch (e) {
      reportError("Couldn't apply the preset", e);
    } finally {
      setBusy(null);
    }
  }

  return (
    <section>
      <SectionTitle action={<span className="text-[12px] text-faint">Backs up your settings first</span>}>Presets</SectionTitle>
      {running && (
        <div className="mb-3">
          <Callout tone="warn">Close Minecraft before applying a preset — the game rewrites its settings when it exits.</Callout>
        </div>
      )}
      <div className="grid grid-cols-2 gap-3">
        {PRESETS.map((p) => {
          const locked = p.premium && !premium;
          const active = inst.performancePreset === p.id;
          return (
            <Card key={p.id} className={cx("p-4 flex flex-col", active && "border-accent/50 bg-accent/[0.03]")}>
              <div className="flex items-center gap-2">
                <div className="font-display font-semibold text-[16px]">{p.name}</div>
                {p.premium && <PremiumBadge />}
                {active && <Badge tone="accent">Applied</Badge>}
              </div>
              <p className="text-[13px] text-muted mt-1 leading-relaxed flex-1">{p.tagline}</p>
              <div className="text-[12px] text-faint mt-2">{summary(p.video)}</div>
              <div className="mt-3">
                {locked ? (
                  <Button size="sm" variant="outline" icon={<Icon.Lock size={14} />} onClick={() => navigate({ page: "premium" })}>
                    Unlock with Premium
                  </Button>
                ) : (
                  <Button size="sm" variant={active ? "secondary" : "primary"} loading={busy === p.id} disabled={running} onClick={() => void apply(p.id)}>
                    {active ? "Apply again" : "Apply"}
                  </Button>
                )}
              </div>
            </Card>
          );
        })}
      </div>
      <div className="mt-3">
        {premium ? (
          <Card className="p-4 flex items-center gap-3">
            <Icon.Sliders size={18} className="text-accent" />
            <div className="flex-1">
              <div className="font-semibold">Custom</div>
              <div className="text-[13px] text-muted">Pick each setting yourself, starting from your current options.</div>
            </div>
            <Button size="sm" onClick={() => setCustom(true)} disabled={running}>
              Edit settings
            </Button>
          </Card>
        ) : (
          <PremiumLock title="Custom preset editor" compact>
            Fine-tune every video setting MOTION manages, with the same automatic backups.
          </PremiumLock>
        )}
      </div>
      <CustomEditor inst={inst} open={custom} onClose={() => setCustom(false)} onApply={(v) => void apply("custom", v).then(() => setCustom(false))} />
      <Modal open={Boolean(result)} onClose={() => setResult(null)} title={`${result?.name} applied`} width={520} footer={<Button variant="primary" onClick={() => setResult(null)}>Done</Button>}>
        {result?.pending ? (
          <p className="text-muted">This installation hasn't been started yet, so Minecraft hasn't created its settings file. MOTION will apply the preset automatically after the first launch.</p>
        ) : result && result.changes.length === 0 ? (
          <p className="text-muted">Your settings already matched this preset — nothing changed.</p>
        ) : (
          <div className="space-y-1.5">
            <p className="text-muted text-[14px] mb-3">A backup was saved first. Changes take effect next time you play:</p>
            {result?.changes.map((c) => (
              <div key={c.key} className="flex items-center gap-2 text-[14px] bg-panel-2 border border-line rounded-lg px-3 py-2">
                <Icon.Check size={15} className="text-accent" />
                <span className="font-medium">{c.label}</span>
                <span className="flex-1" />
                <span className="text-faint">{c.from ?? "—"}</span>
                <span className="text-muted">→</span>
                <span>{c.to}</span>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </section>
  );
}

function CustomEditor({ inst, open, onClose, onApply }: { inst: Instance; open: boolean; onClose: () => void; onApply: (v: VideoSettings) => void }) {
  const [v, setV] = useState<VideoSettings | null>(null);
  useEffect(() => {
    if (!open) return;
    P()
      .readText(path(instanceGameDir(inst), "options.txt"))
      .then((t) => setV(readVideoSettings(t, PRESETS[1]!.video)))
      .catch(() => setV(PRESETS[1]!.video));
  }, [open, inst.id]);
  const set = (patch: Partial<VideoSettings>) => setV((cur) => (cur ? { ...cur, ...patch } : cur));
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Custom video settings"
      width={620}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" disabled={!v} onClick={() => v && onApply(v)}>
            Back up and apply
          </Button>
        </>
      }
    >
      {!v ? (
        <div className="py-10 grid place-items-center">
          <Spinner />
        </div>
      ) : (
        <div className="space-y-4">
          <div>
            <div className="text-[13px] font-medium mb-1.5">Render distance</div>
            <Slider value={v.renderDistance} min={2} max={32} step={1} onChange={(x) => set({ renderDistance: x })} format={(x) => `${x} chunks`} />
          </div>
          <div>
            <div className="text-[13px] font-medium mb-1.5">Simulation distance</div>
            <Slider value={v.simulationDistance} min={5} max={32} step={1} onChange={(x) => set({ simulationDistance: x })} format={(x) => `${x} chunks`} />
          </div>
          <div>
            <div className="text-[13px] font-medium mb-1.5">Entity distance</div>
            <Slider value={Math.round(v.entityDistance * 100)} min={50} max={500} step={25} onChange={(x) => set({ entityDistance: x / 100 })} format={(x) => `${x}%`} />
          </div>
          <div>
            <div className="text-[13px] font-medium mb-1.5">Max framerate</div>
            <Slider value={v.maxFps} min={30} max={260} step={10} onChange={(x) => set({ maxFps: x })} format={(x) => (x >= 260 ? "Unlimited" : `${x} fps`)} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-[13px] font-medium mb-1.5">Graphics</div>
              <Segmented value={v.graphics} onChange={(x) => set({ graphics: x })} options={[{ value: "fast", label: "Fast" }, { value: "fancy", label: "Fancy" }]} />
            </div>
            <div>
              <div className="text-[13px] font-medium mb-1.5">Clouds</div>
              <Segmented value={v.clouds} onChange={(x) => set({ clouds: x })} options={[{ value: "off", label: "Off" }, { value: "fast", label: "Fast" }, { value: "fancy", label: "Fancy" }]} />
            </div>
            <div>
              <div className="text-[13px] font-medium mb-1.5">Particles</div>
              <Segmented value={v.particles} onChange={(x) => set({ particles: x })} options={[{ value: "all", label: "All" }, { value: "decreased", label: "Decreased" }, { value: "minimal", label: "Minimal" }]} />
            </div>
            <div>
              <div className="text-[13px] font-medium mb-1.5">Biome blend</div>
              <Slider value={v.biomeBlend} min={0} max={7} step={1} onChange={(x) => set({ biomeBlend: x })} format={(x) => (x === 0 ? "Off" : `${x * 2 + 1}×${x * 2 + 1}`)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-x-6 gap-y-1">
            <Toggle checked={v.smoothLighting} onChange={(x) => set({ smoothLighting: x })} label="Smooth lighting" />
            <Toggle checked={v.entityShadows} onChange={(x) => set({ entityShadows: x })} label="Entity shadows" />
            <Toggle checked={v.vsync} onChange={(x) => set({ vsync: x })} label="VSync" />
            <Toggle checked={v.viewBobbing} onChange={(x) => set({ viewBobbing: x })} label="View bobbing" />
          </div>
          <div>
            <div className="text-[13px] font-medium mb-1.5">Mipmap levels</div>
            <Slider value={v.mipmapLevels} min={0} max={4} step={1} onChange={(x) => set({ mipmapLevels: x })} />
          </div>
        </div>
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Performance mods to add
// ---------------------------------------------------------------------------

function PerfModRow({ s, inst }: { s: PerfModStatus; inst: Instance }) {
  const busy = useInstalling(inst.id, s.projectId ?? s.mod.slug);
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className={cx("h-9 w-9 rounded-lg grid place-items-center shrink-0", s.installed ? "bg-accent/15 text-accent" : "bg-raised text-muted")}>
        {s.installed ? <Icon.Check size={17} /> : <Icon.Zap size={17} />}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-semibold">{s.mod.title}</span>
          <span className="text-[12px] text-faint capitalize">{s.mod.category}</span>
        </div>
        <div className="text-[13px] text-muted leading-snug">{s.mod.what}</div>
      </div>
      {s.installed ? (
        <Badge tone="accent">Installed</Badge>
      ) : s.version ? (
        <Button size="sm" variant="outline" loading={busy} icon={<Icon.Download size={14} />} onClick={() => void installProject(s.projectId!, inst)}>
          {s.version.version_number.length > 16 ? "Install" : s.version.version_number}
        </Button>
      ) : (
        <span className="text-[12px] text-faint w-28 text-right">Not available for {inst.gameVersion}</span>
      )}
    </div>
  );
}

function ModRecommendations({ inst }: { inst: Instance }) {
  const { items } = useContent(inst, "mods");
  const [status, setStatus] = useState<PerfModStatus[] | null>(null);
  const [creating, setCreating] = useState(false);
  useEffect(() => {
    setStatus(null);
    perfModStatus(inst)
      .then(setStatus)
      .catch(() => setStatus([]));
  }, [inst.id, inst.loader, inst.gameVersion, items?.length]);


  async function fabricCopy() {
    setCreating(true);
    try {
      const lv = pickLoaderVersion(await availableLoaderVersions("fabric", inst.gameVersion));
      const copy = await createInstance({ name: `${inst.name} (Fabric)`.slice(0, 64), gameVersion: inst.gameVersion, loader: "fabric", loaderVersion: lv.version, memory: inst.memory });
      toast({ kind: "success", title: `Created ${copy.name}`, message: "Install performance mods into it below. Your vanilla installation is unchanged." });
      navigate({ page: "performance", instanceId: copy.id });
    } catch (e) {
      reportError("Couldn't create a Fabric installation", e);
    } finally {
      setCreating(false);
    }
  }

  return (
    <section>
      <SectionTitle>Performance mods</SectionTitle>
      {inst.loader === "vanilla" ? (
        <Callout
          tone="info"
          title="Performance mods need a mod loader"
          action={
            <Button size="sm" variant="primary" loading={creating} onClick={() => void fabricCopy()}>
              Create a Fabric copy
            </Button>
          }
        >
          This installation is vanilla. MOTION can create a Fabric installation of Minecraft {inst.gameVersion} next to it, so you can add Sodium and friends without touching this one.
        </Callout>
      ) : (
        <>
          <Card className="divide-y divide-line overflow-hidden">
            {!status
              ? Array.from({ length: 4 }, (_, i) => (
                  <div key={i} className="p-4">
                    <Skeleton className="h-9" />
                  </div>
                ))
              : status.map((s) => <PerfModRow key={s.mod.slug} s={s} inst={inst} />)}
          </Card>
          <p className="text-[12px] text-faint mt-2">Availability is checked live on Modrinth for Minecraft {inst.gameVersion} and {loaderLabel(inst)}. Required libraries are added automatically.</p>
        </>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Memory & Java
// ---------------------------------------------------------------------------

function MemoryJava({ inst }: { inst: Instance }) {
  const system = useStore(app, (s) => s.system);
  const settings = useStore(app, (s) => s.settings);
  const { items } = useContent(inst, "mods");
  const preset = JVM_PRESETS.find((j) => j.id === (inst.jvmPreset ?? settings.jvmPreset))!;
  const save = (patch: Partial<Instance>) => void updateInstance(inst.id, patch).catch((e) => reportError("Couldn't save", e));
  return (
    <Card className="p-5 space-y-4">
      <h3 className="font-semibold">Memory and Java</h3>
      {system ? (
        <div className="grid grid-cols-2 gap-3 text-[13px]">
          <div>
            <div className="text-faint">RAM</div>
            <div>{gb(Math.round(system.totalMemory / 1_048_576))} total</div>
          </div>
          <div>
            <div className="text-faint">CPU</div>
            <div className="truncate" title={system.cpuBrand}>
              {system.physicalCores ?? "?"} cores / {system.logicalCores} threads
            </div>
          </div>
          <div className="col-span-2">
            <div className="text-faint">Graphics</div>
            <div className="truncate">{system.gpus.length ? system.gpus.join(", ") : "Not detected"}</div>
          </div>
        </div>
      ) : (
        <Skeleton className="h-16" />
      )}
      <MemoryControl value={inst.memory} onChange={(memory) => save({ memory })} modded={inst.loader !== "vanilla"} modCount={items?.length ?? 0} />
      <div>
        <div className="text-[13px] font-medium mb-1.5">Garbage collector</div>
        <Select value={preset.id} onChange={(id) => save({ jvmPreset: id })} options={JVM_PRESETS.map((p) => ({ value: p.id, label: p.name }))} />
        <div className="text-[12px] text-muted mt-1.5 leading-relaxed">{preset.description}</div>
        {preset.minHeapMb > (inst.memory?.maxMb ?? settings.memory?.maxMb ?? 4096) && <div className="text-[12px] text-warn mt-1">Allocate at least {gb(preset.minHeapMb)} for this collector.</div>}
      </div>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Monitor
// ---------------------------------------------------------------------------

function Monitor({ inst }: { inst: Instance }) {
  const premium = usePremium();
  const run = useStore(app, (s) => s.running[inst.id]);
  const last = useStore(app, (s) => s.sessions[inst.id]);
  const gc = run?.gc ?? last?.gc;
  const maxHeap = run?.maxHeapMb ?? last?.maxHeapMb;
  const samples = run?.stats ?? [];
  const memSeries = samples.map((s) => s.memoryBytes / 1_048_576);
  const cpuSeries = samples.map((s) => s.cpuPercent);
  const lastSample = samples[samples.length - 1];
  const [, tick] = useState(0);
  useEffect(() => {
    if (!run) return;
    const t = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [run]);
  const heapMarkers = useMemo(() => (gc ? gc.series.map((p, i) => ((p.pauseMs ?? 0) > 50 ? i : -1)).filter((i) => i >= 0) : []), [gc]);

  return (
    <section>
      <SectionTitle action={run ? <span className="text-[12px] text-accent">● Live</span> : last ? <span className="text-[12px] text-faint">Last session {new Date(last.endedAt).toLocaleTimeString()}</span> : undefined}>Monitor</SectionTitle>
      {!run && !last ? (
        <Card className="p-5">
          <div className="text-muted text-[14px]">Start the game to see live statistics: process memory, CPU, launch time and — measured from the JVM's own log — heap usage and garbage-collection pauses.</div>
        </Card>
      ) : (
        <div className="space-y-3">
          <div className="grid grid-cols-4 gap-3">
            <Card className="p-4">
              <Metric label="Status" value={run ? "Running" : last?.exitCode === 0 || last?.exitCode === null ? "Closed" : `Exit ${last?.exitCode}`} sub={run ? `${Math.floor((Date.now() - run.startedAt) / 60000)} min` : undefined} tone={run ? "good" : undefined} />
            </Card>
            <Card className="p-4">
              <Metric label="Startup" value={(run?.startupMs ?? last?.startupMs) !== undefined ? `${(((run?.startupMs ?? last?.startupMs) as number) / 1000).toFixed(1)} s` : "—"} sub={`Prepared in ${(((run?.prepareMs ?? last?.prepareMs) ?? 0) / 1000).toFixed(1)} s`} />
            </Card>
            <Card className="p-4">
              <Metric label="Process memory" value={lastSample ? formatBytes(lastSample.memoryBytes) : last?.peakMemoryBytes ? formatBytes(last.peakMemoryBytes) : "—"} sub={run ? "Now" : "Peak"} />
            </Card>
            <Card className="p-4">
              <Metric label="CPU" value={lastSample ? `${lastSample.cpuPercent.toFixed(0)}%` : last?.avgCpuPercent !== undefined ? `${last.avgCpuPercent.toFixed(0)}%` : "—"} sub={run ? "All cores = 100% × threads" : "Average"} />
            </Card>
          </div>
          {run && (
            <div className="grid grid-cols-2 gap-3">
              <Card className="p-4">
                <div className="text-[12px] text-muted mb-2">Process memory (MB)</div>
                <Sparkline values={memSeries} />
              </Card>
              <Card className="p-4">
                <div className="text-[12px] text-muted mb-2">CPU (%)</div>
                <Sparkline values={cpuSeries} color="var(--color-info, #7dd3fc)" />
              </Card>
            </div>
          )}
          {premium ? (
            <Card className="p-5">
              <div className="flex items-center gap-2 mb-3">
                <h3 className="font-semibold">Java heap and GC pauses</h3>
                <PremiumBadge />
                <span className="flex-1" />
                {gc?.collector && <span className="text-[12px] text-faint">{gc.collector}</span>}
              </div>
              {!gc ? (
                <div className="text-[13px] text-muted">{run?.javaMajor !== undefined && run.javaMajor < 9 ? "GC logging needs Java 9 or newer; this version of Minecraft uses Java 8." : "Waiting for the first garbage collection…"}</div>
              ) : (
                <>
                  <div className="grid grid-cols-4 gap-4 mb-4">
                    <Metric label="Heap in use" value={gc.heapUsedMb !== undefined ? gb(Math.round(gc.heapUsedMb)) : "—"} sub={maxHeap ? `of ${gb(maxHeap)} allocated` : gc.heapCommittedMb ? `${gb(Math.round(gc.heapCommittedMb))} committed` : undefined} />
                    <Metric label="Avg pause" value={`${gc.avgPauseMs.toFixed(1)} ms`} sub={`${gc.pauses} pauses`} />
                    <Metric label="95th pct" value={`${gc.p95PauseMs.toFixed(1)} ms`} sub={`max ${gc.maxPauseMs.toFixed(0)} ms`} />
                    <Metric label="Stutters" value={String(gc.longPauses)} sub="pauses over 50 ms" tone={gc.longPauses > 0 ? "warn" : "good"} />
                  </div>
                  <div className="text-[12px] text-muted mb-1">Heap after each collection (MB){heapMarkers.length ? " — yellow dots are pauses over 50 ms" : ""}</div>
                  <Sparkline values={gc.series.map((p) => p.afterMb)} max={maxHeap && (gc.heapUsedMb ?? 0) > maxHeap * 0.5 ? maxHeap : undefined} markers={heapMarkers} height={80} />
                  <div className="text-[12px] text-faint mt-3 leading-relaxed">
                    {gc.longPauses > 2
                      ? "Several long pauses: try the G1 tuned or ZGC collector, or check that the heap isn't almost full."
                      : maxHeap && gc.heapUsedMb && gc.heapUsedMb > maxHeap * 0.85
                        ? "The heap is nearly full after collections — this installation may need more memory."
                        : "No problems detected."}{" "}
                    {gc.allocMbPerSec !== undefined && `Allocation rate ≈ ${gc.allocMbPerSec.toFixed(0)} MB/s.`}
                  </div>
                </>
              )}
            </Card>
          ) : (
            <PremiumLock title="Heap and GC pause analytics" compact>
              See heap use and every garbage-collection pause, measured from the JVM's own log, with stutter detection.
            </PremiumLock>
          )}
          <Card className="p-4 flex items-start gap-3">
            <Icon.Monitor size={18} className="text-muted mt-0.5" />
            <div className="text-[13px] text-muted leading-relaxed">
              <span className="text-fg font-medium">FPS and frame times</span> are only known inside the game. Minecraft doesn't report them to launchers, so MOTION shows them through the in-game overlay below (or press F3).
            </div>
          </Card>
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Overlay (Sodium Extra)
// ---------------------------------------------------------------------------

function Overlay({ inst }: { inst: Instance }) {
  const overlay = useStore(app, (s) => s.settings.overlay);
  const { items } = useContent(inst, "mods");
  const hasExtra = (items ?? []).some((m) => m.enabled && m.meta?.id === "sodium-extra");
  const hasSodium = (items ?? []).some((m) => m.enabled && m.meta?.id === "sodium");
  const busy = useInstalling(inst.id, "sodium-extra");
  const supported = inst.loader === "fabric" || inst.loader === "quilt" || inst.loader === "neoforge";
  const set = (patch: Partial<typeof overlay>) => updateSettings({ overlay: { ...overlay, ...patch } });
  return (
    <section>
      <SectionTitle>In-game FPS overlay</SectionTitle>
      <Card className="p-5 space-y-4">
        <p className="text-[13px] text-muted leading-relaxed">
          MOTION doesn't draw over your game. The overlay comes from the <span className="text-fg">Sodium Extra</span> mod, which shows current and average FPS, frame-time extremes and memory inside Minecraft. MOTION writes its overlay options before each launch.
        </p>
        {!supported ? (
          <Callout tone="info">The overlay needs a Fabric, Quilt or NeoForge installation with Sodium.</Callout>
        ) : !items ? (
          <Skeleton className="h-10" />
        ) : !hasExtra ? (
          <Callout
            tone="info"
            title="Sodium Extra isn't installed"
            action={
              <Button size="sm" variant="primary" loading={busy} onClick={() => void installProject("sodium-extra", inst)}>
                Install Sodium Extra
              </Button>
            }
          >
            {hasSodium ? "MOTION will show you what it installs before downloading." : "It also needs Sodium, which MOTION installs with it after showing you the list."}
          </Callout>
        ) : null}
        <div className={cx("space-y-4", (!supported || !hasExtra) && "opacity-50 pointer-events-none")}>
          <Toggle checked={overlay.enabled} onChange={(v) => set({ enabled: v })} label="Show FPS overlay" description="Applies the next time you launch." />
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-[13px] font-medium mb-1.5">Position</div>
              <Select
                value={overlay.corner}
                onChange={(v) => set({ corner: v as OverlayCorner })}
                options={[
                  { value: "TOP_LEFT", label: "Top left" },
                  { value: "TOP_RIGHT", label: "Top right" },
                  { value: "BOTTOM_LEFT", label: "Bottom left" },
                  { value: "BOTTOM_RIGHT", label: "Bottom right" },
                ]}
              />
            </div>
            <div>
              <div className="text-[13px] font-medium mb-1.5">Background</div>
              <Select
                value={overlay.contrast}
                onChange={(v) => set({ contrast: v })}
                options={[
                  { value: "NONE", label: "None" },
                  { value: "BACKGROUND", label: "Dark box" },
                  { value: "SHADOW", label: "Text shadow" },
                ]}
              />
            </div>
          </div>
          <Toggle checked={overlay.extended} onChange={(v) => set({ extended: v })} label="Detailed view" description="Average, minimum and maximum FPS instead of just the current value." />
        </div>
      </Card>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Backups
// ---------------------------------------------------------------------------

function Backups({ inst }: { inst: Instance }) {
  const [list, setList] = useState<BackupInfo[] | null>(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    listBackups(inst).then(setList).catch(() => setList([]));
  }, [inst.id, inst.performancePreset, tick]);
  async function restore(b: BackupInfo) {
    const ok = await confirm({
      title: "Restore this backup?",
      message: `Your options.txt and MOTION's performance settings go back to how they were on ${new Date(b.at).toLocaleString()}. The current state is backed up first.`,
      confirmLabel: "Restore",
    });
    if (!ok) return;
    try {
      await restoreBackup(inst, b);
      toast({ kind: "success", title: "Settings restored" });
      setTick((t) => t + 1);
    } catch (e) {
      reportError("Couldn't restore", e);
    }
  }
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold">Backups</h3>
        <Button
          size="sm"
          variant="ghost"
          icon={<Icon.Plus size={14} />}
          onClick={() =>
            void backupSettings(inst, "Manual backup")
              .then(() => {
                setTick((t) => t + 1);
                toast({ kind: "success", title: "Backup saved" });
              })
              .catch((e) => reportError("Backup failed", e))
          }
        >
          Back up now
        </Button>
      </div>
      {!list ? (
        <Skeleton className="h-16" />
      ) : list.length === 0 ? (
        <div className="text-[13px] text-muted">No backups yet. MOTION makes one automatically before every preset.</div>
      ) : (
        <div className="space-y-1.5 max-h-72 overflow-auto -mr-2 pr-2">
          {list.slice(0, 30).map((b) => (
            <div key={b.id} className="flex items-center gap-2 text-[13px] rounded-lg hover:bg-panel-2 px-2 py-1.5">
              <Icon.Clock size={14} className="text-faint shrink-0" />
              <div className="min-w-0 flex-1">
                <div className="truncate">{b.reason}</div>
                <div className="text-[12px] text-faint">{new Date(b.at).toLocaleString()}</div>
              </div>
              <Button size="sm" variant="ghost" icon={<Icon.Undo size={14} />} onClick={() => void restore(b)}>
                Restore
              </Button>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
