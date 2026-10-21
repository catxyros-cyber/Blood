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

export type SyncPutResult = { ok: true; doc: SyncDocument } | { ok: false; conflict: SyncDocument };

export interface Store {
  upsertUserByMinecraft(minecraftUuid: string, username: string): Promise<User>;
  getUser(id: string): Promise<User | null>;
  getUserByMinecraft(minecraftUuid: string): Promise<User | null>;
  getUserByCustomer(customerId: string): Promise<User | null>;
  setStripeCustomer(userId: string, customerId: string): Promise<void>;
  /** Remove the user and everything BLOOD stores about them (sessions, sync, records). */
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

  /** Remove expired sessions, challenges and codes. */
  cleanup(now: number): Promise<void>;
  close(): Promise<void>;
}
