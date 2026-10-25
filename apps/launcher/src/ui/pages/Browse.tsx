import { useEffect, useMemo, useState } from "react";
import type { Instance } from "../../core/instances/model";
import { modrinthLoadersFor } from "../../core/loaders/loaders";
import type { SearchIndex } from "../../core/modrinth/api";
import type { CategoryTag, GameVersionTag, SearchHit } from "../../core/modrinth/types";
import { app, errorMessage, navigate } from "../../services/app";
import { installedProjects, modrinth } from "../../services/content";
import { native } from "../../services/native";
import { useStore } from "../../services/store";
import { Badge, Button, Callout, Card, cx, EmptyState, formatCount, PageHeader, Select, Skeleton, Tabs, TextInput, timeAgo, Toggle } from "../components";
import { useContentVersion } from "../contentEvents";
import { Icon } from "../icons";
import { installModpack, installProject, useInstalling } from "../installFlow";
import { InstanceIcon, loaderLabel } from "../instanceParts";

export type BrowseType = "mod" | "modpack" | "resourcepack" | "shader";

const TYPE_LABELS: Record<BrowseType, string> = { mod: "Mods", modpack: "Modpacks", resourcepack: "Resource packs", shader: "Shaders" };
const PAGE = 20;

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

let categoryCache: CategoryTag[] | null = null;
let versionCache: GameVersionTag[] | null = null;

function HitRow({ hit, type, target, installed }: { hit: SearchHit; type: BrowseType; target?: Instance; installed: boolean }) {
  const busy = useInstalling(type === "modpack" ? "new" : target?.id, hit.project_id);
  const compatible = !target || type === "modpack" || hit.versions.includes(target.gameVersion);
  return (
    <Card className="p-4 flex gap-4 group" onClick={() => navigate({ page: "project", id: hit.project_id, instanceId: target?.id })}>
      {hit.icon_url ? (
        <img src={hit.icon_url} alt="" className="h-16 w-16 rounded-xl object-cover bg-raised shrink-0" loading="lazy" draggable={false} />
      ) : (
        <div className="h-16 w-16 rounded-xl bg-raised border border-line grid place-items-center text-faint shrink-0">
          <Icon.Package size={26} />
        </div>
      )}
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2 min-w-0">
          <span className="font-semibold text-[15px] truncate group-hover:text-accent transition-colors">{hit.title}</span>
          <span className="text-[12.5px] text-faint truncate">by {hit.author}</span>
        </div>
        <p className="text-[13px] text-muted mt-0.5 line-clamp-2">{hit.description}</p>
        <div className="flex items-center gap-3 mt-2 text-[12px] text-faint flex-wrap">
          <span className="flex items-center gap-1">
            <Icon.Download size={13} /> {formatCount(hit.downloads)}
          </span>
          <span className="flex items-center gap-1">
            <Icon.Heart size={13} /> {formatCount(hit.follows)}
          </span>
          <span className="flex items-center gap-1">
            <Icon.Clock size={13} /> {timeAgo(hit.date_modified)}
          </span>
          {(hit.display_categories ?? hit.categories).slice(0, 4).map((c) => (
            <span key={c} className="px-1.5 py-0.5 rounded-md bg-raised text-muted capitalize">
              {c.replace(/-/g, " ")}
            </span>
          ))}
          {type === "mod" && hit.client_side === "unsupported" && <Badge tone="warn">Server only</Badge>}
        </div>
      </div>
      <div className="flex flex-col items-end justify-center gap-2 shrink-0" onClick={(e) => e.stopPropagation()}>
        {installed ? (
          <Badge tone="accent">
            <Icon.Check size={13} /> Installed
          </Badge>
        ) : type === "modpack" ? (
          <Button size="sm" variant="primary" loading={busy} icon={<Icon.Download size={14} />} onClick={() => void installModpack(hit.project_id)}>
            Install
          </Button>
        ) : target ? (
          <Button size="sm" variant={compatible ? "primary" : "outline"} loading={busy} disabled={!compatible} icon={<Icon.Download size={14} />} onClick={() => void installProject(hit.project_id, target)}>
            {compatible ? "Install" : "Not for " + target.gameVersion}
          </Button>
        ) : null}
      </div>
    </Card>
  );
}

export function BrowsePage({ type = "mod", instanceId }: { type?: BrowseType; instanceId?: string }) {
  const instances = useStore(app, (s) => s.instances);
  const selectedId = useStore(app, (s) => s.settings.selectedInstanceId);
  const contentVersion = useContentVersion();

  const defaultTarget = instanceId ?? (type !== "modpack" ? (selectedId ?? instances[0]?.id) : undefined) ?? "";
  const [targetId, setTargetId] = useState<string>(defaultTarget);
  const target = instances.find((i) => i.id === targetId);
  const [query, setQuery] = useState("");
  const q = useDebounced(query, 300);
  const [sort, setSort] = useState<SearchIndex>("relevance");
  const [gameVersion, setGameVersion] = useState("");
  const [loader, setLoader] = useState("");
  const [categories, setCategories] = useState<string[]>([]);
  const [clientOnly, setClientOnly] = useState(true);
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [allCategories, setAllCategories] = useState<CategoryTag[]>(categoryCache ?? []);
  const [versions, setVersions] = useState<GameVersionTag[]>(versionCache ?? []);
  const [installed, setInstalled] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!categoryCache) modrinth().categories().then((c) => setAllCategories((categoryCache = c))).catch(() => undefined);
    if (!versionCache) modrinth().gameVersions().then((v) => setVersions((versionCache = v))).catch(() => undefined);
  }, []);

  useEffect(() => setCategories([]), [type]);
  useEffect(() => {
    if (target && type !== "modpack") installedProjects(target).then(setInstalled).catch(() => setInstalled(new Set()));
    else setInstalled(new Set());
  }, [target?.id, type, contentVersion]);

  // Mods only make sense for modded installations.
  const usable = type === "mod" ? instances.filter((i) => i.loader !== "vanilla") : instances;
  const effectiveTarget = type === "modpack" ? undefined : target && usable.includes(target) ? target : undefined;

  const params = useMemo(() => {
    const gv = effectiveTarget?.gameVersion ?? (gameVersion || undefined);
    const loaders = type === "mod" || type === "modpack" ? (effectiveTarget ? modrinthLoadersFor(effectiveTarget.loader) : loader ? [loader] : undefined) : undefined;
    return { query: q || undefined, projectType: type, gameVersion: gv, loaders, categories, clientOnly: type === "mod" ? clientOnly : false, index: sort };
  }, [q, type, effectiveTarget?.id, gameVersion, loader, categories, clientOnly, sort]);

  useEffect(() => {
    let cancelled = false;
    setHits(null);
    setError(null);
    setOffset(0);
    modrinth()
      .search({ ...params, offset: 0, limit: PAGE })
      .then((r) => {
        if (cancelled) return;
        setHits(r.hits);
        setTotal(r.total_hits);
      })
      .catch((e) => !cancelled && setError(errorMessage(e)));
    return () => {
      cancelled = true;
    };
  }, [params]);

  async function more() {
    setLoadingMore(true);
    try {
      const next = offset + PAGE;
      const r = await modrinth().search({ ...params, offset: next, limit: PAGE });
      setHits((h) => [...(h ?? []), ...r.hits]);
      setOffset(next);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoadingMore(false);
    }
  }

  const projectType = type === "shader" ? "shader" : type;
  const catOptions = allCategories.filter((c) => c.project_type === projectType && c.header !== "loaders" && !["fabric", "forge", "neoforge", "quilt", "liteloader", "rift", "modloader"].includes(c.name));
  const releaseVersions = versions.filter((v) => v.version_type === "release").slice(0, 60);

  return (
    <div className="animate-in">
      <PageHeader title="Browse" subtitle={<>Mods, modpacks, resource packs and shaders from <button type="button" className="text-accent hover:underline cursor-pointer" onClick={() => void native.openUrl("https://modrinth.com")}>Modrinth</button>.</>} />
      <Tabs
        value={type}
        onChange={(t) => navigate({ page: "browse", type: t, instanceId: targetId || undefined })}
        tabs={(Object.keys(TYPE_LABELS) as BrowseType[]).map((t) => ({ value: t, label: TYPE_LABELS[t] }))}
      />
      <div className="grid grid-cols-[1fr_260px] gap-6 mt-5 items-start">
        <div className="min-w-0">
          <div className="flex gap-2 mb-4">
            <TextInput value={query} onChange={setQuery} placeholder={`Search ${TYPE_LABELS[type].toLowerCase()}`} icon={<Icon.Search size={16} />} className="flex-1" autoFocus />
            <Select
              value={sort}
              onChange={setSort}
              className="w-44"
              options={[
                { value: "relevance", label: "Relevance" },
                { value: "downloads", label: "Most downloaded" },
                { value: "follows", label: "Most followed" },
                { value: "newest", label: "Newest" },
                { value: "updated", label: "Recently updated" },
              ]}
            />
          </div>
          {type === "mod" && !effectiveTarget && usable.length === 0 && (
            <div className="mb-4">
              <Callout tone="info" title="No modded installation yet" action={<Button size="sm" onClick={() => navigate({ page: "library" })}>Create one</Button>}>
                Create a Fabric, Quilt, Forge or NeoForge installation to install mods.
              </Callout>
            </div>
          )}
          {error && <Callout tone="danger" title="Modrinth couldn't be reached">{error}</Callout>}
          {!hits ? (
            <div className="space-y-3">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-[104px] rounded-xl" />
              ))}
            </div>
          ) : hits.length === 0 ? (
            <Card>
              <EmptyState icon={<Icon.Search size={24} />} title="Nothing found">
                {effectiveTarget ? `No results for Minecraft ${effectiveTarget.gameVersion} with ${loaderLabel(effectiveTarget)}. Try another search or installation.` : "Try a different search or fewer filters."}
              </EmptyState>
            </Card>
          ) : (
            <>
              <div className="text-[12.5px] text-faint mb-2">{formatCount(total)} results</div>
              <div className="space-y-3">
                {hits.map((h) => (
                  <HitRow key={h.project_id} hit={h} type={type} target={effectiveTarget} installed={installed.has(h.project_id)} />
                ))}
              </div>
              {hits.length < total && (
                <div className="flex justify-center mt-5">
                  <Button onClick={() => void more()} loading={loadingMore}>
                    Load more
                  </Button>
                </div>
              )}
            </>
          )}
        </div>

        <aside className="space-y-5 sticky top-0">
          {type !== "modpack" && (
            <Card className="p-4">
              <div className="text-[12.5px] text-muted font-medium mb-2">Install to</div>
              <Select
                value={effectiveTarget?.id ?? ""}
                onChange={setTargetId}
                options={[{ value: "", label: "Don't filter by installation" }, ...usable.map((i) => ({ value: i.id, label: `${i.name} (${i.gameVersion})` }))]}
              />
              {effectiveTarget && (
                <div className="flex items-center gap-2.5 mt-3">
                  <InstanceIcon inst={effectiveTarget} size={32} />
                  <div className="text-[12.5px] text-muted min-w-0">
                    Showing only content for <span className="text-fg">{effectiveTarget.gameVersion}</span>
                    {type === "mod" && <> · {loaderLabel(effectiveTarget)}</>}
                  </div>
                </div>
              )}
            </Card>
          )}
          {!effectiveTarget && (
            <Card className="p-4 space-y-3">
              <div className="text-[12.5px] text-muted font-medium">Filters</div>
              <Select value={gameVersion} onChange={setGameVersion} options={[{ value: "", label: "Any Minecraft version" }, ...releaseVersions.map((v) => ({ value: v.version, label: v.version }))]} />
              {(type === "mod" || type === "modpack") && (
                <Select
                  value={loader}
                  onChange={setLoader}
                  options={[
                    { value: "", label: "Any loader" },
                    { value: "fabric", label: "Fabric" },
                    { value: "quilt", label: "Quilt" },
                    { value: "neoforge", label: "NeoForge" },
                    { value: "forge", label: "Forge" },
                  ]}
                />
              )}
            </Card>
          )}
          {type === "mod" && (
            <Card className="p-4">
              <Toggle checked={clientOnly} onChange={setClientOnly} label="Client-side only" description="Hide server-only mods." />
            </Card>
          )}
          {catOptions.length > 0 && (
            <Card className="p-4">
              <div className="text-[12.5px] text-muted font-medium mb-2">Categories</div>
              <div className="flex flex-wrap gap-1.5">
                {catOptions.map((c) => {
                  const on = categories.includes(c.name);
                  return (
                    <button
                      key={c.name}
                      type="button"
                      onClick={() => setCategories((cs) => (on ? cs.filter((x) => x !== c.name) : [...cs, c.name]))}
                      className={cx(
                        "h-7 px-2.5 rounded-lg text-[12.5px] capitalize border transition-colors cursor-pointer",
                        on ? "bg-accent/15 border-accent/40 text-accent" : "bg-panel-2 border-line text-muted hover:text-fg",
                      )}
                    >
                      {c.name.replace(/-/g, " ")}
                    </button>
                  );
                })}
              </div>
            </Card>
          )}
          <p className="text-[11.5px] text-faint leading-relaxed">
            Content is provided by Modrinth and its creators under their own licences. Files are downloaded from Modrinth and checked against their published hashes.
          </p>
        </aside>
      </div>
    </div>
  );
}
