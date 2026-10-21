/**
 * Premium license keys: format, normalisation and the request/response shapes shared by
 * the BLOOD server and the launcher.
 *
 * A key looks like BLOOD-7K2QF-M9XRT-4HZ8W-PN3D6: 20 random characters from Crockford's
 * base32 alphabet (100 bits), so keys can't be guessed. People often mistype O/0 and I/1/L,
 * so those are folded together before checking.
 */

import { v, type Infer } from "./validate";

export const KEY_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
export const KEY_LENGTH = 20;
export const KEY_PREFIX = "BLOOD";
/** How long a player must wait between moving their key to another PC. */
export const LICENSE_MOVE_COOLDOWN_MS = 24 * 60 * 60 * 1000;

/** The 20 key characters (no prefix or dashes), or null if this can't be a BLOOD key. */
export function normalizeKey(input: string): string | null {
  let s = input.toUpperCase().replace(/[\s\-_]/g, "");
  if (s.startsWith(KEY_PREFIX)) s = s.slice(KEY_PREFIX.length);
  s = s.replace(/O/g, "0").replace(/[IL]/g, "1");
  if (s.length !== KEY_LENGTH) return null;
  for (const ch of s) if (!KEY_ALPHABET.includes(ch)) return null;
  return s;
}

/** BLOOD-XXXXX-XXXXX-XXXXX-XXXXX */
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
