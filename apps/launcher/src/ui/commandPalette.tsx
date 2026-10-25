/**
 * Command palette (Ctrl+K): type to jump to any page, play an installation, rejoin a server or
 * world, or run common actions. Arrow keys move, Enter runs, Esc closes.
 */

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { fuzzyFilter } from "../core/util/fuzzy";
import { loaderLabel } from "./instanceParts";
import { app, navigate, updateSettings } from "../services/app";
import { isRunning, play, stopGame } from "../services/game";
import { selectedInstance } from "../services/instances";
import { createStore, useStore } from "../services/store";
import { checkForUpdate } from "../services/updater";
import { cx } from "./components";
import { Icon } from "./icons";

const paletteStore = createStore<{ open: boolean }>({ open: false });
export const openPalette = () => paletteStore.set({ open: true });

interface Command {
  id: string;
  title: string;
  hint?: string;
  group: "Play" | "Jump back in" | "Go to" | "Installations" | "Actions";
  icon: ReactNode;
  run: () => void;
}

function useCommands(): Command[] {
  const instances = useStore(app, (s) => s.instances);
  const running = useStore(app, (s) => s.running);
  return useMemo(() => {
    const sel = selectedInstance();
    const out: Command[] = [];
    const ordered = [...instances].sort((a, b) => (b.lastPlayedAt ?? 0) - (a.lastPlayedAt ?? 0));
    for (const inst of ordered) {
      const live = Boolean(running[inst.id]);
      out.push({
        id: `play:${inst.id}`,
        title: live ? `Stop ${inst.name}` : `Play ${inst.name}`,
        hint: `${inst.gameVersion} · ${loaderLabel(inst)}`,
        group: "Play",
        icon: live ? <Icon.Stop size={16} /> : <Icon.Play size={16} />,
        run: () => {
          if (live) void stopGame(inst.id);
          else {
            updateSettings({ selectedInstanceId: inst.id });
            void play(inst.id);
          }
        },
      });
    }
    for (const inst of ordered) {
      for (const r of (inst.recentPlays ?? []).slice(0, 3)) {
        out.push({
          id: `recent:${inst.id}:${r.kind}:${r.target}`,
          title: r.kind === "server" ? `Join ${r.name}` : `Open world ${r.name}`,
          hint: `${inst.name}${r.kind === "server" && r.name !== r.target ? ` · ${r.target}` : ""}`,
          group: "Jump back in",
          icon: r.kind === "server" ? <Icon.Globe size={16} /> : <Icon.Map size={16} />,
          run: () => {
            if (isRunning(inst.id)) return;
            void play(inst.id, { quickPlay: r.kind === "server" ? { kind: "server", address: r.target } : { kind: "world", folder: r.target }, quickPlayName: r.name });
          },
        });
      }
    }
    const pages: [string, ReactNode, () => void, string?][] = [
      ["Home", <Icon.Home size={16} />, () => navigate({ page: "home" })],
      ["Library", <Icon.Library size={16} />, () => navigate({ page: "library" }), "installations"],
      ["MOTION Mods", <Icon.Hud size={16} />, () => navigate({ page: "mods", instanceId: sel?.id }), "hud modules crosshair"],
      ["Performance", <Icon.Gauge size={16} />, () => navigate({ page: "performance", instanceId: sel?.id }), "fps optimize memory"],
      ["Browse mods", <Icon.Compass size={16} />, () => navigate({ page: "browse", type: "mod", instanceId: sel?.id }), "modrinth"],
      ["Browse modpacks", <Icon.Package size={16} />, () => navigate({ page: "browse", type: "modpack" }), "modrinth"],
      ["Browse shaders", <Icon.Sun size={16} />, () => navigate({ page: "browse", type: "shader", instanceId: sel?.id }), "modrinth"],
      ["Browse resource packs", <Icon.Image size={16} />, () => navigate({ page: "browse", type: "resourcepack", instanceId: sel?.id }), "textures modrinth"],
      ["Visuals", <Icon.Paint size={16} />, () => navigate({ page: "customize", instanceId: sel?.id }), "theme capes wings background"],
      ["My Skin", <Icon.User size={16} />, () => navigate({ page: "skins" })],
      ["Premium", <Icon.Crown size={16} />, () => navigate({ page: "premium" })],
      ["Accounts", <Icon.Users size={16} />, () => navigate({ page: "accounts" }), "microsoft sign in"],
      ["Settings", <Icon.Settings size={16} />, () => navigate({ page: "settings" }), "java memory launcher"],
    ];
    for (const [title, icon, run, hint] of pages) out.push({ id: `go:${title}`, title, hint, group: "Go to", icon, run });
    for (const inst of ordered) {
      out.push({ id: `open:${inst.id}`, title: inst.name, hint: "Mods, worlds & servers, logs, settings", group: "Installations", icon: <Icon.Cube size={16} />, run: () => navigate({ page: "instance", id: inst.id }) });
    }
    if (sel) {
      out.push(
        { id: "worlds", title: `Worlds & servers in ${sel.name}`, group: "Actions", icon: <Icon.Map size={16} />, run: () => navigate({ page: "instance", id: sel.id, tab: "worlds" }) },
        { id: "logs", title: `Game log of ${sel.name}`, group: "Actions", icon: <Icon.FileText size={16} />, run: () => navigate({ page: "instance", id: sel.id, tab: "logs" }) },
        { id: "repair", title: `Repair ${sel.name}`, hint: "Re-check every game file", group: "Actions", icon: <Icon.Wrench size={16} />, run: () => void play(sel.id, { repair: true }) },
      );
    }
    out.push({ id: "update", title: "Check for MOTION updates", group: "Actions", icon: <Icon.Refresh size={16} />, run: () => void checkForUpdate(true) });
    return out;
  }, [instances, running]);
}

export function CommandPalette() {
  const open = useStore(paletteStore, (s) => s.open);
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const commands = useCommands();
  const results = useMemo(() => fuzzyFilter(query, commands, (c) => `${c.title} ${c.hint ?? ""}`).slice(0, 40), [query, commands]);

  // Global shortcut.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === "k") {
        e.preventDefault();
        paletteStore.set((s) => ({ open: !s.open }));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (!open) return;
    setQuery("");
    setIndex(0);
    requestAnimationFrame(() => input.current?.focus());
  }, [open]);

  useEffect(() => setIndex(0), [query]);
  useEffect(() => {
    listRef.current?.querySelector(`[data-i="${index}"]`)?.scrollIntoView({ block: "nearest" });
  }, [index]);

  if (!open) return null;
  const close = () => paletteStore.set({ open: false });
  const runAt = (i: number) => {
    const c = results[i];
    if (!c) return;
    close();
    c.run();
  };

  let lastGroup = "";
  return (
    <div className="fixed inset-0 z-[60] bg-black/60 flex justify-center pt-[12vh] px-6" onMouseDown={close}>
      <div
        className="w-full max-w-[600px] h-fit max-h-[70vh] flex flex-col rounded-xl border border-line-strong bg-panel shadow-2xl shadow-black/60 animate-pop overflow-hidden"
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
      >
        <div className="flex items-center gap-3 px-4 h-14 border-b border-line">
          <span className="text-faint">
            <Icon.Search size={18} />
          </span>
          <input
            ref={input}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Play, join, or jump to…"
            className="flex-1 bg-transparent outline-none text-[15px] placeholder:text-faint"
            spellCheck={false}
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                e.preventDefault();
                close();
              } else if (e.key === "ArrowDown") {
                e.preventDefault();
                setIndex((i) => Math.min(results.length - 1, i + 1));
              } else if (e.key === "ArrowUp") {
                e.preventDefault();
                setIndex((i) => Math.max(0, i - 1));
              } else if (e.key === "Enter") {
                e.preventDefault();
                runAt(index);
              }
            }}
          />
          <kbd className="text-[11px] font-mono text-faint border border-line rounded px-1.5 h-5 inline-flex items-center">Esc</kbd>
        </div>
        <div ref={listRef} className="overflow-y-auto py-2">
          {results.length === 0 ? (
            <div className="px-4 py-8 text-center text-muted text-[13.5px]">Nothing matches “{query}”.</div>
          ) : (
            results.map((c, i) => {
              const header = !query.trim() && c.group !== lastGroup ? c.group : null;
              lastGroup = c.group;
              return (
                <div key={c.id}>
                  {header && <div className="px-4 pt-2.5 pb-1 text-[12.5px] text-faint font-medium">{header}</div>}
                  <button
                    type="button"
                    data-i={i}
                    onMouseMove={() => setIndex(i)}
                    onClick={() => runAt(i)}
                    className={cx("w-full flex items-center gap-3 px-4 h-10 text-left cursor-pointer", i === index ? "bg-accent/12 text-fg" : "text-fg/85")}
                  >
                    <span className={i === index ? "text-accent" : "text-faint"}>{c.icon}</span>
                    <span className="truncate text-[14px]">{c.title}</span>
                    {c.hint && <span className="ml-auto pl-3 truncate text-[12px] text-faint max-w-[45%]">{c.hint}</span>}
                  </button>
                </div>
              );
            })
          )}
        </div>
        <div className="flex items-center gap-4 px-4 h-9 border-t border-line text-[11.5px] text-faint">
          <span>↑↓ to move</span>
          <span>Enter to run</span>
          <span className="ml-auto">Ctrl K anywhere</span>
        </div>
      </div>
    </div>
  );
}
