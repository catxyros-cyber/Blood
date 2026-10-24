/** Reading the player's own server list (servers.dat) and world info (level.dat), plus Quick Play support. */

import { nbtCompound, nbtList, nbtNumber, nbtString, type NbtCompound } from "../util/nbt";
import { compareVersions, parseVersion } from "../util/versions";

export interface SavedServer {
  name: string;
  /** host, host:port or [ipv6]:port, exactly as the player typed it in Minecraft. */
  address: string;
  /** PNG data URL of the server's icon, when Minecraft has cached one. */
  icon?: string;
}

/** servers.dat → the multiplayer list, in the order the player sees it in game. */
export function parseServers(root: NbtCompound): SavedServer[] {
  const out: SavedServer[] = [];
  for (const v of nbtList(root.servers)) {
    const s = nbtCompound(v);
    const address = nbtString(s?.ip)?.trim();
    if (!s || !address) continue;
    const icon = nbtString(s.icon);
    out.push({
      name: nbtString(s.name)?.trim() || address,
      address,
      icon: icon && /^[A-Za-z0-9+/=]+$/.test(icon) ? `data:image/png;base64,${icon}` : undefined,
    });
  }
  return out;
}

export type GameMode = "survival" | "creative" | "adventure" | "spectator";
const MODES: GameMode[] = ["survival", "creative", "adventure", "spectator"];

export interface WorldDetails {
  /** The name shown in Minecraft's world list (the folder name can differ). */
  levelName?: string;
  gameMode?: GameMode;
  hardcore: boolean;
  cheats: boolean;
  /** Epoch ms of the last time the world was played. */
  lastPlayed?: number;
  /** Minecraft version the world was last saved with. */
  versionName?: string;
}

/** level.dat (already decompressed and parsed) → what the world list shows. */
export function parseLevel(root: NbtCompound): WorldDetails {
  const data = nbtCompound(root.Data) ?? {};
  const mode = nbtNumber(data.GameType);
  return {
    levelName: nbtString(data.LevelName)?.trim() || undefined,
    gameMode: mode !== undefined ? MODES[mode] : undefined,
    hardcore: nbtNumber(data.hardcore) === 1,
    cheats: nbtNumber(data.allowCommands) === 1,
    lastPlayed: nbtNumber(data.LastPlayed),
    versionName: nbtString(nbtCompound(data.Version)?.Name),
  };
}

/**
 * Quick Play (join a world or server straight from the launcher) arrived in Minecraft 1.20.
 * Older versions can still join a server with --server/--port, but can't open a world.
 */
export function quickPlaySupport(gameVersion: string): { world: boolean; server: boolean } {
  const parsed = parseVersion(gameVersion);
  if (!parsed) {
    // Snapshots like 24w14a: Quick Play exists in every snapshot since 23w14a.
    const snap = /^(\d{2})w(\d{2})[a-z]$/.exec(gameVersion);
    const modern = snap ? Number(snap[1]) * 100 + Number(snap[2]) >= 2314 : false;
    return { world: modern, server: true };
  }
  const modern = compareVersions(gameVersion, "1.20") >= 0;
  return { world: modern, server: true };
}

/** "play.example.net:25570" → host and port, for the pre-1.20 --server/--port arguments. */
export function splitAddress(address: string): { host: string; port: number } {
  const a = address.trim();
  const v6 = /^\[([^\]]+)\](?::(\d+))?$/.exec(a);
  if (v6) return { host: v6[1] ?? a, port: v6[2] ? Number(v6[2]) : 25565 };
  const i = a.lastIndexOf(":");
  if (i > 0 && a.indexOf(":") === i && /^\d+$/.test(a.slice(i + 1))) return { host: a.slice(0, i), port: Number(a.slice(i + 1)) };
  return { host: a, port: 25565 };
}

/** A server address the player typed: host[:port], no spaces or schemes. */
export function validServerAddress(address: string): boolean {
  const a = address.trim();
  if (!a || a.length > 255 || /\s|\/|^-/.test(a)) return false;
  const { host, port } = splitAddress(a);
  return /^[A-Za-z0-9.\-_:]+$/.test(host) && port > 0 && port < 65536;
}
