/**
 * Customize: MOTION Client for an installation — install/update, visual profile, skyboxes,
 * quick effects, in-game menu theme and key. Everything here writes MOTION Client's own config
 * (config/bloomclient/settings.json, options.txt key line); the game picks changes up within
 * seconds if it's running.
 */

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { BUILT_IN_SKYBOXES, DEFAULT_SKY_DAY, DEFAULT_SKY_NIGHT, migrateSky, MENU_KEYS, MENU_THEMES, QUALITY_PRESETS, menuKeyLabel, type ProfileInfo } from "../../core/bloomclient/catalog";
import type { SettingsView } from "../../core/bloomclient/settingsFile";
import { preferredVersion } from "../../core/bloomclient/support";
import type { Instance } from "../../core/instances/model";
import { app, errorMessage, navigate, reportError, toast, updateSettings, THEMES } from "../../services/app";
import {
  createBloomInstallation,
  disable,
  enable,
  importSkybox,
  listCustomSkyboxes,
  listProfiles,
  menuKey,
  readSettings,
  setMenuKey,
  status,
  writeSettings,
  type ClientStatus,
  type CustomSkybox,
} from "../../services/bloomClient";
import { getInstance, selectedInstance } from "../../services/instances";
import { native } from "../../services/native";
import { useStore } from "../../services/store";
import { Petals } from "../backdrop";
import { AccessoryCard } from "../accessoryCard";
import { CapeCard } from "../capeCard";
import { CapeMotionSection, MenuBackgroundSection } from "./customizeExtras";
import { Badge, Button, Card, Callout, cx, PageHeader, Segmented, Select, Skeleton, Spinner, Toggle } from "../components";
import { BloodMark, Icon } from "../icons";
import { loaderLabel } from "../instanceParts";

type Bool = (key: string, fallback: boolean) => boolean;

export function CustomizePage({ instanceId }: { instanceId?: string }) {
  const instances = useStore(app, (s) => s.instances);
  const inst = (instanceId ? getInstance(instanceId) : undefined) ?? selectedInstance();
  if (!inst) {
    return (
      <div>
        <PageHeader title="Visuals" subtitle="MOTION Client: custom skies, decorative weather, particles and visual profiles." />
        <Card className="p-6 text-muted">Create an installation first, then come back to add MOTION Client.</Card>
      </div>
    );
  }
  return (
    <div>
      <PageHeader
        title="Visuals"
        subtitle="Skies, weather, particles and visual profiles for MOTION Client. Everything is client-side and visual only. HUD modules live in MOTION Mods."
        actions={
          <Select
            value={inst.id}
            onChange={(id) => navigate({ page: "customize", instanceId: id })}
            options={instances.map((i) => ({ value: i.id, label: `${i.name} · ${i.gameVersion} ${loaderLabel(i)}` }))}
            className="w-80"
          />
        }
      />
      <CapeCard />
      <AccessoryCard />
      <CustomizeInstance key={inst.id} inst={inst} />
    </div>
  );
}

function CustomizeInstance({ inst }: { inst: Instance }) {
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const [st, setSt] = useState<ClientStatus | null>(null);
  const [view, setView] = useState<SettingsView | null>(null);
  const [profiles, setProfiles] = useState<ProfileInfo[]>([]);
  const [custom, setCustom] = useState<CustomSkybox[]>([]);
  const [busy, setBusy] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const current = getInstance(inst.id) ?? inst;
    setSt(await status(current));
    try {
      setView(await readSettings(current));
      setLoadError(null);
    } catch (e) {
      setLoadError(errorMessage(e));
    }
    setProfiles(await listProfiles(current).catch(() => []));
    setCustom(await listCustomSkyboxes(current).catch(() => []));
  }, [inst]);

  useEffect(() => {
    void reload();
  }, [reload]);

  // Re-read when the window regains focus (the game may have changed things).
  useEffect(() => {
    const onFocus = () => void reload();
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [reload]);

  const write = async (patch: Parameters<typeof writeSettings>[1]) => {
    try {
      setView(await writeSettings(getInstance(inst.id) ?? inst, patch));
    } catch (e) {
      reportError("Couldn't save MOTION Client settings", e);
    }
  };

  const get = useCallback(<T,>(key: string, fallback: T): T => {
    const v = view?.settings[key];
    return v === undefined || v === null ? fallback : (v as T);
  }, [view]);
  const bool: Bool = (key, fallback) => get<boolean>(key, fallback) === true;

  if (!st) return <Skeleton className="h-64 rounded-xl" />;
  const installed = st.enabled;

  return (
    <div className="grid grid-cols-[1fr_340px] gap-6 items-start">
      <div className="space-y-6 min-w-0">
        <StatusCard
          inst={inst}
          st={st}
          busy={busy}
          running={running}
          onEnable={async () => {
            setBusy(true);
            try {
              await enable(getInstance(inst.id) ?? inst);
            } catch (e) {
              reportError("Couldn't add MOTION Client", e);
            } finally {
              setBusy(false);
              void reload();
            }
          }}
          onDisable={async () => {
            setBusy(true);
            try {
              await disable(getInstance(inst.id) ?? inst);
              toast({ kind: "info", title: "MOTION Client removed", message: "The mod was moved to the Recycle Bin. Your MOTION settings are kept." });
            } catch (e) {
              reportError("Couldn't remove MOTION Client", e);
            } finally {
              setBusy(false);
              void reload();
            }
          }}
          onCreateCopy={async () => {
            setBusy(true);
            try {
              const created = await createBloomInstallation(inst);
              updateSettings({ selectedInstanceId: created.id });
              navigate({ page: "customize", instanceId: created.id });
            } catch (e) {
              reportError("Couldn't create the installation", e);
            } finally {
              setBusy(false);
            }
          }}
        />

        {loadError && <Callout tone="warn" title="Settings unreadable">{loadError}</Callout>}

        <fieldset disabled={!installed} className={cx("space-y-6 transition-opacity", !installed && "opacity-45 pointer-events-none")}>
          <Section title="Visual profile" subtitle="A profile sets the sky, weather, particles, colours and HUD style together. Switch any time in game with Right Shift → Profiles.">
            <div className="grid grid-cols-2 xl:grid-cols-4 gap-2.5">
              {profiles.map((p) => {
                const active = (view?.activeProfile ?? "blood_moon") === p.id;
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => void write({ profile: p.id })}
                    className={cx(
                      "text-left rounded-xl border p-3 transition-all cursor-pointer",
                      active ? "border-accent/60 bg-accent/10" : "border-line bg-panel-2 hover:border-line-strong",
                    )}
                  >
                    <div className="flex items-center gap-1.5 font-semibold text-[14px]">
                      {active && <Icon.Check size={14} className="text-accent" />}
                      {p.name}
                    </div>
                    <div className="text-[12px] text-muted mt-1 leading-snug line-clamp-3">{p.description || "Your profile."}</div>
                    {!p.builtIn && <Badge className="mt-2">Yours</Badge>}
                  </button>
                );
              })}
            </div>
          </Section>

          <SkyboxSection inst={inst} view={view} custom={custom} onPick={(slot, id) => void write({ settings: { [slot === "day" ? "sky.day" : "sky.night"]: id, "sky.enabled": true } })} onImported={reload} />

          <Section title="Weather & particles" subtitle="Decorative only: real weather, the world and other players are not affected.">
            <div className="grid grid-cols-2 gap-x-8 gap-y-4">
              <Toggle checked={bool("petals.enabled", true)} onChange={(v) => void write({ settings: { "petals.enabled": v } })} label="Drifting petals" description="Petals drifting on the wind (crimson by default)." />
              <Toggle checked={bool("petals.everywhere", false)} onChange={(v) => void write({ settings: { "petals.everywhere": v } })} label="Petals in every biome" description="Off: cherry groves and flower biomes only." />
              <Toggle checked={bool("snow.enabled", false)} onChange={(v) => void write({ settings: { "snow.enabled": v } })} label="Decorative snowfall" description="Snow particles; no snow layers or weather change." />
              <Toggle checked={bool("rain.enabled", true)} onChange={(v) => void write({ settings: { "rain.enabled": v } })} label="Enhanced rain" description="Rain streaks and splashes when it rains." />
              <Toggle checked={bool("leaves.enabled", true)} onChange={(v) => void write({ settings: { "leaves.enabled": v } })} label="Falling leaves" description="Leaves drifting down in forests." />
              <Toggle checked={bool("fireflies.enabled", true)} onChange={(v) => void write({ settings: { "fireflies.enabled": v } })} label="Fireflies" description="Soft glowing fireflies at night." />
              <Toggle checked={bool("wind.enabled", true)} onChange={(v) => void write({ settings: { "wind.enabled": v } })} label="Wind" description="Pushes petals, leaves, snow and rain." />
              <Toggle checked={bool("hud.enabled", true)} onChange={(v) => void write({ settings: { "hud.enabled": v } })} label="MOTION HUD" description="FPS, coordinates, keystrokes… (arrange in game)." />
            </div>
            <div className="flex items-center justify-between mt-5 pt-4 border-t border-line">
              <div>
                <div className="font-medium text-[14px]">Effect quality</div>
                <div className="text-[13px] text-muted">Particle limits and effect distance. MOTION also lowers effects briefly if your FPS drops.</div>
              </div>
              <Segmented
                value={get<string>("perf.quality", "medium") as "low" | "medium" | "high"}
                onChange={(v) => void write({ settings: { "perf.quality": v } })}
                options={QUALITY_PRESETS.map((q) => ({ value: q.id, label: q.name }))}
              />
            </div>
          </Section>

          <MenuBackgroundSection inst={inst} get={get} write={(settings) => write({ settings })} />

          <CapeMotionSection get={get} write={(settings) => write({ settings })} />

          <Section title="In-game menu" subtitle="The MOTION menu has every setting, the HUD editor and profile management.">
            <div className="flex items-center justify-between gap-6">
              <div>
                <div className="font-medium text-[14px]">Menu theme</div>
                <div className="text-[13px] text-muted">Colours of the MOTION menu and HUD accents.</div>
              </div>
              <div className="flex items-center gap-2">
                {MENU_THEMES.map((t) => {
                  const accent = THEMES.find((x) => x.id === t.id)?.accent ?? "#fff";
                  const active = get<string>("ui.theme", "motion") === t.id;
                  return (
                    <button
                      key={t.id}
                      type="button"
                      title={t.name}
                      onClick={() => void write({ settings: { "ui.theme": t.id } })}
                      className={cx("h-8 w-8 rounded-full border-2 transition-colors cursor-pointer", active ? "border-fg" : "border-transparent")}
                      style={{ background: `linear-gradient(135deg, ${accent}, color-mix(in srgb, ${accent} 40%, #1a1a1a))` }}
                    />
                  );
                })}
              </div>
            </div>
            <MenuKeyRow inst={inst} running={running} />
          </Section>
        </fieldset>
      </div>

      <Preview view={view} custom={custom} installed={installed} />
    </div>
  );
}

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Card className="p-5">
      <div className="mb-4">
        <h2 className="font-display text-[16px] font-semibold">{title}</h2>
        {subtitle && <p className="text-[13px] text-muted mt-0.5">{subtitle}</p>}
      </div>
      {children}
    </Card>
  );
}

function StatusCard({ inst, st, busy, running, onEnable, onDisable, onCreateCopy }: {
  inst: Instance;
  st: ClientStatus;
  busy: boolean;
  running: boolean;
  onEnable: () => void;
  onDisable: () => void;
  onCreateCopy: () => void;
}) {
  const sup = st.support;
  return (
    <Card className="p-5">
      <div className="flex items-start gap-4">
        <div className="h-12 w-12 rounded-xl bg-raised border border-line grid place-items-center shrink-0">
          <BloodMark size={30} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="font-display text-[16px] font-semibold">MOTION Client</div>
            {st.enabled ? <Badge tone="accent">Installed {st.installedVersion}</Badge> : sup.ok ? <Badge>Not installed</Badge> : <Badge tone="warn">Not available here</Badge>}
            {st.bundle && <Badge>Bundled {st.bundle.version}</Badge>}
          </div>
          <p className="text-[13px] text-muted mt-1 leading-relaxed">
            {st.enabled
              ? "Press Right Shift in game to open the MOTION menu. Changes you make here are picked up by the game within a few seconds."
              : sup.ok
                ? `Adds the MOTION Client mod to "${inst.name}"${inst.loader === "fabric" || inst.loader === "quilt" ? " (plus Fabric API from Modrinth if it's missing)" : ""}. Your other mods aren't touched.`
                : sup.message}
          </p>
          {st.updateAvailable && <p className="text-[13px] text-accent mt-1">Version {st.bundle?.version} is ready — it's installed automatically the next time you press Play, or now:</p>}
          {st.notes.length > 0 && (
            <ul className="mt-3 space-y-1.5">
              {st.notes.map((n) => (
                <li key={n.title} className="flex items-start gap-2 text-[13px]">
                  <span className={cx("mt-0.5", n.level === "ok" ? "text-accent" : n.level === "info" ? "text-info" : n.level === "warning" ? "text-warn" : "text-danger")}>
                    {n.level === "ok" ? <Icon.Check size={14} /> : n.level === "info" ? <Icon.Info size={14} /> : <Icon.Alert size={14} />}
                  </span>
                  <span>
                    <span className="font-semibold">{n.title}:</span> <span className="text-muted">{n.message}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex flex-col items-end gap-2 shrink-0">
          {sup.ok && !st.enabled && (
            <Button variant="primary" loading={busy} disabled={running} icon={<Icon.Plus size={16} />} onClick={onEnable}>
              Add MOTION Client
            </Button>
          )}
          {sup.ok && st.enabled && st.updateAvailable && (
            <Button variant="primary" loading={busy} disabled={running} icon={<Icon.ArrowUp size={16} />} onClick={onEnable}>
              Update now
            </Button>
          )}
          {st.enabled && (
            <Button variant="ghost" disabled={busy || running} icon={<Icon.Trash size={15} />} onClick={onDisable}>
              Remove
            </Button>
          )}
          {!sup.ok && sup.fix === "create_fabric_copy" && st.bundle && (
            <Button variant="primary" loading={busy} icon={<Icon.Plus size={16} />} onClick={onCreateCopy}>
              Create Fabric {preferredVersion(st.bundle)} installation
            </Button>
          )}
          {running && <span className="text-[12px] text-faint">Close the game to change mods.</span>}
        </div>
      </div>
    </Card>
  );
}

function SkyboxSection({ inst, view, custom, onPick, onImported }: {
  inst: Instance;
  view: SettingsView | null;
  custom: CustomSkybox[];
  onPick: (slot: "day" | "night", id: string) => void;
  onImported: () => void;
}) {
  const [slot, setSlot] = useState<"day" | "night">("day");
  const [importing, setImporting] = useState(false);
  const day = migrateSky((view?.settings["sky.day"] as string | undefined) ?? DEFAULT_SKY_DAY);
  const night = migrateSky((view?.settings["sky.night"] as string | undefined) ?? DEFAULT_SKY_NIGHT);
  const items = useMemo(
    () => [
      { id: "none", name: "Vanilla sky", preview: null as string | null, custom: false },
      ...BUILT_IN_SKYBOXES.map((s) => ({ id: s.id, name: s.name, preview: s.preview as string | null, custom: false })),
      ...custom.map((c) => ({ id: c.id, name: c.name, preview: null, custom: true })),
    ],
    [custom],
  );
  const doImport = async () => {
    const picked = await native.pickFiles({ title: "Choose a skybox image", filters: [["PNG image", ["png"]]] }).catch(() => [] as string[]);
    if (!picked.length) return;
    setImporting(true);
    try {
      const sky = await importSkybox(inst, picked[0]!);
      onPick(slot, sky.id);
      toast({ kind: "success", title: "Skybox imported", message: `"${sky.name}" is now your ${slot === "day" ? "daytime" : "night"} sky.` });
      onImported();
    } catch (e) {
      reportError("Couldn't import the skybox", e);
    } finally {
      setImporting(false);
    }
  };
  return (
    <Section title="Skybox" subtitle="MOTION crossfades between your day and night skies. Brightness, tint, rotation and more are in game (Right Shift → Skyboxes).">
      <div className="flex items-center justify-between mb-3">
        <Segmented value={slot} onChange={setSlot} options={[{ value: "day", label: "Day sky" }, { value: "night", label: "Night sky" }]} />
        <Button size="sm" loading={importing} icon={<Icon.Upload size={14} />} onClick={() => void doImport()}>
          Import image…
        </Button>
      </div>
      <div className="grid grid-cols-3 xl:grid-cols-5 gap-2.5">
        {items.map((s) => {
          const selected = (slot === "day" ? day : night) === s.id;
          return (
            <button
              key={s.id}
              type="button"
              onClick={() => onPick(slot, s.id)}
              className={cx("group text-left rounded-xl overflow-hidden border transition-all cursor-pointer", selected ? "border-accent ring-2 ring-accent/40" : "border-line hover:border-line-strong")}
            >
              <div className="relative aspect-[16/9] bg-raised">
                {s.preview ? (
                  <img src={s.preview} alt="" className="absolute inset-0 h-full w-full object-cover" />
                ) : s.id === "none" ? (
                  <div className="absolute inset-0 bg-gradient-to-b from-[#78a7ff] to-[#c0d8ff]" />
                ) : (
                  <div className="absolute inset-0 grid place-items-center text-faint">
                    <Icon.Image size={22} />
                  </div>
                )}
                <div className="absolute left-1.5 top-1.5 flex gap-1">
                  {day === s.id && <span className="px-1.5 rounded bg-black/55 text-[11px] font-bold tracking-wide text-white">DAY</span>}
                  {night === s.id && <span className="px-1.5 rounded bg-black/55 text-[11px] font-bold tracking-wide text-white">NIGHT</span>}
                </div>
              </div>
              <div className={cx("px-2 py-1.5 text-[12px] font-medium truncate", selected ? "text-accent" : "text-fg")}>{s.name}</div>
            </button>
          );
        })}
      </div>
      <p className="text-[12px] text-faint mt-3">
        Imports: a PNG in the 3×2 cube layout used by OptiFine skies (e.g. 1536×1024) or a 360° panorama (2:1). OptiFine resource packs can be imported in game.
      </p>
    </Section>
  );
}

function MenuKeyRow({ inst, running }: { inst: Instance; running: boolean }) {
  const [key, setKey] = useState<string | null>(null);
  const [conflicts, setConflicts] = useState<string[]>([]);
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    menuKey(inst)
      .then((r) => {
        setKey(r.key);
        setConflicts(r.conflicts);
      })
      .finally(() => setLoaded(true));
  }, [inst]);
  const options = MENU_KEYS.some((k) => k.key === key) || !key ? MENU_KEYS : [{ key, label: menuKeyLabel(key) }, ...MENU_KEYS];
  return (
    <div className="flex items-center justify-between gap-6 mt-5 pt-4 border-t border-line">
      <div>
        <div className="font-medium text-[14px]">Menu key</div>
        <div className="text-[13px] text-muted">
          {running ? "Close the game to change it here, or change it in game under Keybinds." : "Stored in Minecraft's options.txt; also changeable in game."}
          {conflicts.length > 0 && <span className="text-warn"> Also used by: {conflicts.join(", ")}.</span>}
        </div>
      </div>
      {!loaded ? (
        <Spinner />
      ) : (
        <Select
          value={key ?? "key.keyboard.right.shift"}
          onChange={async (v) => {
            try {
              setConflicts(await setMenuKey(inst, v));
              setKey(v);
            } catch (e) {
              reportError("Couldn't change the key", e);
            }
          }}
          options={options.map((k) => ({ value: k.key, label: k.label, disabled: running }))}
          className="w-44"
        />
      )}
    </div>
  );
}

function Preview({ view, custom, installed }: { view: SettingsView | null; custom: CustomSkybox[]; installed: boolean }) {
  const s = view?.settings ?? {};
  const day = migrateSky((s["sky.day"] as string | undefined) ?? DEFAULT_SKY_DAY);
  const night = migrateSky((s["sky.night"] as string | undefined) ?? DEFAULT_SKY_NIGHT);
  const [time, setTime] = useState<"day" | "night">("day");
  const id = time === "day" ? day : night;
  const sky = BUILT_IN_SKYBOXES.find((b) => b.id === id);
  const customSky = custom.find((c) => c.id === id);
  const petals = s["petals.enabled"] !== false;
  const snow = s["snow.enabled"] === true;
  const theme = (s["ui.theme"] as string | undefined) ?? "motion";
  const accent = THEMES.find((t) => t.id === theme)?.accent ?? "#e3192f";
  const petalColor = typeof s["petals.colorA"] === "string" ? (s["petals.colorA"] as string) : "#d3203a";
  const reduce = useStore(app, (st) => st.settings.reduceMotion);
  return (
    <div className="sticky top-4 space-y-3">
      <Card className="overflow-hidden">
        <div className="relative aspect-[16/11] bg-raised">
          {sky ? (
            <img src={sky.preview} alt="" className="absolute inset-0 h-full w-full object-cover" />
          ) : (
            <div className={cx("absolute inset-0", id === "none" ? "bg-gradient-to-b from-[#78a7ff] to-[#d7e8ff]" : "accent-gradient opacity-60")} />
          )}
          {/* a blocky little landscape so the sky reads as a sky */}
          <svg className="absolute inset-x-0 bottom-0 w-full" viewBox="0 0 160 40" preserveAspectRatio="none" aria-hidden>
            <path d="M0 22h14v-4h12v6h14v-8h10v4h18v-6h12v8h16v-4h14v6h16v-2h14V40H0z" fill="#3b2a3c" opacity=".75" />
            <path d="M0 30h20v-3h16v4h22v-5h18v3h20v-4h16v5h18v-3h30V40H0z" fill="#2c4a2c" />
            <path d="M0 30h20v-3h16v4h22v-5h18v3h20v-4h16v5h18v-3h30v2h-30v3h-18v-5h-16v4h-20v-3h-18v5h-22v-4h-16v3H0z" fill="#5d8f4f" />
          </svg>
          {!reduce && petals && <Petals color={petalColor} count={16} />}
          {!reduce && snow && <Petals color="#ffffff" count={20} />}
          <div className="absolute left-3 top-3 right-3 flex justify-between">
            <Segmented value={time} onChange={setTime} options={[{ value: "day", label: "Day" }, { value: "night", label: "Night" }]} />
          </div>
          {/* mini in-game menu chip */}
          <div className="absolute left-3 bottom-3 rounded-lg px-2.5 py-1.5 text-[11px] font-semibold text-white" style={{ background: "rgba(20,12,18,0.82)", borderLeft: `3px solid ${accent}` }}>
            MOTION menu · {MENU_THEMES.find((t) => t.id === theme)?.name ?? "Crimson"}
          </div>
        </div>
        <div className="p-3.5 text-[13px] text-muted leading-relaxed">
          <span className="text-fg font-medium">{sky?.name ?? customSky?.name ?? (id === "none" ? "Vanilla sky" : "Custom sky")}</span>
          {" — "}
          {sky?.description ?? (customSky ? "Your imported skybox." : "Minecraft's own sky.")}
          <div className="text-faint mt-1.5">A preview: in game the look also depends on your world, time of day, weather and shaders.</div>
        </div>
      </Card>
      {!installed && <div className="text-[13px] text-faint px-1">Add MOTION Client to this installation to change these settings.</div>}
    </div>
  );
}
