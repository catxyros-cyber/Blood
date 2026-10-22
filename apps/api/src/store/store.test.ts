/**
 * Store contract tests. Always run against MemoryStore; also against PostgreSQL
 * when TEST_DATABASE_URL points at a disposable database (CI sets this up).
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { MemoryStore } from "./memory";
import { createPool, PgStore, type PgPool } from "./pg";
import { migrate } from "./migrate";
import type { Store } from "./types";

const migrations = resolve(dirname(fileURLToPath(import.meta.url)), "../../migrations");

async function contract(name: string, make: () => Promise<{ store: Store; done: () => Promise<void> }>) {
  test(`${name}: users, challenges and sessions`, async () => {
    const { store, done } = await make();
    try {
      const u = await store.upsertUserByMinecraft("8667ba71b85a4004af54457a9734eed7", "Steve");
      const again = await store.upsertUserByMinecraft("8667ba71b85a4004af54457a9734eed7", "Steve2");
      assert.equal(again.id, u.id, "same Minecraft profile → same user");
      assert.equal((await store.getUser(u.id))?.username, "Steve2");
      await store.setStripeCustomer(u.id, "cus_1");
      assert.equal((await store.getUserByCustomer("cus_1"))?.id, u.id);

      const now = Date.now();
      await store.createChallenge({ id: "challenge-aaaaaaaaaaaa", serverId: "abc", expiresAt: now + 60_000 });
      await store.createChallenge({ id: "challenge-expired-bbbb", serverId: "def", expiresAt: now - 1 });
      assert.equal((await store.takeChallenge("challenge-aaaaaaaaaaaa", now))?.serverId, "abc");
      assert.equal(await store.takeChallenge("challenge-aaaaaaaaaaaa", now), null, "single use");
      assert.equal(await store.takeChallenge("challenge-expired-bbbb", now), null, "expired");

      const hash = Buffer.from("a".repeat(64), "hex");
      await store.createSession({ tokenHash: hash, userId: u.id, kind: "launcher", device: "PC", createdAt: now, lastUsedAt: now, expiresAt: now + 60_000 });
      const s = await store.useSession(hash, now + 1000);
      assert.equal(s?.userId, u.id);
      assert.equal(s?.kind, "launcher");
      assert.equal(await store.useSession(hash, now + 120_000), null, "expired sessions are rejected");
      await store.deleteSession(hash);
      assert.equal(await store.useSession(hash, now), null);

      const code = Buffer.from("b".repeat(64), "hex");
      await store.createWebCode(code, u.id, now + 60_000);
      assert.equal(await store.takeWebCode(code, now), u.id);
      assert.equal(await store.takeWebCode(code, now), null);
    } finally {
      await done();
    }
  });

  test(`${name}: billing records, events, grants and sync`, async () => {
    const { store, done } = await make();
    try {
      const u = await store.upsertUserByMinecraft("069a79f444e94726a5befca90e38aaf5", "Notch");
      const end = Date.now() + 86_400_000;
      await store.upsertSubscription({ id: "sub_1", userId: u.id, status: "active", priceId: "price_m", plan: "monthly", currentPeriodEnd: end, cancelAtPeriodEnd: false });
      await store.upsertSubscription({ id: "sub_1", userId: u.id, status: "past_due", priceId: "price_m", plan: "monthly", currentPeriodEnd: end, cancelAtPeriodEnd: true });
      const subs = await store.listSubscriptions(u.id);
      assert.equal(subs.length, 1);
      assert.equal(subs[0]!.status, "past_due");
      assert.equal(subs[0]!.cancelAtPeriodEnd, true);
      assert.equal(Math.round(subs[0]!.currentPeriodEnd! / 1000), Math.round(end / 1000));

      await store.upsertPurchase({ id: "cs_1", userId: u.id, paymentIntent: "pi_1", priceId: "price_l", plan: "lifetime", status: "paid" });
      assert.equal(await store.refundPurchase("pi_1"), true);
      assert.equal(await store.refundPurchase("pi_unknown"), false);
      await store.upsertPurchase({ id: "cs_1", userId: u.id, paymentIntent: "pi_1", priceId: "price_l", plan: "lifetime", status: "paid" });
      assert.equal((await store.listPurchases(u.id))[0]!.status, "refunded", "refunds are final");

      assert.equal(await store.hasEvent("evt_1"), false);
      await store.recordEvent("evt_1", "x");
      await store.recordEvent("evt_1", "x");
      assert.equal(await store.hasEvent("evt_1"), true);

      await store.setGrant({ userId: u.id, reason: "test", expiresAt: null });
      assert.equal((await store.getGrant(u.id))?.expiresAt, null);

      const first = await store.putSync(u.id, 0, { a: 1 }, "PC", Date.now());
      assert.ok(first.ok && first.doc.version === 1);
      const conflict = await store.putSync(u.id, 0, { a: 2 }, "Laptop", Date.now());
      assert.ok(!conflict.ok && conflict.conflict.version === 1);
      const second = await store.putSync(u.id, 1, { a: 3, nested: { b: [1, 2] } }, "Laptop", Date.now());
      assert.ok(second.ok && second.doc.version === 2);
      assert.deepEqual((await store.getSync(u.id))?.data, { a: 3, nested: { b: [1, 2] } });

      await store.deleteUser(u.id);
      assert.equal(await store.getUser(u.id), null);
      assert.equal(await store.getSync(u.id), null);
      assert.deepEqual(await store.listSubscriptions(u.id), []);
    } finally {
      await done();
    }
  });
}

await contract("memory", async () => ({ store: new MemoryStore(), done: async () => undefined }));

const url = process.env.TEST_DATABASE_URL;
if (url) {
  let pool: PgPool | null = null;
  await contract("postgres", async () => {
    pool ??= await createPool(url);
    // Fresh schema for every test.
    await pool.query("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    const applied = await migrate(pool, migrations, () => undefined);
    assert.deepEqual(applied, ["001_init.sql", "002_license_keys.sql", "003_capes.sql", "004_tags.sql", "005_animated_capes.sql"]);
    assert.deepEqual(await migrate(pool, migrations, () => undefined), [], "migrations are applied once");
    return { store: new PgStore(pool), done: async () => undefined };
  });
  test.after(async () => {
    await pool?.end();
  });
} else {
  test("postgres store (skipped: set TEST_DATABASE_URL to run)", { skip: true }, () => undefined);
}
