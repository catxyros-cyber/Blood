/**
 * My Skin: your saved skins, a 3D preview, an editor, and applying a skin to your real
 * Minecraft account (confirmed by Mojang before it's shown as applied).
 */

import { useEffect, useMemo, useState } from "react";
import type { SkinModel } from "../../core/skins/skin";
import { activeAccount } from "../../services/accounts";
import { app, navigate, reportError, toast } from "../../services/app";
import { native } from "../../services/native";
import {
  accountSkinUrl,
  addCommunitySkin,
  addSkinFiles,
  applySkin,
  loadCommunitySkins,
  loadSkins,
  normaliseSkin,
  removeSkin,
  saveSkin,
  selectSkin,
  skinState,
  updateSkin,
  type CommunitySkin,
  type SavedSkin,
} from "../../services/skins";
import { useStore } from "../../services/store";
import { Badge, Button, Card, cx, EmptyState, PageHeader, Segmented, Toggle } from "../components";
import { Icon } from "../icons";
import { blankSkin, SkinEditor } from "../skinEditor";
import { SkinViewer } from "../skinViewer";

/** The skin's face (head front + hat), pixel-sharp, for library cards. */
function SkinHead({ png, size = 44 }: { png: string; size?: number }) {
  const s = size / 8;
  const layer = (x: number, y: number) => ({
    position: "absolute" as const,
    inset: 0,
    backgroundImage: `url("${png}")`,
    backgroundSize: `${64 * s}px ${64 * s}px`,
    backgroundPosition: `${-x * s}px ${-y * s}px`,
    imageRendering: "pixelated" as const,
  });
  return (
    <div className="relative rounded-md overflow-hidden shrink-0" style={{ width: size, height: size }}>
      <div style={layer(8, 8)} />
      <div style={layer(40, 8)} />
    </div>
  );
}

type Filter = "all" | "favorites" | "recent";

export function SkinsPage() {
  const st = useStore(skinState, (s) => s);
  const accounts = useStore(app, (s) => s.accounts);
  const account = activeAccount();
  const [filter, setFilter] = useState<Filter>("all");
  const [tab, setTab] = useState<"library" | "community" | "editor">("library");
  const [communityId, setCommunityId] = useState<string | null>(null);
  const [addingCommunityId, setAddingCommunityId] = useState<string | null>(null);
  const [model, setModel] = useState<SkinModel>("classic");
  const [overlay, setOverlay] = useState(true);
  const [animate, setAnimate] = useState(true);
  const [applying, setApplying] = useState(false);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [editorPng, setEditorPng] = useState<string | null>(null);
  const [editorBase, setEditorBase] = useState<{ png: string; id?: string; name: string } | null>(null);
  const [showAccount, setShowAccount] = useState(false);

  useEffect(() => {
    if (!st.loaded) void loadSkins();
    if (!st.communityLoaded) void loadCommunitySkins();
  }, [st.loaded, st.communityLoaded]);

  const selected = st.skins.find((s) => s.id === st.selectedId) ?? null;
  useEffect(() => {
    if (selected) setModel(selected.model);
  }, [selected?.id]);

  const communitySelected = st.communitySkins.find((skin) => skin.id === communityId) ?? st.communitySkins[0] ?? null;
  useEffect(() => {
    if (!communityId && st.communitySkins[0]) setCommunityId(st.communitySkins[0].id);
  }, [communityId, st.communitySkins]);
  useEffect(() => {
    if (tab === "community" && communitySelected) setModel(communitySelected.model);
  }, [tab, communitySelected?.id]);

  const shown = useMemo(() => {
    const list = [...st.skins];
    if (filter === "favorites") return list.filter((s) => s.favorite);
    if (filter === "recent") return list.filter((s) => s.lastUsedAt).sort((a, b) => (b.lastUsedAt ?? 0) - (a.lastUsedAt ?? 0)).slice(0, 8);
    return list.sort((a, b) => Number(b.favorite) - Number(a.favorite) || b.addedAt - a.addedAt);
  }, [st.skins, filter]);

  const accountSkin = accountSkinUrl();
  const previewSrc = tab === "editor" ? editorPng : tab === "community" ? communitySelected?.image ?? null : showAccount ? accountSkin : selected?.png ?? accountSkin;

  const upload = async () => {
    const picked = await native.pickFiles({ title: "Choose Minecraft skins", filters: [["Minecraft skin (PNG)", ["png"]]], multiple: true });
    if (!picked.length) return;
    try {
      const added = await addSkinFiles(picked);
      setShowAccount(false);
      toast({ kind: "success", title: added.length === 1 ? "Skin added" : `${added.length} skins added`, message: added.length < picked.length ? "Some files weren't Minecraft skins and were skipped." : "Select it and press Apply to use it in Minecraft." });
    } catch (e) {
      reportError("Couldn't add that skin", e);
    }
  };

  const addCommunity = async (skin: CommunitySkin) => {
    setAddingCommunityId(skin.id);
    try {
      const saved = await addCommunitySkin(skin.id);
      toast({ kind: "success", title: "Added to your library", message: "\"" + saved.name + "\" is ready to preview and apply." });
      setShowAccount(false);
      setTab("library");
    } catch (e) {
      reportError("Could not add that community skin", e);
    } finally {
      setAddingCommunityId(null);
    }
  };

  const apply = async () => {
    if (!selected) return;
    setApplying(true);
    try {
      await applySkin(selected.id, model);
      toast({ kind: "success", title: "Skin applied", message: `Mojang confirmed "${selected.name}" as ${account?.username ?? "your"} skin. Servers show it after you rejoin.` });
      setShowAccount(true);
    } catch (e) {
      reportError("Couldn't apply the skin", e);
    } finally {
      setApplying(false);
    }
  };

  const openEditor = (base: { png: string; id?: string; name: string }) => {
    setEditorBase(base);
    setEditorPng(base.png);
    setTab("editor");
  };

  return (
    <div>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <Icon.User size={26} /> My Skin
          </span>
        }
        subtitle="Browse MOTION community skins, then preview, edit and apply one to your Minecraft account."
        actions={
          <div className="flex gap-2">
            <Button icon={<Icon.Edit size={15} />} onClick={() => openEditor({ png: blankSkin(), name: "New skin" })}>New skin</Button>
            <Button variant="primary" icon={<Icon.Upload size={15} />} onClick={() => void upload()}>Upload skin</Button>
          </div>
        }
      />

      <div className="grid grid-cols-[minmax(320px,420px)_1fr] gap-6 items-start">
        {/* Preview + apply */}
        <div className="space-y-4 sticky top-4">
          <Card className="overflow-hidden">
            <div className="relative">
              <SkinViewer src={previewSrc} model={model} overlay={overlay} animate={animate} height={460} />
              {!previewSrc && <div className="absolute inset-0 grid place-items-center text-faint text-[13px] pointer-events-none">Upload a skin to see it here.</div>}
            </div>
            <div className="p-4 border-t border-line space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <Segmented value={model} onChange={(m) => { setModel(m); if (selected && tab === "library" && !showAccount) void updateSkin(selected.id, { model: m }); }} options={[{ value: "classic", label: "Classic" }, { value: "slim", label: "Slim" }]} />
                <div className="flex gap-4 whitespace-nowrap">
                  <Toggle checked={overlay} onChange={setOverlay} label="Outer layer" />
                  <Toggle checked={animate} onChange={setAnimate} label="Animate" />
                </div>
              </div>
              <div className="text-[12px] text-faint">Drag to turn, scroll to zoom, double-click to reset.</div>
            </div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center gap-3">
              {account ? (
                <>
                  <span className="h-2 w-2 rounded-full bg-accent" />
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-[13.5px] truncate">{account.username}</div>
                    <div className="text-[12px] text-muted">Signed in with Microsoft</div>
                  </div>
                  {accountSkin && (
                    <Button size="sm" variant={showAccount ? "outline" : "ghost"} onClick={() => setShowAccount((v) => !v)}>
                      {showAccount ? "Showing current skin" : "Show current skin"}
                    </Button>
                  )}
                </>
              ) : (
                <>
                  <span className="h-2 w-2 rounded-full bg-warn" />
                  <div className="flex-1 text-[13px] text-muted">Not signed in. You can preview and edit skins; applying needs a Microsoft account.</div>
                  <Button size="sm" onClick={() => navigate({ page: "accounts" })}>Sign in</Button>
                </>
              )}
            </div>
            <Button
              className="w-full mt-4"
              size="lg"
              variant="primary"
              loading={applying}
              disabled={!selected || !account || applying || tab !== "library"}
              icon={<Icon.Check size={17} />}
              onClick={() => void apply()}
            >
              {selected ? `Apply "${selected.name}" to Minecraft` : "Select a skin to apply"}
            </Button>
            <p className="text-[12px] text-faint mt-2.5 leading-relaxed">
              {selected?.appliedAt
                ? `Applied to your account ${new Date(selected.appliedAt).toLocaleString()} (confirmed by Mojang).`
                : "Skins in your library are local previews until you apply one. Applying changes your skin everywhere you play Minecraft."}
            </p>
            {accounts.length > 1 && <p className="text-[12px] text-faint mt-1">Applies to the active account. Switch accounts on the Accounts page.</p>}
          </Card>
        </div>

        {/* Library / editor */}
        <Card className="p-5 min-w-0">
          <div className="flex items-center justify-between mb-4">
            <Segmented value={tab} onChange={(t) => setTab(t)} options={[{ value: "library", label: "Library" }, { value: "community", label: "Community" }, { value: "editor", label: "Editor" }]} />
            {tab === "library" && (
              <Segmented value={filter} onChange={setFilter} options={[{ value: "all", label: `All (${st.skins.length})` }, { value: "favorites", label: "Favourites" }, { value: "recent", label: "Recent" }]} />
            )}
          </div>

          {tab === "library" ? (
            <>
              <button
                type="button"
                onClick={() => void upload()}
                className="w-full rounded-xl border-2 border-dashed border-line-strong hover:border-accent/60 bg-panel-2/60 py-7 grid place-items-center text-center cursor-pointer transition-colors mb-5"
              >
                <Icon.Upload size={26} className="text-accent" />
                <div className="font-semibold mt-2">Drop PNG skins anywhere on this page</div>
                <div className="text-[12.5px] text-muted">or click to choose files · 64×64 (old 64×32 skins are converted)</div>
              </button>

              {shown.length === 0 ? (
                <EmptyState title={filter === "favorites" ? "No favourites yet" : filter === "recent" ? "Nothing applied yet" : "Your skin library is empty"}>
                  {filter === "all" ? "Upload a skin, or make one with New skin." : filter === "favorites" ? "Star a skin to keep it at the top." : "Skins you apply show up here."}
                </EmptyState>
              ) : (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(210px,1fr))] gap-3">
                  {shown.map((s: SavedSkin) => {
                    const active = s.id === st.selectedId && !showAccount;
                    return (
                      <div
                        key={s.id}
                        onClick={() => {
                          selectSkin(s.id);
                          setShowAccount(false);
                        }}
                        className={cx("rounded-xl border p-3 flex items-center gap-3 cursor-pointer transition-colors", active ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-line-strong")}
                      >
                        <SkinHead png={s.png} />
                        <div className="flex-1 min-w-0">
                          {renaming === s.id ? (
                            <input
                              autoFocus
                              defaultValue={s.name}
                              maxLength={40}
                              className="w-full bg-raised border border-line rounded px-1.5 py-0.5 text-[13px]"
                              onClick={(e) => e.stopPropagation()}
                              onBlur={(e) => {
                                void updateSkin(s.id, { name: e.target.value.trim() });
                                setRenaming(null);
                              }}
                              onKeyDown={(e) => {
                                if (e.key === "Enter") (e.target as HTMLInputElement).blur();
                                if (e.key === "Escape") setRenaming(null);
                              }}
                            />
                          ) : (
                            <div className="font-semibold text-[13px] truncate">{s.name}</div>
                          )}
                          <div className="flex items-center gap-1.5 mt-1">
                            <Badge>{s.model === "slim" ? "Slim" : "Classic"}</Badge>
                            {s.appliedAt && <Badge tone="accent">In use</Badge>}
                          </div>
                        </div>
                        <div className="flex flex-col gap-1" onClick={(e) => e.stopPropagation()}>
                          <button type="button" title={s.favorite ? "Unfavourite" : "Favourite"} onClick={() => void updateSkin(s.id, { favorite: !s.favorite })} className={cx("p-1 rounded hover:bg-white/5 cursor-pointer", s.favorite ? "text-amber-300" : "text-faint")}>
                            <Icon.Star size={14} />
                          </button>
                          <button type="button" title="Rename" onClick={() => setRenaming(s.id)} className="p-1 rounded hover:bg-white/5 text-faint hover:text-fg cursor-pointer">
                            <Icon.Edit size={14} />
                          </button>
                          <button type="button" title="Edit in the editor" onClick={() => openEditor({ png: s.png, id: s.id, name: s.name })} className="p-1 rounded hover:bg-white/5 text-faint hover:text-fg cursor-pointer">
                            <Icon.Paint size={14} />
                          </button>
                          <button
                            type="button"
                            title="Remove"
                            onClick={() => {
                              if (confirm(`Remove "${s.name}" from your library? (Your Minecraft account keeps whatever skin it has.)`)) void removeSkin(s.id);
                            }}
                            className="p-1 rounded hover:bg-white/5 text-faint hover:text-danger cursor-pointer"
                          >
                            <Icon.Trash size={14} />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          ) : tab === "community" ? (
            <>
              <div className="mb-4 text-[12.5px] text-muted">Shared MOTION skins. Add one to your library before applying it to your Minecraft account.</div>
              {!st.communityLoaded ? (
                <EmptyState title="Loading community skins">Fetching the MOTION skin catalog…</EmptyState>
              ) : st.communityError ? (
                <EmptyState title="Community skins unavailable">{st.communityError}</EmptyState>
              ) : st.communitySkins.length === 0 ? (
                <EmptyState title="No community skins published yet">Check back later for skins shared by MOTION.</EmptyState>
              ) : (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3">
                  {st.communitySkins.map((skin) => {
                    const active = skin.id === communitySelected?.id;
                    const added = st.skins.some((local) => local.communityId === skin.id);
                    return (
                      <div
                        key={skin.id}
                        onClick={() => { setCommunityId(skin.id); setModel(skin.model); setShowAccount(false); }}
                        className={cx("rounded-xl border p-3 flex items-center gap-3 cursor-pointer transition-colors", active ? "border-accent bg-accent/10" : "border-line bg-panel-2 hover:border-line-strong")}
                      >
                        <SkinHead png={skin.image} />
                        <div className="flex-1 min-w-0">
                          <div className="font-semibold text-[13px] truncate">{skin.name}</div>
                          <div className="flex items-center gap-1.5 mt-1"><Badge>{skin.model === "slim" ? "Slim" : "Classic"}</Badge><Badge tone="accent">MOTION</Badge></div>
                          <Button
                            className="mt-2"
                            size="sm"
                            variant={added ? "outline" : "primary"}
                            loading={addingCommunityId === skin.id}
                            disabled={added || addingCommunityId !== null}
                            onClick={(e) => { e.stopPropagation(); void addCommunity(skin); }}
                          >
                            {added ? "In your library" : "Add to library"}
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          ) : editorBase ? (
            <SkinEditor
              key={editorBase.id ?? editorBase.png.length}
              png={editorBase.png}
              model={model}
              onChange={setEditorPng}
              onImport={async () => {
                const picked = await native.pickFiles({ title: "Import a skin", filters: [["Minecraft skin (PNG)", ["png"]]] });
                if (!picked.length) return null;
                try {
                  const raw = await native.readDataUrl(picked[0]!, 512 * 1024);
                  if (!raw) return null;
                  return (await normaliseSkin(raw)).png;
                } catch (e) {
                  reportError("Couldn't import that skin", e);
                  return null;
                }
              }}
              onSave={async (png) => {
                try {
                  const saved = await saveSkin({ id: editorBase.id, name: editorBase.name, model, png });
                  setEditorBase({ png, id: saved.id, name: saved.name });
                  toast({ kind: "success", title: "Saved", message: `"${saved.name}" is in your library. Apply it from there.` });
                } catch (e) {
                  reportError("Couldn't save the skin", e);
                }
              }}
            />
          ) : (
            <EmptyState title="Pick a skin to edit" action={<Button onClick={() => openEditor({ png: blankSkin(), name: "New skin" })}>Start a new skin</Button>}>
              Use the brush icon on a skin in your library, or start from a blank one.
            </EmptyState>
          )}
        </Card>
      </div>
    </div>
  );
}
