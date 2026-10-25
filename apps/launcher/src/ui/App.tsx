import { useEffect, useState } from "react";
import { startPresence } from "../services/team";
import { basename } from "../core/util/path";
import { app, boot, navigate, P, reportError, toast } from "../services/app";
import { importContent, importModpackFile } from "../services/content";
import { initGameEvents } from "../services/game";
import { getInstance, loadInstances, selectedInstance } from "../services/instances";
import { events } from "../services/native";
import { loadAnnouncements, loadPlans, refreshPremium } from "../services/premium";
import { useStore } from "../services/store";
import { pullSync, watchForChanges } from "../services/sync";
import { startAutoUpdates } from "../services/updater";
import { startLicenseChecks } from "../services/license";
import { Button, ConfirmHost } from "./components";
import { BloodMark, Icon, Wordmark } from "./icons";
import { AccountsPage } from "./pages/Accounts";
import { BrowsePage } from "./pages/Browse";
import { HomePage } from "./pages/Home";
import { InstancePage } from "./pages/Instance";
import { LibraryPage } from "./pages/Library";
import { PerformancePage } from "./pages/Performance";
import { PremiumPage, PremiumWelcome } from "./pages/Premium";
import { ProjectPage } from "./pages/Project";
import { SettingsPage } from "./pages/Settings";
import { SkinsPage } from "./pages/Skins";
import { CustomizePage } from "./pages/Customize";
import { ModsPage } from "./pages/Mods";
import { Backdrop, useCustomBackground } from "./backdrop";
import { DragOverlay, Sidebar, TitleBar, Toasts } from "./shell";
import { bumpContent } from "./contentEvents";
import { InstallPlanHost } from "./installFlow";
import { CommandPalette } from "./commandPalette";

let started = false;

/** One-time startup work after the core has booted. */
function startServices() {
  if (started) return;
  started = true;
  initGameEvents();
  startPresence();
  watchForChanges();
  void refreshPremium({ quiet: true }).then(() => pullSync());
  void loadPlans();
  void loadAnnouncements();
  startAutoUpdates();
  startLicenseChecks();

  // Refresh Premium whenever the active Minecraft account changes.
  let account = app.get().settings.activeAccountId;
  app.subscribe(() => {
    const next = app.get().settings.activeAccountId;
    if (next !== account) {
      account = next;
      void refreshPremium({ quiet: true });
    }
  });

  void events.onDrag((e) => {
    if (e.kind === "drop" && e.paths.length) void handleDrop(e.paths);
  });
}

/** Files dropped on the window: modpacks become installations, everything else goes into the current installation. */
async function handleDrop(paths: string[]) {
  try {
    // On My Skin, dropped PNGs are skins.
    if (app.get().route.page === "skins") {
      const pngs = paths.filter((p) => p.toLowerCase().endsWith(".png"));
      if (!pngs.length) {
        toast({ kind: "info", title: "Drop PNG skins", message: "Minecraft skins are 64×64 PNG files." });
        return;
      }
      const { addSkinFiles } = await import("../services/skins");
      const added = await addSkinFiles(pngs);
      toast({ kind: "success", title: added.length === 1 ? "Skin added" : `${added.length} skins added`, message: "They're in your skin library." });
      return;
    }
    const packs = paths.filter((p) => p.toLowerCase().endsWith(".mrpack"));
    for (const pack of packs) await importModpackFile(pack);
    const rest = paths.filter((p) => !packs.includes(p));
    if (rest.length === 0) return;

    const route = app.get().route;
    const inst = (route.page === "instance" ? getInstance(route.id) : undefined) ?? selectedInstance();
    if (!inst) {
      toast({ kind: "info", title: "Create an installation first", message: "Then drop mods or packs onto MOTION to add them." });
      return;
    }
    const jars = rest.filter((p) => p.toLowerCase().endsWith(".jar"));
    const zips = rest.filter((p) => p.toLowerCase().endsWith(".zip"));
    const other = rest.length - jars.length - zips.length;
    if (jars.length) await importContent(inst, "mods", jars);
    const resourcepacks: string[] = [];
    const shaderpacks: string[] = [];
    for (const z of zips) {
      // Shader packs contain a shaders/ folder; everything else is treated as a resource pack.
      const names = await P().zipList(z).catch(() => [] as string[]);
      if (names.some((n) => n.startsWith("shaders/"))) shaderpacks.push(z);
      else resourcepacks.push(z);
    }
    if (resourcepacks.length) await importContent(inst, "resourcepacks", resourcepacks);
    if (shaderpacks.length) await importContent(inst, "shaderpacks", shaderpacks);
    if (other) toast({ kind: "warning", title: `${other} file${other === 1 ? "" : "s"} skipped`, message: `MOTION accepts .jar mods, .zip packs and .mrpack modpacks (${rest.filter((p) => !/\.(jar|zip)$/i.test(p)).map(basename).slice(0, 3).join(", ")}).` });
    navigate({ page: "instance", id: inst.id, tab: jars.length ? "content" : shaderpacks.length ? "shaderpacks" : "resourcepacks" });
    bumpContent();
  } catch (e) {
    reportError("Couldn't add the dropped files", e);
  }
}

function Router() {
  const route = useStore(app, (s) => s.route);
  switch (route.page) {
    case "home":
      return <HomePage />;
    case "library":
      return <LibraryPage />;
    case "instance":
      return <InstancePage key={route.id} id={route.id} tab={route.tab} />;
    case "browse":
      return <BrowsePage type={route.type} instanceId={route.instanceId} />;
    case "project":
      return <ProjectPage key={route.id} id={route.id} instanceId={route.instanceId} />;
    case "performance":
      return <PerformancePage instanceId={route.instanceId} />;
    case "accounts":
      return <AccountsPage />;
    case "premium":
      return <PremiumPage />;
    case "settings":
      return <SettingsPage section={route.section} />;
    case "customize":
      return <CustomizePage instanceId={route.instanceId} />;
    case "mods":
      return <ModsPage instanceId={route.instanceId} category={route.category} />;
    case "skins":
      return <SkinsPage />;
  }
}

/** Loading screen: the theme's art behind the mark, wordmark and a thin accent-coloured loading bar. */
function splashBackgroundPath(): string | null {
  try {
    return localStorage.getItem("blood.splashBg");
  } catch {
    return null;
  }
}

function Splash({ error, onRetry }: { error?: string | null; onRetry?: () => void }) {
  const theme = document.documentElement.dataset.theme || "motion";
  const custom = useCustomBackground(splashBackgroundPath());
  return (
    <div className="relative h-full overflow-hidden">
      <img src={custom ?? `/backgrounds/${theme}.jpg`} alt="" className="absolute inset-0 h-full w-full object-cover opacity-40" draggable={false} />
      <div className="absolute inset-0 bg-bg/60" />
      <div className="relative h-full grid place-items-center">
        <div className="flex flex-col items-center text-center animate-in">
          <BloodMark size={64} />
          <Wordmark height={28} className="mt-6" />
          {error ? (
            <>
              <div className="text-danger mt-4 max-w-md selectable">{error}</div>
              <Button className="mt-5" icon={<Icon.Refresh size={16} />} onClick={onRetry}>
                Try again
              </Button>
            </>
          ) : (
            <>
              <div className="mt-7 h-[2px] w-48 rounded-full bg-white/10 overflow-hidden">
                <div className="h-full w-2/5 rounded-full bg-accent" style={{ animation: "bloom-indeterminate 1.1s ease-in-out infinite" }} />
              </div>
              <div className="text-muted mt-3 text-[12.5px]">Starting MOTION…</div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export function App() {
  const ready = useStore(app, (s) => s.ready);
  const fatal = useStore(app, (s) => s.fatal);
  const route = useStore(app, (s) => s.route);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    void boot(loadInstances).then(() => {
      if (app.get().ready) startServices();
    });
  }, [attempt]);

  // Scroll to top on navigation.
  useEffect(() => {
    document.getElementById("bloom-main")?.scrollTo({ top: 0 });
  }, [route.page, (route as { id?: string }).id]);

  return (
    <div className="relative h-full flex flex-col">
      {ready && <Backdrop />}
      <TitleBar />
      {!ready ? (
        <Splash
          error={fatal}
          onRetry={() => {
            app.set({ fatal: null });
            setAttempt((a) => a + 1);
          }}
        />
      ) : (
        <div className="relative flex-1 flex min-h-0">
          <Sidebar />
          <main id="bloom-main" className="flex-1 min-w-0 overflow-y-auto">
            {route.page === "home" ? (
              <Router />
            ) : (
              <div key={route.page} className="max-w-[1120px] mx-auto px-10 py-8 animate-in">
                <Router />
              </div>
            )}
          </main>
        </div>
      )}
      <Toasts />
      <ConfirmHost />
      <DragOverlay />
      <PremiumWelcome />
      <InstallPlanHost />
      {ready && <CommandPalette />}
    </div>
  );
}
