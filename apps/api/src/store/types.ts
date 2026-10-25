/** Persistence interface. Implemented by PostgreSQL (production) and memory (tests/dev). */

import type { PlanId, SubscriptionStatus, SyncDocument } from "@bloom/shared";

export interface User {
  id: string;
  minecraftUuid: string;
  username: string;
  stripeCustomerId: string | null;
  createdAt: number;
}

export interface Session {
  tokenHash: Buffer;
  userId: string;
  kind: "launcher" | "web";
  device: string | null;
  createdAt: number;
  lastUsedAt: number;
  expiresAt: number;
}

export interface Challenge {
  id: string;
  serverId: string;
  expiresAt: number;
}

export interface SubscriptionRecord {
  id: string;
  userId: string;
  status: SubscriptionStatus | string;
  priceId: string | null;
  plan: PlanId | null;
  currentPeriodEnd: number | null;
  cancelAtPeriodEnd: boolean;
  updatedAt?: number;
}

export interface PurchaseRecord {
  id: string;
  userId: string;
  paymentIntent: string | null;
  priceId: string | null;
  plan: PlanId;
  status: "paid" | "refunded";
}

export interface Grant {
  userId: string;
  reason: string;
  expiresAt: number | null;
}

export interface LicenseKey {
  id: string;
  keyHash: Buffer;
  hint: string;
  label: string;
  durationDays: number | null;
  createdAt: number;
  redeemedAt: number | null;
  expiresAt: number | null;
  hwidHash: Buffer | null;
  lastMoveAt: number | null;
  revoked: boolean;
}

export interface Cape {
  id: string;
  name: string;
  access: "everyone" | "premium";
  png: Buffer;
  width: number;
  /** Total PNG height: frames × width/2 (animated capes are a vertical strip of frames). */
  height: number;
  /** Milliseconds per frame (animated capes). */
  frameMs: number;
  enabled: boolean;
  createdAt: number;
}

export interface CommunitySkin {
  id: string;
  name: string;
  model: "classic" | "slim";
  /** PNG bytes; omitted from catalog listings. */
  png: Buffer | null;
  enabled: boolean;
  createdAt: number;
}

export interface Accessory {
  id: string;
  name: string;
  kind: "wings" | "aura" | "avatar";
  access: "everyone" | "premium";
  /** Wings image (PNG); null for auras. */
  png: Buffer | null;
  width: number;
  height: number;
  color: string;
  style: string | null;
  enabled: boolean;
  createdAt: number;
}

/** What one player wears: accessory ids by kind. */
export interface PlayerAccessories {
  wings: string | null;
  aura: string | null;
  avatar: string | null;
}

export interface TaggedPlayer {
  minecraftUuid: string;
  username: string;
  text: string;
  color: string;
  lastSeen: number | null;
  playing: boolean;
}

export type SyncPutResult = { ok: true; doc: SyncDocument } | { ok: false; conflict: SyncDocument };

export interface Store {
  upsertUserByMinecraft(minecraftUuid: string, username: string): Promise<User>;
  getUser(id: string): Promise<User | null>;
  getUserByMinecraft(minecraftUuid: string): Promise<User | null>;
  getUserByCustomer(customerId: string): Promise<User | null>;
  setStripeCustomer(userId: string, customerId: string): Promise<void>;
  /** Remove the user and everything MOTION stores about them (sessions, sync, records). */
  deleteUser(userId: string): Promise<void>;

  createChallenge(c: Challenge): Promise<void>;
  /** Atomically consume an unexpired, unused challenge. */
  takeChallenge(id: string, now: number): Promise<Challenge | null>;

  createSession(s: Session): Promise<void>;
  /** Returns the session if it exists and hasn't expired; updates last_used_at. */
  useSession(tokenHash: Buffer, now: number): Promise<Session | null>;
  deleteSession(tokenHash: Buffer): Promise<void>;
  deleteUserSessions(userId: string): Promise<void>;

  createWebCode(codeHash: Buffer, userId: string, expiresAt: number): Promise<void>;
  takeWebCode(codeHash: Buffer, now: number): Promise<string | null>;

  upsertSubscription(s: SubscriptionRecord): Promise<void>;
  listSubscriptions(userId: string): Promise<SubscriptionRecord[]>;
  upsertPurchase(p: PurchaseRecord): Promise<void>;
  listPurchases(userId: string): Promise<PurchaseRecord[]>;
  /** Mark the purchase paid with this PaymentIntent as refunded. Returns true if one was found. */
  refundPurchase(paymentIntent: string): Promise<boolean>;

  getGrant(userId: string): Promise<Grant | null>;
  setGrant(g: Grant): Promise<void>;

  /** Has this webhook event already been processed? */
  hasEvent(id: string): Promise<boolean>;
  recordEvent(id: string, type: string): Promise<void>;

  getSync(userId: string): Promise<SyncDocument | null>;
  /** Write if `baseVersion` matches the stored version (0 = no document yet). */
  putSync(userId: string, baseVersion: number, data: Record<string, unknown>, device: string | null, now: number): Promise<SyncPutResult>;

  createLicense(k: LicenseKey): Promise<void>;
  getLicense(keyHash: Buffer): Promise<LicenseKey | null>;
  getLicenseById(id: string): Promise<LicenseKey | null>;
  /** Bind a never-redeemed key to a PC (this starts the 24 h move cooldown). Returns false if someone redeemed it first. */
  redeemLicense(keyHash: Buffer, hwidHash: Buffer, now: number, expiresAt: number | null): Promise<boolean>;
  /** Move a redeemed key to another PC if it wasn't moved after `movedBefore`. Returns false otherwise. */
  moveLicense(keyHash: Buffer, hwidHash: Buffer, now: number, movedBefore: number): Promise<boolean>;
  /** Admin: revoke/restore, or clear the PC binding so the key can be activated anywhere. */
  updateLicense(id: string, patch: { revoked?: boolean; unbind?: boolean }): Promise<boolean>;
  listLicenses(limit: number): Promise<LicenseKey[]>;

  createCape(c: Cape): Promise<void>;
  getCape(id: string): Promise<Cape | null>;
  /** Without image bytes (png is an empty buffer). */
  listCapes(includeDisabled: boolean): Promise<Cape[]>;
  updateCape(id: string, patch: { enabled?: boolean; access?: Cape["access"]; name?: string }): Promise<boolean>;
  deleteCape(id: string): Promise<boolean>;
  /** null removes the player's cape. */
  setPlayerCape(minecraftUuid: string, capeId: string | null, now: number): Promise<void>;
  getPlayerCape(minecraftUuid: string): Promise<string | null>;
  /** Cape id per player, for enabled capes only. */
  playerCapes(minecraftUuids: string[]): Promise<Record<string, string>>;

  createCommunitySkin(skin: CommunitySkin): Promise<void>;
  getCommunitySkin(id: string): Promise<CommunitySkin | null>;
  /** Without PNG bytes. */
  listCommunitySkins(includeDisabled: boolean): Promise<CommunitySkin[]>;
  updateCommunitySkin(id: string, enabled: boolean): Promise<boolean>;
  deleteCommunitySkin(id: string): Promise<boolean>;

  createAccessory(a: Accessory): Promise<void>;
  getAccessory(id: string): Promise<Accessory | null>;
  /** Without image bytes. */
  listAccessories(includeDisabled: boolean): Promise<Accessory[]>;
  updateAccessory(id: string, patch: { enabled?: boolean; access?: Accessory["access"] }): Promise<boolean>;
  deleteAccessory(id: string): Promise<boolean>;
  /** id null removes that kind. */
  setPlayerAccessory(minecraftUuid: string, kind: Accessory["kind"], id: string | null, now: number): Promise<void>;
  getPlayerAccessories(minecraftUuid: string): Promise<PlayerAccessories>;
  /** Enabled accessories worn by these players. */
  playerAccessories(minecraftUuids: string[]): Promise<{ wings: Record<string, string>; auras: Record<string, { color: string; style: string }>; avatars: Record<string, string> }>;

  /** Case-insensitive. */
  getUserByUsername(username: string): Promise<User | null>;
  /** null removes the tag. */
  setPlayerTag(minecraftUuid: string, tag: { text: string; color: string } | null, now: number): Promise<void>;
  playerTags(minecraftUuids: string[]): Promise<Record<string, { text: string; color: string }>>;
  /** Everyone with a tag, with their name and when the launcher last checked in. */
  listTagged(): Promise<TaggedPlayer[]>;
  touchPresence(minecraftUuid: string, playing: boolean, now: number): Promise<void>;

  /** Remove expired sessions, challenges and codes. */
  cleanup(now: number): Promise<void>;
  close(): Promise<void>;
}
