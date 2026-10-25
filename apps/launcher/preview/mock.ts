// Temporary (launcher redesign): fakes the Tauri backend with an in-memory file system
// so the launcher UI can be rendered and screenshotted without the desktop app.
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";

const fs = new Map<string, string>();
const D = "/data";
const now = Date.now();
const inst = (id: string, name: string, gameVersion: string, loader: string, extra: Record<string, unknown> = {}) =>
  fs.set(`${D}/instances/${id}/.bloom/instance.json`, JSON.stringify({ id, name, gameVersion, loader, loaderVersion: "0.17.2", createdAt: now - 9e8, ...extra }));

const params = new URLSearchParams(location.search);
if (!params.has("empty")) {
  inst("motion-pvp", "MOTION PvP", "1.21.11", "fabric", { lastPlayedAt: now - 3_600_000, totalPlayMs: 41 * 3_600_000, bloomClient: { enabled: true, version: "0.2.23" }, performancePreset: "balanced", memory: { minMb: 1024, maxMb: 4096 }, recentPlays: [{ kind: "server", target: "mc.hypixel.net", name: "Hypixel", at: now - 4e6 }, { kind: "world", target: "Survival", name: "Survival", at: now - 9e7 }], lastLaunch: { prepareMs: 900, startupMs: 14200, at: now - 3.6e6 } });
  inst("survival", "Survival 26.3", "26.3", "fabric", { lastPlayedAt: now - 86_400_000 * 2, totalPlayMs: 12 * 3_600_000 });
  inst("vanilla", "Vanilla", "1.21.10", "vanilla", { lastPlayedAt: now - 86_400_000 * 9, totalPlayMs: 3_600_000 });
  // ?many: a fuller library, for timing startup.
  if (params.has("many")) for (let i = 0; i < 16; i++) inst(`extra-${i}`, `Modpack ${i + 1}`, "1.21.11", "fabric", { lastPlayedAt: now - 86_400_000 * (20 + i) });
  for (const jar of ["sodium-fabric-0.6.13+mc1.21.11.jar", "lithium-fabric-0.16.2+mc1.21.11.jar", "fabric-api-0.141.6+1.21.11.jar", "iris-fabric-1.9.2+mc1.21.11.jar"])
    fs.set(`${D}/instances/motion-pvp/mods/${jar}`, "jar");
  fs.set(`${D}/instances/motion-pvp/saves/Survival/level.dat`, "nbt");
  fs.set(`${D}/instances/motion-pvp/saves/Creative test/level.dat`, "nbt");
  fs.set(`${D}/accounts.json`, JSON.stringify([{ id: "a1", username: "Tonmoy", xuid: "x", addedAt: now, lastVerifiedAt: now, ...(params.has("noskin") ? {} : { skinUrl: "/preview/skin.png" }) }]));
  fs.set(`${D}/settings.json`, JSON.stringify({ activeAccountId: "a1", selectedInstanceId: "motion-pvp", brand: "motion", theme: params.get("theme") ?? "motion" }));
}

function list(path: string) {
  const prefix = path.endsWith("/") ? path : `${path}/`;
  const names = new Map<string, boolean>();
  for (const k of fs.keys()) {
    if (!k.startsWith(prefix)) continue;
    const rest = k.slice(prefix.length);
    const [first, ...more] = rest.split("/");
    names.set(first, more.length > 0 || names.get(first) === true);
  }
  return [...names].map(([name, isDir]) => ({ name, path: prefix + name, isDir, size: 10, modifiedMs: now }));
}

const notFound = (path: string) => ({ code: "not_found", message: `Not found: ${path}` });

mockWindows("main");
// Call counts per command (read by the benchmark), and an optional delay per call (?lat=ms)
// standing in for the real IPC round trip.
const counts: Record<string, number> = {};
(window as unknown as { __ipc: Record<string, number> }).__ipc = counts;
const latency = Number(params.get("lat") ?? 0);
mockIPC(async (cmd, a: any) => {
  counts[cmd] = (counts[cmd] ?? 0) + 1;
  if (latency && !cmd.startsWith("plugin:")) await new Promise((r) => setTimeout(r, latency));
  return handle(cmd, a);
});
function handle(cmd: string, a: any): unknown {
  const p = a?.path as string;
  switch (cmd) {
    case "app_paths":
      return { dataDir: D, sep: "/", platform: "windows", arch: "x86_64", version: "1.0.29" };
    case "system_info":
      return { totalMemory: 16 * 2 ** 30, availableMemory: 9 * 2 ** 30, cpuBrand: "AMD Ryzen 7 5800X", logicalCores: 16, physicalCores: 8, os: "Windows 11", gpus: ["NVIDIA GeForce RTX 3070"] };
    case "fs_read_text":
      if (fs.has(p)) return fs.get(p);
      throw notFound(p);
    case "fs_write_text":
      fs.set(p, a.contents);
      return null;
    case "fs_exists":
      return fs.has(p) || [...fs.keys()].some((k) => k.startsWith(`${p}/`));
    case "fs_list":
      return list(p);
    case "fs_stat":
      return fs.has(p) ? { name: p.split("/").pop(), path: p, isDir: false, size: fs.get(p)!.length, modifiedMs: now } : null;
    case "fs_mkdir":
    case "fs_trash":
    case "fs_remove_internal":
    case "autostart_set":
    case "secret_set":
    case "secret_delete":
    case "cancel_task":
      return null;
    case "fs_read_tail":
    case "fs_read_data_url":
    case "zip_read_data_url":
    case "zip_read_text":
    case "secret_get":
    case "bloom_client_bundle":
      return null;
    case "zip_list":
    case "find_javas":
    case "running_games":
      return [];
    case "autostart_get":
      return false;
    case "machine_id":
      return "preview-machine";
    case "http_request":
      return { status: 503, headers: {}, body: "" };
    case "plugin:event|listen":
      return Math.floor(Math.random() * 1e6);
    case "plugin:event|unlisten":
      return null;
    default:
      if (cmd.startsWith("plugin:")) return null;
      throw { code: "unsupported", message: `preview: ${cmd}` };
  }
}

const route = params.get("page");
if (route) {
  // The app reads its starting route from the store; navigate after boot.
  const go = () => import("../src/services/app").then(({ app, navigate }) => {
    if (!app.get().ready) return setTimeout(go, 100);
    navigate(JSON.parse(route));
  });
  setTimeout(go, 300);
}
