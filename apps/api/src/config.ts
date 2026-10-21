/**
 * Server configuration from environment variables (see .env.example).
 * Secrets are read here and nowhere else, and are never logged.
 */

import type { PlanId } from "@bloom/shared";

export type BloomEnv = "development" | "production" | "test";

export interface Config {
  env: BloomEnv;
  port: number;
  host: string;
  /** Public URL of this server, e.g. https://bloomlauncher.example (website + API). */
  publicUrl: string;
  /** Trust X-Forwarded-For / X-Forwarded-Proto from a reverse proxy. */
  trustProxy: boolean;
  databaseUrl: string | null;
  websiteDir: string | null;
  contentDir: string;
  stripe: {
    secretKey: string;
    webhookSecret: string;
    prices: Partial<Record<PlanId, string>>;
    apiVersion: string;
  } | null;
  /** Tauri updater manifest (latest.json) location: an https URL or a file path. */
  releaseManifest: string | null;
  sessionDays: number;
  /** Bearer token for the admin key tools (ADMIN_TOKEN). null = admin routes are off. */
  adminToken: string | null;
}

export class ConfigError extends Error {}

function bool(v: string | undefined): boolean {
  return v === "1" || v === "true" || v === "yes";
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const mode = (env.BLOOM_ENV ?? (env.NODE_ENV === "production" ? "production" : "development")) as BloomEnv;
  if (!["development", "production", "test"].includes(mode)) throw new ConfigError(`BLOOM_ENV must be development, production or test (got "${mode}")`);

  const publicUrl = (env.PUBLIC_URL ?? `http://localhost:${env.PORT ?? 8787}`).replace(/\/+$/, "");
  if (mode === "production" && !publicUrl.startsWith("https://")) {
    throw new ConfigError("PUBLIC_URL must be an https:// URL in production.");
  }

  let stripe: Config["stripe"] = null;
  const secretKey = env.STRIPE_SECRET_KEY?.trim();
  if (secretKey) {
    // Keep development and production Stripe accounts/modes apart.
    const live = secretKey.startsWith("sk_live_") || secretKey.startsWith("rk_live_");
    const test = secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_");
    if (!live && !test) throw new ConfigError("STRIPE_SECRET_KEY doesn't look like a Stripe secret key.");
    if (mode === "production" && test && !bool(env.STRIPE_ALLOW_TEST_KEYS)) {
      throw new ConfigError("Refusing to use a Stripe *test* key in production. Set STRIPE_ALLOW_TEST_KEYS=1 for a staging deployment.");
    }
    if (mode !== "production" && live) {
      throw new ConfigError("Refusing to use a Stripe *live* key outside production. Use your test-mode key for development.");
    }
    const webhookSecret = env.STRIPE_WEBHOOK_SECRET?.trim() ?? "";
    if (!webhookSecret.startsWith("whsec_")) throw new ConfigError("STRIPE_WEBHOOK_SECRET is required when Stripe is configured (it starts with whsec_).");
    const prices: Partial<Record<PlanId, string>> = {};
    for (const [plan, key] of [
      ["monthly", "STRIPE_PRICE_MONTHLY"],
      ["yearly", "STRIPE_PRICE_YEARLY"],
      ["lifetime", "STRIPE_PRICE_LIFETIME"],
    ] as const) {
      const id = env[key]?.trim();
      if (id) {
        if (!id.startsWith("price_")) throw new ConfigError(`${key} must be a Stripe price id (price_…).`);
        prices[plan] = id;
      }
    }
    if (Object.keys(prices).length === 0) throw new ConfigError("Configure at least one of STRIPE_PRICE_MONTHLY, STRIPE_PRICE_YEARLY, STRIPE_PRICE_LIFETIME.");
    stripe = { secretKey, webhookSecret, prices, apiVersion: env.STRIPE_API_VERSION?.trim() || "2024-06-20" };
  }

  const databaseUrl = env.DATABASE_URL?.trim() || null;
  if (mode === "production" && !databaseUrl) throw new ConfigError("DATABASE_URL is required in production.");

  const adminToken = env.ADMIN_TOKEN?.trim() || null;
  if (adminToken && adminToken.length < 24) throw new ConfigError("ADMIN_TOKEN must be at least 24 characters (use a long random password).");

  return {
    env: mode,
    port: Number(env.PORT ?? 8787),
    host: env.HOST ?? (mode === "production" ? "0.0.0.0" : "127.0.0.1"),
    publicUrl,
    trustProxy: bool(env.TRUST_PROXY),
    databaseUrl,
    websiteDir: env.WEBSITE_DIR ?? null,
    contentDir: env.CONTENT_DIR ?? "content",
    stripe,
    releaseManifest: env.RELEASE_MANIFEST?.trim() || null,
    sessionDays: Number(env.SESSION_DAYS ?? 90),
    adminToken,
  };
}
