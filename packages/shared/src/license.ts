/**
 * Premium license keys: format, normalisation and the request/response shapes shared by
 * the MOTION server and the launcher.
 *
 * A key looks like MOTION-7K2QF-M9XRT-4HZ8W-PN3D6: 20 random characters from Crockford's
 * base32 alphabet (100 bits), so keys can't be guessed. People often mistype O/0 and I/1/L,
 * so those are folded together before checking.
 */

import { v, type Infer } from "./validate";

export const KEY_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
export const KEY_LENGTH = 20;
export const KEY_PREFIX = "MOTION";
/** Keys handed out before the rename start with BLOOD-; they stay valid. */
const OLD_KEY_PREFIXES = ["BLOOD"];
/** How long a player must wait between moving their key to another PC. */
export const LICENSE_MOVE_COOLDOWN_MS = 24 * 60 * 60 * 1000;

/** The 20 key characters (no prefix or dashes), or null if this can't be a MOTION key. */
export function normalizeKey(input: string): string | null {
  let s = input.toUpperCase().replace(/[\s\-_]/g, "");
  for (const p of [KEY_PREFIX, ...OLD_KEY_PREFIXES]) {
    if (s.startsWith(p) && s.length === p.length + KEY_LENGTH) {
      s = s.slice(p.length);
      break;
    }
  }
  s = s.replace(/O/g, "0").replace(/[IL]/g, "1");
  if (s.length !== KEY_LENGTH) return null;
  for (const ch of s) if (!KEY_ALPHABET.includes(ch)) return null;
  return s;
}

/** MOTION-XXXXX-XXXXX-XXXXX-XXXXX */
export function formatKey(normalized: string): string {
  return [KEY_PREFIX, ...(normalized.match(/.{1,5}/g) ?? [])].join("-");
}

/** Last group of a key, shown in lists so a key can be recognised without revealing it. */
export function keyHint(normalized: string): string {
  return normalized.slice(-5);
}

/** The launcher sends a SHA-256 of the PC's machine id (never the id itself). */
export const LicenseRequest = v.object({
  key: v.string({ min: 20, max: 40 }),
  hwid: v.string({ min: 64, max: 64, pattern: /^[0-9a-f]{64}$/ }),
});
export type LicenseRequest = Infer<typeof LicenseRequest>;

export interface LicenseStatus {
  /** Premium is active on this PC. */
  valid: boolean;
  /** Epoch ms; null = lifetime key. */
  expiresAt: number | null;
  /** The key is activated on a different PC. */
  boundElsewhere: boolean;
  /** Epoch ms when the key can next be moved to another PC (null = it can be moved now). */
  nextMoveAt: number | null;
  /** Last 5 characters of the key. */
  hint: string;
}

export const AdminCreateKeys = v.object({
  count: v.number({ min: 1, max: 200, int: true }),
  /** Days of Premium from activation; omit for lifetime. */
  days: v.optional(v.number({ min: 1, max: 3650, int: true })),
  label: v.optional(v.string({ max: 80, trim: true })),
});
export type AdminCreateKeys = Infer<typeof AdminCreateKeys>;

export const AdminKeyAction = v.object({
  id: v.string({ min: 36, max: 36, pattern: /^[0-9a-f-]{36}$/ }),
  action: v.literal("revoke", "restore", "unbind"),
});
export type AdminKeyAction = Infer<typeof AdminKeyAction>;

export interface AdminKeyInfo {
  id: string;
  hint: string;
  label: string;
  durationDays: number | null;
  createdAt: number;
  redeemedAt: number | null;
  expiresAt: number | null;
  bound: boolean;
  lastMoveAt: number | null;
  revoked: boolean;
}

// ---------------------------------------------------------------- capes

/** Who may wear a cape. */
export type CapeAccess = "everyone" | "premium";

export interface CapeInfo {
  id: string;
  name: string;
  access: CapeAccess;
  /** Animation frames (1 = still). The image is a vertical strip of frames, each width × width/2. */
  frames: number;
  /** Milliseconds per frame. */
  frameMs: number;
  /** Image path on the MOTION server, e.g. /v1/capes/<id>.png */
  image: string;
  /** Image width in pixels (64 = classic pixel-art cape, 128–1024 = HD). Missing on older servers. */
  width?: number;
}

/**
 * Cape PNGs: Minecraft's cape layout (64×32) or an HD multiple of it, up to 2048×1024 per frame.
 * Animated capes stack their frames vertically (64×32 frames → 64×(32·frames)), up to 64 frames.
 */
export const CAPE_MAX_BYTES = 4 * 1024 * 1024;
export const CAPE_MAX_FRAMES = 64;

/** Number of frames in a cape image of this size (0 if the size isn't valid). */
export function capeFrames(w: number, h: number): number {
  const fh = w / 2;
  if (!Number.isInteger(fh) || fh <= 0 || h % fh !== 0) return 0;
  return h / fh;
}

export function capeSizeProblem(w: number, h: number): string | null {
  if (w < 64 || w > 2048 || w % 64 !== 0) return `A cape must be 64, 128, 256, 512, 1024 or 2048 pixels wide. This one is ${w}.`;
  const frames = capeFrames(w, h);
  if (frames < 1) return `A cape frame must be twice as wide as it is tall (64×32, 128×64, …). This image is ${w}×${h}.`;
  if (frames > CAPE_MAX_FRAMES) return `An animated cape can have at most ${CAPE_MAX_FRAMES} frames. This one has ${frames}.`;
  if (w * h > 2048 * 1024 * 4) return "That animation is too big. Use fewer frames or a smaller size.";
  return null;
}

export const AdminCreateCape = v.object({
  name: v.string({ min: 1, max: 40, trim: true }),
  access: v.literal("everyone", "premium"),
  /** Base64 PNG (animated: a vertical strip of frames; the admin page converts GIFs). */
  png: v.string({ min: 16, max: 5_600_000 }),
  /** Milliseconds per frame for animated capes. */
  frameMs: v.optional(v.number({ min: 20, max: 5000, int: true })),
});
export type AdminCreateCape = Infer<typeof AdminCreateCape>;

export const AdminCapeAction = v.object({
  id: v.string({ min: 36, max: 36, pattern: /^[0-9a-f-]{36}$/ }),
  action: v.literal("enable", "disable", "delete", "everyone", "premium"),
});
export type AdminCapeAction = Infer<typeof AdminCapeAction>;

/** capeId "" removes the cape. */
export const SetCapeRequest = v.object({
  capeId: v.string({ min: 0, max: 36, pattern: /^([0-9a-f-]{36})?$/ }),
});
export type SetCapeRequest = Infer<typeof SetCapeRequest>;

// --- accessories (wings & auras) ----------------------------------------------

export type AccessoryKind = "wings" | "aura";
export const AURA_STYLES = ["glow", "embers", "sparkles", "orbit"] as const;
export type AuraStyle = (typeof AURA_STYLES)[number];
export const ACCESSORY_MAX_BYTES = 4 * 1024 * 1024;

/** An accessory the owner published. Wings have an image; auras have a colour and style. */
export interface AccessoryInfo {
  id: string;
  name: string;
  kind: AccessoryKind;
  access: CapeAccess;
  /** Wings only: image path on the server, e.g. /v1/accessories/<id>.png */
  image?: string;
  /** Auras: particle colour (#rrggbb). Wings: an accent colour for previews. */
  color: string;
  /** Auras only. */
  style?: AuraStyle;
}

export const AdminCreateAccessory = v.object({
  name: v.string({ min: 1, max: 40, trim: true }),
  kind: v.literal("wings", "aura"),
  access: v.literal("everyone", "premium"),
  /** Wings: base64 PNG of one wing (root on the left edge, tip on the right). */
  png: v.optional(v.string({ min: 16, max: 5_600_000 })),
  color: v.string({ min: 7, max: 7, pattern: /^#[0-9a-fA-F]{6}$/ }),
  style: v.optional(v.literal("glow", "embers", "sparkles", "orbit")),
});
export type AdminCreateAccessory = Infer<typeof AdminCreateAccessory>;

/** id "" removes the accessory of that kind. */
export const SetAccessoryRequest = v.object({
  kind: v.literal("wings", "aura"),
  id: v.string({ min: 0, max: 36, pattern: /^([0-9a-f-]{36})?$/ }),
});
export type SetAccessoryRequest = Infer<typeof SetAccessoryRequest>;

// --- community skins --------------------------------------------------------

export type SkinModel = "classic" | "slim";
export interface CommunitySkinInfo {
  id: string;
  name: string;
  model: SkinModel;
  /** Public PNG URL on the MOTION API. */
  image: string;
}

export const COMMUNITY_SKIN_MAX_BYTES = 64 * 1024;
export const AdminCreateSkin = v.object({
  name: v.string({ min: 1, max: 40, trim: true }),
  model: v.literal("classic", "slim"),
  /** Base64 PNG skin atlas; must decode to exactly 64×64 pixels. */
  png: v.string({ min: 16, max: 90_000 }),
});
export type AdminCreateSkin = Infer<typeof AdminCreateSkin>;

export const AdminSkinAction = v.object({
  id: v.string({ min: 36, max: 36, pattern: /^[0-9a-f-]{36}$/ }),
  action: v.literal("enable", "disable", "delete"),
});
export type AdminSkinAction = Infer<typeof AdminSkinAction>;

// --- tags ---------------------------------------------------------------------

/** A player's MOTION tag, shown before their name to MOTION users. */
export interface TagInfo {
  text: string;
  color: string;
}

/** Someone with a tag, for the launcher's "MOTION team" list. */
export interface TeamMember extends TagInfo {
  username: string;
  online: boolean;
  playing: boolean;
}

/** text "" removes the tag. */
export const AdminSetTag = v.object({
  username: v.string({ min: 1, max: 16, pattern: /^[A-Za-z0-9_]{1,16}$/ }),
  text: v.string({ min: 0, max: 16, pattern: /^[A-Za-z0-9 _+!*#&.\-]{0,16}$/ }),
  color: v.string({ min: 7, max: 7, pattern: /^#[0-9a-fA-F]{6}$/ }),
});
export type AdminSetTag = Infer<typeof AdminSetTag>;

export const PresenceRequest = v.object({ playing: v.boolean() });
export type PresenceRequest = Infer<typeof PresenceRequest>;

/** Launcher open within this long counts as online. */
export const ONLINE_WINDOW_MS = 150_000;
