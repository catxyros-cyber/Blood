/** Fakes for Stripe's REST API and Mojang's session server, plus an app harness. */

import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { createApp } from "../app";
import type { Config } from "../config";
import { MemoryStore } from "../store/memory";
import { signWebhook, Stripe, type StripeSubscription } from "../stripe";

export const WEBHOOK_SECRET = "whsec_test_secret";

export function testConfig(over: Partial<Config> = {}): Config {
  return {
    env: "test",
    port: 0,
    host: "127.0.0.1",
    publicUrl: "http://bloom.test",
    trustProxy: false,
    databaseUrl: null,
    websiteDir: null,
    contentDir: "content",
    stripe: { secretKey: "sk_test_123", webhookSecret: WEBHOOK_SECRET, prices: { monthly: "price_month", yearly: "price_year", lifetime: "price_life" }, apiVersion: "2024-06-20" },
    releaseManifest: null,
    sessionDays: 90,
    ...over,
  };
}

export function parseForm(body: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const pair of body.split("&").filter(Boolean)) {
    const [k, v] = pair.split("=");
    out[decodeURIComponent(k!)] = decodeURIComponent(v ?? "");
  }
  return out;
}

/** In-memory Stripe. Records every call; objects are plain JSON like the real API. */
export class FakeStripe {
  calls: { method: string; path: string; params: Record<string, string>; headers: Record<string, string> }[] = [];
  subscriptions = new Map<string, StripeSubscription>();
  checkoutSessions = new Map<string, Record<string, unknown>>();
  paymentIntents = new Map<string, { latest_charge: { id: string; refunded: boolean } }>();
  customers: { id: string; metadata: Record<string, string> }[] = [];
  seq = 0;

  fetch: typeof fetch = async (input, init) => {
    const url = new URL(String(input));
    const method = init?.method ?? "GET";
    const params = method === "GET" ? parseForm(url.search.slice(1)) : parseForm(String(init?.body ?? ""));
    const headers = (init?.headers ?? {}) as Record<string, string>;
    this.calls.push({ method, path: url.pathname, params, headers });
    const ok = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
    const p = url.pathname;
    if (method === "GET" && p.startsWith("/v1/prices/")) {
      const id = p.split("/").pop()!;
      const table: Record<string, unknown> = {
        price_month: { id, active: true, currency: "usd", unit_amount: 499, type: "recurring", recurring: { interval: "month" } },
        price_year: { id, active: true, currency: "usd", unit_amount: 3999, type: "recurring", recurring: { interval: "year" } },
        price_life: { id, active: true, currency: "usd", unit_amount: 7999, type: "one_time", recurring: null },
      };
      return table[id] ? ok(table[id]) : ok({ error: { type: "invalid_request_error", message: "No such price" } }, 404);
    }
    if (method === "POST" && p === "/v1/customers") {
      const c = { id: `cus_${++this.seq}`, metadata: { bloom_user_id: params["metadata[bloom_user_id]"]! } };
      this.customers.push(c);
      return ok(c);
    }
    if (method === "GET" && p === "/v1/customers/search") {
      const m = /'([^']+)'$/.exec(params.query ?? "");
      return ok({ data: this.customers.filter((c) => c.metadata.bloom_user_id === m?.[1]) });
    }
    if (method === "POST" && p === "/v1/checkout/sessions") {
      const id = `cs_${++this.seq}`;
      const cs = {
        id,
        object: "checkout.session",
        mode: params.mode,
        customer: params.customer,
        client_reference_id: params.client_reference_id,
        metadata: { bloom_user_id: params["metadata[bloom_user_id]"], plan: params["metadata[plan]"] },
        url: `https://checkout.stripe.com/c/pay/${id}`,
        payment_status: "unpaid",
        status: "open",
        subscription: null,
        payment_intent: null,
      };
      this.checkoutSessions.set(id, cs);
      return ok(cs);
    }
    if (method === "GET" && p === "/v1/checkout/sessions") {
      return ok({ data: [...this.checkoutSessions.values()].filter((s) => s.customer === params.customer) });
    }
    if (method === "GET" && p.startsWith("/v1/subscriptions/")) {
      const sub = this.subscriptions.get(p.split("/").pop()!);
      return sub ? ok(sub) : ok({ error: { type: "invalid_request_error", message: "No such subscription" } }, 404);
    }
    if (method === "GET" && p === "/v1/subscriptions") {
      return ok({ data: [...this.subscriptions.values()].filter((s) => s.customer === params.customer) });
    }
    if (method === "GET" && p.startsWith("/v1/payment_intents/")) {
      return ok(this.paymentIntents.get(p.split("/").pop()!) ?? { latest_charge: null });
    }
    if (method === "POST" && p === "/v1/billing_portal/sessions") {
      return ok({ url: `https://billing.stripe.com/p/session/${params.customer}` });
    }
    return ok({ error: { type: "invalid_request_error", message: `Unhandled ${method} ${p}` } }, 400);
  };

  /** Simulate the customer paying: returns a signed checkout.session.completed event. */
  complete(sessionId: string, opts: { subscriptionStatus?: string; periodEnd?: number } = {}) {
    const cs = this.checkoutSessions.get(sessionId)!;
    cs.status = "complete";
    cs.payment_status = "paid";
    if (cs.mode === "subscription") {
      const subId = `sub_${++this.seq}`;
      cs.subscription = subId;
      this.subscriptions.set(subId, {
        id: subId,
        object: "subscription",
        customer: cs.customer as string,
        status: opts.subscriptionStatus ?? "active",
        cancel_at_period_end: false,
        current_period_end: opts.periodEnd ?? Math.floor(Date.now() / 1000) + 30 * 86400,
        metadata: { bloom_user_id: String(cs.client_reference_id) },
        items: { data: [{ price: { id: cs.metadata && (cs.metadata as Record<string, string>).plan === "yearly" ? "price_year" : "price_month" } }] },
      });
    } else {
      cs.payment_intent = `pi_${++this.seq}`;
      this.paymentIntents.set(cs.payment_intent as string, { latest_charge: { id: `ch_${this.seq}`, refunded: false } });
    }
    return this.event("checkout.session.completed", cs);
  }

  event(type: string, object: unknown, id = `evt_${++this.seq}`) {
    const payload = JSON.stringify({ id, type, created: Math.floor(Date.now() / 1000), data: { object } });
    return { payload, signature: signWebhook(payload, WEBHOOK_SECRET) };
  }
}

/** Mojang's session server: `join` registers (username, serverId); `hasJoined` checks it. */
export class FakeMojang {
  joined = new Map<string, { id: string; name: string }>();
  down = false;
  join(serverId: string, profile: { id: string; name: string }) {
    this.joined.set(`${profile.name}:${serverId}`, profile);
  }
  fetch: typeof fetch = async (input) => {
    if (this.down) throw new TypeError("fetch failed");
    const url = new URL(String(input));
    if (url.hostname === "sessionserver.mojang.com" && url.pathname === "/session/minecraft/hasJoined") {
      const p = this.joined.get(`${url.searchParams.get("username")}:${url.searchParams.get("serverId")}`);
      return p ? new Response(JSON.stringify({ id: p.id, name: p.name, properties: [] }), { status: 200 }) : new Response(null, { status: 204 });
    }
    return new Response("not found", { status: 404 });
  };
}

export interface Harness {
  url: string;
  store: MemoryStore;
  stripe: FakeStripe;
  mojang: FakeMojang;
  logs: Record<string, unknown>[];
  close(): Promise<void>;
  /** Sign in as a Minecraft player and return a bearer token. */
  signIn(name?: string, id?: string): Promise<string>;
  call(method: string, path: string, opts?: { token?: string; json?: unknown; headers?: Record<string, string>; body?: string }): Promise<{ status: number; body: any; headers: Headers }>;
}

export async function harness(configOver: Partial<Config> = {}): Promise<Harness> {
  const store = new MemoryStore();
  const fakeStripe = new FakeStripe();
  const mojang = new FakeMojang();
  const config = testConfig(configOver);
  const logs: Record<string, unknown>[] = [];
  const app = createApp({
    config,
    store,
    stripe: config.stripe ? new Stripe(config.stripe.secretKey, config.stripe.apiVersion, fakeStripe.fetch) : null,
    fetch: mojang.fetch,
    log: (e) => logs.push(e),
  });
  const server: Server = createServer((req, res) => void app.handle(req, res));
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", () => r()));
  const url = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  const call: Harness["call"] = async (method, path, opts = {}) => {
    const headers: Record<string, string> = { ...(opts.headers ?? {}) };
    if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
    let body: string | undefined = opts.body;
    if (opts.json !== undefined) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(opts.json);
    }
    const res = await fetch(`${url}${path}`, { method, headers, body, redirect: "manual" });
    const text = await res.text();
    let parsed: unknown = text;
    try {
      parsed = text ? JSON.parse(text) : null;
    } catch {
      /* not JSON */
    }
    return { status: res.status, body: parsed, headers: res.headers };
  };

  return {
    url,
    store,
    stripe: fakeStripe,
    mojang,
    logs,
    call,
    async signIn(name = "Steve", id = "8667ba71b85a4004af54457a9734eed7") {
      const ch = await call("POST", "/v1/auth/challenge");
      mojang.join(ch.body.serverId, { id, name });
      const v = await call("POST", "/v1/auth/verify", { json: { challengeId: ch.body.challengeId, username: name, device: "Test PC" } });
      if (v.status !== 200) throw new Error(`sign-in failed: ${JSON.stringify(v.body)}`);
      return v.body.token as string;
    },
    async close() {
      app.close();
      await new Promise<void>((r) => server.close(() => r()));
    },
  };
}
