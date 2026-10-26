/**
 * MOTION Mods: built-in HUD modules, performance mods and quality-of-life switches for one
 * installation, with search, categories and one-click presets. Every switch here writes to
 * the real place that controls it (see core/bloomclient/modules.ts).
 */

import { useEffect, useMemo, useRef, useState } from "react";
import {
  activeLayout,
  CROSSHAIR,
  CROSSHAIR_STYLES,
  MOD_PRESETS,
  MODULES,
  POTION_EFFECTS,
  POTION_HIDDEN_KEY,
  widgetInfo,
  widgetOption,
  widgetVisible,
  type ModPreset,
  type ModuleInfo,
} from "../../core/bloomclient/modules";
import type { SettingsView } from "../../core/bloomclient/settingsFile";
import { supportedLabels } from "../../core/bloomclient/support";
import type { Instance } from "../../core/instances/model";
import { app, errorMessage, navigate, reportError, toast, type ModCategory } from "../../services/app";
import { createBloomInstallation, enable as enableClient, readSettings, status as clientStatus, type ClientStatus } from "../../services/bloomClient";
import {
  applyModPreset,
  crosshairValues,
  listClientBackups,
  perfStates,
  readVanillaOptions,
  restoreClientBackup,
  setPerfMod,
  setSettings,
  settingOn,
  setVanillaOption,
  setWidget,
  toggleValues,
  type ClientBackup,
} from "../../services/bloodMods";
import { getInstance, selectedInstance } from "../../services/instances";
import { useStore } from "../../services/store";
import { Badge, Button, Callout, Card, confirm, cx, EmptyState, Menu, PageHeader, Select, Skeleton, Slider, TextInput, Toggle, timeAgo } from "../components";
import { bumpContent, useContent } from "../contentEvents";
import { Icon } from "../icons";
import { loaderLabel } from "../instanceParts";

const CATEGORIES: { value: ModCategory; label: string }[] = [
  { value: "all", label: "All" },
  { value: "hud", label: "HUD" },
  { value: "performance", label: "Performance" },
  { value: "visuals", label: "Visuals" },
  { value: "utility", label: "Utility" },
];

const VANILLA_OPTIONS = ["toggleSprint", "toggleCrouch"];
const PERF_SLUGS = MODULES.filter((m) => m.kind === "perf").map((m) => m.perfSlug!);

export function ModsPage({ instanceId, category = "all" }: { instanceId?: string; category?: ModCategory }) {
  const instances = useStore(app, (s) => s.instances);
  const inst = (instanceId ? getInstance(instanceId) : undefined) ?? selectedInstance();
  const [query, setQuery] = useState("");

  if (!inst) {
    return (
      <>
        <PageHeader title="MOTION Mods" />
        <EmptyState icon={<Icon.Hud size={28} />} title="No installation yet" action={<Button variant="primary" onClick={() => navigate({ page: "library" })}>Create one</Button>}>
          MOTION Mods are configured per installation. Create one in the Library first.
        </EmptyState>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="MOTION Mods"
        subtitle={<>Built-in HUD modules, performance mods and quality-of-life switches. Settings are kept separately for each installation.</>}
        actions={
          <Select
            value={inst.id}
            onChange={(id) => navigate({ page: "mods", instanceId: id, category })}
            options={instances.map((i) => ({ value: i.id, label: `${i.name} · ${i.gameVersion} ${loaderLabel(i)}` }))}
            className="w-[300px]"
          />
        }
      />
      <ModsBody key={inst.id} inst={inst} category={category} query={query} setQuery={setQuery} />
    </>
  );
}

function ModsBody({ inst, category, query, setQuery }: { inst: Instance; category: ModCategory; query: string; setQuery: (q: string) => void }) {
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const { items: mods } = useContent(inst, "mods");
  const [client, setClient] = useState<ClientStatus | null>(null);
  const [view, setView] = useState<SettingsView | null>(null);
  const [vanilla, setVanilla] = useState<Record<string, boolean> | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let live = true;
    clientStatus(inst).then((s) => live && setClient(s)).catch(() => live && setClient(null));
    readSettings(inst)
      .then((v) => live && setView(v))
      .catch((e) => live && setLoadError(errorMessage(e)));
    readVanillaOptions(inst, VANILLA_OPTIONS).then((v) => live && setVanilla(v)).catch(() => live && setVanilla({}));
    return () => {
      live = false;
    };
  }, [inst, tick, mods?.length]);

  const clientReady = Boolean(client?.enabled);
  const layout = view ? activeLayout(view.hud) : null;
  const perf = useMemo(() => (mods ? perfStates(mods, PERF_SLUGS) : []), [mods]);

  function isOn(m: ModuleInfo): boolean | null {
    if (m.kind === "hud") return layout ? widgetVisible(layout, m.widget!) : null;
    if (m.kind === "setting") return view ? settingOn(view.settings, m.setting!) : null;
    if (m.kind === "vanilla") return vanilla ? Boolean(vanilla[m.vanillaOption!]) : null;
    const s = perf.find((p) => p.info.slug === m.perfSlug);
    return mods ? Boolean(s?.item?.enabled) : null;
  }

  /** Why a module can't be switched here, or null when it can. */
  function blocked(m: ModuleInfo): string | null {
    if (m.kind === "hud" || m.kind === "setting") {
      if (!client) return "Checking MOTION Client…";
      // The MOTION Client card at the top explains why; each module only needs a short pointer.
      if (!client.enabled) return client.support.ok ? "Install MOTION Client above to use this." : "Needs MOTION Client (see above).";
    }
    if (m.kind === "perf" && inst.loader === "vanilla") return "Needs a mod loader (Fabric, Quilt, Forge or NeoForge).";
    return null;
  }

  async function run(id: string, fn: () => Promise<unknown>, success?: string) {
    setBusy(id);
    try {
      await fn();
      if (success) toast({ kind: "success", title: success, message: running ? "The running game picks this up in a few seconds." : undefined });
    } catch (e) {
      reportError("Couldn't change that", e);
    } finally {
      setBusy(null);
    }
  }

  async function toggle(m: ModuleInfo, on: boolean) {
    if (m.kind === "hud") return run(m.id, async () => setView(await setWidget(inst, m.widget!, { visible: on })));
    if (m.kind === "setting") return run(m.id, async () => setView(await setSettings(inst, toggleValues(m.setting!, on))));
    if (m.kind === "vanilla")
      return run(m.id, async () => {
        await setVanillaOption(inst, m.vanillaOption!, on);
        setVanilla(await readVanillaOptions(inst, VANILLA_OPTIONS));
      });
    const s = perf.find((p) => p.info.slug === m.perfSlug);
    if (!s) return;
    return run(m.id, async () => {
      const r = await setPerfMod(inst, s, on);
      bumpContent();
      if (r === "installed") toast({ kind: "success", title: `${s.info.title} installed`, message: "Required libraries were added too. It loads the next time you start the game." });
      if (r === "disabled") toast({ kind: "info", title: `${s.info.title} disabled`, message: "The file is kept (as .disabled) so you can turn it back on without downloading." });
    });
  }

  const q = query.trim().toLowerCase();
  const list = MODULES.filter((m) => category === "all" || m.category === category).filter((m) => {
    if (!q) return true;
    const info = m.kind === "perf" ? perf.find((p) => p.info.slug === m.perfSlug)?.info : undefined;
    const text = [m.name, m.description, m.note, ...(m.keywords ?? []), info?.what, info?.title, m.category].join(" ").toLowerCase();
    return q.split(/\s+/).every((w) => text.includes(w));
  });

  return (
    <div className="space-y-6">
      <ClientBanner inst={inst} client={client} running={running} onChanged={() => setTick((t) => t + 1)} />
      {loadError && (
        <Callout tone="danger" title="MOTION Client's settings file can't be read">
          {loadError}
        </Callout>
      )}

      <Presets inst={inst} disabled={!clientReady} onApplied={() => setTick((t) => t + 1)} />

      <div className="flex flex-wrap items-center gap-3">
        <div className="inline-flex p-0.5 rounded-lg bg-panel-2 border border-line">
          {CATEGORIES.map((c) => (
            <button
              key={c.value}
              type="button"
              onClick={() => navigate({ page: "mods", instanceId: inst.id, category: c.value })}
              className={cx(
                "h-8 px-3 rounded-md text-[13px] font-medium transition-colors cursor-pointer",
                category === c.value ? "bg-raised text-fg" : "text-muted hover:text-fg",
              )}
            >
              {c.label}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <TextInput value={query} onChange={setQuery} placeholder="Search modules…" icon={<Icon.Search size={15} />} className="w-[260px]" />
      </div>

      {list.length === 0 ? (
        <EmptyState icon={<Icon.Search size={26} />} title="Nothing matches">
          Try another word, or switch to All.
        </EmptyState>
      ) : (
        <div className="rounded-xl border border-line divide-y divide-line">
          {list.map((m) => (
            <ModuleCard
              key={m.id}
              m={m}
              inst={inst}
              on={isOn(m)}
              blocked={blocked(m)}
              busy={busy === m.id}
              view={view}
              perfItem={perf.find((p) => p.info.slug === m.perfSlug)}
              onToggle={(v) => void toggle(m, v)}
              onView={setView}
              running={running}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- header banner

function ClientBanner({ inst, client, running, onChanged }: { inst: Instance; client: ClientStatus | null; running: boolean; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  if (!client) return <Skeleton className="h-[74px] rounded-xl" />;
  const versions = client.bundle ? supportedLabels(client.bundle).join(", ") : "—";
  return (
    <Card className="p-4 flex items-center gap-4 cut-corner">
      <div className="min-w-0 flex-1">
        <div className="font-semibold flex items-center gap-2 flex-wrap">
          MOTION Client
          {client.enabled ? <Badge tone="accent">v{client.installedVersion} installed</Badge> : <Badge>Not installed</Badge>}
          {client.updateAvailable && <Badge tone="info">Update {client.bundle?.version} on next launch</Badge>}
          {running && <Badge tone="warn">Game running · changes apply live</Badge>}
        </div>
        <div className="text-[13px] text-muted mt-0.5">
          {client.enabled
            ? `Supports Minecraft ${versions} on Fabric, Quilt and NeoForge. Press Right Shift in game for the full menu and the drag-and-drop HUD editor.`
            : client.support.ok
              ? `Adds the HUD modules below to ${inst.name}. Fabric API is installed too if it's missing.`
              : client.support.message}
        </div>
      </div>
      {!client.enabled &&
        (client.support.ok ? (
          <Button
            variant="primary"
            loading={busy}
            icon={<Icon.Download size={15} />}
            onClick={async () => {
              setBusy(true);
              try {
                await enableClient(inst);
                bumpContent();
                onChanged();
              } catch (e) {
                reportError("Couldn't install MOTION Client", e);
              } finally {
                setBusy(false);
              }
            }}
          >
            Install MOTION Client
          </Button>
        ) : client.support.fix === "create_fabric_copy" ? (
          <Button
            variant="primary"
            loading={busy}
            icon={<Icon.Plus size={15} />}
            onClick={async () => {
              setBusy(true);
              try {
                const created = await createBloomInstallation(inst);
                navigate({ page: "mods", instanceId: created.id });
              } catch (e) {
                reportError("Couldn't create the installation", e);
              } finally {
                setBusy(false);
              }
            }}
          >
            Create a MOTION installation
          </Button>
        ) : null)}
    </Card>
  );
}

// ---------------------------------------------------------------- presets and backups

function Presets({ inst, disabled, onApplied }: { inst: Instance; disabled: boolean; onApplied: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [backups, setBackups] = useState<ClientBackup[] | null>(null);
  useEffect(() => {
    listClientBackups(inst).then(setBackups).catch(() => setBackups([]));
  }, [inst, busy]);

  async function apply(p: ModPreset) {
    const ok = await confirm({
      title: `Apply ${p.name}?`,
      message: `${p.description} MOTION Client's current settings are backed up first, so you can restore them from Backups.`,
      confirmLabel: "Apply preset",
    });
    if (!ok) return;
    setBusy(p.id);
    try {
      const r = await applyModPreset(inst, p.id);
      bumpContent();
      onApplied();
      const parts = [r.installed.length ? `Installed or enabled ${r.installed.join(", ")}.` : "", ...r.skipped.map((s) => `${s.title}: ${s.reason}`)].filter(Boolean);
      toast({ kind: r.skipped.length ? "warning" : "success", title: `${p.name} applied`, message: parts.join(" ") || undefined }, 9000);
    } catch (e) {
      reportError(`Couldn't apply ${p.name}`, e);
    } finally {
      setBusy(null);
    }
  }

  return (
    <section>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-[14px] font-semibold text-fg">Presets</h2>
        <Menu
          trigger={
            <Button size="sm" variant="ghost" icon={<Icon.Undo size={14} />}>
              Backups{backups && backups.length ? ` (${backups.length})` : ""}
            </Button>
          }
          items={
            backups && backups.length
              ? backups.slice(0, 10).map((b) => ({
                  label: `${timeAgo(b.at)} · ${b.reason}`,
                  icon: <Icon.Undo size={14} />,
                  onClick: async () => {
                    const ok = await confirm({ title: "Restore this backup?", message: `MOTION Client's settings go back to how they were ${timeAgo(b.at)} (${b.reason}). Your current settings are backed up first.`, confirmLabel: "Restore" });
                    if (!ok) return;
                    try {
                      await restoreClientBackup(inst, b);
                      onApplied();
                      toast({ kind: "success", title: "Settings restored" });
                    } catch (e) {
                      reportError("Couldn't restore the backup", e);
                    }
                  },
                }))
              : [{ label: "No backups yet — presets create one automatically", onClick: () => undefined, disabled: true }]
          }
        />
      </div>
      <div className="grid grid-cols-3 gap-3">
        {MOD_PRESETS.map((p) => (
          <div key={p.id} className="rounded-xl border border-line p-4 flex flex-col">
            <span className="font-semibold">{p.name}</span>
            <p className="text-[13px] text-muted mt-1 leading-relaxed flex-1">{p.description}</p>
            <div className="mt-3">
              <Button size="sm" variant={p.id === "performance" ? "primary" : "secondary"} disabled={disabled} loading={busy === p.id} onClick={() => void apply(p)}>
                Apply
              </Button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- module card

function ModuleCard({
  m,
  inst,
  on,
  blocked,
  busy,
  view,
  perfItem,
  onToggle,
  onView,
  running,
}: {
  m: ModuleInfo;
  inst: Instance;
  on: boolean | null;
  blocked: string | null;
  busy: boolean;
  view: SettingsView | null;
  perfItem?: ReturnType<typeof perfStates>[number];
  onToggle: (on: boolean) => void;
  onView: (v: SettingsView) => void;
  running: boolean;
}) {
  const [open, setOpen] = useState(false);
  const perf = perfItem?.info;
  const description = m.kind === "perf" ? (perf?.what ?? "") : m.description;
  const configurable = m.kind === "hud" || m.id === "crosshair";
  const source = m.kind === "perf" ? `From Modrinth${perfItem?.item && !perfItem.item.enabled ? " · file disabled" : ""}` : m.kind === "vanilla" ? "Minecraft setting" : null;
  return (
    <div className="px-4 py-3">
      <div className="flex items-start gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline gap-2">
            <span className="text-[14px] font-medium">{m.name}</span>
            {source && <span className="text-[12px] text-faint">{source}</span>}
          </div>
          <p className="text-[13px] text-muted leading-snug mt-0.5">{description}</p>
          {m.note && <p className="text-[12px] text-faint mt-1 leading-snug">{m.note}</p>}
          {blocked && <p className="text-[12px] text-faint mt-1">{blocked}</p>}
          {m.kind === "vanilla" && running && <p className="text-[12px] text-warn mt-1">Close Minecraft to change this here.</p>}
          {configurable && !blocked && view && (
            <button type="button" onClick={() => setOpen((o) => !o)} className="mt-1.5 text-[13px] font-medium text-accent hover:underline cursor-pointer">
              {open ? "Hide options" : "Options"}
            </button>
          )}
        </div>
        {on === null && !blocked ? (
          <Skeleton className="h-[22px] w-[38px] rounded-full mt-0.5" />
        ) : (
          <div className={cx("mt-0.5", busy && "opacity-50 pointer-events-none")} title={blocked ?? undefined}>
            <Toggle checked={Boolean(on) && !blocked} disabled={Boolean(blocked) || busy || (m.kind === "vanilla" && running)} onChange={onToggle} />
          </div>
        )}
      </div>
      {open && configurable && view && (
        <div className="mt-3 pt-3 border-t border-line animate-in">
          {m.kind === "hud" ? <WidgetOptions inst={inst} widget={m.widget!} view={view} onView={onView} /> : <CrosshairOptions inst={inst} view={view} onView={onView} />}
        </div>
      )}
    </div>
  );
}

function WidgetOptions({ inst, widget, view, onView }: { inst: Instance; widget: string; view: SettingsView; onView: (v: SettingsView) => void }) {
  const info = widgetInfo(widget)!;
  const layout = activeLayout(view.hud);
  const scale = layout.widgets[widget]?.scale ?? 1;
  const hidden = Array.isArray(view.settings[POTION_HIDDEN_KEY]) ? (view.settings[POTION_HIDDEN_KEY] as string[]) : [];
  const save = (p: Promise<SettingsView>) => p.then(onView).catch((e) => reportError("Couldn't save", e));
  return (
    <div className="space-y-2.5">
      <div className="text-[12px] text-faint">
        Layout: <span className="text-muted">{layout.name}</span> · move widgets with the in-game HUD editor (Right Shift → HUD Editor).
      </div>
      {info.options.map((opt) => (
        <div key={opt.id} className="flex items-center justify-between gap-4 text-[13px]">
          <span>{opt.label}</span>
          <Toggle checked={widgetOption(layout, widget, opt.id)} onChange={(v) => void save(setWidget(inst, widget, { options: { [opt.id]: v } }))} />
        </div>
      ))}
      <div className="text-[13px]">
        <div className="mb-1">Scale</div>
        <LiveSlider value={scale} min={0.5} max={3} step={0.05} format={(v) => `${v.toFixed(2)}×`} onCommit={(v) => void save(setWidget(inst, widget, { scale: Math.round(v * 20) / 20 }))} />
      </div>
      {widget === "potions" && (
        <div className="text-[13px]">
          <div className="mb-1.5">Hide individual effects</div>
          <div className="flex flex-wrap gap-1.5">
            {POTION_EFFECTS.map((e) => {
              const isHidden = hidden.includes(e.id);
              return (
                <button
                  key={e.id}
                  type="button"
                  onClick={() => void save(setSettings(inst, { [POTION_HIDDEN_KEY]: isHidden ? hidden.filter((h) => h !== e.id) : [...hidden, e.id] }))}
                  className={cx(
                    "h-6 px-2 rounded-md border text-[12px] cursor-pointer transition-colors",
                    isHidden ? "border-accent/40 bg-accent/12 text-fg line-through decoration-accent/70" : "border-line text-muted hover:text-fg",
                  )}
                >
                  {e.name}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function CrosshairOptions({ inst, view, onView }: { inst: Instance; view: SettingsView; onView: (v: SettingsView) => void }) {
  const [live, setLive] = useState<Record<string, number>>({});
  const stored = crosshairValues(view.settings);
  // Sliders preview instantly; the file is written once the slider settles.
  const c = { ...stored, size: live.Size ?? stored.size, gap: live.Gap ?? stored.gap, thickness: live.Thickness ?? stored.thickness, opacity: live.Opacity ?? stored.opacity };
  const save = (values: Record<string, string | number | boolean>) => void setSettings(inst, values).then(onView).catch((e) => reportError("Couldn't save", e));
  return (
    <div className="grid grid-cols-[1fr_112px] gap-4">
      <div className="space-y-2.5 text-[13px]">
        <div>
          <div className="mb-1">Style</div>
          <Select value={c.style} onChange={(v) => save({ [CROSSHAIR.style.key]: v })} options={CROSSHAIR_STYLES.map((s) => ({ value: s.value, label: s.label }))} />
        </div>
        {(
          [
            ["Size", CROSSHAIR.size, c.size, (v: number) => `${v} px`],
            ["Gap", CROSSHAIR.gap, c.gap, (v: number) => `${v} px`],
            ["Thickness", CROSSHAIR.thickness, c.thickness, (v: number) => `${v} px`],
            ["Opacity", CROSSHAIR.opacity, c.opacity, (v: number) => `${Math.round(v * 100)}%`],
          ] as const
        ).map(([label, def, value, fmt]) => (
          <div key={label}>
            <div className="mb-1">{label}</div>
            <LiveSlider value={value} min={def.min} max={def.max} step={def.step} format={fmt} onCommit={(v) => save({ [def.key]: v })} onLive={(v) => setLive((l) => ({ ...l, [label]: v }))} />
          </div>
        ))}
        <div className="flex items-center justify-between">
          <span>Colour</span>
          <input type="color" value={c.color} onChange={(e) => save({ [CROSSHAIR.color.key]: e.target.value })} className="h-7 w-12 rounded border border-line bg-transparent cursor-pointer" />
        </div>
        <div className="flex items-center justify-between">
          <span>Dark outline</span>
          <Toggle checked={c.outline} onChange={(v) => save({ [CROSSHAIR.outline.key]: v })} />
        </div>
      </div>
      <CrosshairPreview c={c} />
    </div>
  );
}

/** Same geometry as the in-game renderer (CrosshairRenderer.java), drawn at 4× GUI scale. */
function CrosshairPreview({ c }: { c: ReturnType<typeof crosshairValues> }) {
  const k = 4;
  const size = 112;
  const mid = size / 2;
  const t = c.thickness * k;
  const rects: [number, number, number, number][] = [];
  const arm = c.size * k;
  const gap = c.gap * k;
  const half = Math.floor(c.thickness / 2) * k;
  const h = (x0: number, x1: number) => rects.push([x0, mid - half, x1 - x0, t]);
  const v = (y0: number, y1: number) => rects.push([mid - half, y0, t, y1 - y0]);
  if (c.style === "cross" || c.style === "cross_dot" || c.style === "t") {
    h(mid - half - gap - arm, mid - half - gap);
    h(mid + half + gap + (c.thickness % 2 ? k : 0), mid + half + gap + arm + (c.thickness % 2 ? k : 0));
    if (c.style !== "t") v(mid - half - gap - arm, mid - half - gap);
    v(mid + half + gap + (c.thickness % 2 ? k : 0), mid + half + gap + arm + (c.thickness % 2 ? k : 0));
  }
  if (c.style === "dot" || c.style === "cross_dot") rects.push([mid - half, mid - half, t, t]);
  if (c.style === "square") {
    const s = (c.size + c.gap) * k;
    rects.push([mid - s, mid - s, 2 * s + k, t], [mid - s, mid + s - t + k, 2 * s + k, t], [mid - s, mid - s, t, 2 * s + k], [mid + s - t + k, mid - s, t, 2 * s + k]);
  }
  return (
    <div className="rounded-xl overflow-hidden border border-line self-start" style={{ background: "linear-gradient(160deg,#3b4a5a,#6f8a6a 55%,#4c6b3f)" }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} shapeRendering="crispEdges">
        {c.outline && rects.map(([x, y, w, hh], i) => <rect key={`o${i}`} x={x - k} y={y - k} width={w + 2 * k} height={hh + 2 * k} fill="#000" opacity={c.opacity * 0.6} />)}
        {rects.map(([x, y, w, hh], i) => (
          <rect key={i} x={x} y={y} width={w} height={hh} fill={c.color} opacity={c.opacity} />
        ))}
      </svg>
    </div>
  );
}

/** A slider that updates instantly but writes the file only after it stops moving (250 ms). */
function LiveSlider({ value, min, max, step, format, onCommit, onLive }: {
  value: number;
  min: number;
  max: number;
  step: number;
  format?: (v: number) => string;
  onCommit: (v: number) => void;
  onLive?: (v: number) => void;
}) {
  const [local, setLocal] = useState(value);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => setLocal(value), [value]);
  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);
  return (
    <Slider
      value={local}
      min={min}
      max={max}
      step={step}
      format={format}
      onChange={(v) => {
        setLocal(v);
        onLive?.(v);
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => onCommit(v), 250);
      }}
    />
  );
}
