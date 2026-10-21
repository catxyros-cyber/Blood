/** PostgreSQL Store (node-postgres). */

import { randomUUID } from "node:crypto";
import type { SyncDocument } from "@bloom/shared";
import type { Challenge, Grant, PurchaseRecord, Session, Store, SubscriptionRecord, SyncPutResult, User } from "./types";

/** The subset of pg.Pool that BLOOD uses (keeps this file free of @types/pg). */
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

export class PgStore implements Store {
  constructor(private pool: PgPool) {}

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
}
