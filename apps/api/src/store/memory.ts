/** In-memory Store for tests and database-less local development. Not for production. */

import { randomUUID } from "node:crypto";
import type { SyncDocument } from "@bloom/shared";
import type { Cape, Challenge, Grant, LicenseKey, PurchaseRecord, Session, Store, SubscriptionRecord, SyncPutResult, User } from "./types";

const key = (b: Buffer) => b.toString("hex");

export class MemoryStore implements Store {
  users = new Map<string, User>();
  sessions = new Map<string, Session>();
  challenges = new Map<string, Challenge & { used: boolean }>();
  webCodes = new Map<string, { userId: string; expiresAt: number; used: boolean }>();
  subscriptions = new Map<string, SubscriptionRecord>();
  purchases = new Map<string, PurchaseRecord>();
  grants = new Map<string, Grant>();
  licenses = new Map<string, LicenseKey>();
  capes = new Map<string, Cape>();
  playerCapeMap = new Map<string, string>();
  events = new Map<string, string>();
  sync = new Map<string, SyncDocument>();

  async upsertUserByMinecraft(minecraftUuid: string, username: string): Promise<User> {
    const existing = [...this.users.values()].find((u) => u.minecraftUuid === minecraftUuid);
    if (existing) {
      existing.username = username;
      return { ...existing };
    }
    const u: User = { id: randomUUID(), minecraftUuid, username, stripeCustomerId: null, createdAt: Date.now() };
    this.users.set(u.id, u);
    return { ...u };
  }
  async getUser(id: string) {
    const u = this.users.get(id);
    return u ? { ...u } : null;
  }
  async getUserByMinecraft(minecraftUuid: string) {
    for (const u of this.users.values()) if (u.minecraftUuid === minecraftUuid) return { ...u };
    return null;
  }
  async getUserByCustomer(customerId: string) {
    for (const u of this.users.values()) if (u.stripeCustomerId === customerId) return { ...u };
    return null;
  }
  async setStripeCustomer(userId: string, customerId: string) {
    const u = this.users.get(userId);
    if (u) u.stripeCustomerId = customerId;
  }

  async deleteUser(userId: string) {
    this.users.delete(userId);
    for (const [k, s] of this.sessions) if (s.userId === userId) this.sessions.delete(k);
    for (const [k, c] of this.webCodes) if (c.userId === userId) this.webCodes.delete(k);
    for (const [k, s] of this.subscriptions) if (s.userId === userId) this.subscriptions.delete(k);
    for (const [k, p] of this.purchases) if (p.userId === userId) this.purchases.delete(k);
    this.grants.delete(userId);
    this.sync.delete(userId);
  }

  async createChallenge(c: Challenge) {
    this.challenges.set(c.id, { ...c, used: false });
  }
  async takeChallenge(id: string, now: number) {
    const c = this.challenges.get(id);
    if (!c || c.used || c.expiresAt <= now) return null;
    c.used = true;
    return { id: c.id, serverId: c.serverId, expiresAt: c.expiresAt };
  }

  async createSession(s: Session) {
    this.sessions.set(key(s.tokenHash), { ...s });
  }
  async useSession(tokenHash: Buffer, now: number) {
    const s = this.sessions.get(key(tokenHash));
    if (!s || s.expiresAt <= now) return null;
    s.lastUsedAt = now;
    return { ...s };
  }
  async deleteSession(tokenHash: Buffer) {
    this.sessions.delete(key(tokenHash));
  }
  async deleteUserSessions(userId: string) {
    for (const [k, s] of this.sessions) if (s.userId === userId) this.sessions.delete(k);
  }

  async createWebCode(codeHash: Buffer, userId: string, expiresAt: number) {
    this.webCodes.set(key(codeHash), { userId, expiresAt, used: false });
  }
  async takeWebCode(codeHash: Buffer, now: number) {
    const c = this.webCodes.get(key(codeHash));
    if (!c || c.used || c.expiresAt <= now) return null;
    c.used = true;
    return c.userId;
  }

  async upsertSubscription(s: SubscriptionRecord) {
    this.subscriptions.set(s.id, { ...s, updatedAt: Date.now() });
  }
  async listSubscriptions(userId: string) {
    return [...this.subscriptions.values()].filter((s) => s.userId === userId).map((s) => ({ ...s }));
  }
  async upsertPurchase(p: PurchaseRecord) {
    const prev = this.purchases.get(p.id);
    // A refund is final: replays of the original "paid" event must not undo it.
    if (prev?.status === "refunded") return;
    this.purchases.set(p.id, { ...p });
  }
  async listPurchases(userId: string) {
    return [...this.purchases.values()].filter((p) => p.userId === userId).map((p) => ({ ...p }));
  }
  async refundPurchase(paymentIntent: string) {
    for (const p of this.purchases.values()) {
      if (p.paymentIntent === paymentIntent) {
        p.status = "refunded";
        return true;
      }
    }
    return false;
  }

  async getGrant(userId: string) {
    const g = this.grants.get(userId);
    return g ? { ...g } : null;
  }
  async setGrant(g: Grant) {
    this.grants.set(g.userId, { ...g });
  }

  async hasEvent(id: string) {
    return this.events.has(id);
  }
  async recordEvent(id: string, type: string) {
    this.events.set(id, type);
  }

  async getSync(userId: string) {
    const d = this.sync.get(userId);
    return d ? structuredClone(d) : null;
  }
  async putSync(userId: string, baseVersion: number, data: Record<string, unknown>, device: string | null, now: number): Promise<SyncPutResult> {
    const cur = this.sync.get(userId);
    if ((cur?.version ?? 0) !== baseVersion) return { ok: false, conflict: cur ? structuredClone(cur) : { version: 0, updatedAt: 0, data: {} } };
    const doc: SyncDocument = { version: baseVersion + 1, updatedAt: now, device: device ?? undefined, data: structuredClone(data) };
    this.sync.set(userId, doc);
    return { ok: true, doc: structuredClone(doc) };
  }

  async cleanup(now: number) {
    for (const [k, s] of this.sessions) if (s.expiresAt <= now) this.sessions.delete(k);
    for (const [k, c] of this.challenges) if (c.expiresAt <= now) this.challenges.delete(k);
    for (const [k, c] of this.webCodes) if (c.expiresAt <= now) this.webCodes.delete(k);
  }
  async close() {}

  async createLicense(k: LicenseKey) {
    this.licenses.set(k.id, { ...k });
  }
  private findLicense(keyHash: Buffer) {
    for (const k of this.licenses.values()) if (k.keyHash.equals(keyHash)) return k;
    return null;
  }
  async getLicense(keyHash: Buffer) {
    const k = this.findLicense(keyHash);
    return k ? { ...k } : null;
  }
  async getLicenseById(id: string) {
    const k = this.licenses.get(id);
    return k ? { ...k } : null;
  }
  async redeemLicense(keyHash: Buffer, hwidHash: Buffer, now: number, expiresAt: number | null) {
    const k = this.findLicense(keyHash);
    if (!k || k.redeemedAt !== null || k.revoked) return false;
    Object.assign(k, { redeemedAt: now, hwidHash, expiresAt, lastMoveAt: now });
    return true;
  }
  async moveLicense(keyHash: Buffer, hwidHash: Buffer, now: number, movedBefore: number) {
    const k = this.findLicense(keyHash);
    if (!k || k.redeemedAt === null || k.revoked || (k.lastMoveAt !== null && k.lastMoveAt > movedBefore)) return false;
    Object.assign(k, { hwidHash, lastMoveAt: now });
    return true;
  }
  async updateLicense(id: string, patch: { revoked?: boolean; unbind?: boolean }) {
    const k = this.licenses.get(id);
    if (!k) return false;
    if (patch.revoked !== undefined) k.revoked = patch.revoked;
    if (patch.unbind) Object.assign(k, { hwidHash: null, lastMoveAt: null });
    return true;
  }
  async listLicenses(limit: number) {
    return [...this.licenses.values()].sort((a, b) => b.createdAt - a.createdAt).slice(0, limit).map((k) => ({ ...k }));
  }

  async createCape(c: Cape) {
    this.capes.set(c.id, { ...c });
  }
  async getCape(id: string) {
    const c = this.capes.get(id);
    return c ? { ...c } : null;
  }
  async listCapes(includeDisabled: boolean) {
    return [...this.capes.values()]
      .filter((c) => includeDisabled || c.enabled)
      .sort((a, b) => a.createdAt - b.createdAt)
      .map((c) => ({ ...c, png: Buffer.alloc(0) }));
  }
  async updateCape(id: string, patch: { enabled?: boolean; access?: Cape["access"]; name?: string }) {
    const c = this.capes.get(id);
    if (!c) return false;
    if (patch.enabled !== undefined) c.enabled = patch.enabled;
    if (patch.access !== undefined) c.access = patch.access;
    if (patch.name !== undefined) c.name = patch.name;
    return true;
  }
  async deleteCape(id: string) {
    for (const [u, cid] of this.playerCapeMap) if (cid === id) this.playerCapeMap.delete(u);
    return this.capes.delete(id);
  }
  async setPlayerCape(minecraftUuid: string, capeId: string | null) {
    if (capeId) this.playerCapeMap.set(minecraftUuid, capeId);
    else this.playerCapeMap.delete(minecraftUuid);
  }
  async getPlayerCape(minecraftUuid: string) {
    return this.playerCapeMap.get(minecraftUuid) ?? null;
  }
  async playerCapes(uuids: string[]) {
    const out: Record<string, string> = {};
    for (const u of uuids) {
      const id = this.playerCapeMap.get(u);
      if (id && this.capes.get(id)?.enabled) out[u] = id;
    }
    return out;
  }
}
