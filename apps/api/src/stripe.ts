/**
 * Minimal client for Stripe's official REST API (https://docs.stripe.com/api)
 * plus webhook signature verification. Using the HTTP API directly keeps the
 * server dependency-light; the API version is pinned with the Stripe-Version
 * header so responses don't change under us.
 */

import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";

export type Fetch = typeof fetch;

export class StripeError extends Error {
  constructor(
    public status: number,
    public type: string,
    message: string,
    public code?: string,
  ) {
    super(message);
  }
}

type Param = string | number | boolean | null | undefined | Param[] | { [k: string]: Param };

/** Encode nested params the way Stripe expects: a[b][0][c]=x. */
export function encodeForm(params: Record<string, Param>): string {
  const out: string[] = [];
  const walk = (prefix: string, value: Param) => {
    if (value === undefined || value === null) return;
    if (Array.isArray(value)) value.forEach((v, i) => walk(`${prefix}[${i}]`, v));
    else if (typeof value === "object") for (const [k, v] of Object.entries(value)) walk(`${prefix}[${k}]`, v);
    else out.push(`${encodeURIComponent(prefix)}=${encodeURIComponent(String(value))}`);
  };
  for (const [k, v] of Object.entries(params)) walk(k, v);
  return out.join("&");
}

export class Stripe {
  constructor(
    private secretKey: string,
    private apiVersion: string,
    private fetchImpl: Fetch = fetch,
    private base = "https://api.stripe.com",
  ) {}

  async request<T>(method: "GET" | "POST" | "DELETE", path: string, params: Record<string, Param> = {}): Promise<T> {
    const body = encodeForm(params);
    const url = method === "GET" && body ? `${this.base}${path}?${body}` : `${this.base}${path}`;
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.secretKey}`,
      "Stripe-Version": this.apiVersion,
      "User-Agent": "bloom-api/0.1",
    };
    if (method === "POST") {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      // Safe retries: Stripe de-duplicates POSTs with the same key.
      headers["Idempotency-Key"] = randomUUID();
    }
    let lastErr: unknown;
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const res = await this.fetchImpl(url, { method, headers, body: method === "POST" ? body : undefined, signal: AbortSignal.timeout(20_000) });
        const text = await res.text();
        let json: unknown;
        try {
          json = text ? JSON.parse(text) : {};
        } catch {
          throw new StripeError(res.status, "api_error", "Stripe returned invalid JSON");
        }
        if (res.ok) return json as T;
        const err = (json as { error?: { type?: string; message?: string; code?: string } }).error ?? {};
        // Retry rate limits and server errors; never client errors.
        if ((res.status === 429 || res.status >= 500) && attempt < 2) {
          lastErr = new StripeError(res.status, err.type ?? "api_error", err.message ?? `HTTP ${res.status}`);
          await new Promise((r) => setTimeout(r, 400 * 2 ** attempt));
          continue;
        }
        throw new StripeError(res.status, err.type ?? "api_error", err.message ?? `Stripe returned HTTP ${res.status}`, err.code);
      } catch (e) {
        if (e instanceof StripeError) throw e;
        lastErr = e;
        if (attempt < 2) await new Promise((r) => setTimeout(r, 400 * 2 ** attempt));
      }
    }
    throw lastErr instanceof Error ? lastErr : new Error("Stripe request failed");
  }
}

// --- Objects (only the fields BLOOD reads) ------------------------------------

export interface StripePrice {
  id: string;
  active: boolean;
  currency: string;
  unit_amount: number | null;
  type: "one_time" | "recurring";
  recurring: { interval: "day" | "week" | "month" | "year" } | null;
}

export interface StripeSubscription {
  id: string;
  object: "subscription";
  customer: string;
  status: string;
  cancel_at_period_end: boolean;
  current_period_end?: number;
  metadata?: Record<string, string>;
  items: { data: { price: { id: string }; current_period_end?: number }[] };
}

export interface StripeCheckoutSession {
  id: string;
  object: "checkout.session";
  mode: "payment" | "subscription" | "setup";
  customer: string | null;
  client_reference_id: string | null;
  payment_status: "paid" | "unpaid" | "no_payment_required";
  status: "open" | "complete" | "expired" | null;
  subscription: string | null;
  payment_intent: string | null;
  metadata?: Record<string, string>;
  url?: string | null;
}

export interface StripeCharge {
  id: string;
  object: "charge";
  payment_intent: string | null;
  refunded: boolean;
}

export interface StripeEvent<T = unknown> {
  id: string;
  type: string;
  created: number;
  data: { object: T };
}

/** Period end across API versions (moved to subscription items in 2025-03-31). */
export function periodEnd(sub: StripeSubscription): number | null {
  const t = sub.current_period_end ?? sub.items?.data?.[0]?.current_period_end;
  return typeof t === "number" ? t * 1000 : null;
}

// --- Webhooks ---------------------------------------------------------------

export class WebhookSignatureError extends Error {}

/**
 * Verify a `Stripe-Signature` header (https://docs.stripe.com/webhooks#verify-manually):
 * HMAC-SHA256 of "<timestamp>.<raw body>" with the endpoint secret, compared in
 * constant time against every v1 signature, within a 5-minute tolerance.
 */
export function verifyWebhook(rawBody: Buffer | string, header: string | undefined, secret: string, toleranceSec = 300, now = Date.now()): StripeEvent {
  if (!header) throw new WebhookSignatureError("Missing Stripe-Signature header");
  let timestamp = "";
  const signatures: string[] = [];
  for (const part of header.split(",")) {
    const [k, v] = part.split("=", 2);
    if (k?.trim() === "t" && v) timestamp = v.trim();
    if (k?.trim() === "v1" && v) signatures.push(v.trim());
  }
  if (!timestamp || signatures.length === 0) throw new WebhookSignatureError("Malformed Stripe-Signature header");
  const t = Number(timestamp);
  if (!Number.isFinite(t) || Math.abs(now / 1000 - t) > toleranceSec) throw new WebhookSignatureError("Webhook timestamp outside the tolerance window");
  const payload = typeof rawBody === "string" ? rawBody : rawBody.toString("utf8");
  const expected = createHmac("sha256", secret).update(`${timestamp}.${payload}`, "utf8").digest();
  const match = signatures.some((sig) => {
    const buf = Buffer.from(sig, "hex");
    return buf.length === expected.length && timingSafeEqual(buf, expected);
  });
  if (!match) throw new WebhookSignatureError("No matching webhook signature");
  try {
    return JSON.parse(payload) as StripeEvent;
  } catch {
    throw new WebhookSignatureError("Webhook body is not JSON");
  }
}

/** Build a valid signature header (for tests and local tooling). */
export function signWebhook(payload: string, secret: string, timestamp = Math.floor(Date.now() / 1000)): string {
  const sig = createHmac("sha256", secret).update(`${timestamp}.${payload}`, "utf8").digest("hex");
  return `t=${timestamp},v1=${sig}`;
}
