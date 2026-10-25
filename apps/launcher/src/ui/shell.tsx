/** Window chrome: custom title bar, sidebar, task tray, toasts and the drag-and-drop overlay. */

import { getCurrentWindow } from "@tauri-apps/api/window";
import { useEffect, useState, type ReactNode } from "react";
import type { Account } from "../services/app";
import { app, dismissToast, navigate, type Route } from "../services/app";
import { activeAccount, refreshAccountSkin } from "../services/accounts";
import { secureSkinUrl } from "../core/auth/microsoft";
import { cancelLaunch } from "../services/game";
import { native } from "../services/native";
import { useStore } from "../services/store";
import { cx, IconButton, ProgressBar, Spinner } from "./components";
import { BloodMark, Icon, Wordmark } from "./icons";
import { openPalette } from "./commandPalette";

export function TitleBar() {
  const win = getCurrentWindow();
  const ready = useStore(app, (s) => s.ready);
  const [maximized, setMaximized] = useState(false);
  useEffect(() => {
    void win.isMaximized().then(setMaximized);
    const un = win.onResized(() => void win.isMaximized().then(setMaximized));
    return () => {
      void un.then((f) => f());
    };
  }, [win]);
  const btn = "h-full w-[46px] grid place-items-center text-muted hover:text-fg hover:bg-raised transition-colors";
  return (
    <div className="relative z-10 h-9 shrink-0 flex items-center bg-bg border-b border-line select-none" data-tauri-drag-region>
      {!ready && (
        <div className="flex items-center gap-2 pl-3.5 pointer-events-none opacity-80" data-tauri-drag-region>
          <BloodMark size={15} />
          <Wordmark height={11} />
        </div>
      )}
      <div className="flex-1 h-full flex items-center justify-center" data-tauri-drag-region>
        {ready && (
          <button
            type="button"
            onClick={openPalette}
            className="h-6 w-[300px] max-w-[40vw] flex items-center gap-2 px-2.5 rounded-md bg-panel-2 border border-line text-faint hover:text-muted hover:border-line-strong text-[12px] cursor-pointer transition-colors"
          >
            <Icon.Search size={13} />
            <span className="flex-1 text-left truncate">Search</span>
            <kbd className="font-mono text-[11px] opacity-70">Ctrl K</kbd>
          </button>
        )}
      </div>
      <div className="flex h-full">
        <button className={btn} aria-label="Minimize" onClick={() => void win.minimize()}>
          <Icon.Minus size={15} />
        </button>
        <button className={btn} aria-label={maximized ? "Restore" : "Maximize"} onClick={() => void win.toggleMaximize()}>
          <Icon.Square size={13} />
        </button>
        <button className={cx(btn, "hover:!bg-[#c42b1c] hover:!text-white")} aria-label="Close" onClick={() => void win.close()}>
          <Icon.Close size={15} />
        </button>
      </div>
    </div>
  );
}

/** Minecraft face cropped from the skin texture (8×8 at 8,8 plus the hat layer). */
export function Avatar({ account, size = 28 }: { account?: Account; size?: number }) {
  const [imageFailed, setImageFailed] = useState(false);
  const skin = secureSkinUrl(account?.skinUrl);
  useEffect(() => setImageFailed(false), [skin]);

  if (!skin || imageFailed) {
    return (
      <div className="rounded-md bg-raised border border-line grid place-items-center text-muted shrink-0" style={{ width: size, height: size }}>
        <Icon.User size={size * 0.55} />
      </div>
    );
  }

  const image = (left: number) => ({ width: size * 8, height: size * 8, left, top: -size, imageRendering: "pixelated" as const });
  return (
    <div className="relative rounded-md overflow-hidden shrink-0 border border-white/10" style={{ width: size, height: size }}>
      <img src={skin} alt="" draggable={false} onError={() => setImageFailed(true)} className="absolute max-w-none pixelated" style={image(-size)} />
      <img src={skin} alt="" draggable={false} onError={() => setImageFailed(true)} className="absolute max-w-none pixelated" style={image(-size * 5)} />
    </div>
  );
}
function NavItem({ icon, label, active, onClick, badge }: { icon: ReactNode; label: string; active: boolean; onClick: () => void; badge?: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "group relative flex items-center gap-3 h-9 w-full px-3 rounded-lg text-[14px] font-medium transition-colors cursor-pointer",
        active ? "bg-white/[0.07] text-fg" : "text-muted hover:text-fg hover:bg-white/[0.04]",
      )}
    >
      <span className={cx("transition-colors", active ? "text-accent" : "text-faint group-hover:text-muted")}>{icon}</span>
      <span className="flex-1 text-left">{label}</span>
      {badge}
    </button>
  );
}

function NavGroup({ children }: { children: ReactNode }) {
  return <div className="px-3 pt-5 pb-1.5 text-[11px] font-medium text-faint">{children}</div>;
}

function isActive(route: Route, page: Route["page"]): boolean {
  if (page === "library") return route.page === "library" || route.page === "instance";
  if (page === "browse") return route.page === "browse" || route.page === "project";
  if (page === "mods") return route.page === "mods";
  return route.page === page;
}

export function Sidebar() {
  const route = useStore(app, (s) => s.route);
  const accounts = useStore(app, (s) => s.accounts);
  const activeId = useStore(app, (s) => s.settings.activeAccountId);
  const runningCount = useStore(app, (s) => Object.keys(s.running).length);
  const premium = useStore(app, (s) => s.premium.entitlement?.premium === true || s.premium.license?.status === "active");
  const awaiting = useStore(app, (s) => s.premium.awaitingPayment);
  const account = activeAccount();
  useEffect(() => {
    // New or switched-to accounts without a face yet: look it up now rather than at next start.
    if (account && !account.skinUrl) void refreshAccountSkin(account).catch(() => undefined);
  }, [account?.id, account?.skinUrl]);
  void accounts;
  void activeId;
  return (
    <aside className="relative z-10 w-[220px] shrink-0 flex flex-col border-r border-line glass">
      <div className="flex items-center gap-2.5 px-5 h-16">
        <BloodMark size={24} />
        <Wordmark height={15} />
      </div>
      <nav className="px-3 flex flex-col gap-0.5">
        <NavItem icon={<Icon.Home size={18} />} label="Home" active={isActive(route, "home")} onClick={() => navigate({ page: "home" })} />
        <NavItem
          icon={<Icon.Library size={18} />}
          label="Library"
          active={isActive(route, "library")}
          onClick={() => navigate({ page: "library" })}
          badge={runningCount > 0 ? <span className="h-1.5 w-1.5 rounded-full bg-accent" title="Running" /> : undefined}
        />
        <NavItem icon={<Icon.Compass size={18} />} label="Browse" active={isActive(route, "browse")} onClick={() => navigate({ page: "browse" })} />
        <NavGroup>MOTION</NavGroup>
        <NavItem icon={<Icon.Hud size={18} />} label="MOTION Mods" active={isActive(route, "mods")} onClick={() => navigate({ page: "mods" })} />
        <NavItem icon={<Icon.Gauge size={18} />} label="Performance" active={isActive(route, "performance")} onClick={() => navigate({ page: "performance" })} />
        <NavItem icon={<Icon.Paint size={18} />} label="Visuals" active={isActive(route, "customize")} onClick={() => navigate({ page: "customize" })} />
        <NavItem icon={<Icon.User size={18} />} label="My Skin" active={isActive(route, "skins")} onClick={() => navigate({ page: "skins" })} />
        <NavItem
          icon={<Icon.Crown size={18} />}
          label="Premium"
          active={isActive(route, "premium")}
          onClick={() => navigate({ page: "premium" })}
          badge={awaiting ? <Spinner size={12} /> : undefined}
        />
      </nav>
      <div className="flex-1" />
      <TaskTray />
      <div className="p-3 border-t border-line flex items-center gap-1">
        <button
          type="button"
          onClick={() => navigate({ page: "accounts" })}
          title="Accounts"
          className={cx("flex flex-1 min-w-0 items-center gap-2.5 p-1.5 rounded-lg transition-colors cursor-pointer", route.page === "accounts" ? "bg-white/[0.07]" : "hover:bg-white/[0.04]")}
        >
          <Avatar account={account} size={28} />
          <div className="min-w-0 text-left">
            <div className="text-[13px] font-semibold truncate flex items-center gap-1.5">
              {account?.username ?? "Sign in"}
              {premium && (
                <span className="text-amber-200 shrink-0" title="MOTION Premium supporter">
                  <Icon.Crown size={12} />
                </span>
              )}
            </div>
            <div className="text-[12px] text-faint truncate">{account ? (premium ? "Premium" : "Microsoft account") : "Add an account to play"}</div>
          </div>
        </button>
        <IconButton
          label="Settings"
          onClick={() => navigate({ page: "settings" })}
          className={cx("h-9 w-9 shrink-0", isActive(route, "settings") && "bg-white/[0.07] text-fg")}
        >
          <Icon.Settings size={18} />
        </IconButton>
      </div>
    </aside>
  );
}

function TaskTray() {
  const tasks = useStore(app, (s) => s.tasks);
  if (tasks.length === 0) return null;
  return (
    <div className="mx-3 mb-3 space-y-2">
      {tasks.slice(-3).map((t) => (
        <div key={t.id} className="rounded-lg border border-line bg-panel-2 p-3 animate-in">
          <div className="flex items-start gap-2">
            <div className="min-w-0 flex-1">
              <div className="text-[13px] font-semibold truncate">{t.title}</div>
              <div className="text-[12px] text-muted truncate">{t.stage}</div>
            </div>
            {t.cancellable && (
              <IconButton
                label="Cancel"
                className="h-6 w-6 -mr-1 -mt-1"
                onClick={() => {
                  if (t.instanceId && t.id.startsWith("launch-")) cancelLaunch(t.instanceId);
                  else for (const s of ["files", "pack", "file"]) void native.cancelTask(`${t.id}:${s}`);
                }}
              >
                <Icon.Close size={13} />
              </IconButton>
            )}
          </div>
          <ProgressBar value={t.progress} className="mt-2.5" />
          {t.detail && <div className="text-[11px] text-faint mt-1.5 tabular-nums">{t.detail}</div>}
        </div>
      ))}
    </div>
  );
}

export function Toasts() {
  const toasts = useStore(app, (s) => s.toasts);
  const tone = {
    success: { icon: <Icon.Check size={17} />, c: "text-accent" },
    error: { icon: <Icon.Alert size={17} />, c: "text-danger" },
    warning: { icon: <Icon.Alert size={17} />, c: "text-warn" },
    info: { icon: <Icon.Info size={17} />, c: "text-info" },
  };
  return (
    <div className="fixed bottom-5 right-5 z-[60] flex flex-col gap-2 w-[360px]">
      {toasts.map((t) => (
        <div key={t.id} className="flex gap-3 rounded-xl border border-line-strong bg-raised p-3.5 shadow-xl shadow-black/40 animate-in">
          <div className={cx("mt-0.5", tone[t.kind].c)}>{tone[t.kind].icon}</div>
          <div className="flex-1 min-w-0">
            <div className="font-semibold text-[14px]">{t.title}</div>
            {t.message && <div className="text-[13px] text-muted mt-0.5 leading-relaxed selectable">{t.message}</div>}
            {t.action && (
              <button
                type="button"
                className="mt-2 text-[13px] font-semibold text-accent hover:underline cursor-pointer"
                onClick={() => {
                  t.action!.run();
                  dismissToast(t.id);
                }}
              >
                {t.action.label}
              </button>
            )}
          </div>
          <IconButton label="Dismiss" className="h-6 w-6 -mr-1 -mt-1" onClick={() => dismissToast(t.id)}>
            <Icon.Close size={13} />
          </IconButton>
        </div>
      ))}
    </div>
  );
}

export function DragOverlay() {
  const active = useStore(app, (s) => s.dragActive);
  if (!active) return null;
  return (
    <div className="fixed inset-0 z-[70] pointer-events-none p-4">
      <div className="h-full w-full rounded-xl border-2 border-dashed border-accent/60 bg-bg/80 grid place-items-center animate-in">
        <div className="text-center">
          <Icon.Upload size={32} className="mx-auto text-accent" />
          <div className="font-display text-lg font-semibold mt-3">Drop to add</div>
          <div className="text-muted mt-1">.jar mods, resource/shader pack .zip files, or a .mrpack modpack</div>
        </div>
      </div>
    </div>
  );
}
