import { useEffect, useMemo, useRef, useState } from "react";
import type { Instance } from "../../core/instances/model";
import { LOADERS, type LoaderId } from "../../core/loaders/loaders";
import { JVM_PRESETS, recommendMemory, splitArgs, validateJvmArgs } from "../../core/perf/memory";
import { PRESETS } from "../../core/perf/presets";
import { projectUrl } from "../../core/modrinth/api";
import { guessJavaMajor } from "../../core/mojang/version";
import { app, errorMessage, navigate, P, path, reportError, toast } from "../../services/app";
import {
  checkUpdates,
  contentDir,
  identifyContent,
  importFolder,
  listWorlds,
  modIcon,
  openContentFolder,
  pickAndImport,
  removeContent,
  toggleContent,
  updateContent,
  type ContentItem,
  type ContentKind,
  type UpdateInfo,
  type WorldInfo,
} from "../../services/content";
import { isRunning, play } from "../../services/game";
import { applyCrashFix, fixLabel } from "../../services/crashFix";
import { listServers, worldDetails } from "../../services/worlds";
import { quickPlaySupport, validServerAddress, type SavedServer } from "../../core/mojang/worlds";
import { getInstance, instanceGameDir, updateInstance } from "../../services/instances";
import { native, type JavaInfo } from "../../services/native";
import { useStore } from "../../services/store";
import {
  Badge,
  Button,
  SectionTitle,
  Callout,
  Card,
  confirm,
  cx,
  EmptyState,
  Field,
  formatBytes,
  gb,
  IconButton,
  Menu,
  Modal,
  Select,
  Skeleton,
  Slider,
  Spinner,
  Tabs,
  TextInput,
  timeAgo,
  Toggle,
} from "../components";
import { bumpContent, useContent } from "../contentEvents";
import { Icon } from "../icons";
import { InstanceIcon, loaderLabel, RenameModal, VersionLoaderPicker } from "../instanceParts";
import { PremiumBadge, usePremium } from "../premiumParts";
import { LiveStats, PlayButton } from "./Home";
import { confirmDelete, instanceMenu } from "./Library";

type Tab = "content" | "resourcepacks" | "shaderpacks" | "worlds" | "logs" | "settings";

export function InstancePage({ id, tab = "content" }: { id: string; tab?: Tab }) {
  const inst = useStore(app, (s) => s.instances.find((i) => i.id === id));
  const [renaming, setRenaming] = useState(false);
  if (!inst) {
    return (
      <Card>
        <EmptyState title="Installation not found" action={<Button onClick={() => navigate({ page: "library" })}>Back to Library</Button>}>
          It may have been deleted.
        </EmptyState>
      </Card>
    );
  }
  const setTab = (t: Tab) => navigate({ page: "instance", id, tab: t });
  return (
    <div className="animate-in">
      <button type="button" className="flex items-center gap-1 text-muted hover:text-fg text-[13px] mb-5 cursor-pointer" onClick={() => navigate({ page: "library" })}>
        <Icon.ChevronLeft size={16} /> Library
      </button>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-4 mb-7">
        <InstanceIcon inst={inst} size={56} />
        <div className="min-w-0 flex-1">
          <h1 className="font-display text-[24px] font-semibold tracking-tight truncate">{inst.name}</h1>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1 text-[13px] text-muted">
            {[
              `Minecraft ${inst.gameVersion}`,
              loaderLabel(inst),
              inst.modpack ? `${inst.modpack.name}${inst.modpack.version ? ` ${inst.modpack.version}` : ""}` : null,
              inst.performancePreset ? `${PRESETS.find((p) => p.id === inst.performancePreset)?.name ?? "Custom"} preset` : null,
              inst.bloomClient?.enabled ? "MOTION Client" : null,
            ]
              .filter((x): x is string => Boolean(x))
              .map((part, i) => (
                // Each detail stays on one line; the row wraps between them.
                <span key={part} className={cx("whitespace-nowrap", part === "MOTION Client" && "text-fg/80", i === 0 && "text-fg/80")}>
                  {part}
                </span>
              ))}
          </div>
          <LiveStats instanceId={inst.id} />
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => navigate({ page: "mods", instanceId: inst.id })}
            className="h-10 px-4 rounded-lg inline-flex items-center gap-2 text-[14px] font-medium border border-line bg-raised hover:bg-line-strong transition-colors cursor-pointer"
          >
            <Icon.Hud size={16} className="text-muted" /> MOTION Mods
          </button>
          <PlayButton inst={inst} size="md" />
          <Menu
            trigger={
              <IconButton label="More" className="h-10 w-10 border border-line">
                <Icon.More size={18} />
              </IconButton>
            }
            items={[
              { label: "Visuals", icon: <Icon.Paint size={15} />, onClick: () => navigate({ page: "customize", instanceId: inst.id }) },
              ...instanceMenu(inst, { onRename: () => setRenaming(true) }).filter((x) => x === "divider" || x.label !== "Open"),
            ]}
          />
        </div>
      </div>
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { value: "content", label: "Mods" },
          { value: "resourcepacks", label: "Resource packs" },
          { value: "shaderpacks", label: "Shaders" },
          { value: "worlds", label: "Worlds & servers" },
          { value: "logs", label: "Logs" },
          { value: "settings", label: "Settings" },
        ]}
      />
      <div className="mt-6">
        {tab === "content" && <ModsTab inst={inst} />}
        {(tab === "resourcepacks" || tab === "shaderpacks") && <PacksTab inst={inst} kind={tab} />}
        {tab === "worlds" && <WorldsTab inst={inst} />}
        {tab === "logs" && <LogsTab inst={inst} />}
        {tab === "settings" && <SettingsTab inst={inst} />}
      </div>
      <RenameModal inst={inst} open={renaming} onClose={() => setRenaming(false)} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Mods
// ---------------------------------------------------------------------------

function ItemIcon({ item, size = 38, fallback }: { item: ContentItem; size?: number; fallback?: "pack" | "shader" }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    void modIcon(item).then((u) => alive && setSrc(u));
    return () => {
      alive = false;
    };
  }, [item.path, item.modifiedMs, item.modrinth?.iconUrl]);
  if (src) return <img src={src} alt="" className="rounded-lg object-cover shrink-0 bg-raised pixelated" style={{ width: size, height: size }} draggable={false} />;
  return (
    <div className="rounded-lg shrink-0 bg-raised border border-line grid place-items-center text-faint" style={{ width: size, height: size }}>
      {fallback === "shader" ? <Icon.Sun size={size * 0.45} /> : fallback === "pack" ? <Icon.Paint size={size * 0.45} /> : <Icon.Puzzle size={size * 0.45} />}
    </div>
  );
}


const checkedThisSession = new Set<string>();

function ModsTab({ inst }: { inst: Instance }) {
  const { items, error, reload } = useContent(inst, "mods");
  const notify = useStore(app, (s) => s.settings.modUpdateNotifications);
  const premium = usePremium();
  const [query, setQuery] = useState("");
  const [updates, setUpdates] = useState<UpdateInfo[] | null>(null);
  const [checking, setChecking] = useState(false);
  const [updatingAll, setUpdatingAll] = useState(false);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (items ?? []).filter((m) => !q || [m.meta?.name, m.modrinth?.title, m.fileName, m.meta?.id].some((x) => x?.toLowerCase().includes(q)));
  }, [items, query]);

  async function check(quiet = false) {
    if (!items) return;
    setChecking(true);
    try {
      const identified = await identifyContent(inst, items);
      const fresh = identified ? await import("../../services/content").then((c) => c.listContent(inst, "mods")) : items;
      const u = await checkUpdates(inst, fresh);
      setUpdates(u);
      if (identified) reload();
      if (!quiet) toast({ kind: "info", title: u.length ? `${u.length} update${u.length === 1 ? "" : "s"} available` : "All mods are up to date" });
    } catch (e) {
      if (!quiet) reportError("Couldn't check for updates", e);
    } finally {
      setChecking(false);
    }
  }

  useEffect(() => {
    if (items && notify && inst.loader !== "vanilla" && !checkedThisSession.has(inst.id) && items.length > 0) {
      checkedThisSession.add(inst.id);
      void check(true);
    }
  }, [items === null]);

  async function update(u: UpdateInfo) {
    try {
      await updateContent(inst, "mods", u);
      setUpdates((list) => list?.filter((x) => x !== u) ?? null);
      bumpContent();
      toast({ kind: "success", title: `Updated ${u.item.meta?.name ?? u.item.modrinth?.title ?? u.item.fileName}`, message: `Now ${u.version.version_number}. The old file is in the Recycle Bin.` });
    } catch (e) {
      reportError("Update failed", e);
    }
  }

  async function updateAll() {
    if (!updates?.length) return;
    setUpdatingAll(true);
    for (const u of [...updates]) await update(u);
    setUpdatingAll(false);
  }

  async function remove(item: ContentItem) {
    const ok = await confirm({
      title: `Remove ${item.meta?.name ?? item.modrinth?.title ?? item.fileName}?`,
      message: "The file is moved to the Recycle Bin.",
      confirmLabel: "Remove",
      danger: true,
    });
    if (!ok) return;
    try {
      await removeContent(inst, [item]);
      bumpContent();
    } catch (e) {
      reportError("Couldn't remove the mod", e);
    }
  }

  async function toggle(item: ContentItem) {
    try {
      await toggleContent(inst, "mods", item);
      bumpContent();
    } catch (e) {
      reportError("Couldn't change the mod", e);
    }
  }

  const running = isRunning(inst.id);
  const updateFor = (m: ContentItem) => updates?.find((u) => u.item.path === m.path);

  return (
    <div>
      {inst.loader === "vanilla" && (
        <div className="mb-4">
          <Callout tone="info" title="This is a vanilla installation" action={<Button size="sm" onClick={() => navigate({ page: "instance", id: inst.id, tab: "settings" })}>Change loader</Button>}>
            Mods need a mod loader such as Fabric, Quilt, Forge or NeoForge. Choose one in Settings, or create a separate modded installation.
          </Callout>
        </div>
      )}
      {running && (
        <div className="mb-4">
          <Callout tone="warn">Minecraft is running. Changes to mods take effect the next time you play.</Callout>
        </div>
      )}
      <div className="flex items-center gap-2 mb-4 flex-wrap">
        <TextInput value={query} onChange={setQuery} placeholder="Search mods" icon={<Icon.Search size={16} />} className="w-64" />
        <div className="flex-1" />
        <Button icon={<Icon.Compass size={16} />} onClick={() => navigate({ page: "browse", type: "mod", instanceId: inst.id })} disabled={inst.loader === "vanilla"}>
          Browse mods
        </Button>
        <Button icon={<Icon.Plus size={16} />} onClick={() => void pickAndImport(inst, "mods").then(bumpContent).catch((e) => reportError("Import failed", e))}>
          Add files
        </Button>
        <Menu
          trigger={
            <IconButton label="More actions" className="h-9 w-9 border border-line">
              <Icon.More size={18} />
            </IconButton>
          }
          items={[
            { label: "Import mods from a folder", icon: <Icon.Folder size={15} />, onClick: () => void importFolder(inst).then(bumpContent).catch((e) => reportError("Import failed", e)) },
            { label: "Check for updates", icon: <Icon.Refresh size={15} />, onClick: () => void check(), disabled: checking || inst.loader === "vanilla" },
            "divider",
            { label: "Open mods folder", icon: <Icon.Folder size={15} />, onClick: () => void openContentFolder(inst, "mods") },
            { label: "Open config folder", icon: <Icon.Sliders size={15} />, onClick: () => void openContentFolder(inst, "config") },
          ]}
        />
      </div>

      {updates && updates.length > 0 && (
        <div className="mb-4">
          <Callout
            tone="accent"
            title={`${updates.length} update${updates.length === 1 ? "" : "s"} available on Modrinth`}
            action={
              premium ? (
                <Button size="sm" variant="primary" loading={updatingAll} onClick={() => void updateAll()}>
                  Update all
                </Button>
              ) : (
                <span className="flex items-center gap-2 text-[13px] text-muted">
                  Update all <PremiumBadge />
                </span>
              )
            }
          >
            Update mods one by one below. Old files go to the Recycle Bin.
          </Callout>
        </div>
      )}


      {error && <Callout tone="danger">{error}</Callout>}
      {!items ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-[62px] rounded-xl" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <Card>
          <EmptyState icon={<Icon.Puzzle size={24} />} title="No mods yet">
            Browse Modrinth, drop .jar files onto this window, or use “Add files”.
          </EmptyState>
        </Card>
      ) : (
        <Card className="divide-y divide-line overflow-hidden">
          {shown.map((m) => {
            const u = updateFor(m);
            return (
              <div key={m.path} className={cx("flex items-center gap-3.5 px-4 py-3 hover:bg-panel-2 transition-colors", !m.enabled && "opacity-55")}>
                <ItemIcon item={m} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold truncate">{m.meta?.name ?? m.modrinth?.title ?? m.baseName}</span>
                    {(m.meta?.version || m.modrinth?.versionNumber) && <span className="text-[13px] text-muted truncate">{m.modrinth?.versionNumber ?? m.meta?.version}</span>}
                  </div>
                  <div className="text-[12px] text-faint truncate">
                    {m.fileName} · {formatBytes(m.size)}
                    {m.meta?.loaders.length ? ` · ${m.meta.loaders.join(", ")}` : ""}
                  </div>
                </div>
                {m.modrinth && <Badge className="hidden xl:inline-flex">Modrinth</Badge>}
                {u && (
                  <Button size="sm" variant="outline" icon={<Icon.ArrowUp size={14} />} onClick={() => void update(u)}>
                    {u.version.version_number}
                  </Button>
                )}
                <Toggle checked={m.enabled} onChange={() => void toggle(m)} />
                <Menu
                  trigger={
                    <IconButton label="More">
                      <Icon.More size={17} />
                    </IconButton>
                  }
                  items={[
                    ...(m.modrinth
                      ? [
                          { label: "View in MOTION", icon: <Icon.Compass size={15} />, onClick: () => navigate({ page: "project", id: m.modrinth!.projectId, instanceId: inst.id }) },
                          { label: "Open on Modrinth", icon: <Icon.External size={15} />, onClick: () => void native.openUrl(projectUrl("mod", m.modrinth!.projectId)) },
                        ]
                      : []),
                    { label: "Show in folder", icon: <Icon.Folder size={15} />, onClick: () => void openContentFolder(inst, "mods") },
                    "divider" as const,
                    { label: "Remove", icon: <Icon.Trash size={15} />, danger: true, onClick: () => void remove(m) },
                  ]}
                />
              </div>
            );
          })}
          {shown.length === 0 && <div className="text-muted text-center py-8">No mods match “{query}”.</div>}
        </Card>
      )}
      {items && items.length > 0 && (
        <p className="text-[12px] text-faint mt-3">
          {items.filter((m) => m.enabled).length} of {items.length} enabled. Disabled mods are renamed to .disabled and skipped by the loader.
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Resource packs & shaders
// ---------------------------------------------------------------------------

function PacksTab({ inst, kind }: { inst: Instance; kind: "resourcepacks" | "shaderpacks" }) {
  const { items, error } = useContent(inst, kind);
  const mods = useContent(inst, "mods");
  const shader = kind === "shaderpacks";
  const hasShaderLoader = (mods.items ?? []).some((m) => m.enabled && ["iris", "oculus"].includes(m.meta?.id ?? ""));
  const label = shader ? "shader pack" : "resource pack";

  async function remove(item: ContentItem) {
    const ok = await confirm({ title: `Remove ${item.baseName}?`, message: "It is moved to the Recycle Bin.", confirmLabel: "Remove", danger: true });
    if (!ok) return;
    await removeContent(inst, [item]).then(bumpContent).catch((e) => reportError("Couldn't remove", e));
  }

  return (
    <div>
      {shader && inst.loader !== "vanilla" && mods.items && !hasShaderLoader && (
        <div className="mb-4">
          <Callout
            tone="info"
            title="Shaders need a shader loader mod"
            action={
              <Button size="sm" onClick={() => navigate({ page: "project", id: inst.loader === "forge" ? "oculus" : "iris", instanceId: inst.id })}>
                View {inst.loader === "forge" ? "Oculus" : "Iris"}
              </Button>
            }
          >
            Install {inst.loader === "forge" ? "Oculus" : "Iris"} to use shader packs. MOTION won't install it without asking.
          </Callout>
        </div>
      )}
      {shader && inst.loader === "vanilla" && (
        <div className="mb-4">
          <Callout tone="info">Vanilla Minecraft can't load shader packs. Use a Fabric, Quilt or NeoForge installation with Iris.</Callout>
        </div>
      )}
      <div className="flex items-center gap-2 mb-4">
        <div className="text-muted text-[13px]">Select packs in Minecraft's {shader ? "Video Settings → Shader Packs" : "Options → Resource Packs"} screen.</div>
        <div className="flex-1" />
        <Button icon={<Icon.Compass size={16} />} onClick={() => navigate({ page: "browse", type: shader ? "shader" : "resourcepack", instanceId: inst.id })}>
          Browse {shader ? "shaders" : "packs"}
        </Button>
        <Button icon={<Icon.Plus size={16} />} onClick={() => void pickAndImport(inst, kind).then(bumpContent).catch((e) => reportError("Import failed", e))}>
          Add files
        </Button>
        <IconButton label="Open folder" className="h-9 w-9 border border-line" onClick={() => void openContentFolder(inst, kind)}>
          <Icon.Folder size={17} />
        </IconButton>
      </div>
      {error && <Callout tone="danger">{error}</Callout>}
      {!items ? (
        <Skeleton className="h-40 rounded-xl" />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState icon={shader ? <Icon.Sun size={24} /> : <Icon.Paint size={24} />} title={`No ${label}s yet`}>
            Browse Modrinth or drop .zip files onto this window.
          </EmptyState>
        </Card>
      ) : (
        <Card className="divide-y divide-line overflow-hidden">
          {items.map((m) => (
            <div key={m.path} className={cx("flex items-center gap-3.5 px-4 py-3", !m.enabled && "opacity-55")}>
              <ItemIcon item={m} fallback={shader ? "shader" : "pack"} />
              <div className="min-w-0 flex-1">
                <div className="font-semibold truncate">{m.modrinth?.title ?? m.baseName.replace(/\.zip$/i, "")}</div>
                <div className="text-[12px] text-faint truncate">
                  {m.isDir ? "Folder" : formatBytes(m.size)} · added {timeAgo(m.modifiedMs)}
                </div>
              </div>
              <Toggle checked={m.enabled} onChange={() => void toggleContent(inst, kind, m).then(bumpContent).catch((e) => reportError("Couldn't change", e))} />
              <IconButton label="Remove" onClick={() => void remove(m)}>
                <Icon.Trash size={16} />
              </IconButton>
            </div>
          ))}
        </Card>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Worlds
// ---------------------------------------------------------------------------

function WorldsTab({ inst }: { inst: Instance }) {
  const [worlds, setWorlds] = useState<WorldInfo[] | null>(null);
  const [servers, setServers] = useState<SavedServer[] | null>(null);
  const [address, setAddress] = useState("");
  const running = useStore(app, (s) => Boolean(s.running[inst.id]) || s.tasks.some((t) => t.id === `launch-${inst.id}`));
  const support = quickPlaySupport(inst.gameVersion);
  useEffect(() => {
    listWorlds(inst).then(setWorlds).catch(() => setWorlds([]));
    listServers(inst).then(setServers).catch(() => setServers([]));
  }, [inst.id, running]);

  const join = (target: string, name?: string) => {
    if (!validServerAddress(target)) {
      toast({ kind: "warning", title: "That isn't a server address", message: "Use host or host:port, like play.example.net or 203.0.113.5:25570." });
      return;
    }
    void play(inst.id, { quickPlay: { kind: "server", address: target.trim() }, quickPlayName: name });
  };

  return (
    <div className="space-y-7">
      <section>
        <SectionTitle
          action={
            <Button size="sm" icon={<Icon.Folder size={14} />} onClick={() => void openContentFolder(inst, "saves")}>
              Open saves folder
            </Button>
          }
        >
          Worlds
        </SectionTitle>
        {!worlds ? (
          <Skeleton className="h-32 rounded-xl" />
        ) : worlds.length === 0 ? (
          <Card>
            <EmptyState icon={<Icon.Map size={24} />} title="No worlds yet">
              Worlds you create in this installation appear here, ready to open with one click.
            </EmptyState>
          </Card>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {worlds.map((w) => (
              <WorldCard key={w.path} inst={inst} world={w} canPlay={support.world} busy={running} />
            ))}
          </div>
        )}
        {worlds && worlds.length > 0 && !support.world && (
          <p className="text-[12px] text-faint mt-2">Opening a world straight from MOTION needs Minecraft 1.20 or newer.</p>
        )}
      </section>

      <section>
        <SectionTitle>Servers</SectionTitle>
        <div className="flex items-center gap-2 mb-3">
          <TextInput value={address} onChange={setAddress} placeholder="Server address, e.g. play.example.net" icon={<Icon.Globe size={16} />} className="flex-1 max-w-md" mono onEnter={() => join(address)} />
          <Button variant="primary" icon={<Icon.Play size={14} />} disabled={!address.trim() || running} onClick={() => join(address)}>
            Join
          </Button>
        </div>
        {!servers ? (
          <Skeleton className="h-20 rounded-xl" />
        ) : servers.length === 0 ? (
          <p className="text-[13px] text-muted">Servers you add in Minecraft's Multiplayer screen show up here, so you can join them straight from MOTION.</p>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {servers.map((sv, i) => (
              <Card key={`${sv.address}-${i}`} className="p-3.5 flex items-center gap-3">
                {sv.icon ? (
                  <img src={sv.icon} alt="" className="h-11 w-11 rounded-lg pixelated shrink-0" draggable={false} />
                ) : (
                  <div className="h-11 w-11 rounded-lg bg-raised border border-line grid place-items-center text-faint shrink-0">
                    <Icon.Globe size={18} />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="font-semibold truncate">{sv.name}</div>
                  <div className="text-[12px] text-faint font-mono truncate selectable">{sv.address}</div>
                </div>
                <Button size="sm" variant="outline" icon={<Icon.Play size={13} />} disabled={running} onClick={() => join(sv.address, sv.name)}>
                  Join
                </Button>
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

const MODE_LABEL: Record<string, string> = { survival: "Survival", creative: "Creative", adventure: "Adventure", spectator: "Spectator" };

function WorldCard({ inst, world, canPlay, busy }: { inst: Instance; world: WorldInfo; canPlay: boolean; busy: boolean }) {
  const [d, setD] = useState<Awaited<ReturnType<typeof worldDetails>> | null>(null);
  useEffect(() => {
    let live = true;
    void worldDetails(world.path).then((x) => live && setD(x));
    return () => {
      live = false;
    };
  }, [world.path, world.modifiedMs]);
  const name = d?.levelName ?? world.name;
  const facts = [
    d?.hardcore ? "Hardcore" : d?.gameMode ? MODE_LABEL[d.gameMode] : null,
    d?.cheats ? "Cheats" : null,
    d?.versionName,
    `played ${timeAgo(d?.lastPlayed ?? world.modifiedMs)}`,
  ].filter(Boolean);
  return (
    <Card className="p-3.5 flex items-center gap-3">
      {d?.icon ? (
        <img src={d.icon} alt="" className="h-11 w-11 rounded-lg shrink-0 object-cover" draggable={false} />
      ) : (
        <div className="h-11 w-11 rounded-lg bg-raised border border-line grid place-items-center text-muted shrink-0">
          <Icon.Map size={20} />
        </div>
      )}
      <div className="min-w-0 flex-1">
        <div className="font-semibold truncate" title={name !== world.name ? `Folder: ${world.name}` : undefined}>
          {name}
        </div>
        <div className={cx("text-[12px] truncate", d?.hardcore ? "text-danger" : "text-faint")}>{facts.join(" · ")}</div>
      </div>
      <IconButton label="Open folder" onClick={() => void native.openPath(world.path)}>
        <Icon.Folder size={16} />
      </IconButton>
      {canPlay && (
        <Button size="sm" variant="outline" icon={<Icon.Play size={13} />} disabled={busy} onClick={() => void play(inst.id, { quickPlay: { kind: "world", folder: world.name }, quickPlayName: name })}>
          Play
        </Button>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Logs
// ---------------------------------------------------------------------------

/** "What went wrong": the Crash Doctor's findings for the last session, with one-click fixes. */
function CrashDoctor({ inst }: { inst: Instance }) {
  const session = useStore(app, (s) => s.sessions[inst.id]);
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const [busy, setBusy] = useState<number | null>(null);
  const [hidden, setHidden] = useState<number | null>(null);
  if (running || !session || session.exitCode === 0 || session.exitCode === null || !session.crash || hidden === session.endedAt) return null;
  const found = session.crash;
  return (
    <div className="mb-3 rounded-xl border border-danger/30 bg-danger/[0.06] p-4 animate-in">
      <div className="flex items-start gap-3">
        <span className="text-danger mt-0.5">
          <Icon.Alert size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="font-semibold">
            {found.length ? "What went wrong" : `The game crashed (exit code ${session.exitCode})`}
            <span className="ml-2 text-[12px] font-normal text-faint">{new Date(session.endedAt).toLocaleTimeString()}</span>
          </div>
          {found.length === 0 && (
            <p className="text-[13px] text-muted mt-1">MOTION didn't recognise the cause. The errors in red below usually name the mod involved; recently added or updated mods are the most common culprits.</p>
          )}
          <div className="mt-2 space-y-2.5">
            {found.map((d, i) => (
              <div key={d.id} className={cx("rounded-xl border border-line bg-panel/70 p-3", i > 0 && "opacity-90")}>
                <div className="flex items-start gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="text-[14px] font-semibold">{d.title}</div>
                    <p className="text-[13px] text-muted mt-0.5 selectable">{d.detail}</p>
                  </div>
                  {d.fix && (
                    <Button
                      size="sm"
                      variant={i === 0 ? "primary" : "secondary"}
                      loading={busy === i}
                      icon={<Icon.Wrench size={14} />}
                      onClick={async () => {
                        if (!d.fix) return;
                        setBusy(i);
                        try {
                          await applyCrashFix(inst.id, d.fix);
                        } finally {
                          setBusy(null);
                        }
                      }}
                    >
                      {fixLabel(d.fix)}
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
        <IconButton label="Dismiss" className="h-7 w-7 -mt-1 -mr-1" onClick={() => setHidden(session.endedAt)}>
          <Icon.Close size={14} />
        </IconButton>
      </div>
    </div>
  );
}

const LEVEL_COLORS: Record<string, string> = { ERROR: "text-danger", FATAL: "text-danger", WARN: "text-warn", DEBUG: "text-faint", TRACE: "text-faint" };

function LogsTab({ inst }: { inst: Instance }) {
  const live = useStore(app, (s) => s.logs[inst.id]);
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const [saved, setSaved] = useState<string | null>(null);
  const [level, setLevel] = useState<"all" | "warn" | "error">("all");
  const [query, setQuery] = useState("");
  const [follow, setFollow] = useState(true);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (live?.length) return;
    // Nothing from this session: show the last game's log file.
    native
      .readTail(path(P().dataDir, "logs", "game", `${inst.id}.log`), 512 * 1024)
      .then(setSaved)
      .catch(() => setSaved(null));
  }, [inst.id, live?.length]);

  const lines = useMemo(() => {
    const src = live?.length ? live : (saved ?? "").split(/\r?\n/).filter(Boolean).map((text) => ({ t: 0, level: /\bERROR\b|Exception|FATAL/.test(text) ? "ERROR" : /\bWARN\b/.test(text) ? "WARN" : "INFO", text }));
    const q = query.trim().toLowerCase();
    return src.filter((l) => (level === "all" || (level === "warn" ? ["WARN", "ERROR", "FATAL"].includes(l.level) : ["ERROR", "FATAL"].includes(l.level))) && (!q || l.text.toLowerCase().includes(q)));
  }, [live, saved, level, query]);

  useEffect(() => {
    if (follow && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [lines.length, follow]);

  return (
    <div>
      <div className="flex items-center gap-2 mb-3">
        <TextInput value={query} onChange={setQuery} placeholder="Filter log" icon={<Icon.Search size={16} />} className="w-64" />
        <Select
          value={level}
          onChange={setLevel}
          options={[
            { value: "all", label: "All messages" },
            { value: "warn", label: "Warnings and errors" },
            { value: "error", label: "Errors only" },
          ]}
          className="w-52"
        />
        <div className="flex-1" />
        <span className="text-[13px] text-muted flex items-center gap-2">
          {running ? <span className="text-accent">● Live</span> : live?.length ? "Last session" : saved ? "Last session (from disk)" : ""}
        </span>
        <Toggle checked={follow} onChange={setFollow} />
        <span className="text-[13px] text-muted">Follow</span>
        <IconButton
          label="Copy log"
          className="h-9 w-9 border border-line"
          onClick={() => void navigator.clipboard.writeText(lines.map((l) => l.text).join("\n")).then(() => toast({ kind: "success", title: "Log copied" }))}
        >
          <Icon.Copy size={16} />
        </IconButton>
        <IconButton label="Open logs folder" className="h-9 w-9 border border-line" onClick={() => void openContentFolder(inst, "logs")}>
          <Icon.Folder size={16} />
        </IconButton>
      </div>
      <CrashDoctor inst={inst} />
      <div ref={box} className="h-[460px] overflow-auto rounded-xl border border-line bg-[#070908] p-4 font-mono text-[12px] leading-[1.6] selectable">
        {lines.length === 0 ? (
          <div className="text-faint">{running ? "Waiting for output…" : "No log yet. Press Play to start the game; its output appears here."}</div>
        ) : (
          lines.map((l, i) => (
            <div key={i} className={cx("whitespace-pre-wrap break-all", LEVEL_COLORS[l.level] ?? "text-fg/85")}>
              {l.t ? <span className="text-faint">{new Date(l.t).toLocaleTimeString()} </span> : null}
              {l.text}
            </div>
          ))
        )}
      </div>
      <p className="text-[12px] text-faint mt-2">Access tokens are removed from logs before they are shown or saved.</p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

function JavaPicker({ value, onChange, required }: { value?: string; onChange: (v: string | undefined) => void; required?: number }) {
  const [found, setFound] = useState<string[] | null>(null);
  const [info, setInfo] = useState<JavaInfo | null>(null);
  const [probeError, setProbeError] = useState<string | null>(null);
  useEffect(() => {
    setInfo(null);
    setProbeError(null);
    if (!value) return;
    native
      .javaProbe(value)
      .then(setInfo)
      .catch((e) => setProbeError(errorMessage(e)));
  }, [value]);
  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <Select
          className="flex-1"
          value={value ?? ""}
          onChange={(v) => onChange(v || undefined)}
          options={[
            { value: "", label: "Automatic — the Java version Minecraft asks for (recommended)" },
            ...[...new Set([...(found ?? []), ...(value ? [value] : [])])].map((p) => ({ value: p, label: p })),
          ]}
        />
        <Button onClick={() => void native.findJavas().then(setFound)} icon={<Icon.Search size={15} />}>
          Find
        </Button>
        <Button
          onClick={() =>
            void native.pickFiles({ title: "Choose java.exe or javaw.exe", filters: [["Java", ["exe"]]] }).then(([p]) => {
              if (p) onChange(p);
            })
          }
        >
          Browse…
        </Button>
      </div>
      {found && found.length === 0 && <div className="text-[13px] text-muted">No other Java installations were found.</div>}
      {info && (
        <div className={cx("text-[13px]", required && info.major < required ? "text-danger" : "text-muted")}>
          Java {info.version} ({info.is64bit ? "64-bit" : "32-bit"})
          {required && info.major < required ? ` — this Minecraft version needs Java ${required}.` : ""}
        </div>
      )}
      {probeError && <div className="text-[13px] text-danger">{probeError}</div>}
    </div>
  );
}

export function MemoryControl({ value, onChange, modded, modCount }: { value: { minMb: number; maxMb: number } | undefined; onChange: (v: { minMb: number; maxMb: number } | undefined) => void; modded: boolean; modCount: number }) {
  const system = useStore(app, (s) => s.system);
  const totalMb = system ? Math.floor(system.totalMemory / 1_048_576) : 16384;
  const advice = recommendMemory(totalMb, { modded, modCount });
  const max = Math.max(2048, Math.floor((totalMb * 0.85) / 512) * 512);
  const current = value?.maxMb ?? advice.recommendedMb;
  return (
    <div className="space-y-3">
      <Toggle
        checked={Boolean(value)}
        onChange={(on) => onChange(on ? { minMb: 512, maxMb: advice.recommendedMb } : undefined)}
        label="Custom memory for this installation"
        description={value ? undefined : `Using the launcher default. Suggested here: ${gb(advice.recommendedMb)}.`}
      />
      {value && (
        <>
          <Slider value={current} min={1024} max={max} step={512} onChange={(v) => onChange({ minMb: Math.min(value.minMb, v), maxMb: v })} format={gb} />
          <div className={cx("text-[13px]", current > advice.maxSafeMb ? "text-warn" : "text-muted")}>
            {current > advice.maxSafeMb
              ? `More than ${gb(advice.maxSafeMb)} may leave too little memory for Windows on this PC (${gb(totalMb)} total).`
              : `${advice.reason} This PC has ${gb(totalMb)}.`}
          </div>
        </>
      )}
    </div>
  );
}

/** Java major version a Minecraft version needs: from its downloaded metadata, else a safe estimate. */
function useRequiredJava(gameVersion: string): number {
  const [major, setMajor] = useState(() => guessJavaMajor(gameVersion));
  useEffect(() => {
    setMajor(guessJavaMajor(gameVersion));
    P()
      .readText(path(P().dataDir, "versions", gameVersion, `${gameVersion}.json`))
      .then((t) => {
        const v = t ? (JSON.parse(t) as { javaVersion?: { majorVersion?: number } }) : null;
        if (v?.javaVersion?.majorVersion) setMajor(v.javaVersion.majorVersion);
      })
      .catch(() => undefined);
  }, [gameVersion]);
  return major;
}

function SettingsTab({ inst }: { inst: Instance }) {
  const [draft, setDraft] = useState<Instance>(inst);
  const [jvmText, setJvmText] = useState((inst.jvmArgs ?? []).join(" "));
  const [gameText, setGameText] = useState((inst.gameArgs ?? []).join(" "));
  const [saving, setSaving] = useState(false);
  const mods = useContent(inst, "mods");
  useEffect(() => {
    setDraft(inst);
    setJvmText((inst.jvmArgs ?? []).join(" "));
    setGameText((inst.gameArgs ?? []).join(" "));
  }, [inst.id]);

  const javaMajor = useRequiredJava(draft.gameVersion);
  const jvmIssues = validateJvmArgs(splitArgs(jvmText), javaMajor);
  const dirty =
    JSON.stringify({ ...draft, jvmArgs: splitArgs(jvmText), gameArgs: splitArgs(gameText) }) !==
    JSON.stringify({ ...inst, jvmArgs: inst.jvmArgs ?? [], gameArgs: inst.gameArgs ?? [] });
  const loaderChanged = draft.loader !== inst.loader;
  const enabledMods = (mods.items ?? []).filter((m) => m.enabled).length;

  async function save() {
    if (loaderChanged && enabledMods > 0) {
      const ok = await confirm({
        title: `Switch to ${LOADERS.find((l) => l.id === draft.loader)?.name}?`,
        message: `This installation has ${enabledMods} mod${enabledMods === 1 ? "" : "s"} for ${loaderLabel(inst)}. Mods usually only work with the loader they were made for; MOTION will flag incompatible ones.`,
        confirmLabel: "Switch loader",
      });
      if (!ok) return;
    }
    setSaving(true);
    try {
      await updateInstance(inst.id, { ...draft, jvmArgs: splitArgs(jvmText), gameArgs: splitArgs(gameText) });
      toast({ kind: "success", title: "Settings saved" });
    } catch (e) {
      reportError("Couldn't save", e);
    } finally {
      setSaving(false);
    }
  }

  const set = (patch: Partial<Instance>) => setDraft((d) => ({ ...d, ...patch }));

  return (
    <div className="grid grid-cols-[1fr_300px] gap-6 items-start">
      <div className="space-y-5">
        <Card className="p-5 space-y-4">
          <h3 className="font-semibold">General</h3>
          <Field label="Name">
            <TextInput value={draft.name} onChange={(v) => set({ name: v })} />
          </Field>
          <VersionLoaderPicker
            gameVersion={draft.gameVersion}
            loader={draft.loader}
            loaderVersion={draft.loaderVersion}
            onChange={(v) => set({ gameVersion: v.gameVersion, loader: v.loader as LoaderId, loaderVersion: v.loader === "vanilla" ? undefined : v.loaderVersion })}
          />
          {draft.gameVersion !== inst.gameVersion && (
            <Callout tone="warn">Opening a world in an older Minecraft version than it was last played in can corrupt it. Back up worlds before downgrading.</Callout>
          )}
        </Card>

        <Card className="p-5 space-y-4">
          <h3 className="font-semibold">Game folder</h3>
          <div className="flex items-center gap-2">
            <div className="flex-1 min-w-0 h-9 px-3 rounded-[10px] bg-panel-2 border border-line flex items-center text-[13px] text-muted truncate selectable">{draft.gameDir ?? instanceGameDir({ ...draft, gameDir: undefined })}</div>
            <Button
              onClick={() =>
                void native.pickGameDir().then((dir) => {
                  if (dir) set({ gameDir: dir });
                })
              }
            >
              Change…
            </Button>
            {draft.gameDir && <Button variant="ghost" onClick={() => set({ gameDir: undefined })}>Use default</Button>}
          </div>
          <p className="text-[13px] text-faint">Mods, packs, worlds and options live here. Changing it doesn't move existing files.</p>
        </Card>

        <Card className="p-5 space-y-4">
          <h3 className="font-semibold">Java and memory</h3>
          <Field label="Java runtime">
            <JavaPicker value={draft.javaPath} onChange={(v) => set({ javaPath: v })} required={javaMajor} />
          </Field>
          <MemoryControl value={draft.memory} onChange={(memory) => set({ memory })} modded={draft.loader !== "vanilla"} modCount={enabledMods} />
          <Field label="Garbage collector preset">
            <Select
              value={draft.jvmPreset ?? ""}
              onChange={(v) => set({ jvmPreset: (v || undefined) as Instance["jvmPreset"] })}
              options={[{ value: "", label: "Launcher default" }, ...JVM_PRESETS.map((p) => ({ value: p.id, label: `${p.name} — Java ${p.minJava}+` }))]}
            />
          </Field>
          <Field label="Extra JVM arguments" hint="Advanced. Memory is set above; MOTION builds the classpath itself.">
            <TextInput value={jvmText} onChange={setJvmText} placeholder="-XX:+AlwaysPreTouch" mono />
          </Field>
          {jvmIssues.map((i) => (
            <div key={i.arg + i.message} className={cx("text-[13px]", i.severity === "error" ? "text-danger" : "text-warn")}>
              {i.arg}: {i.message}
            </div>
          ))}
        </Card>

        <Card className="p-5 space-y-4">
          <h3 className="font-semibold">Window</h3>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Width">
              <TextInput value={String(draft.resolution?.width ?? "")} onChange={(v) => set({ resolution: v ? { width: Number(v) || 0, height: draft.resolution?.height ?? 720 } : undefined })} placeholder="Default" />
            </Field>
            <Field label="Height">
              <TextInput value={String(draft.resolution?.height ?? "")} onChange={(v) => set({ resolution: v ? { width: draft.resolution?.width ?? 1280, height: Number(v) || 0 } : undefined })} placeholder="Default" />
            </Field>
          </div>
          <Toggle checked={draft.fullscreen ?? false} onChange={(v) => set({ fullscreen: v })} label="Start in fullscreen" />
          <Field label="Extra game arguments" hint="Passed to Minecraft itself, e.g. --quickPlaySingleplayer.">
            <TextInput value={gameText} onChange={setGameText} mono />
          </Field>
        </Card>

        {dirty && (
          <div className="flex items-center gap-2 sticky bottom-0 py-3 px-4 -mx-1 rounded-xl border border-line-strong bg-raised shadow-xl shadow-black/30 animate-in">
            <span className="text-[13px] text-muted flex-1">You have unsaved changes.</span>
            <Button
              variant="ghost"
              onClick={() => {
                setDraft(inst);
                setJvmText((inst.jvmArgs ?? []).join(" "));
                setGameText((inst.gameArgs ?? []).join(" "));
              }}
            >
              Discard
            </Button>
            <Button variant="primary" onClick={() => void save()} loading={saving} disabled={jvmIssues.some((i) => i.severity === "error")}>
              Save changes
            </Button>
          </div>
        )}
      </div>

      <div className="space-y-4">
        <Card className="p-5">
          <h3 className="font-semibold mb-1">Repair</h3>
          <p className="text-[13px] text-muted mb-3">Re-check every game file, library, asset and the Java runtime against Mojang's checksums and download anything damaged.</p>
          <Button icon={<Icon.Wrench size={15} />} disabled={isRunning(inst.id)} onClick={() => void play(inst.id, { repair: true })}>
            Repair and launch
          </Button>
        </Card>
        <Card className="p-5">
          <h3 className="font-semibold mb-1">Folders</h3>
          <div className="flex flex-col gap-1.5 mt-2">
            {(["", "mods", "config", "resourcepacks", "shaderpacks", "saves", "screenshots", "logs"] as const).map((f) => (
              <button key={f} type="button" className="flex items-center gap-2 text-[13px] text-muted hover:text-fg cursor-pointer" onClick={() => void openContentFolder(inst, f as ContentKind | "")}>
                <Icon.Folder size={14} /> {f || "Game folder"}
              </button>
            ))}
          </div>
        </Card>
        <Card className="p-5 border-danger/25">
          <h3 className="font-semibold mb-1">Delete installation</h3>
          <p className="text-[13px] text-muted mb-3">Moves the folder (with worlds and mods) to the Recycle Bin.</p>
          <Button variant="danger" icon={<Icon.Trash size={15} />} disabled={isRunning(inst.id)} onClick={() => void confirmDelete(inst).then((ok) => ok && navigate({ page: "library" }))}>
            Delete…
          </Button>
        </Card>
        <InstallationInfo inst={inst} />
      </div>
    </div>
  );
}

function InstallationInfo({ inst }: { inst: Instance }) {
  const [size, setSize] = useState<number | null>(null);
  useEffect(() => {
    let total = 0;
    void (async () => {
      for (const k of ["mods", "resourcepacks", "shaderpacks"] as ContentKind[]) {
        for (const e of await P().list(contentDir(inst, k)).catch(() => [])) total += e.size;
      }
      setSize(total);
    })();
  }, [inst.id]);
  return (
    <Card className="p-5 text-[13px] space-y-1.5">
      <h3 className="font-semibold text-[14px] mb-2">About</h3>
      <div className="flex justify-between">
        <span className="text-muted">Created</span>
        <span>{new Date(inst.createdAt).toLocaleDateString()}</span>
      </div>
      <div className="flex justify-between">
        <span className="text-muted">Content size</span>
        <span>{size === null ? <Spinner size={12} /> : formatBytes(size)}</span>
      </div>
      {inst.lastLaunch && (
        <div className="flex justify-between">
          <span className="text-muted">Last startup</span>
          <span>{inst.lastLaunch.startupMs ? `${(inst.lastLaunch.startupMs / 1000).toFixed(1)} s` : "—"}</span>
        </div>
      )}
      <div className="flex justify-between">
        <span className="text-muted">Folder id</span>
        <span className="font-mono text-[12px] selectable">{inst.id}</span>
      </div>
    </Card>
  );
}

/** Small modal used by Browse/Project to choose a target installation. */
export function PickInstanceModal({ open, onClose, onPick, filter }: { open: boolean; onClose: () => void; onPick: (inst: Instance) => void; filter?: (i: Instance) => boolean }) {
  const instances = useStore(app, (s) => s.instances);
  const list = instances.filter((i) => !filter || filter(i));
  return (
    <Modal open={open} onClose={onClose} title="Install to…" width={480}>
      {list.length === 0 ? (
        <div className="text-muted py-6 text-center">No compatible installation. Create one with a matching version and loader first.</div>
      ) : (
        <div className="space-y-1.5 pb-1">
          {list.map((i) => (
            <button key={i.id} type="button" className="w-full flex items-center gap-3 p-2.5 rounded-xl hover:bg-raised text-left cursor-pointer" onClick={() => onPick(getInstance(i.id) ?? i)}>
              <InstanceIcon inst={i} size={38} />
              <div className="min-w-0">
                <div className="font-semibold truncate">{i.name}</div>
                <div className="text-[12px] text-muted">
                  {i.gameVersion} · {loaderLabel(i)}
                </div>
              </div>
            </button>
          ))}
        </div>
      )}
    </Modal>
  );
}
