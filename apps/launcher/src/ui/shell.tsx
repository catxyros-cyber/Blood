/** Window chrome: custom title bar, sidebar, task tray, toasts and the drag-and-drop overlay. */

import { getCurrentWindow } from "@tauri-apps/api/window";
import { useEffect, useState, type ReactNode } from "react";
import type { Account } from "../services/app";
import { app, dismissToast, navigate, type Route } from "../services/app";
import { activeAccount } from "../services/accounts";
import { cancelLaunch } from "../services/game";
import { native } from "../services/native";
import { useStore } from "../services/store";
import { cx, IconButton, ProgressBar, Spinner } from "./components";
import { BloodMark, Icon, Wordmark } from "./icons";

export function TitleBar() {
  const win = getCurrentWindow();
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
    <div className="relative z-10 h-9 shrink-0 flex items-center bg-bg/35 backdrop-blur-md border-b border-white/5 select-none" data-tauri-drag-region>
      <div className="flex items-center gap-2 pl-3.5 pointer-events-none opacity-80" data-tauri-drag-region>
        <BloodMark size={15} />
        <Wordmark height={11} />
      </div>
      <div className="flex-1 h-full" data-tauri-drag-region />
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
  if (!account?.skinUrl) {
    return (
      <div className="rounded-lg bg-raised border border-line grid place-items-center text-muted" style={{ width: size, height: size }}>
        <Icon.User size={size * 0.55} />
      </div>
    );
  }
  const layer = (x: number) => ({
    backgroundImage: `url("${account.skinUrl}")`,
    backgroundSize: `${size * 8}px auto`,
    backgroundPosition: `-${(x / 8) * size}px -${size}px`,
  });
  return (
    <div className="relative rounded-lg overflow-hidden pixelated shrink-0" style={{ width: size, height: size }}>
      <div className="absolute inset-0 pixelated" style={layer(8)} />
      <div className="absolute inset-0 pixelated" style={layer(40)} />
    </div>
  );
}

function NavItem({ icon, label, active, onClick, badge }: { icon: ReactNode; label: string; active: boolean; onClick: () => void; badge?: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "group relative flex items-center gap-3 h-10 w-full px-3 rounded-xl text-[14px] font-medium transition-all cursor-pointer",
        active ? "bg-gradient-to-r from-accent/16 to-accent/4 text-fg" : "text-muted hover:text-fg hover:bg-white/5",
      )}
    >
      {active && <span className="nav-active-bar absolute left-0 top-2.5 bottom-2.5 w-[3px] rounded-r-full bg-accent" />}
      <span className={cx("transition-colors", active ? "text-accent" : "text-faint group-hover:text-muted")}>{icon}</span>
      <span className="flex-1 text-left">{label}</span>
      {badge}
    </button>
  );
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
  const premium = useStore(app, (s) => s.premium.entitlement?.premium === true);
  const awaiting = useStore(app, (s) => s.premium.awaitingPayment);
  const account = activeAccount();
  void accounts;
  void activeId;
  return (
    <aside className="relative z-10 w-[232px] shrink-0 flex flex-col border-r border-white/5 glass">
      <div className="flex items-center gap-3 px-5 pt-5 pb-6">
        <BloodMark size={34} />
        <div>
          <Wordmark height={21} />
          <div className="text-[10.5px] text-faint mt-1.5 tracking-[0.18em] uppercase">Built for frames</div>
        </div>
      </div>
      <nav className="px-3 space-y-1">
        <NavItem icon={<Icon.Home size={19} />} label="Home" active={isActive(route, "home")} onClick={() => navigate({ page: "home" })} />
        <NavItem
          icon={<Icon.Library size={19} />}
          label="Library"
          active={isActive(route, "library")}
          onClick={() => navigate({ page: "library" })}
          badge={runningCount > 0 ? <span className="h-2 w-2 rounded-full bg-accent shadow-[0_0_8px] shadow-accent" title="Running" /> : undefined}
        />
        <NavItem icon={<Icon.Hud size={19} />} label="BLOOD Mods" active={isActive(route, "mods")} onClick={() => navigate({ page: "mods" })} />
        <NavItem icon={<Icon.Gauge size={19} />} label="Performance" active={isActive(route, "performance")} onClick={() => navigate({ page: "performance" })} />
        <NavItem icon={<Icon.Compass size={19} />} label="Browse" active={isActive(route, "browse")} onClick={() => navigate({ page: "browse" })} />
        <NavItem icon={<Icon.Paint size={19} />} label="Visuals" active={isActive(route, "customize")} onClick={() => navigate({ page: "customize" })} />
        <NavItem
          icon={<Icon.Crown size={19} />}
          label="Premium"
          active={isActive(route, "premium")}
          onClick={() => navigate({ page: "premium" })}
          badge={awaiting ? <Spinner size={12} /> : undefined}
        />
      </nav>
      <div className="flex-1" />
      <TaskTray />
      <div className="p-3 border-t border-white/5 space-y-1">
        <button
          type="button"
          onClick={() => navigate({ page: "accounts" })}
          className={cx("flex items-center gap-3 w-full p-2 rounded-xl transition-colors cursor-pointer", route.page === "accounts" ? "bg-accent/12" : "hover:bg-white/5")}
        >
          <Avatar account={account} size={30} />
          <div className="min-w-0 text-left">
            <div className="text-[13.5px] font-semibold truncate flex items-center gap-1.5">
              {account?.username ?? "Sign in"}
              {premium && (
                <span className="text-amber-200" title="BLOOD Premium supporter">
                  <Icon.Crown size={13} />
                </span>
              )}
            </div>
            <div className="text-[11.5px] text-faint">{account ? (premium ? "Premium supporter" : "Microsoft account") : "Add an account to play"}</div>
          </div>
        </button>
        <NavItem icon={<Icon.Settings size={19} />} label="Settings" active={isActive(route, "settings")} onClick={() => navigate({ page: "settings" })} />
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
        <div key={t.id} className="rounded-xl border border-line bg-panel-2 p-3 animate-in">
          <div className="flex items-start gap-2">
            <div className="min-w-0 flex-1">
              <div className="text-[12.5px] font-semibold truncate">{t.title}</div>
              <div className="text-[11.5px] text-muted truncate">{t.stage}</div>
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
    <div className="fixed bottom-5 right-5 z-[60] flex flex-col gap-2.5 w-[380px]">
      {toasts.map((t) => (
        <div key={t.id} className="flex gap-3 rounded-2xl border border-line-strong bg-raised/95 backdrop-blur p-4 shadow-2xl shadow-black/50 animate-in">
          <div className={cx("mt-0.5", tone[t.kind].c)}>{tone[t.kind].icon}</div>
          <div className="flex-1 min-w-0">
            <div className="font-semibold text-[13.5px]">{t.title}</div>
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
      <div className="h-full w-full rounded-3xl border-2 border-dashed border-accent/70 bg-accent/5 grid place-items-center animate-pop">
        <div className="text-center">
          <Icon.Upload size={40} className="mx-auto text-accent" />
          <div className="font-display text-xl font-semibold mt-3">Drop to add</div>
          <div className="text-muted mt-1">.jar mods, resource/shader pack .zip files, or a .mrpack modpack</div>
        </div>
      </div>
    </div>
  );
}
