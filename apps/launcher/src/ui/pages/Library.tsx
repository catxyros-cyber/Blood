import { useMemo, useState } from "react";
import type { Instance } from "../../core/instances/model";
import { app, navigate, reportError, toast, updateSettings } from "../../services/app";
import { importModpackFile, openContentFolder } from "../../services/content";
import { formatPlaytime, isRunning, play } from "../../services/game";
import { deleteInstance, duplicateInstance } from "../../services/instances";
import { useStore } from "../../services/store";
import { Badge, Button, Card, confirm, cx, EmptyState, IconButton, Menu, PageHeader, Segmented, TextInput, timeAgo } from "../components";
import { Icon } from "../icons";
import { InstanceIcon, loaderLabel, NewInstanceModal, RenameModal } from "../instanceParts";
import { PlayButton } from "./Home";

type Sort = "recent" | "name" | "version";

export async function confirmDelete(inst: Instance) {
  const ok = await confirm({
    title: `Delete ${inst.name}?`,
    message: inst.gameDir ? (
      <>MOTION forgets this installation. Its custom game folder ({inst.gameDir}) is left untouched.</>
    ) : (
      <>The installation folder — including its worlds, mods and screenshots — is moved to the Recycle Bin, so you can still restore it.</>
    ),
    confirmLabel: "Delete installation",
    danger: true,
  });
  if (!ok) return false;
  try {
    await deleteInstance(inst.id);
    toast({ kind: "success", title: `${inst.name} moved to the Recycle Bin` });
    return true;
  } catch (e) {
    reportError("Couldn't delete the installation", e);
    return false;
  }
}

export function instanceMenu(inst: Instance, opts: { onRename: () => void }) {
  return [
    { label: "Play", icon: <Icon.Play size={15} />, onClick: () => void play(inst.id), disabled: isRunning(inst.id) },
    { label: "Open", icon: <Icon.Library size={15} />, onClick: () => navigate({ page: "instance", id: inst.id }) },
    { label: "Optimize", icon: <Icon.Gauge size={15} />, onClick: () => navigate({ page: "performance", instanceId: inst.id }) },
    "divider" as const,
    { label: "Rename", icon: <Icon.Edit size={15} />, onClick: opts.onRename },
    {
      label: "Duplicate",
      icon: <Icon.Copy size={15} />,
      onClick: () =>
        void duplicateInstance(inst.id)
          .then((c) => toast({ kind: "success", title: `Created ${c.name}`, message: "Mods, configs, packs and worlds were copied." }))
          .catch((e) => reportError("Couldn't duplicate", e)),
    },
    { label: "Repair files", icon: <Icon.Wrench size={15} />, onClick: () => void play(inst.id, { repair: true }), disabled: isRunning(inst.id) },
    { label: "Open folder", icon: <Icon.Folder size={15} />, onClick: () => void openContentFolder(inst, "").catch((e) => reportError("Couldn't open the folder", e)) },
    "divider" as const,
    { label: "Delete", icon: <Icon.Trash size={15} />, danger: true, onClick: () => void confirmDelete(inst), disabled: isRunning(inst.id) },
  ];
}

function InstanceCard({ inst, selected }: { inst: Instance; selected: boolean }) {
  const running = useStore(app, (s) => Boolean(s.running[inst.id]));
  const [renaming, setRenaming] = useState(false);
  return (
    <Card
      className={cx("p-4 group relative", selected && "border-accent/40")}
      onClick={() => navigate({ page: "instance", id: inst.id })}
    >
      <div className="flex items-start gap-3.5">
        <InstanceIcon inst={inst} size={54} />
        <div className="min-w-0 flex-1">
          <div className="font-semibold truncate text-[15px]">{inst.name}</div>
          <div className="text-[12.5px] text-muted truncate mt-0.5">
            {inst.gameVersion} · {loaderLabel(inst)}
          </div>
          <div className="text-[12px] text-faint mt-1">{running ? <span className="text-accent">● Running</span> : inst.lastPlayedAt ? `Played ${timeAgo(inst.lastPlayedAt)}` : "Not played yet"}</div>
        </div>
        <Menu
          trigger={
            <IconButton label="More" className="opacity-60 group-hover:opacity-100">
              <Icon.More size={18} />
            </IconButton>
          }
          items={instanceMenu(inst, { onRename: () => setRenaming(true) })}
        />
      </div>
      <div className="flex items-center justify-between mt-4">
        <div className="flex gap-1.5 flex-wrap">
          {inst.modpack && <Badge tone="info">{inst.modpack.name}</Badge>}
          {selected && <Badge tone="accent">Selected</Badge>}
          {inst.totalPlayMs ? <span className="text-[11.5px] text-faint self-center">{formatPlaytime(inst.totalPlayMs)}</span> : null}
        </div>
        <div onClick={(e) => e.stopPropagation()}>
          <PlayButton inst={inst} size="md" />
        </div>
      </div>
      <RenameModal inst={inst} open={renaming} onClose={() => setRenaming(false)} />
    </Card>
  );
}

export function LibraryPage() {
  const instances = useStore(app, (s) => s.instances);
  const selectedId = useStore(app, (s) => s.settings.selectedInstanceId);
  const [creating, setCreating] = useState(false);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<Sort>("recent");

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = instances.filter((i) => !q || i.name.toLowerCase().includes(q) || i.gameVersion.includes(q) || i.loader.includes(q));
    return [...list].sort((a, b) => {
      if (sort === "name") return a.name.localeCompare(b.name);
      if (sort === "version") return b.gameVersion.localeCompare(a.gameVersion, undefined, { numeric: true });
      return (b.lastPlayedAt ?? b.createdAt) - (a.lastPlayedAt ?? a.createdAt);
    });
  }, [instances, query, sort]);

  return (
    <div className="animate-in">
      <PageHeader
        title="Library"
        subtitle={`${instances.length} installation${instances.length === 1 ? "" : "s"}. Each has its own mods, packs, worlds and settings.`}
        actions={
          <>
            <Button icon={<Icon.Package size={16} />} onClick={() => void importModpackFile()}>
              Import modpack
            </Button>
            <Button variant="primary" icon={<Icon.Plus size={16} />} onClick={() => setCreating(true)}>
              New installation
            </Button>
          </>
        }
      />
      {instances.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Icon.Cube size={24} />}
            title="No installations yet"
            action={
              <div className="flex gap-2 justify-center">
                <Button variant="primary" icon={<Icon.Plus size={16} />} onClick={() => setCreating(true)}>
                  New installation
                </Button>
                <Button icon={<Icon.Compass size={16} />} onClick={() => navigate({ page: "browse", type: "modpack" })}>
                  Browse modpacks
                </Button>
              </div>
            }
          >
            Create a vanilla or modded installation, or install a modpack from Modrinth.
          </EmptyState>
        </Card>
      ) : (
        <>
          <div className="flex items-center gap-3 mb-5">
            <TextInput value={query} onChange={setQuery} placeholder="Search installations" icon={<Icon.Search size={16} />} className="w-80" />
            <div className="flex-1" />
            <Segmented
              value={sort}
              onChange={setSort}
              options={[
                { value: "recent", label: "Recent" },
                { value: "name", label: "Name" },
                { value: "version", label: "Version" },
              ]}
            />
          </div>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(330px,1fr))] gap-4">
            {shown.map((i) => (
              <div key={i.id} onDoubleClick={() => updateSettings({ selectedInstanceId: i.id })}>
                <InstanceCard inst={i} selected={i.id === (selectedId ?? instances[0]?.id)} />
              </div>
            ))}
          </div>
          {shown.length === 0 && <div className="text-muted text-center py-12">No installations match “{query}”.</div>}
        </>
      )}
      <NewInstanceModal open={creating} onClose={() => setCreating(false)} />
    </div>
  );
}
