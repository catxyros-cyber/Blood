/** The player's worlds and saved servers for an installation, read from Minecraft's own files. */

import type { Instance } from "../core/instances/model";
import { readNbtFile } from "../core/util/nbt";
import { parseLevel, parseServers, type SavedServer, type WorldDetails } from "../core/mojang/worlds";
import { path } from "./app";
import { instanceGameDir } from "./instances";
import { native } from "./native";

function fromDataUrl(url: string): Uint8Array {
  const b64 = url.slice(url.indexOf(",") + 1);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function readNbtAt(file: string, maxBytes: number) {
  const url = await native.readDataUrl(file, maxBytes);
  return url ? readNbtFile(fromDataUrl(url)) : null;
}

/** The multiplayer list exactly as the game shows it. Empty when the player hasn't added any server yet. */
export async function listServers(inst: Instance): Promise<SavedServer[]> {
  try {
    const root = await readNbtAt(path(instanceGameDir(inst), "servers.dat"), 4 * 1024 * 1024);
    return root ? parseServers(root) : [];
  } catch (e) {
    console.warn("servers.dat", e);
    return [];
  }
}

/** Name, game mode, last played and icon for one world folder. Never throws: missing details are left out. */
export async function worldDetails(worldPath: string): Promise<WorldDetails & { icon?: string }> {
  const [level, icon] = await Promise.all([
    readNbtAt(path(worldPath, "level.dat"), 8 * 1024 * 1024)
      .then((r) => (r ? parseLevel(r) : null))
      .catch(() => null),
    native.readDataUrl(path(worldPath, "icon.png"), 512 * 1024).catch(() => null),
  ]);
  return { hardcore: false, cheats: false, ...(level ?? {}), icon: icon ?? undefined };
}
