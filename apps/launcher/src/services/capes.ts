/**
 * BLOOD capes: pick one of the capes the owner published. The choice is stored on BLOOD's server
 * against your Minecraft account; BLOOD Client shows it in game (to other BLOOD users too).
 */

import type { CapeInfo } from "@bloom/shared";
import { createStore } from "./store";
import { API_URL, premiumRequest } from "./premium";

export interface CapeChoice extends CapeInfo {
  allowed: boolean;
}

export const capeState = createStore<{ status: "idle" | "loading" | "ready" | "error"; capes: CapeChoice[]; capeId: string | null; error?: string }>({
  status: "idle",
  capes: [],
  capeId: null,
});

/** Full image URL for a cape. */
export function capeImage(c: CapeInfo): string {
  return `${API_URL}${c.image}`;
}

export async function loadCapes(): Promise<void> {
  if (!API_URL) {
    capeState.set({ status: "error", error: "This build isn't connected to BLOOD's server." });
    return;
  }
  capeState.set({ status: "loading", error: undefined });
  try {
    const r = await premiumRequest<{ capeId: string | null; capes: CapeChoice[] }>("GET", "/v1/me/cape");
    capeState.set({ status: "ready", capes: r.capes, capeId: r.capeId });
  } catch (e) {
    capeState.set({ status: "error", error: e instanceof Error ? e.message : String(e) });
  }
}

/** "" removes the cape. */
export async function chooseCape(capeId: string): Promise<void> {
  const r = await premiumRequest<{ capeId: string | null }>("PUT", "/v1/me/cape", { capeId });
  capeState.set({ capeId: r.capeId });
}
