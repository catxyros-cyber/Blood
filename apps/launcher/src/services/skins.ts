/**
 * My Skin: a library of saved skins (kept in skins.json in MOTION's data folder, as PNG data
 * URLs), and applying one to your real Minecraft account.
 *
 * Applying: Mojang's skin API (api.minecraftservices.com/minecraft/profile/skins) takes either a
 * file upload or a URL. The launcher hands the PNG to MOTION's server, which serves it for ten
 * minutes at an unguessable URL; the launcher then asks Mojang to use that URL, signed in with
 * the player's own Minecraft token (which never reaches MOTION's server). The skin only counts
 * as applied when Mojang answers with it as the active skin.
 */

import { BloomError } from "../core/platform";
import { guessModel, legacyMirrors, skinSizeProblem, type SkinModel } from "../core/skins/skin";
import { activeAccount, launchCredentials } from "./accounts";
import { app, P, path } from "./app";
import { native } from "./native";
import { API_URL, premiumRequest } from "./premium";
import { createStore } from "./store";

export interface SavedSkin {
  id: string;
  name: string;
  model: SkinModel;
  /** data:image/png;base64,… (64×64) */
  png: string;
  favorite: boolean;
  addedAt: number;
  lastUsedAt: number | null;
  /** When this skin was last confirmed by Mojang as your account's skin. */
  appliedAt: number | null;
}

export const skinState = createStore<{ loaded: boolean; skins: SavedSkin[]; selectedId: string | null }>({ loaded: false, skins: [], selectedId: null });

function libraryPath() {
  return path(P().dataDir, "skins.json");
}

export async function loadSkins(): Promise<void> {
  let skins: SavedSkin[] = [];
  try {
    const text = await P().readText(libraryPath());
    const v = text ? (JSON.parse(text) as unknown) : [];
    if (Array.isArray(v)) skins = v.filter((s): s is SavedSkin => !!s && typeof s.id === "string" && typeof s.png === "string" && s.png.startsWith("data:image/png;base64,"));
  } catch {
    skins = [];
  }
  skinState.set({ loaded: true, skins, selectedId: skinState.get().selectedId ?? skins[0]?.id ?? null });
}

async function save(skins: SavedSkin[]) {
  skinState.set({ skins });
  await P().writeText(libraryPath(), JSON.stringify(skins, null, 2));
}

function newId() {
  return Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
}

/** Decode a data URL into an image in the webview. */
function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new BloomError("invalid", "That file isn't a readable image."));
    img.src = src;
  });
}

/**
 * Checks and normalises a skin image: must be 64×64 or the old 64×32 (converted to 64×64 by
 * mirroring the right arm and leg, as Minecraft does). Returns a clean PNG data URL and the
 * model it most likely uses.
 */
export async function normaliseSkin(dataUrl: string): Promise<{ png: string; model: SkinModel }> {
  const img = await loadImage(dataUrl);
  const problem = skinSizeProblem(img.naturalWidth, img.naturalHeight);
  if (problem) throw new BloomError("invalid", problem);
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const g = c.getContext("2d")!;
  g.imageSmoothingEnabled = false;
  g.drawImage(img, 0, 0);
  if (img.naturalHeight === 32) {
    for (const { from, to } of legacyMirrors()) {
      g.save();
      g.translate(to.x + to.w, to.y);
      g.scale(-1, 1);
      g.drawImage(c, from.x, from.y, from.w, from.h, 0, 0, to.w, to.h);
      g.restore();
    }
  }
  const data = g.getImageData(0, 0, 64, 64).data;
  const model = guessModel((x, y) => data[(y * 64 + x) * 4 + 3]!);
  return { png: c.toDataURL("image/png"), model };
}

/** Add skins from PNG files on disk (file picker or drag and drop). Returns the new entries. */
export async function addSkinFiles(paths: string[]): Promise<SavedSkin[]> {
  const added: SavedSkin[] = [];
  const errors: string[] = [];
  for (const p of paths) {
    const name = (p.split(/[\\/]/).pop() ?? "Skin").replace(/\.png$/i, "").slice(0, 40) || "Skin";
    try {
      if (!/\.png$/i.test(p)) throw new BloomError("invalid", "Skins are PNG files.");
      const raw = await native.readDataUrl(p, 512 * 1024);
      if (!raw) throw new BloomError("not_found", "The file couldn't be read (or it's over 512 KB).");
      const { png, model } = await normaliseSkin(raw);
      added.push({ id: newId(), name, model, png, favorite: false, addedAt: Date.now(), lastUsedAt: null, appliedAt: null });
    } catch (e) {
      errors.push(`${name}: ${e instanceof Error ? e.message : String(e)}`);
    }
  }
  if (added.length) {
    await save([...added, ...skinState.get().skins]);
    skinState.set({ selectedId: added[0]!.id });
  }
  if (errors.length && !added.length) throw new BloomError("invalid", errors.join("\n"));
  return added;
}

/** Save a skin made in the editor (or update one). */
export async function saveSkin(skin: Omit<SavedSkin, "id" | "addedAt" | "lastUsedAt" | "appliedAt" | "favorite"> & { id?: string }): Promise<SavedSkin> {
  const skins = skinState.get().skins;
  const existing = skin.id ? skins.find((s) => s.id === skin.id) : undefined;
  const next: SavedSkin = existing
    ? { ...existing, name: skin.name, model: skin.model, png: skin.png }
    : { id: newId(), name: skin.name, model: skin.model, png: skin.png, favorite: false, addedAt: Date.now(), lastUsedAt: null, appliedAt: null };
  await save(existing ? skins.map((s) => (s.id === next.id ? next : s)) : [next, ...skins]);
  skinState.set({ selectedId: next.id });
  return next;
}

export async function updateSkin(id: string, patch: Partial<Pick<SavedSkin, "name" | "model" | "favorite">>) {
  await save(skinState.get().skins.map((s) => (s.id === id ? { ...s, ...patch, name: (patch.name ?? s.name).slice(0, 40) || s.name } : s)));
}

export async function removeSkin(id: string) {
  const skins = skinState.get().skins.filter((s) => s.id !== id);
  await save(skins);
  if (skinState.get().selectedId === id) skinState.set({ selectedId: skins[0]?.id ?? null });
}

export function selectSkin(id: string) {
  skinState.set({ selectedId: id });
}

/** Your account's current skin (from Mojang), as a URL the preview can show. */
export function accountSkinUrl(): string | null {
  return activeAccount()?.skinUrl ?? null;
}

interface MojangProfile {
  id: string;
  name: string;
  skins?: { id: string; state: string; url: string; variant?: string }[];
}

/**
 * Apply a saved skin to the signed-in Minecraft account. Resolves only after Mojang confirms
 * the new skin is active; throws a readable error otherwise.
 */
export async function applySkin(id: string, model: SkinModel): Promise<{ url: string }> {
  const skin = skinState.get().skins.find((s) => s.id === id);
  if (!skin) throw new BloomError("not_found", "That skin isn't in your library any more.");
  const account = activeAccount();
  if (!account) throw new BloomError("signed_out", "Sign in with Microsoft first (Accounts).");
  if (!API_URL) throw new BloomError("api_unconfigured", "This build isn't connected to MOTION's server, which hands the skin to Mojang.");
  const creds = await launchCredentials(account);
  if (creds.offline) throw new BloomError("offline", "Couldn't reach Microsoft to refresh your sign-in. Check your connection and try again.");

  // 1. Give the PNG to MOTION's server for a short-lived public URL.
  const { url } = await premiumRequest<{ url: string }>("POST", "/v1/me/skin-upload", { png: skin.png });

  // 2. Ask Mojang to use it.
  const res = await P().request({
    method: "POST",
    url: "https://api.minecraftservices.com/minecraft/profile/skins",
    headers: { Authorization: `Bearer ${creds.accessToken}`, Accept: "application/json" },
    json: { variant: model, url },
    timeoutMs: 30_000,
  });
  if (res.status === 401) throw new BloomError("unauthorized", "Mojang didn't accept your sign-in. Sign out and back in (Accounts), then try again.");
  if (res.status === 429) throw new BloomError("rate_limited", "Mojang is limiting skin changes right now. Wait a minute and try again.");
  if (res.status < 200 || res.status >= 300) {
    let detail = "";
    try {
      const b = JSON.parse(res.body) as { errorMessage?: string; error?: string };
      detail = b.errorMessage ?? b.error ?? "";
    } catch {
      /* not JSON */
    }
    throw new BloomError(`mojang_${res.status}`, `Mojang refused the skin (HTTP ${res.status})${detail ? `: ${detail}` : "."}`);
  }

  // 3. Only report success when Mojang lists a new active skin.
  let profile: MojangProfile;
  try {
    profile = JSON.parse(res.body) as MojangProfile;
  } catch {
    throw new BloomError("mojang_unexpected", "Mojang answered, but not with your profile. Check your skin on minecraft.net.");
  }
  const active = profile.skins?.find((s) => s.state === "ACTIVE");
  if (!active) throw new BloomError("mojang_unexpected", "Mojang didn't report an active skin. Check your skin on minecraft.net.");

  const now = Date.now();
  await save(skinState.get().skins.map((s) => (s.id === id ? { ...s, model, lastUsedAt: now, appliedAt: now } : { ...s, appliedAt: null })));
  // Show the new skin for this account straight away (Mojang's URL for it).
  app.set((st) => ({ accounts: st.accounts.map((a) => (a.id === account.id ? { ...a, skinUrl: active.url } : a)) }));
  return { url: active.url };
}
