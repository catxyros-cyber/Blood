/** BLOOD API: routes and request pipeline. `createApp` is pure wiring so tests can inject fakes. */

import { createHash, randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import { readFile } from "node:fs/promises";
import type { IncomingMessage, ServerResponse } from "node:http";
import { join } from "node:path";
import {
  AdminCapeAction,
  AdminCreateCape,
  AdminCreateKeys,
  AdminKeyAction,
  AdminSetTag,
  CAPE_MAX_BYTES,
  capeFrames,
  ONLINE_WINDOW_MS,
  PresenceRequest,
  capeSizeProblem,
  normalizeUuid,
  SetCapeRequest,
  type CapeInfo,
  CheckoutRequest,
  formatKey,
  KEY_ALPHABET,
  KEY_LENGTH,
  keyHint,
  LICENSE_MOVE_COOLDOWN_MS,
  LicenseRequest,
  normalizeKey,
  type AdminKeyInfo,
  type LicenseStatus,
  PLAN_NAMES,
  SYNC_MAX_BYTES,
  SyncPutRequest,
  VerifyRequest,
  WebExchangeRequest,
  type Announcement,
  type ChallengeResponse,
  type Entitlement,
  type MeResponse,
  type PlanId,
  type PlanInfo,
  type PlansResponse,
  type PublicUser,
  type ReleaseInfo,
  type Schema,
  type SessionResponse,
} from "@bloom/shared";
import type { Config } from "./config";
import { computeEntitlement, planForPrice } from "./entitlements";
import { HttpError, makeCtx, RateLimiter, Router, serializeCookie, writeReply, type Ctx, type Reply } from "./http";
import { hasJoined, MojangUnavailable } from "./mojang";
import { serveStatic } from "./static";
import type { Cape, LicenseKey, Store, User } from "./store/types";
import {
  periodEnd,
  Stripe,
  StripeError,
  verifyWebhook,
  WebhookSignatureError,
  type Fetch,
  type StripeCharge,
  type StripeCheckoutSession,
  type StripeEvent,
  type StripePrice,
  type StripeSubscription,
} from "./stripe";

export interface Deps {
  config: Config;
  store: Store;
  stripe: Stripe | null;
  /** Used for Mojang and the release manifest. */
  fetch?: Fetch;
  now?: () => number;
  log?: (entry: Record<string, unknown>) => void;
}

const SESSION_COOKIE = "bloom_session";
const CHALLENGE_TTL = 2 * 60_000;
const WEB_CODE_TTL = 5 * 60_000;
const WEB_SESSION_DAYS = 30;

const sha256 = (s: string) => createHash("sha256").update(s).digest();
const token = (prefix: string) => `${prefix}_${randomBytes(32).toString("base64url")}`;

function parse<T>(schema: Schema<T>, input: unknown): T {
  const r = schema.parse(input);
  if (!r.ok) throw new HttpError(400, "invalid_request", r.error);
  return r.value;
}

function publicUser(u: User): PublicUser {
  return { id: u.id, minecraftUuid: u.minecraftUuid, username: u.username, createdAt: u.createdAt };
}

/** Compare dotted versions numerically; a pre-release sorts before its release. */
export function compareVersions(a: string, b: string): number {
  const split = (v: string) => {
    const [core, pre] = v.replace(/^v/, "").split("-", 2);
    return { nums: (core ?? "").split(".").map((n) => Number(n) || 0), pre: pre ?? "" };
  };
  const x = split(a);
  const y = split(b);
  for (let i = 0; i < Math.max(x.nums.length, y.nums.length); i++) {
    const d = (x.nums[i] ?? 0) - (y.nums[i] ?? 0);
    if (d !== 0) return d;
  }
  if (x.pre === y.pre) return 0;
  if (!x.pre) return 1;
  if (!y.pre) return -1;
  return x.pre < y.pre ? -1 : 1;
}

interface TauriManifest {
  version: string;
  notes?: string;
  pub_date?: string;
  platforms: Record<string, { url: string; signature: string }>;
}

export function createApp(deps: Deps) {
  const { config, store, stripe } = deps;
  const fetchImpl = deps.fetch ?? fetch;
  const now = deps.now ?? Date.now;
  const log = deps.log ?? ((e) => console.log(JSON.stringify(e)));
  const origin = new URL(config.publicUrl).origin;
  const cookieSecure = config.publicUrl.startsWith("https://");

  const limits = {
    auth: new RateLimiter(10, 10 / 60),
    billing: new RateLimiter(20, 20 / 60),
    general: new RateLimiter(120, 2),
    // Key checks: generous enough for a launcher re-checking, far too slow to guess keys.
    license: new RateLimiter(30, 30 / 60),
    admin: new RateLimiter(30, 30 / 60),
    // Clients look up capes of nearby players in batches; generous but bounded.
    capes: new RateLimiter(60, 1),
  };
  const sweeper = setInterval(() => {
    for (const l of Object.values(limits)) l.sweep();
    void store.cleanup(now()).catch(() => undefined);
  }, 5 * 60_000);
  sweeper.unref();

  function limit(ctx: Ctx, group: keyof typeof limits) {
    const r = limits[group].take(`${group}:${ctx.ip}`, now());
    if (!r.ok) throw new HttpError(429, "rate_limited", "Too many requests. Please slow down.", { "Retry-After": String(r.retryAfterSec) });
  }

  // --- auth helpers ---------------------------------------------------------

  async function authenticate(ctx: Ctx, opts: { required: true }): Promise<{ user: User; kind: "launcher" | "web" }>;
  async function authenticate(ctx: Ctx, opts: { required: false }): Promise<{ user: User; kind: "launcher" | "web" } | null>;
  async function authenticate(ctx: Ctx, opts: { required: boolean }) {
    const header = ctx.req.headers.authorization;
    let raw: string | undefined;
    let viaCookie = false;
    if (header?.startsWith("Bearer ")) raw = header.slice(7).trim();
    else if (ctx.cookies[SESSION_COOKIE]) {
      raw = ctx.cookies[SESSION_COOKIE];
      viaCookie = true;
    }
    if (!raw) {
      if (opts.required) throw new HttpError(401, "unauthenticated", "Sign in first.");
      return null;
    }
    // Cookie-authenticated writes must come from our own site (CSRF protection on top of SameSite=Lax).
    if (viaCookie && ctx.method !== "GET" && ctx.method !== "HEAD") {
      const o = ctx.req.headers.origin;
      if (o !== origin) throw new HttpError(403, "bad_origin", "This request must come from the BLOOD website.");
    }
    const session = await store.useSession(sha256(raw), now());
    if (!session || (viaCookie && session.kind !== "web")) {
      if (opts.required) throw new HttpError(401, "session_expired", "Your session has expired. Please sign in again.");
      return null;
    }
    const user = await store.getUser(session.userId);
    if (!user) throw new HttpError(401, "session_expired", "Your session has expired. Please sign in again.");
    ctx.state.tokenHash = sha256(raw);
    return { user, kind: session.kind };
  }

  /**
   * Premium for this user. When the launcher sends a license key (X-Blood-License + X-Blood-Hwid)
   * and that key is active on that PC, the request counts as Premium too, so key holders get
   * server features like cloud sync on the PC their key is bound to.
   */
  async function entitlementFor(userId: string, ctx?: Ctx): Promise<Entitlement> {
    const [subscriptions, purchases, grant] = await Promise.all([store.listSubscriptions(userId), store.listPurchases(userId), store.getGrant(userId)]);
    const ent = computeEntitlement({ subscriptions, purchases, grant }, now());
    if (ent.premium || !ctx) return ent;
    const lic = await licenseFromHeaders(ctx);
    if (!lic) return ent;
    return { ...ent, premium: true, source: "license", plan: null, status: null, currentPeriodEnd: lic.expiresAt, cancelAtPeriodEnd: false };
  }

  function requireStripe(): Stripe {
    if (!stripe || !config.stripe) throw new HttpError(503, "billing_disabled", "Purchases are not available on this server.");
    return stripe;
  }

  // --- Stripe sync ----------------------------------------------------------

  async function saveSubscription(userId: string, sub: StripeSubscription) {
    const priceId = sub.items?.data?.[0]?.price?.id ?? null;
    await store.upsertSubscription({
      id: sub.id,
      userId,
      status: sub.status,
      priceId,
      plan: planForPrice(config.stripe?.prices ?? {}, priceId),
      currentPeriodEnd: periodEnd(sub),
      cancelAtPeriodEnd: Boolean(sub.cancel_at_period_end),
    });
  }

  async function saveCheckout(userId: string, cs: StripeCheckoutSession) {
    if (cs.mode === "subscription" && cs.subscription) {
      const sub = await requireStripe().request<StripeSubscription>("GET", `/v1/subscriptions/${encodeURIComponent(cs.subscription)}`);
      await saveSubscription(userId, sub);
    } else if (cs.mode === "payment" && cs.payment_status === "paid") {
      const plan = (cs.metadata?.plan as PlanId | undefined) ?? "lifetime";
      if (plan !== "lifetime") return;
      await store.upsertPurchase({ id: cs.id, userId, paymentIntent: cs.payment_intent, priceId: config.stripe?.prices.lifetime ?? null, plan, status: "paid" });
    }
  }

  async function userForStripeObject(obj: { customer?: string | null; client_reference_id?: string | null; metadata?: Record<string, string> }): Promise<User | null> {
    const ref = obj.client_reference_id ?? obj.metadata?.bloom_user_id;
    if (ref) {
      const u = await store.getUser(ref);
      if (u) {
        if (obj.customer && !u.stripeCustomerId) await store.setStripeCustomer(u.id, obj.customer);
        return u;
      }
    }
    if (obj.customer) return store.getUserByCustomer(obj.customer);
    return null;
  }

  async function handleEvent(event: StripeEvent) {
    const obj = event.data.object as Record<string, unknown>;
    switch (event.type) {
      case "checkout.session.completed":
      case "checkout.session.async_payment_succeeded": {
        const cs = obj as unknown as StripeCheckoutSession;
        const user = await userForStripeObject(cs);
        if (!user) return log({ msg: "webhook: no user for checkout session", event: event.id });
        await saveCheckout(user.id, cs);
        return;
      }
      case "customer.subscription.created":
      case "customer.subscription.updated":
      case "customer.subscription.deleted":
      case "customer.subscription.paused":
      case "customer.subscription.resumed": {
        const sub = obj as unknown as StripeSubscription;
        const user = await userForStripeObject({ customer: sub.customer, metadata: sub.metadata });
        if (!user) return log({ msg: "webhook: no user for subscription", event: event.id });
        await saveSubscription(user.id, sub);
        return;
      }
      case "charge.refunded": {
        const charge = obj as unknown as StripeCharge;
        if (charge.refunded && charge.payment_intent) await store.refundPurchase(charge.payment_intent);
        return;
      }
      default:
        return; // Unhandled event types are acknowledged.
    }
  }

  /** Re-read everything Stripe knows about this user ("Restore purchases"). */
  async function syncFromStripe(user: User): Promise<void> {
    const s = requireStripe();
    let customer = user.stripeCustomerId;
    if (!customer) {
      const found = await s.request<{ data: { id: string }[] }>("GET", "/v1/customers/search", { query: `metadata['bloom_user_id']:'${user.id}'` });
      customer = found.data[0]?.id ?? null;
      if (!customer) return;
      await store.setStripeCustomer(user.id, customer);
    }
    const subs = await s.request<{ data: StripeSubscription[] }>("GET", "/v1/subscriptions", { customer, status: "all", limit: 20 });
    for (const sub of subs.data) await saveSubscription(user.id, sub);
    const sessions = await s.request<{ data: StripeCheckoutSession[] }>("GET", "/v1/checkout/sessions", { customer, limit: 50 });
    for (const cs of sessions.data) {
      if (cs.mode !== "payment" || cs.payment_status !== "paid") continue;
      await saveCheckout(user.id, cs);
      if (cs.payment_intent) {
        const pi = await s.request<{ latest_charge?: StripeCharge | string | null }>("GET", `/v1/payment_intents/${encodeURIComponent(cs.payment_intent)}`, {
          expand: ["latest_charge"],
        });
        if (pi.latest_charge && typeof pi.latest_charge === "object" && pi.latest_charge.refunded) await store.refundPurchase(cs.payment_intent);
      }
    }
  }

  async function ensureCustomer(user: User): Promise<string> {
    if (user.stripeCustomerId) return user.stripeCustomerId;
    const c = await requireStripe().request<{ id: string }>("POST", "/v1/customers", {
      name: user.username,
      metadata: { bloom_user_id: user.id, minecraft_uuid: user.minecraftUuid },
    });
    await store.setStripeCustomer(user.id, c.id);
    return c.id;
  }

  // --- plans (cached prices) --------------------------------------------------

  let plansCache: { at: number; value: PlansResponse } | null = null;
  async function plans(): Promise<PlansResponse> {
    if (!stripe || !config.stripe) {
      return { billingEnabled: false, plans: (["monthly", "yearly", "lifetime"] as PlanId[]).map((id) => ({ id, name: PLAN_NAMES[id], interval: id === "monthly" ? "month" : id === "yearly" ? "year" : null, amount: 0, currency: "usd", available: false })) };
    }
    if (plansCache && now() - plansCache.at < 10 * 60_000) return plansCache.value;
    const out: PlanInfo[] = [];
    for (const id of ["monthly", "yearly", "lifetime"] as PlanId[]) {
      const priceId = config.stripe.prices[id];
      const fallback: PlanInfo = { id, name: PLAN_NAMES[id], interval: id === "monthly" ? "month" : id === "yearly" ? "year" : null, amount: 0, currency: "usd", available: false };
      if (!priceId) {
        out.push(fallback);
        continue;
      }
      try {
        const p = await stripe.request<StripePrice>("GET", `/v1/prices/${encodeURIComponent(priceId)}`);
        const interval = p.recurring?.interval === "month" || p.recurring?.interval === "year" ? p.recurring.interval : null;
        out.push({ id, name: PLAN_NAMES[id], interval, amount: p.unit_amount ?? 0, currency: p.currency, available: p.active && p.unit_amount !== null });
      } catch (e) {
        log({ msg: "could not load price", plan: id, error: (e as Error).message });
        out.push(fallback);
      }
    }
    const value = { billingEnabled: true, plans: out };
    plansCache = { at: now(), value };
    return value;
  }

  // --- content ----------------------------------------------------------------

  async function readContent<T>(file: string, fallback: T): Promise<T> {
    try {
      return JSON.parse(await readFile(join(config.contentDir, file), "utf8")) as T;
    } catch {
      return fallback;
    }
  }

  let manifestCache: { at: number; value: TauriManifest | null } | null = null;
  async function releaseManifest(): Promise<TauriManifest | null> {
    if (!config.releaseManifest) return null;
    if (manifestCache && now() - manifestCache.at < 5 * 60_000) return manifestCache.value;
    let value: TauriManifest | null = null;
    try {
      const src = config.releaseManifest;
      const text = src.startsWith("https://") ? await (await fetchImpl(src, { signal: AbortSignal.timeout(10_000) })).text() : await readFile(src, "utf8");
      const parsed = JSON.parse(text) as TauriManifest;
      if (parsed.version && parsed.platforms) value = parsed;
    } catch (e) {
      log({ msg: "release manifest unavailable", error: (e as Error).message });
    }
    manifestCache = { at: now(), value };
    return value;
  }

  // --- routes -----------------------------------------------------------------

  const r = new Router();

  r.get("/healthz", () => ({ status: 200, body: { ok: true } }));

  r.get("/v1/plans", async () => ({ status: 200, body: await plans(), headers: { "Cache-Control": "public, max-age=300" } }));

  r.post("/v1/auth/challenge", async (ctx) => {
    limit(ctx, "auth");
    const challengeId = randomBytes(18).toString("base64url");
    // Mojang serverIds are short hex strings (like a server's SHA-1 hash).
    const serverId = randomBytes(20).toString("hex");
    const expiresAt = now() + CHALLENGE_TTL;
    await store.createChallenge({ id: challengeId, serverId, expiresAt });
    const body: ChallengeResponse = { challengeId, serverId, expiresAt };
    return { status: 200, body };
  });

  r.post("/v1/auth/verify", async (ctx) => {
    limit(ctx, "auth");
    const req = parse(VerifyRequest, await ctx.json());
    const challenge = await store.takeChallenge(req.challengeId, now());
    if (!challenge) throw new HttpError(400, "challenge_expired", "The sign-in challenge expired. Please try again.");
    let profile;
    try {
      profile = await hasJoined(req.username, challenge.serverId, fetchImpl);
    } catch (e) {
      if (e instanceof MojangUnavailable) throw new HttpError(503, "mojang_unavailable", "Minecraft's servers can't be reached right now. Try again shortly.");
      throw e;
    }
    if (!profile) throw new HttpError(401, "not_verified", "Minecraft couldn't confirm this account. Make sure you're signed in to the launcher and try again.");
    const user = await store.upsertUserByMinecraft(profile.uuid, profile.username);
    const raw = token("bl");
    const expiresAt = now() + config.sessionDays * 86_400_000;
    await store.createSession({ tokenHash: sha256(raw), userId: user.id, kind: "launcher", device: req.device ?? null, createdAt: now(), lastUsedAt: now(), expiresAt });
    log({ msg: "signed in", user: user.id, rid: ctx.requestId });
    const body: SessionResponse = { token: raw, expiresAt, user: publicUser(user), entitlement: await entitlementFor(user.id) };
    return { status: 200, body };
  });

  r.post("/v1/auth/logout", async (ctx) => {
    const auth = await authenticate(ctx, { required: false });
    if (auth && ctx.state.tokenHash) await store.deleteSession(ctx.state.tokenHash as Buffer);
    ctx.setHeader("Set-Cookie", serializeCookie(SESSION_COOKIE, "", { maxAge: 0, secure: cookieSecure }));
    return { status: 204 };
  });

  r.get("/v1/me", async (ctx) => {
    limit(ctx, "general");
    const { user } = await authenticate(ctx, { required: true });
    const body: MeResponse = { user: publicUser(user), entitlement: await entitlementFor(user.id, ctx) };
    return { status: 200, body, headers: { "Cache-Control": "no-store" } };
  });

  r.delete("/v1/me", async (ctx) => {
    limit(ctx, "billing");
    const { user } = await authenticate(ctx, { required: true });
    const ent = await entitlementFor(user.id);
    if (ent.source === "subscription" && !ent.cancelAtPeriodEnd) {
      throw new HttpError(409, "active_subscription", "Cancel your subscription in the billing portal before deleting your account.");
    }
    await deleteUser(user.id);
    ctx.setHeader("Set-Cookie", serializeCookie(SESSION_COOKIE, "", { maxAge: 0, secure: cookieSecure }));
    log({ msg: "account deleted", user: user.id });
    return { status: 204 };
  });

  async function deleteUser(userId: string) {
    // Payment records stay with Stripe (legal/tax retention); BLOOD removes its own data.
    await store.deleteUserSessions(userId);
    await store.deleteUser(userId);
  }

  r.post("/v1/auth/web-link", async (ctx) => {
    limit(ctx, "auth");
    const { user, kind } = await authenticate(ctx, { required: true });
    if (kind !== "launcher") throw new HttpError(403, "forbidden", "Only the launcher can create website sign-in links.");
    const code = randomBytes(24).toString("base64url");
    await store.createWebCode(sha256(code), user.id, now() + WEB_CODE_TTL);
    const next = ctx.query.get("next");
    const page = next && /^[a-z]+\.html$/.test(next) ? next : "account.html";
    return { status: 200, body: { url: `${config.publicUrl}/${page}#code=${code}` } };
  });

  r.post("/v1/auth/web-exchange", async (ctx) => {
    limit(ctx, "auth");
    if (ctx.req.headers.origin && ctx.req.headers.origin !== origin) throw new HttpError(403, "bad_origin", "Cross-site request refused.");
    const { code } = parse(WebExchangeRequest, await ctx.json());
    const userId = await store.takeWebCode(sha256(code), now());
    if (!userId) throw new HttpError(400, "link_expired", "This sign-in link has expired. Open the website again from BLOOD.");
    const raw = token("bw");
    const maxAge = WEB_SESSION_DAYS * 86_400;
    await store.createSession({ tokenHash: sha256(raw), userId, kind: "web", device: "website", createdAt: now(), lastUsedAt: now(), expiresAt: now() + maxAge * 1000 });
    ctx.setHeader("Set-Cookie", serializeCookie(SESSION_COOKIE, raw, { maxAge, secure: cookieSecure }));
    return { status: 204 };
  });

  r.post("/v1/billing/checkout", async (ctx) => {
    limit(ctx, "billing");
    const s = requireStripe();
    const { user } = await authenticate(ctx, { required: true });
    const { plan } = parse(CheckoutRequest, await ctx.json());
    const priceId = config.stripe!.prices[plan];
    if (!priceId) throw new HttpError(400, "plan_unavailable", `${PLAN_NAMES[plan]} isn't available.`);
    const ent = await entitlementFor(user.id);
    if (ent.source === "lifetime") throw new HttpError(409, "already_premium", "You already own BLOOD Premium Lifetime.");
    if (ent.source === "subscription" && plan !== "lifetime") {
      throw new HttpError(409, "already_subscribed", "You already have a subscription. Change or cancel it in the billing portal.");
    }
    const customer = await ensureCustomer(user);
    const metadata = { bloom_user_id: user.id, plan };
    const session = await s.request<StripeCheckoutSession>("POST", "/v1/checkout/sessions", {
      mode: plan === "lifetime" ? "payment" : "subscription",
      customer,
      client_reference_id: user.id,
      line_items: [{ price: priceId, quantity: 1 }],
      success_url: `${config.publicUrl}/success.html?session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: `${config.publicUrl}/cancel.html`,
      allow_promotion_codes: true,
      metadata,
      ...(plan === "lifetime" ? { payment_intent_data: { metadata } } : { subscription_data: { metadata } }),
    });
    if (!session.url) throw new HttpError(502, "stripe_error", "Stripe didn't return a checkout page.");
    log({ msg: "checkout created", user: user.id, plan });
    return { status: 200, body: { url: session.url } };
  });

  r.post("/v1/billing/portal", async (ctx) => {
    limit(ctx, "billing");
    const s = requireStripe();
    const { user } = await authenticate(ctx, { required: true });
    if (!user.stripeCustomerId) throw new HttpError(404, "no_billing", "There's no billing history for this account yet.");
    const portal = await s.request<{ url: string }>("POST", "/v1/billing_portal/sessions", { customer: user.stripeCustomerId, return_url: `${config.publicUrl}/account.html` });
    return { status: 200, body: { url: portal.url } };
  });

  r.post("/v1/billing/refresh", async (ctx) => {
    limit(ctx, "billing");
    const { user } = await authenticate(ctx, { required: true });
    await syncFromStripe(user);
    const fresh = (await store.getUser(user.id)) ?? user;
    const body: MeResponse = { user: publicUser(fresh), entitlement: await entitlementFor(user.id) };
    return { status: 200, body };
  });

  r.post("/v1/stripe/webhook", async (ctx) => {
    if (!config.stripe) throw new HttpError(404, "not_found", "Not found");
    const raw = await ctx.raw(512 * 1024);
    let event: StripeEvent;
    try {
      event = verifyWebhook(raw, ctx.req.headers["stripe-signature"] as string | undefined, config.stripe.webhookSecret, 300, now());
    } catch (e) {
      if (e instanceof WebhookSignatureError) {
        log({ msg: "webhook rejected", reason: e.message, ip: ctx.ip });
        throw new HttpError(400, "bad_signature", "Invalid signature");
      }
      throw e;
    }
    if (await store.hasEvent(event.id)) return { status: 200, body: { received: true, duplicate: true } };
    await handleEvent(event);
    await store.recordEvent(event.id, event.type);
    log({ msg: "webhook processed", type: event.type, event: event.id });
    return { status: 200, body: { received: true } };
  });

  r.get("/v1/sync", async (ctx) => {
    limit(ctx, "general");
    const { user } = await authenticate(ctx, { required: true });
    if (!(await entitlementFor(user.id, ctx)).premium) throw new HttpError(402, "premium_required", "Cloud sync is a BLOOD Premium feature.");
    const doc = await store.getSync(user.id);
    return { status: 200, body: doc ?? { version: 0, updatedAt: 0, data: {} }, headers: { "Cache-Control": "no-store" } };
  });

  r.put("/v1/sync", async (ctx) => {
    limit(ctx, "general");
    const { user } = await authenticate(ctx, { required: true });
    if (!(await entitlementFor(user.id, ctx)).premium) throw new HttpError(402, "premium_required", "Cloud sync is a BLOOD Premium feature.");
    const req = parse(SyncPutRequest, await ctx.json(SYNC_MAX_BYTES + 4096));
    if (Buffer.byteLength(JSON.stringify(req.data)) > SYNC_MAX_BYTES) throw new HttpError(413, "payload_too_large", "Synced settings are limited to 256 KB.");
    const result = await store.putSync(user.id, req.baseVersion, req.data, req.device ?? null, now());
    if (!result.ok) return { status: 409, body: { error: { code: "sync_conflict", message: "Settings changed on another device." }, current: result.conflict } };
    return { status: 200, body: result.doc };
  });

  r.get("/v1/releases", async () => {
    const releases = await readContent<ReleaseInfo[]>("releases.json", []);
    return { status: 200, body: { releases }, headers: { "Cache-Control": "public, max-age=300" } };
  });

  r.get("/v1/news", async (ctx) => {
    const auth = await authenticate(ctx, { required: false }).catch(() => null);
    const premium = auth ? (await entitlementFor(auth.user.id, ctx)).premium : false;
    const items = (await readContent<Announcement[]>("news.json", [])).filter((a) => !a.premiumOnly || premium);
    return { status: 200, body: { announcements: items }, headers: { "Cache-Control": "private, max-age=300" } };
  });

  r.get("/v1/download", async () => {
    const m = await releaseManifest();
    const url = m?.platforms["windows-x86_64-nsis"]?.url ?? m?.platforms["windows-x86_64"]?.url ?? (await readContent<ReleaseInfo[]>("releases.json", []))[0]?.downloadUrl;
    if (!url) throw new HttpError(404, "no_release", "No release has been published yet.");
    return { status: 302, headers: { Location: url, "Cache-Control": "public, max-age=300" } };
  });

  // Tauri updater endpoint: https://v2.tauri.app/plugin/updater/#dynamic-update-server
  r.get("/v1/updates/:target/:arch/:current", async (ctx) => {
    const m = await releaseManifest();
    if (!m) return { status: 204 };
    const key = `${ctx.params.target}-${ctx.params.arch}`;
    const platform = m.platforms[`${key}-nsis`] ?? m.platforms[key];
    if (!platform || compareVersions(m.version, ctx.params.current ?? "0") <= 0) return { status: 204 };
    return { status: 200, body: { version: m.version, notes: m.notes ?? "", pub_date: m.pub_date, url: platform.url, signature: platform.signature } };
  });

  // --- license keys -------------------------------------------------------------
  //
  // A key is bound to the first PC that redeems it. Only that PC gets Premium. The owner can
  // move it to another PC, but at most once every 24 hours, so passing a key around doesn't work.

  const keyHash = (normalized: string) => sha256(`blood-key:${normalized}`);
  // The launcher already sends a hash of the machine id; hash again so the stored value isn't
  // something a launcher could send.
  const hwidHash = (hwid: string) => sha256(`blood-hwid:${hwid}`);

  function licenseStatus(k: LicenseKey, hw: Buffer | null, at: number): LicenseStatus {
    const boundHere = Boolean(hw && k.hwidHash && k.hwidHash.equals(hw));
    const expired = k.expiresAt !== null && k.expiresAt <= at;
    const next = k.lastMoveAt !== null ? k.lastMoveAt + LICENSE_MOVE_COOLDOWN_MS : null;
    return {
      valid: boundHere && !expired && !k.revoked,
      expiresAt: k.expiresAt,
      boundElsewhere: Boolean(k.hwidHash) && !boundHere,
      nextMoveAt: next !== null && next > at ? next : null,
      hint: k.hint,
    };
  }

  async function findKey(raw: string): Promise<LicenseKey> {
    const norm = normalizeKey(raw);
    if (!norm) throw new HttpError(400, "invalid_key", "That doesn't look like a BLOOD key. Keys look like BLOOD-XXXXX-XXXXX-XXXXX-XXXXX.");
    const k = await store.getLicense(keyHash(norm));
    if (!k) throw new HttpError(404, "invalid_key", "This key doesn't exist. Check it for typos.");
    if (k.revoked) throw new HttpError(403, "key_revoked", "This key has been disabled.");
    return k;
  }

  async function licenseFromHeaders(ctx: Ctx): Promise<LicenseKey | null> {
    const rawKey = ctx.req.headers["x-blood-license"];
    const rawHw = ctx.req.headers["x-blood-hwid"];
    if (typeof rawKey !== "string" || typeof rawHw !== "string" || !/^[0-9a-f]{64}$/.test(rawHw)) return null;
    const norm = normalizeKey(rawKey);
    if (!norm) return null;
    const k = await store.getLicense(keyHash(norm));
    return k && licenseStatus(k, hwidHash(rawHw), now()).valid ? k : null;
  }

  r.post("/v1/license/activate", async (ctx) => {
    limit(ctx, "license");
    const req = parse(LicenseRequest, await ctx.json());
    let k = await findKey(req.key);
    const hw = hwidHash(req.hwid);
    const at = now();
    if (k.redeemedAt === null) {
      const expiresAt = k.durationDays ? at + k.durationDays * 86_400_000 : null;
      if (await store.redeemLicense(k.keyHash, hw, at, expiresAt)) log({ msg: "license redeemed", key: k.id });
    } else if (!k.hwidHash) {
      // Unbound by an admin: the next PC to activate takes it (only one, if two race).
      await store.moveLicense(k.keyHash, hw, at, at - 1);
    }
    k = (await store.getLicense(k.keyHash))!;
    return { status: 200, body: licenseStatus(k, hw, at) };
  });

  r.post("/v1/license/move", async (ctx) => {
    limit(ctx, "license");
    const req = parse(LicenseRequest, await ctx.json());
    let k = await findKey(req.key);
    const hw = hwidHash(req.hwid);
    const at = now();
    if (k.redeemedAt === null) throw new HttpError(409, "not_redeemed", "This key hasn't been activated yet. Activate it instead.");
    if (k.hwidHash?.equals(hw)) return { status: 200, body: licenseStatus(k, hw, at) };
    if (k.expiresAt !== null && k.expiresAt <= at) throw new HttpError(410, "key_expired", "This key has expired.");
    const moved = await store.moveLicense(k.keyHash, hw, at, at - LICENSE_MOVE_COOLDOWN_MS);
    if (moved) log({ msg: "license moved", key: k.id });
    k = (await store.getLicense(k.keyHash))!;
    return { status: 200, body: licenseStatus(k, hw, at) };
  });

  // --- admin (license keys) -----------------------------------------------------

  const adminTokenHash = config.adminToken ? sha256(`admin:${config.adminToken}`) : null;
  function requireAdmin(ctx: Ctx) {
    // With no ADMIN_TOKEN the admin routes don't exist.
    if (!adminTokenHash) throw new HttpError(404, "not_found", "Not found");
    limit(ctx, "admin");
    const header = ctx.req.headers.authorization;
    const given = header?.startsWith("Bearer ") ? header.slice(7).trim() : "";
    if (!given || !timingSafeEqual(sha256(`admin:${given}`), adminTokenHash)) {
      log({ msg: "admin auth failed", ip: ctx.ip });
      throw new HttpError(401, "unauthenticated", "Wrong admin password.");
    }
  }

  function newKey(): string {
    const bytes = randomBytes(KEY_LENGTH);
    let out = "";
    // 256 is a multiple of 32, so `byte & 31` is uniform.
    for (const b of bytes) out += KEY_ALPHABET[b & 31];
    return out;
  }

  const adminInfo = (k: LicenseKey): AdminKeyInfo => ({
    id: k.id,
    hint: k.hint,
    label: k.label,
    durationDays: k.durationDays,
    createdAt: k.createdAt,
    redeemedAt: k.redeemedAt,
    expiresAt: k.expiresAt,
    bound: Boolean(k.hwidHash),
    lastMoveAt: k.lastMoveAt,
    revoked: k.revoked,
  });

  r.post("/v1/admin/keys", async (ctx) => {
    requireAdmin(ctx);
    const req = parse(AdminCreateKeys, await ctx.json());
    const keys: string[] = [];
    for (let i = 0; i < req.count; i++) {
      const norm = newKey();
      await store.createLicense({
        id: randomUUID(),
        keyHash: keyHash(norm),
        hint: keyHint(norm),
        label: req.label ?? "",
        durationDays: req.days ?? null,
        createdAt: now(),
        redeemedAt: null,
        expiresAt: null,
        hwidHash: null,
        lastMoveAt: null,
        revoked: false,
      });
      keys.push(formatKey(norm));
    }
    log({ msg: "license keys created", count: keys.length, days: req.days ?? null });
    // The only time the full keys exist anywhere: the server keeps just their hashes.
    return { status: 200, body: { keys } };
  });

  r.get("/v1/admin/keys", async (ctx) => {
    requireAdmin(ctx);
    const list = await store.listLicenses(500);
    return { status: 200, body: { keys: list.map(adminInfo) } };
  });

  r.post("/v1/admin/keys/update", async (ctx) => {
    requireAdmin(ctx);
    const req = parse(AdminKeyAction, await ctx.json());
    const patch = req.action === "unbind" ? { unbind: true } : { revoked: req.action === "revoke" };
    if (!(await store.updateLicense(req.id, patch))) throw new HttpError(404, "not_found", "No such key.");
    log({ msg: "license key updated", key: req.id, action: req.action });
    return { status: 200, body: adminInfo((await store.getLicenseById(req.id))!) };
  });

  // --- capes ------------------------------------------------------------------
  //
  // The owner publishes cape images (admin routes). Players pick one in the launcher. BLOOD Client
  // looks up the capes of players around it and draws them; only BLOOD users see these capes.

  const capeInfo = (c: Cape): CapeInfo => ({
    id: c.id,
    name: c.name,
    access: c.access,
    image: `/v1/capes/${c.id}.png`,
    frames: Math.max(1, capeFrames(c.width, c.height)),
    frameMs: c.frameMs,
  });

  /** Reads a PNG's size from its header; null if it isn't a PNG. */
  function pngSize(buf: Buffer): { w: number; h: number } | null {
    const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    if (buf.length < 24 || sig.some((b, i) => buf[i] !== b) || buf.toString("latin1", 12, 16) !== "IHDR") return null;
    return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  }

  r.get("/v1/capes", async () => {
    const capes = (await store.listCapes(false)).map(capeInfo);
    return { status: 200, body: { capes }, headers: { "Cache-Control": "public, max-age=60" } };
  });

  r.get("/v1/capes/:file", async (ctx) => {
    limit(ctx, "capes");
    const m = /^([0-9a-f-]{36})\.png$/.exec(ctx.params.file ?? "");
    const cape = m ? await store.getCape(m[1]!) : null;
    if (!cape || !cape.enabled) throw new HttpError(404, "not_found", "No such cape.");
    // Capes never change in place (a new image is a new cape id), so they can be cached for long.
    return {
      status: 200,
      raw: cape.png,
      headers: {
        "Content-Type": "image/png",
        "Cache-Control": "public, max-age=86400, immutable",
        // BLOOD Client reads the animation speed from here (frames come from the image size).
        "X-Cape-Frame-Ms": String(cape.frameMs),
        "Access-Control-Expose-Headers": "X-Cape-Frame-Ms",
      },
    };
  });

  /** Which cape each player wears: ?uuids=a,b,c (up to 100 Minecraft UUIDs). */
  r.get("/v1/cape-owners", async (ctx) => {
    limit(ctx, "capes");
    const raw = (ctx.query.get("uuids") ?? "").split(",").filter(Boolean);
    if (raw.length > 100) throw new HttpError(400, "too_many", "Ask for at most 100 players at a time.");
    const uuids = [...new Set(raw.map((u) => normalizeUuid(u)).filter((u): u is string => typeof u === "string" && /^[0-9a-f]{32}$/.test(u)))];
    const [capes, tags] = await Promise.all([store.playerCapes(uuids), store.playerTags(uuids)]);
    return { status: 200, body: { capes, tags }, headers: { "Cache-Control": "public, max-age=30" } };
  });

  r.get("/v1/me/cape", async (ctx) => {
    limit(ctx, "general");
    const { user } = await authenticate(ctx, { required: true });
    const capeId = await store.getPlayerCape(user.minecraftUuid);
    const ent = await entitlementFor(user.id, ctx);
    const capes = (await store.listCapes(false)).map((c) => ({ ...capeInfo(c), allowed: c.access === "everyone" || ent.premium }));
    return { status: 200, body: { capeId, capes } };
  });

  r.put("/v1/me/cape", async (ctx) => {
    limit(ctx, "general");
    const { user } = await authenticate(ctx, { required: true });
    const { capeId } = parse(SetCapeRequest, await ctx.json());
    if (!capeId) {
      await store.setPlayerCape(user.minecraftUuid, null, now());
      return { status: 200, body: { capeId: null } };
    }
    const cape = await store.getCape(capeId);
    if (!cape || !cape.enabled) throw new HttpError(404, "not_found", "That cape isn't available.");
    if (cape.access === "premium" && !(await entitlementFor(user.id, ctx)).premium) {
      throw new HttpError(402, "premium_required", "This cape is for BLOOD Premium players.");
    }
    await store.setPlayerCape(user.minecraftUuid, cape.id, now());
    log({ msg: "cape chosen", user: user.id, cape: cape.id });
    return { status: 200, body: { capeId: cape.id } };
  });

  r.post("/v1/admin/capes", async (ctx) => {
    requireAdmin(ctx);
    const req = parse(AdminCreateCape, await ctx.json(6_000_000));
    const png = Buffer.from(req.png.replace(/^data:image\/png;base64,/, ""), "base64");
    if (png.length > CAPE_MAX_BYTES) throw new HttpError(400, "too_large", "The cape image must be at most 4 MB.");
    const size = pngSize(png);
    if (!size) throw new HttpError(400, "not_png", "That file isn't a PNG image.");
    const problem = capeSizeProblem(size.w, size.h);
    if (problem) throw new HttpError(400, "bad_size", problem);
    const cape: Cape = { id: randomUUID(), name: req.name, access: req.access, png, width: size.w, height: size.h, frameMs: req.frameMs ?? 100, enabled: true, createdAt: now() };
    await store.createCape(cape);
    log({ msg: "cape published", cape: cape.id });
    return { status: 200, body: capeInfo(cape) };
  });

  r.get("/v1/admin/capes", async (ctx) => {
    requireAdmin(ctx);
    const capes = (await store.listCapes(true)).map((c) => ({ ...capeInfo(c), enabled: c.enabled, width: c.width, height: c.height, createdAt: c.createdAt }));
    return { status: 200, body: { capes } };
  });

  r.post("/v1/admin/capes/update", async (ctx) => {
    requireAdmin(ctx);
    const req = parse(AdminCapeAction, await ctx.json());
    let ok: boolean;
    if (req.action === "delete") ok = await store.deleteCape(req.id);
    else if (req.action === "enable" || req.action === "disable") ok = await store.updateCape(req.id, { enabled: req.action === "enable" });
    else ok = await store.updateCape(req.id, { access: req.action });
    if (!ok) throw new HttpError(404, "not_found", "No such cape.");
    log({ msg: "cape updated", cape: req.id, action: req.action });
    return { status: 200, body: { ok: true } };
  });

  // --- tags & presence -------------------------------------------------------------

  const teamList = async () =>
    (await store.listTagged()).map((t) => {
      const online = t.lastSeen !== null && now() - t.lastSeen < ONLINE_WINDOW_MS;
      return { username: t.username, text: t.text, color: t.color, online, playing: online && t.playing };
    });

  /** Players with a tag (owner, staff…) and whether they're online. */
  r.get("/v1/team", async (ctx) => {
    limit(ctx, "capes");
    return { status: 200, body: { team: await teamList() }, headers: { "Cache-Control": "public, max-age=20" } };
  });

  /** The launcher checks in about once a minute while it's open. */
  r.post("/v1/me/online", async (ctx) => {
    limit(ctx, "general");
    const { user } = await authenticate(ctx, { required: true });
    const { playing } = parse(PresenceRequest, await ctx.json());
    await store.touchPresence(user.minecraftUuid, playing, now());
    return { status: 200, body: { ok: true } };
  });

  r.get("/v1/admin/tags", async (ctx) => {
    requireAdmin(ctx);
    return { status: 200, body: { team: await teamList() } };
  });

  r.post("/v1/admin/tags", async (ctx) => {
    requireAdmin(ctx);
    const req = parse(AdminSetTag, await ctx.json());
    const user = await store.getUserByUsername(req.username);
    if (!user) throw new HttpError(404, "no_player", `${req.username} hasn't signed in to BLOOD yet. They need to open the launcher and sign in once.`);
    const text = req.text.trim();
    await store.setPlayerTag(user.minecraftUuid, text ? { text, color: req.color.toLowerCase() } : null, now());
    log({ msg: text ? "tag set" : "tag removed", player: user.minecraftUuid });
    return { status: 200, body: { ok: true, username: user.username } };
  });

  // --- pipeline ---------------------------------------------------------------

  const securityHeaders: Record<string, string> = {
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "strict-origin-when-cross-origin",
    "X-Frame-Options": "DENY",
    "Cross-Origin-Opener-Policy": "same-origin",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
  };
  if (cookieSecure) securityHeaders["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains";

  async function handle(req: IncomingMessage, res: ServerResponse) {
    const started = now();
    const requestId = randomUUID();
    const ctx = makeCtx(req, { trustProxy: config.trustProxy, requestId });
    let reply: Reply;
    try {
      if (ctx.path.startsWith("/v1/") || ctx.path === "/healthz") {
        const m = r.match(ctx.method, ctx.path);
        if (m === null) throw new HttpError(404, "not_found", "Not found");
        if (m === "method_not_allowed") throw new HttpError(405, "method_not_allowed", "Method not allowed");
        ctx.params = m.params;
        reply = await m.handler(ctx);
      } else if (config.websiteDir && (ctx.method === "GET" || ctx.method === "HEAD")) {
        reply = await serveStatic(config.websiteDir, ctx.path);
      } else {
        throw new HttpError(404, "not_found", "Not found");
      }
    } catch (e) {
      if (e instanceof HttpError) {
        reply = { status: e.status, body: { error: { code: e.code, message: e.message } }, headers: e.headers };
      } else if (e instanceof StripeError) {
        log({ msg: "stripe error", status: e.status, type: e.type, code: e.code, error: e.message, rid: requestId });
        reply = { status: 502, body: { error: { code: "stripe_error", message: "The payment provider returned an error. Please try again." } } };
      } else {
        log({ msg: "unhandled error", error: (e as Error).stack ?? String(e), rid: requestId });
        reply = { status: 500, body: { error: { code: "internal", message: "Something went wrong on our side." } } };
      }
    }
    const headers: Record<string, string | string[]> = { ...securityHeaders, "X-Request-Id": requestId };
    for (const [k, v] of Object.entries(ctx.responseHeaders)) headers[k] = v;
    if (ctx.path.startsWith("/v1/")) headers["Cache-Control"] ??= "no-store";
    writeReply(res, reply, headers, ctx.method === "HEAD");
    // Never log headers, cookies, tokens or bodies.
    log({ t: new Date(started).toISOString(), rid: requestId, method: ctx.method, path: ctx.path, status: reply.status, ms: now() - started, ip: ctx.ip });
  }

  return {
    handle,
    close() {
      clearInterval(sweeper);
    },
  };
}
