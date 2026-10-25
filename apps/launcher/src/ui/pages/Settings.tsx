import { useEffect, useState, type ReactNode } from "react";
import { JVM_PRESETS, splitArgs, validateJvmArgs } from "../../core/perf/memory";
import { PRESETS, type PresetId } from "../../core/perf/presets";
import { app, DEFAULT_SETTINGS, MS_CLIENT_ID_BUILD, navigate, P, path, reportError, THEMES, toast, updateSettings, type Settings } from "../../services/app";
import { native } from "../../services/native";
import { API_URL } from "../../services/premium";
import { useStore } from "../../services/store";
import { checkForUpdate } from "../../services/updater";
import { Button, Card, confirm, cx, Field, gb, PageHeader, Segmented, Select, Slider, TextInput, Toggle } from "../components";
import { forgetCustomBackground } from "../backdrop";
import { Icon } from "../icons";
import { PremiumBadge, usePremium } from "../premiumParts";
import { MemoryControl } from "./Instance";

export const FREE_ACCENTS = ["#39ff88", "#c6ff3d", "#00e5a0", "#e3192f", "#ff1f3d", "#b3122a", "#ff4a1f", "#ff2e63", "#c81d4e", "#f2f2f2"];

const SECTIONS = [
  ["appearance", "Appearance", <Icon.Paint size={16} key="a" />],
  ["launcher", "Launcher", <Icon.Home size={16} key="b" />],
  ["game", "Game defaults", <Icon.Cube size={16} key="c" />],
  ["storage", "Storage", <Icon.Folder size={16} key="d" />],
  ["privacy", "Privacy", <Icon.Shield size={16} key="e" />],
  ["advanced", "Advanced", <Icon.Terminal size={16} key="f" />],
  ["about", "About", <Icon.Info size={16} key="g" />],
] as const;

function Section({ id, title, children, description }: { id: string; title: string; children: ReactNode; description?: string }) {
  return (
    <section id={`settings-${id}`} className="scroll-mt-6">
      <h2 className="font-display text-[16px] font-semibold">{title}</h2>
      {description && <p className="text-[13px] text-muted mt-1">{description}</p>}
      <Card className="p-5 mt-3 space-y-5">{children}</Card>
    </section>
  );
}

function Row({ label, description, children }: { label: ReactNode; description?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-8">
      <div className="min-w-0">
        <div className="font-medium">{label}</div>
        {description && <div className="text-[13px] text-muted mt-0.5">{description}</div>}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

export function SettingsPage({ section }: { section?: string }) {
  const s = useStore(app, (x) => x.settings);
  const paths = useStore(app, (x) => x.paths);
  const system = useStore(app, (x) => x.system);
  const premium = usePremium();
  const [jvmText, setJvmText] = useState(s.jvmArgs.join(" "));
  const [gameText, setGameText] = useState(s.gameArgs.join(" "));
  const [autostart, setAutostart] = useState<boolean | null>(null);
  const [customAccent, setCustomAccent] = useState(s.accent);

  useEffect(() => {
    native.autostartGet().then(setAutostart).catch(() => setAutostart(null));
  }, []);
  useEffect(() => {
    if (section) document.getElementById(`settings-${section}`)?.scrollIntoView({ behavior: "smooth" });
  }, [section]);

  const set = (patch: Partial<Settings>) => updateSettings(patch);
  const jvmIssues = validateJvmArgs(splitArgs(jvmText), 21);

  return (
    <div className="animate-in">
      <PageHeader title="Settings" subtitle="Saved automatically. Launcher settings apply right away; game settings apply the next time Minecraft starts." />
      <div className="grid grid-cols-[200px_1fr] gap-8 items-start">
        <nav className="sticky top-0 space-y-1">
          {SECTIONS.map(([id, label, icon]) => (
            <button
              key={id}
              type="button"
              onClick={() => document.getElementById(`settings-${id}`)?.scrollIntoView({ behavior: "smooth" })}
              className="flex items-center gap-2.5 w-full h-9 px-3 rounded-lg text-[13.5px] text-muted hover:text-fg hover:bg-panel-2 cursor-pointer"
            >
              {icon}
              {label}
            </button>
          ))}
        </nav>

        <div className="space-y-10 max-w-3xl">
          <Section id="appearance" title="Appearance">
            <div>
              <div className="font-medium mb-2">Theme</div>
              <div className="grid grid-cols-5 gap-2.5">
                {THEMES.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => set({ theme: t.id })}
                    className={cx("rounded-lg overflow-hidden border-2 text-left transition-colors cursor-pointer", s.theme === t.id ? "border-accent" : "border-transparent hover:border-line-strong")}
                  >
                    <div className="relative aspect-[16/9]">
                      <img src={`/backgrounds/${t.id}-thumb.jpg`} alt="" className="absolute inset-0 h-full w-full object-cover" />
                      <span className="absolute right-1.5 bottom-1.5 h-4 w-4 rounded-full border border-white/40" style={{ background: t.accent }} />
                    </div>
                    <div className="px-2 py-1.5 text-[12.5px] font-medium bg-panel-2">{t.name}</div>
                  </button>
                ))}
              </div>
            </div>
            <div>
              <div className="font-medium mb-2">Accent colour</div>
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  type="button"
                  onClick={() => set({ accentFromTheme: true })}
                  className={cx("h-8 px-3 rounded-full border text-[12.5px] font-medium cursor-pointer", s.accentFromTheme ? "border-accent text-accent bg-accent/10" : "border-line text-muted hover:text-fg")}
                >
                  Match theme
                </button>
                {FREE_ACCENTS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    aria-label={`Accent ${c}`}
                    onClick={() => set({ accent: c, accentFromTheme: false })}
                    className={cx("h-8 w-8 rounded-full border-2 transition-colors cursor-pointer", !s.accentFromTheme && s.accent === c ? "border-fg" : "border-transparent")}
                    style={{ background: c }}
                  />
                ))}
                <div className="w-px h-6 bg-line mx-2" />
                {premium ? (
                  <div className="flex items-center gap-2">
                    <input type="color" value={customAccent} onChange={(e) => setCustomAccent(e.target.value)} onBlur={() => set({ accent: customAccent, accentFromTheme: false })} className="h-8 w-10 rounded bg-transparent cursor-pointer" aria-label="Custom accent colour" />
                    <span className="text-[12.5px] text-muted">Any colour</span>
                  </div>
                ) : (
                  <button type="button" onClick={() => navigate({ page: "premium" })} className="flex items-center gap-1.5 text-[12.5px] text-muted hover:text-fg cursor-pointer">
                    <Icon.Lock size={13} /> Any colour with <PremiumBadge />
                  </button>
                )}
              </div>
            </div>
            <div>
              <div className="flex items-center justify-between mb-1">
                <div className="font-medium">Accent intensity</div>
                <span className="text-[12.5px] text-muted">{s.accentIntensity <= 25 ? "Subtle" : s.accentIntensity <= 75 ? "Balanced" : "Vivid"}</span>
              </div>
              <div className="text-[13px] text-muted mb-2">How saturated the accent colour is. 0% is fully muted.</div>
              <Slider value={s.accentIntensity} min={0} max={100} step={5} format={(v) => `${v}%`} onChange={(v) => set({ accentIntensity: v })} />
            </div>
            <BackgroundRows />
            <Row label="Ember particles" description="Small sparks rising on the Home screen (paused while MOTION is in the background, off with Reduce motion).">
              <Toggle checked={s.petals} onChange={(v) => set({ petals: v })} />
            </Row>
            <Row label="Reduce motion" description="Turn off animations, transitions and particles in the launcher.">
              <Toggle checked={s.reduceMotion} onChange={(v) => set({ reduceMotion: v })} />
            </Row>
          </Section>

          <Section id="launcher" title="Launcher">
            <Row label="Start MOTION with Windows" description="Opens minimized in the taskbar when you sign in to Windows.">
              <Toggle
                checked={Boolean(autostart)}
                disabled={autostart === null}
                onChange={(v) =>
                  void native
                    .autostartSet(v)
                    .then(() => setAutostart(v))
                    .catch((e) => reportError("Couldn't change startup", e))
                }
              />
            </Row>
            <Row label="Open on">
              <Select value={s.startPage} onChange={(v) => set({ startPage: v })} options={[{ value: "home", label: "Home" }, { value: "library", label: "Library" }]} className="w-40" />
            </Row>
            <Row label="When a game starts">
              <Select value={s.onLaunch} onChange={(v) => set({ onLaunch: v })} options={[{ value: "minimize", label: "Minimize MOTION" }, { value: "keep", label: "Keep MOTION open" }]} className="w-48" />
            </Row>
            <Row label="Show snapshots" description="Include snapshots and old versions in version lists.">
              <Toggle checked={s.showSnapshots} onChange={(v) => set({ showSnapshots: v })} />
            </Row>
            <Row label="Mod update notifications" description="Check Modrinth for mod updates when you open an installation.">
              <Toggle checked={s.modUpdateNotifications} onChange={(v) => set({ modUpdateNotifications: v })} />
            </Row>
            <Row label="Update MOTION automatically" description="New versions download in the background and install by themselves when you're not playing (with a 15-second countdown you can postpone). Updates are signed and verified before installing.">
              <div className="flex items-center gap-3">
                <Button size="sm" variant="ghost" onClick={() => void checkForUpdate(true)}>
                  Check now
                </Button>
                <Toggle
                  checked={s.autoUpdate}
                  onChange={(v) => {
                    set({ autoUpdate: v });
                    // Turning it back on picks up a waiting update right away.
                    if (v) void checkForUpdate(false);
                  }}
                />
              </div>
            </Row>
            <Row label="Parallel downloads" description="Lower this on slow or unstable connections.">
              <Select value={String(s.downloadThreads)} onChange={(v) => set({ downloadThreads: Number(v) })} options={["4", "8", "16", "32"].map((n) => ({ value: n, label: n }))} className="w-24" />
            </Row>
          </Section>

          <Section id="game" title="Game defaults" description="Used by installations that don't override them. Changes apply the next time you start the game (a running game keeps its current settings).">
            <MemoryControl value={s.memory ?? undefined} onChange={(memory) => set({ memory: memory ?? null })} modded modCount={40} />
            <Field label="Java runtime" hint="Leave on automatic: MOTION installs the Java version each Minecraft release needs.">
              <div className="flex gap-2">
                <TextInput value={s.javaPath ?? ""} onChange={(v) => set({ javaPath: v.trim() || null })} placeholder="Automatic" className="flex-1" mono />
                <Button
                  onClick={() =>
                    void native.pickFiles({ title: "Choose javaw.exe", filters: [["Java", ["exe"]]] }).then(([p]) => {
                      if (p) set({ javaPath: p });
                    })
                  }
                >
                  Browse…
                </Button>
              </div>
            </Field>
            <Field label="Garbage collector">
              <Select value={s.jvmPreset} onChange={(v) => set({ jvmPreset: v })} options={JVM_PRESETS.map((p) => ({ value: p.id, label: `${p.name} — ${p.description}` }))} />
            </Field>
            <Field label="JVM arguments">
              <TextInput value={jvmText} onChange={setJvmText} mono placeholder="-XX:+AlwaysPreTouch" />
            </Field>
            {jvmIssues.map((i) => (
              <div key={i.arg + i.message} className={cx("text-[12.5px] -mt-3", i.severity === "error" ? "text-danger" : "text-warn")}>
                {i.arg}: {i.message}
              </div>
            ))}
            <Field label="Game arguments">
              <TextInput value={gameText} onChange={setGameText} mono />
            </Field>
            {(jvmText !== s.jvmArgs.join(" ") || gameText !== s.gameArgs.join(" ")) && (
              <Button size="sm" variant="primary" disabled={jvmIssues.some((i) => i.severity === "error")} onClick={() => set({ jvmArgs: splitArgs(jvmText), gameArgs: splitArgs(gameText) })}>
                Save arguments
              </Button>
            )}
            <div className="grid grid-cols-3 gap-3 items-end">
              <Field label="Window width">
                <TextInput value={String(s.resolution?.width ?? "")} onChange={(v) => set({ resolution: v ? { width: Number(v) || 854, height: s.resolution?.height ?? 480 } : null })} placeholder="854" />
              </Field>
              <Field label="Window height">
                <TextInput value={String(s.resolution?.height ?? "")} onChange={(v) => set({ resolution: v ? { width: s.resolution?.width ?? 854, height: Number(v) || 480 } : null })} placeholder="480" />
              </Field>
              <div className="pb-1.5">
                <Toggle checked={s.fullscreen} onChange={(v) => set({ fullscreen: v })} label="Fullscreen" />
              </div>
            </div>
            <Row label="Preset for new installations">
              <Select
                value={s.defaultPreset}
                onChange={(v) => set({ defaultPreset: v as PresetId })}
                options={PRESETS.map((p) => ({ value: p.id, label: p.premium && !premium ? `${p.name} (Premium)` : p.name, disabled: p.premium && !premium }))}
                className="w-44"
              />
            </Row>
            <Row label="In-game FPS overlay" description="Shown by Sodium Extra in installations that have it. Configure details on the Performance page.">
              <Toggle checked={s.overlay.enabled} onChange={(v) => set({ overlay: { ...s.overlay, enabled: v } })} />
            </Row>
          </Section>

          <Section id="storage" title="Storage">
            <Row label="MOTION data folder" description={<span className="font-mono text-[12px] selectable">{paths?.dataDir}</span>}>
              <Button size="sm" icon={<Icon.Folder size={14} />} onClick={() => void native.openPath(P().dataDir)}>
                Open
              </Button>
            </Row>
            <Row
              label="New installations folder"
              description={
                s.defaultGameRoot ? <span className="font-mono text-[12px] selectable">{s.defaultGameRoot}</span> : "Inside the MOTION data folder. Choose another drive to keep worlds and mods elsewhere."
              }
            >
              <div className="flex gap-2">
                {s.defaultGameRoot && (
                  <Button size="sm" variant="ghost" onClick={() => set({ defaultGameRoot: null })}>
                    Reset
                  </Button>
                )}
                <Button
                  size="sm"
                  onClick={() =>
                    void native.pickGameDir().then((dir) => {
                      if (dir) set({ defaultGameRoot: dir });
                    })
                  }
                >
                  Change…
                </Button>
              </div>
            </Row>
            <p className="text-[12.5px] text-faint">Game files, libraries, assets and Java runtimes are shared between installations in the data folder, so each version is only downloaded once. Existing installations aren't moved.</p>
          </Section>

          <Section id="privacy" title="Privacy and diagnostics">
            <p className="text-[13px] text-muted leading-relaxed">
              MOTION has no telemetry, analytics or ads. It talks to Microsoft and Mojang to sign in and download the game, Modrinth and mod-loader sites for mods, and — only if you use Premium or announcements — MOTION's own server. Tokens stay in the Windows Credential Manager and are removed from every log.
            </p>
            <Row label="Minecraft news on Home" description="Loads Mojang's patch notes from launchercontent.mojang.com.">
              <Toggle checked={s.news} onChange={(v) => set({ news: v })} />
            </Row>
            <Row label="MOTION announcements" description={API_URL ? `Loads release notes and announcements from ${new URL(API_URL).host}.` : "Not configured in this build."}>
              <Toggle checked={s.announcements} onChange={(v) => set({ announcements: v })} disabled={!API_URL} />
            </Row>
            <Row label="Launcher logs" description="Useful for support. Logs never contain passwords or tokens.">
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="ghost"
                  icon={<Icon.Copy size={14} />}
                  onClick={() =>
                    void navigator.clipboard
                      .writeText(
                        [`MOTION ${paths?.version}`, system?.os, system?.cpuBrand, system ? `${gb(Math.round(system.totalMemory / 1_048_576))} RAM` : "", ...(system?.gpus ?? [])].filter(Boolean).join("\n"),
                      )
                      .then(() => toast({ kind: "success", title: "System info copied" }))
                  }
                >
                  Copy system info
                </Button>
                <Button size="sm" icon={<Icon.Folder size={14} />} onClick={() => void native.openLogDir().catch((e) => reportError("Couldn't open logs", e))}>
                  Open logs
                </Button>
              </div>
            </Row>
          </Section>

          <Section id="advanced" title="Advanced">
{!MS_CLIENT_ID_BUILD && (
            <Field
              label="Microsoft application (client) id"
              hint={MS_CLIENT_ID_BUILD ? "This build includes an approved id. Only change this if you're developing MOTION." : "This build has no id. Enter the Azure app id you registered (docs/MICROSOFT_AUTH.md)."}
            >
              <TextInput value={s.msClientId} onChange={(v) => set({ msClientId: v.trim() })} placeholder={MS_CLIENT_ID_BUILD || "00000000-0000-0000-0000-000000000000"} mono />
            </Field>
            )}
            <Row label="Reset settings" description="Restore every launcher setting to its default. Installations and accounts are kept.">
              <Button
                size="sm"
                variant="danger"
                onClick={() =>
                  void confirm({ title: "Reset all settings?", message: "Your installations, mods, worlds and accounts are not affected.", confirmLabel: "Reset", danger: true }).then((ok) => {
                    if (ok) {
                      updateSettings({ ...DEFAULT_SETTINGS, selectedInstanceId: s.selectedInstanceId, activeAccountId: s.activeAccountId });
                      setJvmText("");
                      setGameText("");
                    }
                  })
                }
              >
                Reset
              </Button>
            </Row>
          </Section>

          <Section id="about" title="About">
            <div className="flex items-center gap-4">
              <img src="/motion-mark.svg" alt="" width={48} height={48} />
              <div>
                <div className="font-display font-semibold text-[17px]">MOTION {paths?.version}</div>
                <div className="text-[13px] text-muted">Built for frames.</div>
              </div>
            </div>
            <p className="text-[12.5px] text-faint leading-relaxed">
              Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Minecraft is a trademark of Mojang Synergies AB. Mods and other content from Modrinth belong to their creators and are distributed under their own licences.
            </p>
            {API_URL && (
              <div className="flex gap-4 text-[13px]">
                {[
                  ["Website", ""],
                  ["Release notes", "/releases.html"],
                  ["Support", "/support.html"],
                  ["Privacy", "/privacy.html"],
                  ["Terms", "/terms.html"],
                ].map(([label, p]) => (
                  <button key={label} type="button" className="text-accent hover:underline cursor-pointer" onClick={() => void native.openUrl(`${API_URL}${p}`)}>
                    {label}
                  </button>
                ))}
              </div>
            )}
          </Section>
        </div>
      </div>
    </div>
  );
}

/** Background art: theme, your own image, or none; plus dim/blur for pages other than Home. */
function BackgroundRows() {
  const s = useStore(app, (x) => x.settings);
  const bg = s.background;
  const set = (patch: Partial<Settings["background"]>) => updateSettings({ background: { ...bg, ...patch } });
  const pickImage = async () => {
    const picked = await native.pickFiles({ title: "Choose a background image", filters: [["Images", ["png", "jpg", "jpeg", "webp"]]] }).catch(() => [] as string[]);
    if (!picked.length) return;
    try {
      const dir = path(P().dataDir, "backgrounds");
      await P().mkdir(dir);
      const res = await native.importFiles([picked[0]!], dir);
      const copied = res.copied[0] ?? path(dir, picked[0]!.split(/[\\/]/).pop()!);
      if (bg.custom) forgetCustomBackground(bg.custom);
      forgetCustomBackground(copied);
      set({ mode: "custom", custom: copied });
    } catch (e) {
      reportError("Couldn't use that image", e);
    }
  };
  return (
    <>
      <Row label="Background" description={bg.mode === "custom" && bg.custom ? `Your image: ${bg.custom.split(/[\\/]/).pop()}` : "The art behind the launcher. Your own image is copied into MOTION's data folder."}>
        <div className="flex items-center gap-2">
          <Segmented
            value={bg.mode}
            onChange={(mode) => (mode === "custom" && !bg.custom ? void pickImage() : set({ mode }))}
            options={[
              { value: "theme", label: "Theme art" },
              { value: "custom", label: "My image" },
              { value: "none", label: "None" },
            ]}
          />
          <Button size="sm" icon={<Icon.Image size={14} />} onClick={() => void pickImage()}>
            Choose…
          </Button>
        </div>
      </Row>
      {bg.mode !== "none" && (
        <>
          <Row label="Background dimming" description="How much the art is darkened behind pages other than Home.">
            <div className="w-56">
              <Slider value={Math.round(bg.dim * 100)} min={30} max={95} step={5} onChange={(v) => set({ dim: v / 100 })} format={(v) => `${v}%`} />
            </div>
          </Row>
          <Row label="Background blur" description="Softens the art behind pages other than Home.">
            <div className="w-56">
              <Slider value={bg.blur} min={0} max={30} step={2} onChange={(v) => set({ blur: v })} format={(v) => `${v}px`} />
            </div>
          </Row>
        </>
      )}
    </>
  );
}
