import { useEffect, useMemo, useState } from "react";
import type { Instance } from "../../core/instances/model";
import { modrinthLoadersFor } from "../../core/loaders/loaders";
import { projectUrl } from "../../core/modrinth/api";
import { compatibleVersions } from "../../core/modrinth/resolve";
import type { Project, TeamMember, Version } from "../../core/modrinth/types";
import { app, errorMessage, navigate } from "../../services/app";
import { installedProjects, modrinth } from "../../services/content";
import { native } from "../../services/native";
import { useStore } from "../../services/store";
import { Badge, Button, Callout, Card, cx, EmptyState, formatBytes, formatCount, Modal, Select, Skeleton, Spinner, Tabs, timeAgo } from "../components";
import { useContentVersion } from "../contentEvents";
import { Icon } from "../icons";
import { installModpack, installProject, useInstalling } from "../installFlow";
import { loaderLabel } from "../instanceParts";
import { Markdown } from "../markdown";

type Tab = "description" | "gallery" | "versions";

const SIDE: Record<string, string> = { required: "Required", optional: "Optional", unsupported: "Not needed", unknown: "Unknown" };

export function ProjectPage({ id, instanceId }: { id: string; instanceId?: string }) {
  const instances = useStore(app, (s) => s.instances);
  const selectedId = useStore(app, (s) => s.settings.selectedInstanceId);
  const contentVersion = useContentVersion();
  const [project, setProject] = useState<Project | null>(null);
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [versions, setVersions] = useState<Version[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("description");
  const [targetId, setTargetId] = useState(instanceId ?? selectedId ?? instances[0]?.id ?? "");
  const [installed, setInstalled] = useState(false);
  const [viewing, setViewing] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    modrinth()
      .project(id)
      .then((p) => {
        if (!alive) return;
        setProject(p);
        void modrinth().members(p.id).then((m) => alive && setMembers(m)).catch(() => undefined);
        void modrinth().versions(p.id).then((v) => alive && setVersions(v)).catch(() => alive && setVersions([]));
      })
      .catch((e) => alive && setError(errorMessage(e)));
    return () => {
      alive = false;
    };
  }, [id]);

  const isPack = project?.project_type === "modpack";
  const isMod = project?.project_type === "mod";
  const usable = instances.filter((i) => !isMod || i.loader !== "vanilla");
  const target: Instance | undefined = isPack ? undefined : usable.find((i) => i.id === targetId);
  const busy = useInstalling(isPack ? "new" : target?.id, project?.id ?? id);

  useEffect(() => {
    if (target && project) installedProjects(target).then((s) => setInstalled(s.has(project.id))).catch(() => setInstalled(false));
    else setInstalled(false);
  }, [target?.id, project?.id, contentVersion]);

  const compatible = useMemo(() => {
    if (!versions || !target || !project) return versions ?? [];
    return compatibleVersions(versions, { gameVersion: target.gameVersion, loaders: isMod ? modrinthLoadersFor(target.loader) : [] });
  }, [versions, target?.id, project?.id]);

  if (error) {
    return (
      <Card>
        <EmptyState title="Couldn't load this project" action={<Button onClick={() => navigate({ page: "browse" })}>Back to Browse</Button>}>
          {error}
        </EmptyState>
      </Card>
    );
  }
  if (!project) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
    );
  }

  const owner = members.find((m) => m.role === "Owner") ?? members[0];
  const noCompat = target && versions && compatible.length === 0;

  return (
    <div className="animate-in">
      <button type="button" className="flex items-center gap-1 text-muted hover:text-fg text-[13px] mb-4 cursor-pointer" onClick={() => navigate({ page: "browse", type: (["mod", "modpack", "resourcepack", "shader"].includes(project.project_type) ? project.project_type : "mod") as "mod", instanceId })}>
        <Icon.ChevronLeft size={16} /> Browse
      </button>
      <div className="flex gap-5 items-start mb-6">
        {project.icon_url ? (
          <img src={project.icon_url} alt="" className="h-24 w-24 rounded-xl object-cover bg-raised shrink-0" draggable={false} />
        ) : (
          <div className="h-24 w-24 rounded-xl bg-raised border border-line grid place-items-center text-faint shrink-0">
            <Icon.Package size={36} />
          </div>
        )}
        <div className="min-w-0 flex-1">
          <h1 className="font-display text-[24px] font-semibold tracking-tight">{project.title}</h1>
          <p className="text-muted mt-1 max-w-2xl">{project.description}</p>
          <div className="flex items-center gap-4 mt-3 text-[13px] text-muted flex-wrap">
            {owner && (
              <span className="flex items-center gap-1.5">
                {owner.user.avatar_url && <img src={owner.user.avatar_url} alt="" className="h-5 w-5 rounded-full" />} {owner.user.username}
              </span>
            )}
            <span className="flex items-center gap-1">
              <Icon.Download size={14} /> {formatCount(project.downloads)} downloads
            </span>
            <span className="flex items-center gap-1">
              <Icon.Heart size={14} /> {formatCount(project.followers)}
            </span>
            <span>Updated {timeAgo(project.updated)}</span>
          </div>
        </div>
        <Card className="p-4 w-[300px] shrink-0">
          {isPack ? (
            <>
              <div className="text-[13px] text-muted mb-3">Installs as a new installation with everything the pack needs.</div>
              <Button variant="primary" className="w-full" loading={busy} icon={<Icon.Download size={16} />} onClick={() => void installModpack(project.id)}>
                Install modpack
              </Button>
            </>
          ) : usable.length === 0 ? (
            <div className="text-[13px] text-muted">
              Create {isMod ? "a modded" : "an"} installation first.{" "}
              <button type="button" className="text-accent hover:underline cursor-pointer" onClick={() => navigate({ page: "library" })}>
                Go to Library
              </button>
            </div>
          ) : (
            <>
              <Select value={target?.id ?? ""} onChange={setTargetId} options={usable.map((i) => ({ value: i.id, label: `${i.name} (${i.gameVersion}, ${loaderLabel(i)})` }))} className="mb-3" />
              {installed ? (
                <Button className="w-full" disabled icon={<Icon.Check size={16} />}>
                  Installed
                </Button>
              ) : (
                <Button variant="primary" className="w-full" loading={busy || !versions} disabled={Boolean(noCompat)} icon={<Icon.Download size={16} />} onClick={() => target && void installProject(project.id, target)}>
                  {noCompat ? "No compatible version" : "Install"}
                </Button>
              )}
              {noCompat && <div className="text-[13px] text-warn mt-2">No version for Minecraft {target!.gameVersion}{isMod ? ` with ${loaderLabel(target!)}` : ""}.</div>}
              {compatible[0] && !installed && <div className="text-[12px] text-faint mt-2 truncate">Latest compatible: {compatible[0].version_number}</div>}
            </>
          )}
          <Button variant="ghost" size="sm" className="w-full mt-2" icon={<Icon.External size={14} />} onClick={() => void native.openUrl(projectUrl(project.project_type, project.slug))}>
            View on Modrinth
          </Button>
        </Card>
      </div>

      {isMod && project.client_side === "unsupported" && (
        <div className="mb-4">
          <Callout tone="warn" title="Server-side mod">This mod only runs on servers. Installing it in a client installation does nothing or can cause crashes.</Callout>
        </div>
      )}

      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { value: "description", label: "Description" },
          { value: "gallery", label: "Gallery", count: project.gallery.length },
          { value: "versions", label: "Versions", count: project.versions.length },
        ]}
      />
      <div className="grid grid-cols-[1fr_280px] gap-6 mt-5 items-start">
        <div className="min-w-0">
          {tab === "description" && (
            <Card className="p-6">
              <Markdown source={project.body} />
            </Card>
          )}
          {tab === "gallery" &&
            (project.gallery.length === 0 ? (
              <Card>
                <EmptyState icon={<Icon.Image size={24} />} title="No images" />
              </Card>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                {project.gallery.map((g) => (
                  <button key={g.url} type="button" className="rounded-xl overflow-hidden border border-line bg-raised text-left cursor-pointer" onClick={() => setViewing(g.url)}>
                    <img src={g.url} alt={g.title ?? ""} className="w-full aspect-video object-cover" loading="lazy" />
                    {g.title && <div className="px-3 py-2 text-[13px] text-muted truncate">{g.title}</div>}
                  </button>
                ))}
              </div>
            ))}
          {tab === "versions" && <VersionList versions={versions} target={target} project={project} compatibleIds={new Set(compatible.map((v) => v.id))} />}
        </div>
        <aside className="space-y-4">
          <Card className="p-4 text-[13px] space-y-2">
            <div className="text-[13px] text-muted font-medium">Compatibility</div>
            <div className="flex justify-between">
              <span className="text-muted">Client</span>
              <span>{SIDE[project.client_side]}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted">Server</span>
              <span>{SIDE[project.server_side]}</span>
            </div>
            {project.loaders.length > 0 && (
              <div>
                <div className="text-muted mb-1">Loaders</div>
                <div className="flex flex-wrap gap-1">
                  {project.loaders.map((l) => (
                    <Badge key={l} className="capitalize">
                      {l}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
            <div>
              <div className="text-muted mb-1">Minecraft</div>
              <div className="text-fg/90 leading-relaxed">{summarizeVersions(project.game_versions)}</div>
            </div>
          </Card>
          <Card className="p-4 text-[13px] space-y-2">
            <div className="text-[13px] text-muted font-medium">Links</div>
            {[
              ["Source code", project.source_url],
              ["Issues", project.issues_url],
              ["Wiki", project.wiki_url],
              ["Discord", project.discord_url],
            ]
              .filter(([, u]) => u)
              .map(([label, u]) => (
                <button key={label} type="button" className="flex items-center gap-2 text-accent hover:underline cursor-pointer" onClick={() => void native.openUrl(u!)}>
                  <Icon.Link size={14} /> {label}
                </button>
              ))}
            {project.license && (
              <div className="text-muted pt-1">
                Licence: <span className="text-fg">{project.license.name || project.license.id}</span>
              </div>
            )}
          </Card>
          <div className="flex flex-wrap gap-1.5">
            {[...project.categories, ...(project.additional_categories ?? [])].map((c) => (
              <span key={c} className="px-2 py-1 rounded-lg bg-raised text-muted text-[12px] capitalize">
                {c.replace(/-/g, " ")}
              </span>
            ))}
          </div>
        </aside>
      </div>
      <Modal open={Boolean(viewing)} onClose={() => setViewing(null)} title={project.title} width={1000}>
        {viewing && <img src={viewing} alt="" className="w-full rounded-xl" />}
      </Modal>
    </div>
  );
}

/** "1.16.5 – 1.21.1" style summary of a long version list. */
function summarizeVersions(list: string[]): string {
  const releases = list.filter((v) => /^\d+\.\d+(\.\d+)?$/.test(v));
  if (releases.length === 0) return list.slice(-3).join(", ") || "—";
  if (releases.length <= 4) return releases.join(", ");
  return `${releases[0]} – ${releases[releases.length - 1]} (${releases.length} versions)`;
}

function VersionList({ versions, target, project, compatibleIds }: { versions: Version[] | null; target?: Instance; project: Project; compatibleIds: Set<string> }) {
  const [onlyCompatible, setOnlyCompatible] = useState(Boolean(target));
  const [expanded, setExpanded] = useState<string | null>(null);
  const [depTitles, setDepTitles] = useState<Record<string, string>>({});
  if (!versions) {
    return (
      <div className="py-10 grid place-items-center text-muted">
        <Spinner size={20} />
      </div>
    );
  }
  const list = onlyCompatible && target ? versions.filter((v) => compatibleIds.has(v.id)) : versions;
  const open = async (v: Version) => {
    setExpanded(expanded === v.id ? null : v.id);
    const ids = v.dependencies.map((d) => d.project_id).filter((x): x is string => Boolean(x) && !depTitles[x!]);
    if (ids.length) {
      const projects = await modrinth().projects(ids).catch(() => []);
      setDepTitles((t) => ({ ...t, ...Object.fromEntries(projects.map((p) => [p.id, p.title])) }));
    }
  };
  return (
    <div>
      {target && (
        <label className="flex items-center gap-2 text-[13px] text-muted mb-3 cursor-pointer">
          <input type="checkbox" checked={onlyCompatible} onChange={(e) => setOnlyCompatible(e.target.checked)} className="accent-[var(--bloom-accent)]" />
          Only versions for {target.name} ({target.gameVersion})
        </label>
      )}
      <Card className="divide-y divide-line overflow-hidden">
        {list.slice(0, 80).map((v) => {
          const file = v.files.find((f) => f.primary) ?? v.files[0];
          const ok = compatibleIds.has(v.id);
          return (
            <div key={v.id}>
              <div className="flex items-center gap-3 px-4 py-3">
                <Badge tone={v.version_type === "release" ? "accent" : v.version_type === "beta" ? "warn" : "danger"} className="capitalize w-16 justify-center">
                  {v.version_type}
                </Badge>
                <button type="button" className="min-w-0 flex-1 text-left cursor-pointer" onClick={() => void open(v)}>
                  <div className="font-medium truncate">{v.name || v.version_number}</div>
                  <div className="text-[12px] text-faint truncate">
                    {v.version_number} · {v.loaders.join(", ")} · {summarizeVersions(v.game_versions)} · {timeAgo(v.date_published)}
                    {file && ` · ${formatBytes(file.size)}`}
                  </div>
                </button>
                {target && project.project_type !== "modpack" ? (
                  <Button size="sm" variant={ok ? "outline" : "ghost"} icon={<Icon.Download size={14} />} onClick={() => void installProject(project.id, target, v)}>
                    {ok ? "Install" : "Install anyway"}
                  </Button>
                ) : project.project_type === "modpack" ? (
                  <Button size="sm" variant="outline" icon={<Icon.Download size={14} />} onClick={() => void installModpack(project.id, v)}>
                    Install
                  </Button>
                ) : null}
              </div>
              {expanded === v.id && (
                <div className="px-4 pb-4 -mt-1 space-y-3">
                  {v.dependencies.length > 0 && (
                    <div className="text-[13px]">
                      <div className="text-muted mb-1">Dependencies</div>
                      <div className="flex flex-wrap gap-1.5">
                        {v.dependencies.map((d, i) => (
                          <span
                            key={i}
                            className={cx(
                              "px-2 py-1 rounded-lg border text-[12px]",
                              d.dependency_type === "required" ? "border-accent/30 text-accent" : d.dependency_type === "incompatible" ? "border-danger/30 text-danger" : "border-line text-muted",
                            )}
                          >
                            {(d.project_id && depTitles[d.project_id]) || d.file_name || d.project_id || "Unknown"} · {d.dependency_type}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {v.changelog && (
                    <div className="rounded-xl bg-panel-2 border border-line p-4 max-h-72 overflow-auto text-[13px]">
                      <Markdown source={v.changelog} />
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
        {list.length === 0 && <div className="text-muted text-center py-8">No versions match.</div>}
      </Card>
    </div>
  );
}
