import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, writeFile, mkdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { harness, WEBHOOK_SECRET } from "./test/fakes";
import { compareVersions } from "./app";
import { signWebhook, verifyWebhook, encodeForm } from "./stripe";
import { computeEntitlement } from "./entitlements";
import { loadConfig, ConfigError } from "./config";
import { serveStatic } from "./static";

test("sign-in uses Mojang's join/hasJoined handshake and never asks for tokens", async () => {
  const h = await harness();
  try {
    const ch = await h.call("POST", "/v1/auth/challenge");
    assert.equal(ch.status, 200);
    assert.match(ch.body.serverId, /^[0-9a-f]{40}$/);

    // Not joined → refused
    const notJoined = await h.call("POST", "/v1/auth/verify", { json: { challengeId: ch.body.challengeId, username: "Steve" } });
    assert.equal(notJoined.status, 401);
    // The challenge was consumed; it can't be retried.
    h.mojang.join(ch.body.serverId, { id: "8667ba71-b85a-4004-af54-457a9734eed7", name: "Steve" });
    const reused = await h.call("POST", "/v1/auth/verify", { json: { challengeId: ch.body.challengeId, username: "Steve" } });
    assert.equal(reused.body.error.code, "challenge_expired");

    const token = await h.signIn("Steve", "8667ba71-b85a-4004-af54-457a9734eed7");
    assert.match(token, /^bl_/);
    const me = await h.call("GET", "/v1/me", { token });
    assert.equal(me.status, 200);
    assert.equal(me.body.user.username, "Steve");
    assert.equal(me.body.user.minecraftUuid, "8667ba71b85a4004af54457a9734eed7");
    assert.equal(me.body.entitlement.premium, false);
    assert.equal(me.headers.get("cache-control"), "no-store");

    // Only a hash of the token is stored.
    const stored = [...h.store.sessions.values()][0]!;
    assert.notEqual(stored.tokenHash.toString("utf8"), token);
    assert.ok(!JSON.stringify(h.logs).includes(token), "tokens never reach the logs");

    assert.equal((await h.call("GET", "/v1/me")).status, 401);
    assert.equal((await h.call("GET", "/v1/me", { token: "bl_nope" })).status, 401);
    await h.call("POST", "/v1/auth/logout", { token });
    assert.equal((await h.call("GET", "/v1/me", { token })).status, 401);

    h.mojang.down = true;
    const ch2 = await h.call("POST", "/v1/auth/challenge");
    const down = await h.call("POST", "/v1/auth/verify", { json: { challengeId: ch2.body.challengeId, username: "Steve" } });
    assert.equal(down.status, 503);

    const bad = await h.call("POST", "/v1/auth/verify", { json: { challengeId: "x", username: "Steve" } });
    assert.equal(bad.status, 400);
    assert.equal(bad.body.error.code, "invalid_request");
  } finally {
    await h.close();
  }
});

test("subscription lifecycle: checkout → signed webhook → Premium → sync → cancellation", async () => {
  const h = await harness();
  try {
    const plans = await h.call("GET", "/v1/plans");
    assert.equal(plans.body.billingEnabled, true);
    assert.deepEqual(
      plans.body.plans.map((p: { id: string; amount: number; interval: string | null; available: boolean }) => [p.id, p.amount, p.interval, p.available]),
      [
        ["monthly", 499, "month", true],
        ["yearly", 3999, "year", true],
        ["lifetime", 7999, null, true],
      ],
    );

    const token = await h.signIn();
    // Premium features are refused before payment.
    assert.equal((await h.call("GET", "/v1/sync", { token })).status, 402);

    const checkout = await h.call("POST", "/v1/billing/checkout", { token, json: { plan: "monthly" } });
    assert.equal(checkout.status, 200);
    assert.match(checkout.body.url, /^https:\/\/checkout\.stripe\.com\//);
    const created = h.stripe.calls.find((c) => c.path === "/v1/checkout/sessions")!;
    const userId = [...h.store.users.values()][0]!.id;
    assert.equal(created.params.mode, "subscription");
    assert.equal(created.params["line_items[0][price]"], "price_month");
    assert.equal(created.params.client_reference_id, userId);
    assert.equal(created.params.customer, "cus_1");
    assert.equal(created.params.success_url, "http://bloom.test/success.html?session_id={CHECKOUT_SESSION_ID}");
    assert.equal(created.params["subscription_data[metadata][bloom_user_id]"], userId);
    assert.ok(created.headers["Idempotency-Key"], "POSTs carry an idempotency key");
    assert.equal(created.headers["Stripe-Version"], "2024-06-20");

    // The client claiming success changes nothing: still not premium until Stripe tells us.
    assert.equal((await h.call("GET", "/v1/me", { token })).body.entitlement.premium, false);

    const sessionId = [...h.stripe.checkoutSessions.keys()][0]!;
    const ev = h.stripe.complete(sessionId);
    const hook = await h.call("POST", "/v1/stripe/webhook", { body: ev.payload, headers: { "Stripe-Signature": ev.signature, "Content-Type": "application/json" } });
    assert.equal(hook.status, 200);
    const me = await h.call("GET", "/v1/me", { token });
    assert.equal(me.body.entitlement.premium, true);
    assert.equal(me.body.entitlement.plan, "monthly");
    assert.equal(me.body.entitlement.status, "active");
    assert.ok(me.body.entitlement.currentPeriodEnd > Date.now());

    // Replayed event is acknowledged but not re-processed.
    const replay = await h.call("POST", "/v1/stripe/webhook", { body: ev.payload, headers: { "Stripe-Signature": ev.signature, "Content-Type": "application/json" } });
    assert.equal(replay.body.duplicate, true);

    // A second subscription is refused; the portal is offered instead.
    assert.equal((await h.call("POST", "/v1/billing/checkout", { token, json: { plan: "yearly" } })).status, 409);
    const portal = await h.call("POST", "/v1/billing/portal", { token });
    assert.equal(portal.body.url, "https://billing.stripe.com/p/session/cus_1");

    // Cloud sync with optimistic concurrency.
    const put1 = await h.call("PUT", "/v1/sync", { token, json: { baseVersion: 0, device: "PC", data: { settings: { accent: "#4ade80" } } } });
    assert.equal(put1.status, 200);
    assert.equal(put1.body.version, 1);
    const stale = await h.call("PUT", "/v1/sync", { token, json: { baseVersion: 0, data: { settings: {} } } });
    assert.equal(stale.status, 409);
    assert.equal(stale.body.current.version, 1);
    const got = await h.call("GET", "/v1/sync", { token });
    assert.deepEqual(got.body.data, { settings: { accent: "#4ade80" } });
    const huge = await h.call("PUT", "/v1/sync", { token, json: { baseVersion: 1, data: { blob: "x".repeat(300 * 1024) } } });
    assert.equal(huge.status, 413);

    // Account deletion is blocked while a subscription renews.
    assert.equal((await h.call("DELETE", "/v1/me", { token })).status, 409);

    // Stripe cancels the subscription → Premium ends.
    const sub = [...h.stripe.subscriptions.values()][0]!;
    const cancelled = h.stripe.event("customer.subscription.deleted", { ...sub, status: "canceled" });
    await h.call("POST", "/v1/stripe/webhook", { body: cancelled.payload, headers: { "Stripe-Signature": cancelled.signature, "Content-Type": "application/json" } });
    const after = await h.call("GET", "/v1/me", { token });
    assert.equal(after.body.entitlement.premium, false);
    assert.equal(after.body.entitlement.status, "canceled");
    assert.equal((await h.call("GET", "/v1/sync", { token })).status, 402);

    assert.equal((await h.call("DELETE", "/v1/me", { token })).status, 204);
    assert.equal(h.store.users.size, 0);
    assert.equal(h.store.sync.size, 0);
  } finally {
    await h.close();
  }
});

test("lifetime purchase, refund and restore", async () => {
  const h = await harness();
  try {
    const token = await h.signIn();
    const checkout = await h.call("POST", "/v1/billing/checkout", { token, json: { plan: "lifetime" } });
    assert.equal(checkout.status, 200);
    const created = h.stripe.calls.find((c) => c.path === "/v1/checkout/sessions")!;
    assert.equal(created.params.mode, "payment");
    assert.equal(created.params["line_items[0][price]"], "price_life");

    const sessionId = [...h.stripe.checkoutSessions.keys()][0]!;
    const ev = h.stripe.complete(sessionId);
    await h.call("POST", "/v1/stripe/webhook", { body: ev.payload, headers: { "Stripe-Signature": ev.signature, "Content-Type": "application/json" } });
    let me = await h.call("GET", "/v1/me", { token });
    assert.equal(me.body.entitlement.source, "lifetime");
    assert.equal(me.body.entitlement.premium, true);
    assert.equal((await h.call("POST", "/v1/billing/checkout", { token, json: { plan: "lifetime" } })).status, 409);

    // Refund revokes; a late replay of the original event can't restore it.
    const cs = h.stripe.checkoutSessions.get(sessionId)!;
    const refund = h.stripe.event("charge.refunded", { id: "ch_x", object: "charge", payment_intent: cs.payment_intent, refunded: true });
    await h.call("POST", "/v1/stripe/webhook", { body: refund.payload, headers: { "Stripe-Signature": refund.signature, "Content-Type": "application/json" } });
    const late = h.stripe.event("checkout.session.completed", cs);
    await h.call("POST", "/v1/stripe/webhook", { body: late.payload, headers: { "Stripe-Signature": late.signature, "Content-Type": "application/json" } });
    me = await h.call("GET", "/v1/me", { token });
    assert.equal(me.body.entitlement.premium, false);

    // "Restore purchases" re-reads Stripe: a separate paid session that wasn't delivered by webhook is found.
    const second = await h.call("POST", "/v1/billing/checkout", { token, json: { plan: "lifetime" } });
    assert.equal(second.status, 200);
    const id2 = [...h.stripe.checkoutSessions.keys()][1]!;
    h.stripe.complete(id2); // payment happens, but the webhook is lost
    const restored = await h.call("POST", "/v1/billing/refresh", { token });
    assert.equal(restored.status, 200);
    assert.equal(restored.body.entitlement.premium, true);
  } finally {
    await h.close();
  }
});

test("webhooks: signatures are required and checked", async () => {
  const h = await harness();
  try {
    const payload = JSON.stringify({ id: "evt_1", type: "customer.subscription.updated", data: { object: {} } });
    const post = (headers: Record<string, string>) => h.call("POST", "/v1/stripe/webhook", { body: payload, headers: { "Content-Type": "application/json", ...headers } });
    assert.equal((await post({})).status, 400);
    assert.equal((await post({ "Stripe-Signature": signWebhook(payload, "whsec_wrong") })).status, 400);
    const old = signWebhook(payload, WEBHOOK_SECRET, Math.floor(Date.now() / 1000) - 3600);
    assert.equal((await post({ "Stripe-Signature": old })).status, 400);
    assert.equal((await post({ "Stripe-Signature": signWebhook(payload, WEBHOOK_SECRET) })).status, 200);
    // Multiple v1 signatures (secret rotation): any match is accepted.
    const t = Math.floor(Date.now() / 1000);
    const good = signWebhook(payload, WEBHOOK_SECRET, t).split("v1=")[1];
    const header = `t=${t},v1=${"0".repeat(64)},v1=${good}`;
    assert.equal(verifyWebhook(payload, header, WEBHOOK_SECRET).id, "evt_1");
  } finally {
    await h.close();
  }
});

test("website sign-in link, cookie session and CSRF origin check", async () => {
  const h = await harness();
  try {
    const token = await h.signIn();
    const link = await h.call("POST", "/v1/auth/web-link", { token });
    assert.equal(link.status, 200);
    const code = new URL(link.body.url).hash.replace("#code=", "");
    assert.match(link.body.url, /^http:\/\/bloom\.test\/account\.html#code=/);

    const ex = await h.call("POST", "/v1/auth/web-exchange", { json: { code } });
    assert.equal(ex.status, 204);
    const cookie = ex.headers.get("set-cookie")!;
    assert.match(cookie, /bloom_session=bw_/);
    assert.match(cookie, /HttpOnly/);
    assert.match(cookie, /SameSite=Lax/);
    // One-time code
    assert.equal((await h.call("POST", "/v1/auth/web-exchange", { json: { code } })).status, 400);

    const session = cookie.split(";")[0]!;
    const me = await h.call("GET", "/v1/me", { headers: { Cookie: session } });
    assert.equal(me.body.user.username, "Steve");
    // Cookie-authenticated writes need our Origin.
    assert.equal((await h.call("POST", "/v1/billing/refresh", { headers: { Cookie: session } })).status, 403);
    assert.equal((await h.call("POST", "/v1/billing/refresh", { headers: { Cookie: session, Origin: "https://evil.example" } })).status, 403);
    assert.equal((await h.call("POST", "/v1/billing/refresh", { headers: { Cookie: session, Origin: "http://bloom.test" } })).status, 200);
    // Website sessions can't mint new links.
    assert.equal((await h.call("POST", "/v1/auth/web-link", { headers: { Cookie: session, Origin: "http://bloom.test" } })).status, 403);
  } finally {
    await h.close();
  }
});

test("rate limiting on sign-in", async () => {
  const h = await harness();
  try {
    let last = 0;
    for (let i = 0; i < 12; i++) last = (await h.call("POST", "/v1/auth/challenge")).status;
    assert.equal(last, 429);
  } finally {
    await h.close();
  }
});

test("billing is disabled cleanly without Stripe", async () => {
  const h = await harness({ stripe: null });
  try {
    const plans = await h.call("GET", "/v1/plans");
    assert.equal(plans.body.billingEnabled, false);
    const token = await h.signIn();
    assert.equal((await h.call("POST", "/v1/billing/checkout", { token, json: { plan: "monthly" } })).status, 503);
    assert.equal((await h.call("POST", "/v1/stripe/webhook", { body: "{}" })).status, 404);
  } finally {
    await h.close();
  }
});

test("updater endpoint, releases and downloads", async () => {
  const dir = await mkdtemp(join(tmpdir(), "bloom-"));
  const manifest = join(dir, "latest.json");
  await writeFile(
    manifest,
    JSON.stringify({ version: "0.2.0", notes: "Faster", pub_date: "2026-11-01T00:00:00Z", platforms: { "windows-x86_64": { url: "https://github.com/o/r/releases/download/v0.2.0/Bloom_0.2.0_x64-setup.exe", signature: "sig" } } }),
  );
  await writeFile(join(dir, "releases.json"), JSON.stringify([{ version: "0.2.0", date: "2026-11-01", notes: "Faster" }]));
  const h = await harness({ releaseManifest: manifest, contentDir: dir });
  try {
    const up = await h.call("GET", "/v1/updates/windows/x86_64/0.1.0");
    assert.equal(up.status, 200);
    assert.equal(up.body.version, "0.2.0");
    assert.equal(up.body.signature, "sig");
    assert.equal((await h.call("GET", "/v1/updates/windows/x86_64/0.2.0")).status, 204);
    assert.equal((await h.call("GET", "/v1/updates/linux/x86_64/0.1.0")).status, 204);
    const dl = await h.call("GET", "/v1/download");
    assert.equal(dl.status, 302);
    assert.match(dl.headers.get("location")!, /Bloom_0\.2\.0_x64-setup\.exe$/);
    assert.equal((await h.call("GET", "/v1/releases")).body.releases[0].version, "0.2.0");
    assert.equal((await h.call("GET", "/v1/nope")).status, 404);
    assert.equal((await h.call("DELETE", "/v1/plans")).status, 405);
  } finally {
    await h.close();
  }
  assert.ok(compareVersions("0.10.0", "0.9.9") > 0);
  assert.ok(compareVersions("1.0.0-beta.1", "1.0.0") < 0);
  assert.equal(compareVersions("v1.2.3", "1.2.3"), 0);
});

test("entitlement rules", () => {
  const now = Date.now();
  const sub = (status: string, end = now + 1e9) => ({ id: status, userId: "u", status, priceId: "p", plan: "monthly" as const, currentPeriodEnd: end, cancelAtPeriodEnd: false });
  assert.equal(computeEntitlement({ subscriptions: [sub("active")], purchases: [], grant: null }, now).premium, true);
  assert.equal(computeEntitlement({ subscriptions: [sub("past_due")], purchases: [], grant: null }, now).premium, true, "grace while Stripe retries");
  for (const s of ["canceled", "unpaid", "incomplete", "incomplete_expired", "paused"]) {
    assert.equal(computeEntitlement({ subscriptions: [sub(s)], purchases: [], grant: null }, now).premium, false, s);
  }
  // An active subscription wins over an older canceled one.
  assert.equal(computeEntitlement({ subscriptions: [sub("canceled", now + 2e9), sub("active")], purchases: [], grant: null }, now).status, "active");
  assert.equal(computeEntitlement({ subscriptions: [], purchases: [{ id: "c", userId: "u", paymentIntent: "pi", priceId: null, plan: "lifetime", status: "refunded" }], grant: null }, now).premium, false);
  assert.equal(computeEntitlement({ subscriptions: [], purchases: [], grant: { userId: "u", reason: "x", expiresAt: now - 1 } }, now).premium, false);
  assert.equal(computeEntitlement({ subscriptions: [], purchases: [], grant: { userId: "u", reason: "x", expiresAt: null } }, now).source, "grant");
});

test("config keeps test and live Stripe keys apart", () => {
  const base = { STRIPE_WEBHOOK_SECRET: "whsec_x", STRIPE_PRICE_MONTHLY: "price_1" };
  assert.throws(() => loadConfig({ ...base, BLOOM_ENV: "development", STRIPE_SECRET_KEY: "sk_live_abc" }), ConfigError);
  assert.throws(() => loadConfig({ ...base, BLOOM_ENV: "production", PUBLIC_URL: "https://b.example", DATABASE_URL: "postgres://x", STRIPE_SECRET_KEY: "sk_test_abc" }), ConfigError);
  assert.ok(loadConfig({ ...base, BLOOM_ENV: "production", PUBLIC_URL: "https://b.example", DATABASE_URL: "postgres://x", STRIPE_SECRET_KEY: "sk_test_abc", STRIPE_ALLOW_TEST_KEYS: "1" }).stripe);
  assert.throws(() => loadConfig({ BLOOM_ENV: "development", STRIPE_SECRET_KEY: "sk_test_abc", STRIPE_PRICE_MONTHLY: "price_1" }), /WEBHOOK_SECRET/);
  assert.throws(() => loadConfig({ BLOOM_ENV: "production", PUBLIC_URL: "http://insecure.example" }), /https/);
  assert.throws(() => loadConfig({ BLOOM_ENV: "production", PUBLIC_URL: "https://b.example" }), /DATABASE_URL/);
  assert.equal(loadConfig({ BLOOM_ENV: "development" }).stripe, null);
});

test("form encoding and static files", async () => {
  assert.equal(encodeForm({ a: 1, b: { c: [{ d: "x y" }] }, e: undefined }), "a=1&b%5Bc%5D%5B0%5D%5Bd%5D=x%20y");
  const dir = await mkdtemp(join(tmpdir(), "site-"));
  await mkdir(join(dir, "css"));
  await writeFile(join(dir, "index.html"), "<h1>home</h1>");
  await writeFile(join(dir, "pricing.html"), "<h1>pricing</h1>");
  await writeFile(join(dir, "css", "site.css"), "body{}");
  assert.equal(String((await serveStatic(dir, "/")).raw), "<h1>home</h1>");
  assert.equal(String((await serveStatic(dir, "/pricing")).raw), "<h1>pricing</h1>");
  const css = await serveStatic(dir, "/css/site.css");
  assert.equal(css.headers?.["Content-Type"], "text/css; charset=utf-8");
  await assert.rejects(serveStatic(dir, "/../../etc/passwd"));
  await assert.rejects(serveStatic(dir, "/%2e%2e/%2e%2e/etc/passwd"));
});

test("license keys: admin creates keys, one PC redeems, moving is limited to once per 24 h", async () => {
  const ADMIN = "a-very-long-admin-password-123";
  const h = await harness({ adminToken: ADMIN });
  const pcA = "a".repeat(64);
  const pcB = "b".repeat(64);
  try {
    // Admin routes need the password.
    assert.equal((await h.call("POST", "/v1/admin/keys", { json: { count: 1 } })).status, 401);
    assert.equal((await h.call("POST", "/v1/admin/keys", { token: "wrong-password-wrong-password", json: { count: 1 } })).status, 401);
    const made = await h.call("POST", "/v1/admin/keys", { token: ADMIN, json: { count: 2, days: 30, label: "giveaway" } });
    assert.equal(made.status, 200);
    assert.equal(made.body.keys.length, 2);
    const key: string = made.body.keys[0];
    assert.match(key, /^BLOOD(-[0-9A-HJKMNP-TV-Z]{5}){4}$/);
    // Only hashes are stored, and the keys never reach the logs.
    const stored = [...h.store.licenses.values()];
    assert.ok(stored.every((k) => !k.keyHash.toString("latin1").includes(key.slice(6, 11))));
    assert.ok(!JSON.stringify(h.logs).includes(key));

    // Bad and unknown keys.
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key: "BLOOD-NOPE", hwid: pcA } })).status, 400);
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key: "BLOOD-00000-00000-00000-00000", hwid: pcA } })).body.error.code, "invalid_key");
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: "not-a-hash" } })).status, 400);

    // PC A redeems (lower case, no dashes, O for 0 all accepted).
    const sloppy = key.toLowerCase().replace(/-/g, " ").replace(/0/g, "o");
    const a1 = await h.call("POST", "/v1/license/activate", { json: { key: sloppy, hwid: pcA } });
    assert.equal(a1.status, 200);
    assert.equal(a1.body.valid, true);
    assert.equal(a1.body.boundElsewhere, false);
    assert.ok(a1.body.expiresAt > Date.now() + 29 * 86_400_000);
    // Re-checking from PC A keeps working.
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } })).body.valid, true);

    // PC B (a shared key) gets nothing, and can't take it over within 24 h of activation.
    const b1 = await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcB } });
    assert.equal(b1.body.valid, false);
    assert.equal(b1.body.boundElsewhere, true);
    assert.ok(b1.body.nextMoveAt > Date.now());
    const b2 = await h.call("POST", "/v1/license/move", { json: { key, hwid: pcB } });
    assert.equal(b2.body.valid, false);
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } })).body.valid, true);

    // A day later the owner can move it to PC B; PC A loses Premium and must wait 24 h to move back.
    const rec = [...h.store.licenses.values()].find((k) => k.redeemedAt !== null)!;
    rec.lastMoveAt = Date.now() - 25 * 3_600_000;
    const b3 = await h.call("POST", "/v1/license/move", { json: { key, hwid: pcB } });
    assert.equal(b3.body.valid, true);
    const a2 = await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } });
    assert.equal(a2.body.valid, false);
    assert.equal(a2.body.boundElsewhere, true);
    assert.equal((await h.call("POST", "/v1/license/move", { json: { key, hwid: pcA } })).body.valid, false);

    // A signed-in player on the PC that holds the key gets server Premium (cloud sync) too; other PCs don't.
    const token = await h.signIn();
    assert.equal((await h.call("GET", "/v1/sync", { token })).status, 402);
    assert.equal((await h.call("GET", "/v1/sync", { token, headers: { "X-Blood-License": key, "X-Blood-Hwid": pcA } })).status, 402);
    assert.equal((await h.call("GET", "/v1/sync", { token, headers: { "X-Blood-License": key, "X-Blood-Hwid": pcB } })).status, 200);
    const me = await h.call("GET", "/v1/me", { token, headers: { "X-Blood-License": key, "X-Blood-Hwid": pcB } });
    assert.equal(me.body.entitlement.premium, true);
    assert.equal(me.body.entitlement.source, "license");

    // Admin: list, unbind (next PC to activate takes it), revoke, restore.
    const list = await h.call("GET", "/v1/admin/keys", { token: ADMIN });
    assert.equal(list.body.keys.length, 2);
    const info = list.body.keys.find((k: { id: string }) => k.id === rec.id);
    assert.equal(info.bound, true);
    assert.equal(info.label, "giveaway");
    assert.equal(info.hint, key.slice(-5));
    await h.call("POST", "/v1/admin/keys/update", { token: ADMIN, json: { id: rec.id, action: "unbind" } });
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } })).body.valid, true);
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcB } })).body.valid, false);
    await h.call("POST", "/v1/admin/keys/update", { token: ADMIN, json: { id: rec.id, action: "revoke" } });
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } })).body.error.code, "key_revoked");
    await h.call("POST", "/v1/admin/keys/update", { token: ADMIN, json: { id: rec.id, action: "restore" } });
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } })).body.valid, true);

    // Expired keys stop working.
    rec.expiresAt = Date.now() - 1000;
    h.store.licenses.get(rec.id)!.expiresAt = Date.now() - 1000;
    assert.equal((await h.call("POST", "/v1/license/activate", { json: { key, hwid: pcA } })).body.valid, false);
  } finally {
    await h.close();
  }
});

test("admin key routes don't exist without ADMIN_TOKEN", async () => {
  const h = await harness();
  try {
    assert.equal((await h.call("GET", "/v1/admin/keys", { token: "anything-anything-anything" })).status, 404);
    assert.throws(() => loadConfig({ ADMIN_TOKEN: "short" }), ConfigError);
  } finally {
    await h.close();
  }
});
