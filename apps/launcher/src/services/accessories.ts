/**
 * MOTION accessories: wings, an aura and a 3D avatar, one of each. Like capes, the choice is stored on
 * MOTION's server against your Minecraft account, and MOTION Client shows them in game (to
 * other MOTION players too).
 */

import type { AccessoryInfo, AccessoryKind } from "@bloom/shared";
import { createStore } from "./store";
import { API_URL, premiumRequest } from "./premium";

export interface AccessoryChoice extends AccessoryInfo {
  allowed: boolean;
}

export const accessoryState = createStore<{
  status: "idle" | "loading" | "ready" | "error";
  accessories: AccessoryChoice[];
  wings: string | null;
  aura: string | null;
  avatar: string | null;
  error?: string;
}>({ status: "idle", accessories: [], wings: null, aura: null, avatar: null });

export function accessoryImage(a: AccessoryInfo): string | null {
  return a.image ? `${API_URL}${a.image}` : null;
}

export async function loadAccessories(): Promise<void> {
  if (!API_URL) {
    accessoryState.set({ status: "error", error: "This build isn't connected to MOTION's server." });
    return;
  }
  accessoryState.set({ status: "loading", error: undefined });
  try {
    const r = await premiumRequest<{ wings: string | null; aura: string | null; avatar?: string | null; accessories: AccessoryChoice[] }>("GET", "/v1/me/accessories");
    accessoryState.set({ status: "ready", accessories: r.accessories, wings: r.wings, aura: r.aura, avatar: r.avatar ?? null });
  } catch (e) {
    accessoryState.set({ status: "error", error: e instanceof Error ? e.message : String(e) });
  }
}

/** id "" removes that kind. */
export async function chooseAccessory(kind: AccessoryKind, id: string): Promise<void> {
  const r = await premiumRequest<{ wings: string | null; aura: string | null; avatar?: string | null }>("PUT", "/v1/me/accessories", { kind, id });
  accessoryState.set({ wings: r.wings, aura: r.aura, avatar: r.avatar ?? null });
}
