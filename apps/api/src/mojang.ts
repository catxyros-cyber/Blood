/**
 * Ownership verification with Mojang's session server — the same check every
 * Minecraft multiplayer server performs. The launcher calls `join` with its own
 * Minecraft session and our random serverId; we ask `hasJoined`. A 200 proves
 * the player controls a session for that profile (and therefore owns the game).
 */

import { normalizeUuid } from "@bloom/shared";
import type { Fetch } from "./stripe";

export interface VerifiedProfile {
  uuid: string;
  username: string;
}

export class MojangUnavailable extends Error {}

export async function hasJoined(username: string, serverId: string, fetchImpl: Fetch = fetch): Promise<VerifiedProfile | null> {
  const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`;
  let res: Response;
  try {
    res = await fetchImpl(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(10_000) });
  } catch {
    throw new MojangUnavailable("Mojang's session server could not be reached");
  }
  if (res.status === 204 || res.status === 403 || res.status === 404) return null;
  if (!res.ok) throw new MojangUnavailable(`Mojang's session server returned HTTP ${res.status}`);
  const body = (await res.json().catch(() => null)) as { id?: string; name?: string } | null;
  if (!body?.id || !body.name) return null;
  return { uuid: normalizeUuid(body.id), username: body.name };
}
