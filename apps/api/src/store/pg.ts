/** PostgreSQL Store (node-postgres). */

import { randomUUID } from "node:crypto";
import type { SyncDocument } from "@bloom/shared";
import type { Accessory, Cape, Challenge, Grant, LicenseKey, PurchaseRecord, Session, Store, SubscriptionRecord, SyncPutResult, User } from "./types";

/** The subset of pg.Pool that MOTION uses (keeps this file free of @types/pg). */
export interface PgPool {
  query<R = Record<string, unknown>>(text: string, values?: unknown[]): Promise<{ rows: R[]; rowCount: number | null }>;
  connect(): Promise<PgClient>;
  end(): Promise<void>;
}
export interface PgClient {
  query<R = Record<string, unknown>>(text: string, values?: unknown[]): Promise<{ rows: R[]; rowCount: number | null }>;
  release(): void;
}

export async function createPool(databaseUrl: string): Promise<PgPool> {
  const name = "pg";
  const mod = (await import(name)) as { default?: { Pool: new (o: object) => PgPool }; Pool?: new (o: object) => PgPool };
  const Pool = mod.default?.Pool ?? mod.Pool;
  if (!Pool) throw new Error("The 'pg' package is not installed.");
  const url = new URL(databaseUrl);
  // sslmode=require (typical for hosted Postgres) → TLS; verify certificates unless explicitly disabled.
  const sslmode = url.searchParams.get("sslmode");
  const ssl = sslmode === "require" || sslmode === "verify-full" ? { rejectUnauthorized: process.env.PGSSL_NO_VERIFY !== "1" } : undefined;
  return new Pool({ connectionString: databaseUrl, ssl, max: Number(process.env.PG_POOL_MAX ?? 10), idleTimeoutMillis: 30_000 });
}

const ms = (d: Date | string | null | undefined): number | null => (d ? new Date(d).getTime() : null);
const ts = (n: number | null | undefined): Date | null => (n === null || n === undefined ? null : new Date(n));

interface UserRow {
  id: string;
  minecraft_uuid: string;
  username: string;
  stripe_customer_id: string | null;
  created_at: Date;
}
const toUser = (r: UserRow): User => ({
  id: r.id,
  minecraftUuid: r.minecraft_uuid,
  username: r.username,
  stripeCustomerId: r.stripe_customer_id,
  createdAt: ms(r.created_at)!,
});

interface SessionRow {
  token_hash: Buffer;
  user_id: string;
  kind: "launcher" | "web";
  device: string | null;
  created_at: Date;
  last_used_at: Date;
  expires_at: Date;
}

interface SubRow {
  id: string;
  user_id: string;
  status: string;
  price_id: string | null;
  plan: string | null;
  current_period_end: Date | null;
  cancel_at_period_end: boolean;
  updated_at: Date;
}

interface PurchaseRow {
  id: string;
  user_id: string;
  payment_intent: string | null;
  price_id: string | null;
  plan: string;
  status: "paid" | "refunded";
}

interface LicenseRow {
  id: string;
  key_hash: Buffer;
  key_hint: string;
  label: string;
  duration_days: number | null;
  created_at: Date;
  redeemed_at: Date | null;
  expires_at: Date | null;
  hwid_hash: Buffer | null;
  last_move_at: Date | null;
  revoked: boolean;
}
const toLicense = (r: LicenseRow): LicenseKey => ({
  id: r.id,
  keyHash: r.key_hash,
  hint: r.key_hint,
  label: r.label,
  durationDays: r.duration_days,
  createdAt: ms(r.created_at)!,
  redeemedAt: ms(r.redeemed_at),
  expiresAt: ms(r.expires_at),
  hwidHash: r.hwid_hash,
  lastMoveAt: ms(r.last_move_at),
  revoked: r.revoked,
});

export class PgStore implements Store {
  constructor(private pool: PgPool) {}

  async createLicense(k: LicenseKey) {
    await this.pool.query(
      `INSERT INTO license_keys (id, key_hash, key_hint, label, duration_days, created_at, revoked) VALUES ($1, $2, $3, $4, $5, $6, false)`,
      [k.id, k.keyHash, k.hint, k.label, k.durationDays, ts(k.createdAt)],
    );
  }
  async getLicense(keyHash: Buffer) {
    const { rows } = await this.pool.query<LicenseRow>("SELECT * FROM license_keys WHERE key_hash = $1", [keyHash]);
    return rows[0] ? toLicense(rows[0]) : null;
  }
  async getLicenseById(id: string) {
    const { rows } = await this.pool.query<LicenseRow>("SELECT * FROM license_keys WHERE id = $1", [id]);
    return rows[0] ? toLicense(rows[0]) : null;
  }
  async redeemLicense(keyHash: Buffer, hwidHash: Buffer, now: number, expiresAt: number | null) {
    const r = await this.pool.query(
      `UPDATE license_keys SET redeemed_at = $3, hwid_hash = $2, expires_at = $4, last_move_at = $3
       WHERE key_hash = $1 AND redeemed_at IS NULL AND NOT revoked`,
      [keyHash, hwidHash, ts(now), ts(expiresAt)],
    );
    return (r.rowCount ?? 0) > 0;
  }
  async moveLicense(keyHash: Buffer, hwidHash: Buffer, now: number, movedBefore: number) {
    const r = await this.pool.query(
      `UPDATE license_keys SET hwid_hash = $2, last_move_at = $3
       WHERE key_hash = $1 AND redeemed_at IS NOT NULL AND NOT revoked AND (last_move_at IS NULL OR last_move_at <= $4)`,
      [keyHash, hwidHash, ts(now), ts(movedBefore)],
    );
    return (r.rowCount ?? 0) > 0;
  }
  async updateLicense(id: string, patch: { revoked?: boolean; unbind?: boolean }) {
    const sets: string[] = [];
    const vals: unknown[] = [id];
    if (patch.revoked !== undefined) {
      vals.push(patch.revoked);
      sets.push(`revoked = $${vals.length}`);
    }
    if (patch.unbind) sets.push("hwid_hash = NULL", "last_move_at = NULL");
    if (!sets.length) return false;
    const r = await this.pool.query(`UPDATE license_keys SET ${sets.join(", ")} WHERE id = $1`, vals);
    return (r.rowCount ?? 0) > 0;
  }
  async listLicenses(limit: number) {
    const { rows } = await this.pool.query<LicenseRow>("SELECT * FROM license_keys ORDER BY created_at DESC LIMIT $1", [limit]);
    return rows.map(toLicense);
  }

  async upsertUserByMinecraft(minecraftUuid: string, username: string): Promise<User> {
    const { rows } = await this.pool.query<UserRow>(
      `INSERT INTO users (id, minecraft_uuid, username) VALUES ($1, $2, $3)
       ON CONFLICT (minecraft_uuid) DO UPDATE SET username = EXCLUDED.username, updated_at = now()
       RETURNING *`,
      [randomUUID(), minecraftUuid, username],
    );
    return toUser(rows[0]!);
  }
  async getUser(id: string) {
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE id = $1", [id]);
    return rows[0] ? toUser(rows[0]) : null;
  }
  async getUserByMinecraft(minecraftUuid: string) {
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE minecraft_uuid = $1", [minecraftUuid]);
    return rows[0] ? toUser(rows[0]) : null;
  }
  async getUserByCustomer(customerId: string) {
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE stripe_customer_id = $1", [customerId]);
    return rows[0] ? toUser(rows[0]) : null;
  }
  async setStripeCustomer(userId: string, customerId: string) {
    await this.pool.query("UPDATE users SET stripe_customer_id = $2, updated_at = now() WHERE id = $1", [userId, customerId]);
  }

  async deleteUser(userId: string) {
    // Sessions, codes, subscriptions, purchases, grants and sync rows cascade.
    await this.pool.query("DELETE FROM users WHERE id = $1", [userId]);
  }

  async createChallenge(c: Challenge) {
    await this.pool.query("INSERT INTO auth_challenges (id, server_id, expires_at) VALUES ($1, $2, $3)", [c.id, c.serverId, ts(c.expiresAt)]);
  }
  async takeChallenge(id: string, now: number) {
    const { rows } = await this.pool.query<{ id: string; server_id: string; expires_at: Date }>(
      `UPDATE auth_challenges SET used_at = $2 WHERE id = $1 AND used_at IS NULL AND expires_at > $2 RETURNING id, server_id, expires_at`,
      [id, ts(now)],
    );
    const r = rows[0];
    return r ? { id: r.id, serverId: r.server_id, expiresAt: ms(r.expires_at)! } : null;
  }

  async createSession(s: Session) {
    await this.pool.query(
      `INSERT INTO sessions (token_hash, user_id, kind, device, created_at, last_used_at, expires_at) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [s.tokenHash, s.userId, s.kind, s.device, ts(s.createdAt), ts(s.lastUsedAt), ts(s.expiresAt)],
    );
  }
  async useSession(tokenHash: Buffer, now: number) {
    const { rows } = await this.pool.query<SessionRow>(
      `UPDATE sessions SET last_used_at = $2 WHERE token_hash = $1 AND expires_at > $2 RETURNING *`,
      [tokenHash, ts(now)],
    );
    const r = rows[0];
    return r
      ? { tokenHash: r.token_hash, userId: r.user_id, kind: r.kind, device: r.device, createdAt: ms(r.created_at)!, lastUsedAt: ms(r.last_used_at)!, expiresAt: ms(r.expires_at)! }
      : null;
  }
  async deleteSession(tokenHash: Buffer) {
    await this.pool.query("DELETE FROM sessions WHERE token_hash = $1", [tokenHash]);
  }
  async deleteUserSessions(userId: string) {
    await this.pool.query("DELETE FROM sessions WHERE user_id = $1", [userId]);
  }

  async createWebCode(codeHash: Buffer, userId: string, expiresAt: number) {
    await this.pool.query("INSERT INTO web_link_codes (code_hash, user_id, expires_at) VALUES ($1, $2, $3)", [codeHash, userId, ts(expiresAt)]);
  }
  async takeWebCode(codeHash: Buffer, now: number) {
    const { rows } = await this.pool.query<{ user_id: string }>(
      `UPDATE web_link_codes SET used_at = $2 WHERE code_hash = $1 AND used_at IS NULL AND expires_at > $2 RETURNING user_id`,
      [codeHash, ts(now)],
    );
    return rows[0]?.user_id ?? null;
  }

  async upsertSubscription(s: SubscriptionRecord) {
    await this.pool.query(
      `INSERT INTO subscriptions (id, user_id, status, price_id, plan, current_period_end, cancel_at_period_end)
       VALUES ($1, $2, $3, $4, $5, $6, $7)
       ON CONFLICT (id) DO UPDATE SET user_id = EXCLUDED.user_id, status = EXCLUDED.status, price_id = EXCLUDED.price_id,
         plan = EXCLUDED.plan, current_period_end = EXCLUDED.current_period_end,
         cancel_at_period_end = EXCLUDED.cancel_at_period_end, updated_at = now()`,
      [s.id, s.userId, s.status, s.priceId, s.plan, ts(s.currentPeriodEnd), s.cancelAtPeriodEnd],
    );
  }
  async listSubscriptions(userId: string) {
    const { rows } = await this.pool.query<SubRow>("SELECT * FROM subscriptions WHERE user_id = $1", [userId]);
    return rows.map((r) => ({
      id: r.id,
      userId: r.user_id,
      status: r.status,
      priceId: r.price_id,
      plan: r.plan as SubscriptionRecord["plan"],
      currentPeriodEnd: ms(r.current_period_end),
      cancelAtPeriodEnd: r.cancel_at_period_end,
      updatedAt: ms(r.updated_at) ?? undefined,
    }));
  }
  async upsertPurchase(p: PurchaseRecord) {
    // A refund is final: replays of the original "paid" event must not undo it.
    await this.pool.query(
      `INSERT INTO purchases (id, user_id, payment_intent, price_id, plan, status) VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (id) DO UPDATE SET payment_intent = COALESCE(EXCLUDED.payment_intent, purchases.payment_intent),
         status = CASE WHEN purchases.status = 'refunded' THEN 'refunded' ELSE EXCLUDED.status END, updated_at = now()`,
      [p.id, p.userId, p.paymentIntent, p.priceId, p.plan, p.status],
    );
  }
  async listPurchases(userId: string) {
    const { rows } = await this.pool.query<PurchaseRow>("SELECT * FROM purchases WHERE user_id = $1", [userId]);
    return rows.map((r) => ({ id: r.id, userId: r.user_id, paymentIntent: r.payment_intent, priceId: r.price_id, plan: r.plan as PurchaseRecord["plan"], status: r.status }));
  }
  async refundPurchase(paymentIntent: string) {
    const r = await this.pool.query("UPDATE purchases SET status = 'refunded', updated_at = now() WHERE payment_intent = $1", [paymentIntent]);
    return (r.rowCount ?? 0) > 0;
  }

  async getGrant(userId: string) {
    const { rows } = await this.pool.query<{ user_id: string; reason: string; expires_at: Date | null }>("SELECT * FROM grants WHERE user_id = $1", [userId]);
    const r = rows[0];
    return r ? { userId: r.user_id, reason: r.reason, expiresAt: ms(r.expires_at) } : null;
  }
  async setGrant(g: Grant) {
    await this.pool.query(
      `INSERT INTO grants (user_id, reason, expires_at) VALUES ($1, $2, $3)
       ON CONFLICT (user_id) DO UPDATE SET reason = EXCLUDED.reason, expires_at = EXCLUDED.expires_at`,
      [g.userId, g.reason, ts(g.expiresAt)],
    );
  }

  async hasEvent(id: string) {
    const { rows } = await this.pool.query("SELECT 1 FROM stripe_events WHERE id = $1", [id]);
    return rows.length > 0;
  }
  async recordEvent(id: string, type: string) {
    await this.pool.query("INSERT INTO stripe_events (id, type) VALUES ($1, $2) ON CONFLICT (id) DO NOTHING", [id, type]);
  }

  async getSync(userId: string) {
    const { rows } = await this.pool.query<{ version: number; data: Record<string, unknown>; device: string | null; updated_at: Date }>(
      "SELECT version, data, device, updated_at FROM sync_documents WHERE user_id = $1",
      [userId],
    );
    const r = rows[0];
    return r ? { version: r.version, data: r.data, device: r.device ?? undefined, updatedAt: ms(r.updated_at)! } : null;
  }
  async putSync(userId: string, baseVersion: number, data: Record<string, unknown>, device: string | null, now: number): Promise<SyncPutResult> {
    const json = JSON.stringify(data);
    const result =
      baseVersion === 0
        ? await this.pool.query<{ version: number }>(
            `INSERT INTO sync_documents (user_id, version, data, device, updated_at) VALUES ($1, 1, $2::jsonb, $3, $4)
             ON CONFLICT (user_id) DO NOTHING RETURNING version`,
            [userId, json, device, ts(now)],
          )
        : await this.pool.query<{ version: number }>(
            `UPDATE sync_documents SET version = version + 1, data = $3::jsonb, device = $4, updated_at = $5
             WHERE user_id = $1 AND version = $2 RETURNING version`,
            [userId, baseVersion, json, device, ts(now)],
          );
    const row = result.rows[0];
    if (row) return { ok: true, doc: { version: row.version, data, device: device ?? undefined, updatedAt: now } };
    const current: SyncDocument = (await this.getSync(userId)) ?? { version: 0, updatedAt: 0, data: {} };
    return { ok: false, conflict: current };
  }

  async cleanup(now: number) {
    await this.pool.query("DELETE FROM sessions WHERE expires_at <= $1", [ts(now)]);
    await this.pool.query("DELETE FROM auth_challenges WHERE expires_at <= $1", [ts(now)]);
    await this.pool.query("DELETE FROM web_link_codes WHERE expires_at <= $1", [ts(now)]);
  }
  async close() {
    await this.pool.end();
  }

  // ------------------------------------------------------------------ capes

  async createCape(c: Cape) {
    await this.pool.query(
      "INSERT INTO capes (id, name, access, png, width, height, frame_ms, enabled, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)",
      [c.id, c.name, c.access, c.png, c.width, c.height, c.frameMs, c.enabled, ts(c.createdAt)],
    );
  }
  async getCape(id: string) {
    const { rows } = await this.pool.query<CapeRow>("SELECT * FROM capes WHERE id = $1", [id]);
    return rows[0] ? toCape(rows[0]) : null;
  }
  async listCapes(includeDisabled: boolean) {
    const { rows } = await this.pool.query<CapeRow>(
      `SELECT id, name, access, ''::bytea AS png, width, height, frame_ms, enabled, created_at FROM capes ${includeDisabled ? "" : "WHERE enabled"} ORDER BY created_at`,
    );
    return rows.map(toCape);
  }
  async updateCape(id: string, patch: { enabled?: boolean; access?: Cape["access"]; name?: string }) {
    const sets: string[] = [];
    const vals: unknown[] = [id];
    if (patch.enabled !== undefined) {
      vals.push(patch.enabled);
      sets.push(`enabled = $${vals.length}`);
    }
    if (patch.access !== undefined) {
      vals.push(patch.access);
      sets.push(`access = $${vals.length}`);
    }
    if (patch.name !== undefined) {
      vals.push(patch.name);
      sets.push(`name = $${vals.length}`);
    }
    if (!sets.length) return false;
    const r = await this.pool.query(`UPDATE capes SET ${sets.join(", ")} WHERE id = $1`, vals);
    return (r.rowCount ?? 0) > 0;
  }
  async deleteCape(id: string) {
    const r = await this.pool.query("DELETE FROM capes WHERE id = $1", [id]);
    return (r.rowCount ?? 0) > 0;
  }
  async setPlayerCape(minecraftUuid: string, capeId: string | null, now: number) {
    if (!capeId) {
      await this.pool.query("DELETE FROM player_capes WHERE minecraft_uuid = $1", [minecraftUuid]);
      return;
    }
    await this.pool.query(
      `INSERT INTO player_capes (minecraft_uuid, cape_id, updated_at) VALUES ($1, $2, $3)
       ON CONFLICT (minecraft_uuid) DO UPDATE SET cape_id = EXCLUDED.cape_id, updated_at = EXCLUDED.updated_at`,
      [minecraftUuid, capeId, ts(now)],
    );
  }
  async getPlayerCape(minecraftUuid: string) {
    const { rows } = await this.pool.query<{ cape_id: string }>("SELECT cape_id FROM player_capes WHERE minecraft_uuid = $1", [minecraftUuid]);
    return rows[0]?.cape_id ?? null;
  }
  async getUserByUsername(username: string) {
    const { rows } = await this.pool.query<UserRow>("SELECT * FROM users WHERE lower(username) = lower($1) ORDER BY updated_at DESC LIMIT 1", [username]);
    return rows[0] ? toUser(rows[0]) : null;
  }
  async setPlayerTag(minecraftUuid: string, tag: { text: string; color: string } | null, now: number) {
    if (!tag) {
      await this.pool.query("DELETE FROM player_tags WHERE minecraft_uuid = $1", [minecraftUuid]);
      return;
    }
    await this.pool.query(
      `INSERT INTO player_tags (minecraft_uuid, text, color, updated_at) VALUES ($1, $2, $3, $4)
       ON CONFLICT (minecraft_uuid) DO UPDATE SET text = EXCLUDED.text, color = EXCLUDED.color, updated_at = EXCLUDED.updated_at`,
      [minecraftUuid, tag.text, tag.color, ts(now)],
    );
  }
  async playerTags(uuids: string[]) {
    const out: Record<string, { text: string; color: string }> = {};
    if (!uuids.length) return out;
    const { rows } = await this.pool.query<{ minecraft_uuid: string; text: string; color: string }>(
      "SELECT minecraft_uuid, text, color FROM player_tags WHERE minecraft_uuid = ANY($1::text[])",
      [uuids],
    );
    for (const r of rows) out[r.minecraft_uuid] = { text: r.text, color: r.color };
    return out;
  }
  async listTagged() {
    const { rows } = await this.pool.query<{ minecraft_uuid: string; username: string | null; text: string; color: string; last_seen: Date | null; playing: boolean | null }>(
      `SELECT t.minecraft_uuid, u.username, t.text, t.color, p.last_seen, p.playing
       FROM player_tags t LEFT JOIN users u ON u.minecraft_uuid = t.minecraft_uuid
       LEFT JOIN presence p ON p.minecraft_uuid = t.minecraft_uuid ORDER BY t.updated_at`,
    );
    return rows.map((r) => ({
      minecraftUuid: r.minecraft_uuid,
      username: r.username ?? r.minecraft_uuid,
      text: r.text,
      color: r.color,
      lastSeen: r.last_seen ? r.last_seen.getTime() : null,
      playing: r.playing ?? false,
    }));
  }
  async touchPresence(minecraftUuid: string, playing: boolean, now: number) {
    await this.pool.query(
      `INSERT INTO presence (minecraft_uuid, last_seen, playing) VALUES ($1, $2, $3)
       ON CONFLICT (minecraft_uuid) DO UPDATE SET last_seen = EXCLUDED.last_seen, playing = EXCLUDED.playing`,
      [minecraftUuid, ts(now), playing],
    );
  }
  async playerCapes(uuids: string[]) {
    if (!uuids.length) return {};
    const { rows } = await this.pool.query<{ minecraft_uuid: string; cape_id: string }>(
      `SELECT p.minecraft_uuid, p.cape_id FROM player_capes p JOIN capes c ON c.id = p.cape_id
       WHERE c.enabled AND p.minecraft_uuid = ANY($1::text[])`,
      [uuids],
    );
    const out: Record<string, string> = {};
    for (const r of rows) out[r.minecraft_uuid] = r.cape_id;
    return out;
  }
  // ------------------------------------------------------------------ accessories

  async createAccessory(a: Accessory) {
    await this.pool.query(
      "INSERT INTO accessories (id, name, kind, access, png, width, height, color, style, enabled, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)",
      [a.id, a.name, a.kind, a.access, a.png, a.width, a.height, a.color, a.style, a.enabled, ts(a.createdAt)],
    );
  }
  async getAccessory(id: string) {
    const { rows } = await this.pool.query<AccessoryRow>("SELECT * FROM accessories WHERE id = $1", [id]);
    return rows[0] ? toAccessory(rows[0]) : null;
  }
  async listAccessories(includeDisabled: boolean) {
    const { rows } = await this.pool.query<AccessoryRow>(
      `SELECT id, name, kind, access, NULL::bytea AS png, width, height, color, style, enabled, created_at FROM accessories ${includeDisabled ? "" : "WHERE enabled"} ORDER BY created_at`,
    );
    return rows.map(toAccessory);
  }
  async updateAccessory(id: string, patch: { enabled?: boolean; access?: Accessory["access"] }) {
    const sets: string[] = [];
    const vals: unknown[] = [id];
    if (patch.enabled !== undefined) {
      vals.push(patch.enabled);
      sets.push(`enabled = $${vals.length}`);
    }
    if (patch.access !== undefined) {
      vals.push(patch.access);
      sets.push(`access = $${vals.length}`);
    }
    if (!sets.length) return (await this.getAccessory(id)) !== null;
    const r = await this.pool.query(`UPDATE accessories SET ${sets.join(", ")} WHERE id = $1`, vals);
    return (r.rowCount ?? 0) > 0;
  }
  async deleteAccessory(id: string) {
    const r = await this.pool.query("DELETE FROM accessories WHERE id = $1", [id]);
    return (r.rowCount ?? 0) > 0;
  }
  async setPlayerAccessory(minecraftUuid: string, kind: Accessory["kind"], id: string | null, now: number) {
    if (!id) {
      await this.pool.query("DELETE FROM player_accessories WHERE minecraft_uuid = $1 AND kind = $2", [minecraftUuid, kind]);
      return;
    }
    await this.pool.query(
      `INSERT INTO player_accessories (minecraft_uuid, kind, accessory_id, updated_at) VALUES ($1, $2, $3, $4)
       ON CONFLICT (minecraft_uuid, kind) DO UPDATE SET accessory_id = EXCLUDED.accessory_id, updated_at = EXCLUDED.updated_at`,
      [minecraftUuid, kind, id, ts(now)],
    );
  }
  async getPlayerAccessories(minecraftUuid: string) {
    const { rows } = await this.pool.query<{ kind: string; accessory_id: string }>(
      "SELECT kind, accessory_id FROM player_accessories WHERE minecraft_uuid = $1",
      [minecraftUuid],
    );
    return { wings: rows.find((r) => r.kind === "wings")?.accessory_id ?? null, aura: rows.find((r) => r.kind === "aura")?.accessory_id ?? null };
  }
  async playerAccessories(uuids: string[]) {
    const wings: Record<string, string> = {};
    const auras: Record<string, { color: string; style: string }> = {};
    if (!uuids.length) return { wings, auras };
    const { rows } = await this.pool.query<{ minecraft_uuid: string; kind: string; id: string; color: string; style: string | null }>(
      `SELECT p.minecraft_uuid, p.kind, a.id, a.color, a.style FROM player_accessories p JOIN accessories a ON a.id = p.accessory_id
       WHERE a.enabled AND p.minecraft_uuid = ANY($1::text[])`,
      [uuids],
    );
    for (const r of rows) {
      if (r.kind === "wings") wings[r.minecraft_uuid] = r.id;
      else auras[r.minecraft_uuid] = { color: r.color, style: r.style ?? "glow" };
    }
    return { wings, auras };
  }

}

interface CapeRow {
  id: string;
  name: string;
  access: "everyone" | "premium";
  png: Buffer;
  width: number;
  height: number;
  frame_ms: number;
  enabled: boolean;
  created_at: Date;
}

const toCape = (r: CapeRow): Cape => ({
  id: r.id,
  name: r.name,
  access: r.access,
  png: r.png,
  width: r.width,
  height: r.height,
  frameMs: r.frame_ms ?? 100,
  enabled: r.enabled,
  createdAt: r.created_at.getTime(),
});

interface AccessoryRow {
  id: string;
  name: string;
  kind: "wings" | "aura";
  access: "everyone" | "premium";
  png: Buffer | null;
  width: number;
  height: number;
  color: string;
  style: string | null;
  enabled: boolean;
  created_at: Date;
}

const toAccessory = (r: AccessoryRow): Accessory => ({
  id: r.id,
  name: r.name,
  kind: r.kind,
  access: r.access,
  png: r.png,
  width: r.width,
  height: r.height,
  color: r.color,
  style: r.style,
  enabled: r.enabled,
  createdAt: r.created_at.getTime(),
});
