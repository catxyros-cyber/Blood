import { test } from "node:test";
import assert from "node:assert/strict";
import { gzipSync } from "node:zlib";
import { readNbt, readNbtFile } from "../util/nbt";
import { parseLevel, parseServers, quickPlaySupport, splitAddress, validServerAddress } from "./worlds";
import { buildLaunchCommand, type LaunchContext } from "./args";
import { v1211, v1122 } from "../test/fixtures";

// ---- a tiny NBT writer, just for building fixtures
type W = { t: number; v: unknown };
const enc = new TextEncoder();
const str = (s: string) => {
  const b = enc.encode(s);
  return [b.length >> 8, b.length & 255, ...b];
};
const i32 = (n: number) => [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255];
const i64 = (n: number) => {
  const b = new Uint8Array(8);
  new DataView(b.buffer).setBigInt64(0, BigInt(n));
  return [...b];
};
const S = (v: string): W => ({ t: 8, v });
const B = (v: number): W => ({ t: 1, v });
const I = (v: number): W => ({ t: 3, v });
const L = (v: number): W => ({ t: 4, v });
const C = (v: Record<string, W>): W => ({ t: 10, v });
const LIST = (t: number, v: W[]): W => ({ t: 9, v: { t, items: v } });
function payload(w: W): number[] {
  switch (w.t) {
    case 1:
      return [(w.v as number) & 255];
    case 3:
      return i32(w.v as number);
    case 4:
      return i64(w.v as number);
    case 8:
      return str(w.v as string);
    case 9: {
      const { t, items } = w.v as { t: number; items: W[] };
      return [t, ...i32(items.length), ...items.flatMap(payload)];
    }
    case 10: {
      const out: number[] = [];
      for (const [k, x] of Object.entries(w.v as Record<string, W>)) out.push(x.t, ...str(k), ...payload(x));
      return [...out, 0];
    }
  }
  throw new Error("unsupported");
}
const file = (root: Record<string, W>) => new Uint8Array([10, ...str(""), ...payload(C(root))]);

test("nbt: reads servers.dat and keeps the in-game order", () => {
  const bytes = file({
    servers: LIST(10, [
      C({ name: S("Hypixel"), ip: S("mc.hypixel.net"), icon: S("iVBORw0KGgo=") }),
      C({ ip: S("play.example.net:25570"), acceptTextures: B(1) }),
      C({ name: S("broken") }),
    ]),
  });
  const servers = parseServers(readNbt(bytes));
  assert.deepEqual(servers, [
    { name: "Hypixel", address: "mc.hypixel.net", icon: "data:image/png;base64,iVBORw0KGgo=" },
    { name: "play.example.net:25570", address: "play.example.net:25570", icon: undefined },
  ]);
});

test("nbt: reads a gzip level.dat", async () => {
  const bytes = gzipSync(
    file({
      Data: C({ LevelName: S("My Base"), GameType: I(1), hardcore: B(0), allowCommands: B(1), LastPlayed: L(1729771200000), Version: C({ Name: S("1.21.1") }) }),
    }),
  );
  const level = parseLevel(await readNbtFile(new Uint8Array(bytes)));
  assert.deepEqual(level, { levelName: "My Base", gameMode: "creative", hardcore: false, cheats: true, lastPlayed: 1729771200000, versionName: "1.21.1" });
});

test("nbt: truncated or hostile data throws instead of hanging", () => {
  assert.throws(() => readNbt(new Uint8Array([10, 0, 0, 9])));
  assert.throws(() => readNbt(new Uint8Array([8, 0, 0])), /root/);
  // A list claiming 2^31 entries.
  assert.throws(() => readNbt(new Uint8Array([10, 0, 0, 9, 0, 1, 97, 3, 0x7f, 0xff, 0xff, 0xff])));
});

test("quick play: which versions can open a world", () => {
  assert.deepEqual(quickPlaySupport("1.21.1"), { world: true, server: true });
  assert.deepEqual(quickPlaySupport("1.20"), { world: true, server: true });
  assert.deepEqual(quickPlaySupport("1.19.4"), { world: false, server: true });
  assert.deepEqual(quickPlaySupport("24w14a"), { world: true, server: true });
  assert.deepEqual(quickPlaySupport("22w45a"), { world: false, server: true });
});

test("quick play: server addresses", () => {
  assert.deepEqual(splitAddress("mc.hypixel.net"), { host: "mc.hypixel.net", port: 25565 });
  assert.deepEqual(splitAddress("play.example.net:25570"), { host: "play.example.net", port: 25570 });
  assert.deepEqual(splitAddress("[2001:db8::1]:25566"), { host: "2001:db8::1", port: 25566 });
  assert.ok(validServerAddress("play.example.net:25570"));
  assert.ok(!validServerAddress("http://evil"));
  assert.ok(!validServerAddress("--jvm-arg"));
  assert.ok(!validServerAddress("a b"));
  assert.ok(!validServerAddress("host:99999"));
});

const ctx = (over: Partial<LaunchContext> = {}): LaunchContext => ({
  env: { os: "windows", arch: "x64" },
  username: "Steve",
  uuid: "u",
  accessToken: "t",
  xuid: "x",
  clientId: "c",
  userType: "msa",
  versionName: "1.21.1",
  gameDir: "C:\\g",
  assetsRoot: "C:\\a",
  assetsIndexName: "17",
  nativesDir: "C:\\n",
  librariesDir: "C:\\l",
  classpath: ["C:\\a.jar"],
  launcherName: "motion",
  launcherVersion: "1",
  memory: { minMb: 512, maxMb: 4096 },
  extraJvmArgs: [],
  extraGameArgs: [],
  ...over,
});

test("launch args: Quick Play opens a world on 1.20+", () => {
  const g = buildLaunchCommand(v1211, ctx({ quickPlay: { kind: "world", folder: "My Base" } })).gameArgs;
  assert.equal(g[g.indexOf("--quickPlaySingleplayer") + 1], "My Base");
});

test("launch args: joining a server on an old version uses --server/--port", () => {
  const g = buildLaunchCommand(v1122, ctx({ versionName: "1.12.2", quickPlay: { kind: "server", address: "play.example.net:25570" } })).gameArgs;
  assert.equal(g[g.indexOf("--server") + 1], "play.example.net");
  assert.equal(g[g.indexOf("--port") + 1], "25570");
});

test("launch args: joining a server on 1.20+ uses Quick Play", () => {
  const g = buildLaunchCommand(v1211, ctx({ quickPlay: { kind: "server", address: "mc.hypixel.net" } })).gameArgs;
  assert.equal(g[g.indexOf("--quickPlayMultiplayer") + 1], "mc.hypixel.net");
  assert.ok(!g.includes("--server"));
  assert.ok(!g.includes("--quickPlaySingleplayer"));
});
