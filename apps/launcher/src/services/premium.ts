/**
 * BLOOD account, Premium entitlement and checkout.
 *
 * Signing in to BLOOD's API works like joining a Minecraft server: the API
 * hands out a random serverId, BLOOD calls Mojang's session server "join"
 * with the player's own Minecraft session, and the API confirms it with
 * Mojang. The Minecraft token is sent only to Mojang, never to BLOOD's API.
 *
 * Premium is whatever the server says. The last answer is cached so Premium
 * features keep working briefly while offline; the server re-checks on every
 * request for server-side features (cloud sync).
 */

import type { Entitlement, MeResponse, PlanId, PlansResponse, SessionResponse, ChallengeResponse, Announcement } from "@bloom/shared";
import { BloomError, toBloomError, type HttpRequest } from "../core/platform";
import { activeAccount, launchCredentials } from "./accounts";
import { app, errorMessage, P, path, reportError, toast, type Account, type PremiumState } from "./app";
import { native } from "./native";

export const API_URL: string = ((import.meta as unknown as { env?: Record<string, string> }).env?.VITE_BLOOM_API_URL ?? "").replace(/\/+$/, "");

const tokenKey = (uuid: string) => `bloom-api:${uuid}`;
/** How long a cached Premium answer is trusted without reaching the server. */
const OFFLINE_GRACE_MS = 7 * 86_400_000;

function setPremium(patch: Partial<PremiumState>) {
  app.set((s) => ({ premium: { ...s.premium, ...patch } }));
}

export function isPremium(): boolean {
  return app.get().premium.entitlement?.premium === true;
}

async function apiCall<T>(method: HttpRequest["method"], route: string, opts: { token?: string; json?: unknown } = {}): Promise<{ status: number; body: T }> {
  if (!API_URL) throw new BloomError("api_unconfigured", "BLOOD's online services aren't configured in this build.");
  const headers: Record<string, string> = { Accept: "application/json" };
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  const res = await P().request({ method, url: `${API_URL}${route}`, headers, json: opts.json, timeoutMs: 20_000 });
  let body: unknown = null;
  try {
    body = res.body ? JSON.parse(res.body) : null;
  } catch {
    body = null;
  }
  if (res.status >= 200 && res.status < 300) return { status: res.status, body: body as T };
  const err = (body as { error?: { code?: string; message?: string } } | null)?.error;
  throw new BloomError(err?.code ?? `http_${res.status}`, err?.message ?? `BLOOD's server returned HTTP ${res.status}`);
}

// ---------------------------------------------------------------------------
// Cache (entitlement only — never the token)
// ---------------------------------------------------------------------------

interface CacheFile {
  accountId: string;
  user: MeResponse["user"];
  entitlement: Entitlement;
  savedAt: number;
}

const cachePath = () => path(P().dataDir, "premium.json");

async function writeCache(accountId: string, me: MeResponse) {
  const data: CacheFile = { accountId, user: me.user, entitlement: me.entitlement, savedAt: Date.now() };
  await P().writeText(cachePath(), JSON.stringify(data)).catch(() => undefined);
}

async function readCache(accountId: string): Promise<CacheFile | null> {
  try {
    const raw = await P().readText(cachePath());
    if (!raw) return null;
    const c = JSON.parse(raw) as CacheFile;
    if (c.accountId !== accountId) return null;
    const expired = Date.now() - c.savedAt > OFFLINE_GRACE_MS || (c.entitlement.currentPeriodEnd !== null && c.entitlement.currentPeriodEnd < Date.now());
    if (expired) c.entitlement = { ...c.entitlement, premium: false };
    return c;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Sign-in
// ---------------------------------------------------------------------------

/** Prove ownership of the active Minecraft account to BLOOD's API and get a BLOOD session. */
async function connect(account: Account): Promise<string> {
  const creds = await launchCredentials(account);
  if (creds.offline) throw new BloomError("offline", "You're offline.");
  const { body: ch } = await apiCall<ChallengeResponse>("POST", "/v1/auth/challenge");
  const join = await P().request({
    method: "POST",
    url: "https://sessionserver.mojang.com/session/minecraft/join",
    json: { accessToken: creds.accessToken, selectedProfile: creds.uuid, serverId: ch.serverId },
    timeoutMs: 15_000,
  });
  if (join.status !== 204 && join.status !== 200) {
    throw new BloomError("auth_failed", `Minecraft's session server refused the sign-in (HTTP ${join.status}). Try signing in to your Microsoft account again.`);
  }
  const { body } = await apiCall<SessionResponse>("POST", "/v1/auth/verify", { json: { challengeId: ch.challengeId, username: creds.username, device: "BLOOD for Windows" } });
  await native.secretSet(tokenKey(account.id), body.token);
  return body.token;
}

async function token(account: Account, fresh = false): Promise<string> {
  if (!fresh) {
    const t = await native.secretGet(tokenKey(account.id)).catch(() => null);
    if (t) return t;
  }
  return connect(account);
}

/** Call an authenticated route, reconnecting once if the BLOOD session expired. */
async function authed<T>(method: HttpRequest["method"], route: string, json?: unknown): Promise<T> {
  const account = activeAccount();
  if (!account) throw new BloomError("signed_out", "Sign in with your Microsoft account first.");
  let t = await token(account);
  try {
    return (await apiCall<T>(method, route, { token: t, json })).body;
  } catch (e) {
    const code = toBloomError(e).code;
    if (code !== "unauthenticated" && code !== "session_expired") throw e;
    t = await token(account, true);
    return (await apiCall<T>(method, route, { token: t, json })).body;
  }
}

/** Load (or refresh) the BLOOD account for the active Minecraft account. Safe to call any time. */
export async function refreshPremium(opts: { quiet?: boolean } = {}): Promise<void> {
  if (!API_URL) {
    setPremium({ configured: false, status: "signed_out", entitlement: undefined, user: undefined });
    return;
  }
  const account = activeAccount();
  if (!account) {
    setPremium({ configured: true, status: "signed_out", user: undefined, entitlement: undefined, cached: false });
    return;
  }
  const prevUser = app.get().premium.user;
  setPremium({ configured: true, status: "connecting", error: undefined, ...(prevUser && prevUser.minecraftUuid !== account.id ? { user: undefined, entitlement: undefined } : {}) });
  try {
    const me = await authed<MeResponse>("GET", "/v1/me");
    const was = app.get().premium.entitlement?.premium;
    setPremium({ status: "ready", user: me.user, entitlement: me.entitlement, cached: false });
    await writeCache(account.id, me);
    if (app.get().premium.awaitingPayment && me.entitlement.premium && !was) {
      setPremium({ awaitingPayment: false, welcome: true });
    }
  } catch (e) {
    const err = toBloomError(e);
    const cached = await readCache(account.id);
    if (cached) {
      setPremium({ status: "ready", user: cached.user, entitlement: cached.entitlement, cached: true, error: errorMessage(err) });
    } else {
      setPremium({ status: "error", error: errorMessage(err) });
    }
    if (!opts.quiet && !["offline", "timeout", "network"].includes(err.code)) reportError("Couldn't reach BLOOD's servers", err);
  }
}

export async function loadPlans(): Promise<PlansResponse | null> {
  if (!API_URL) return null;
  try {
    const { body } = await apiCall<PlansResponse>("GET", "/v1/plans");
    setPremium({ plans: body });
    return body;
  } catch {
    return null;
  }
}

export async function loadAnnouncements(): Promise<void> {
  if (!API_URL) return;
  try {
    const account = activeAccount();
    const t = account ? await native.secretGet(tokenKey(account.id)).catch(() => null) : null;
    const { body } = await apiCall<{ announcements: Announcement[] }>("GET", "/v1/news", { token: t ?? undefined });
    app.set({ announcements: body.announcements });
  } catch {
    /* optional */
  }
}

// ---------------------------------------------------------------------------
// Billing
// ---------------------------------------------------------------------------

let pollTimer: ReturnType<typeof setInterval> | null = null;

/** Open Stripe Checkout in the browser, then watch for the purchase to land. */
export async function startCheckout(plan: PlanId): Promise<void> {
  const { url } = await authed<{ url: string }>("POST", "/v1/billing/checkout", { plan });
  await native.openUrl(url);
  setPremium({ awaitingPayment: true });
  if (pollTimer) clearInterval(pollTimer);
  const started = Date.now();
  pollTimer = setInterval(() => {
    const s = app.get().premium;
    if (!s.awaitingPayment || Date.now() - started > 20 * 60_000) {
      if (pollTimer) clearInterval(pollTimer);
      pollTimer = null;
      return;
    }
    void refreshPremium({ quiet: true });
  }, 5000);
}

export function stopWaitingForPayment() {
  setPremium({ awaitingPayment: false });
}

export async function openBillingPortal(): Promise<void> {
  const { url } = await authed<{ url: string }>("POST", "/v1/billing/portal");
  await native.openUrl(url);
}

/** Ask the server to re-read Stripe ("Restore purchases"). */
export async function restorePurchases(): Promise<void> {
  const me = await authed<MeResponse>("POST", "/v1/billing/refresh");
  setPremium({ status: "ready", user: me.user, entitlement: me.entitlement, cached: false });
  const account = activeAccount();
  if (account) await writeCache(account.id, me);
  toast(me.entitlement.premium ? { kind: "success", title: "Premium is active" } : { kind: "info", title: "No purchases found", message: "This Minecraft account has no BLOOD Premium purchase." });
}

/** Open the website's account page, already signed in (one-time link). */
export async function openWebAccount(): Promise<void> {
  const { url } = await authed<{ url: string }>("POST", "/v1/auth/web-link");
  await native.openUrl(url);
}

/** Forget the BLOOD session on this PC (the Microsoft account stays signed in). */
export async function signOutBloom(): Promise<void> {
  const account = activeAccount();
  if (!account) return;
  const t = await native.secretGet(tokenKey(account.id)).catch(() => null);
  if (t) await apiCall("POST", "/v1/auth/logout", { token: t }).catch(() => undefined);
  await native.secretDelete(tokenKey(account.id)).catch(() => undefined);
  await P().writeText(cachePath(), "{}").catch(() => undefined);
  setPremium({ status: "signed_out", user: undefined, entitlement: undefined, cached: false });
}

export async function forgetAccountToken(accountId: string) {
  await native.secretDelete(tokenKey(accountId)).catch(() => undefined);
}

export { authed as premiumRequest };
