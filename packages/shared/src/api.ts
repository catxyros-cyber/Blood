/**
 * The BLOOD API contract (apps/api). All routes are under /v1 and exchange
 * JSON. Authenticated routes take `Authorization: Bearer <token>`.
 *
 * Sign-in works like a Minecraft multiplayer server: the API issues a random
 * serverId, the launcher performs Mojang's standard "join" call with its own
 * session, and the API checks `hasJoined` with Mojang. BLOOD's servers never
 * receive the player's Minecraft or Microsoft tokens.
 */

import { v, type Infer } from "./validate";
import type { PlanId, PlanInfo } from "./plans";

export type SubscriptionStatus =
  | "active"
  | "trialing"
  | "past_due"
  | "canceled"
  | "unpaid"
  | "incomplete"
  | "incomplete_expired"
  | "paused";

export interface Entitlement {
  premium: boolean;
  /** Where Premium comes from, if anywhere. */
  source: "subscription" | "lifetime" | "grant" | "license" | null;
  plan: PlanId | null;
  status: SubscriptionStatus | "lifetime" | null;
  /** Epoch ms. End of the paid period (subscriptions). */
  currentPeriodEnd: number | null;
  cancelAtPeriodEnd: boolean;
  /** When the server computed this. */
  checkedAt: number;
}

export interface PublicUser {
  id: string;
  minecraftUuid: string;
  username: string;
  createdAt: number;
}

// --- /v1/plans --------------------------------------------------------------
export interface PlansResponse {
  plans: PlanInfo[];
  /** False when the server has no Stripe configuration (purchases disabled). */
  billingEnabled: boolean;
}

// --- /v1/auth ---------------------------------------------------------------
export interface ChallengeResponse {
  challengeId: string;
  /** Pass to Mojang's session/minecraft/join as `serverId`. */
  serverId: string;
  expiresAt: number;
}

export const VerifyRequest = v.object({
  challengeId: v.string({ min: 16, max: 64, pattern: /^[A-Za-z0-9_-]+$/ }),
  username: v.string({ min: 1, max: 16, pattern: /^[A-Za-z0-9_]+$/ }),
  /** Free-form device label shown in the account's session list. */
  device: v.optional(v.string({ max: 64 })),
});
export type VerifyRequest = Infer<typeof VerifyRequest>;

export interface SessionResponse {
  token: string;
  expiresAt: number;
  user: PublicUser;
  entitlement: Entitlement;
}

export interface MeResponse {
  user: PublicUser;
  entitlement: Entitlement;
}

export interface WebLinkResponse {
  /** One-time link (valid for 5 minutes) that signs the user in on the website. */
  url: string;
}

export const WebExchangeRequest = v.object({ code: v.string({ min: 16, max: 128, pattern: /^[A-Za-z0-9_-]+$/ }) });

// --- /v1/billing ------------------------------------------------------------
export const CheckoutRequest = v.object({ plan: v.literal<PlanId>("monthly", "yearly", "lifetime") });
export type CheckoutRequest = Infer<typeof CheckoutRequest>;

export interface RedirectResponse {
  url: string;
}

// --- /v1/sync ---------------------------------------------------------------
/** Settings and installation metadata only — never mods, worlds or tokens. */
export interface SyncDocument {
  /** Monotonic server version for optimistic concurrency. */
  version: number;
  updatedAt: number;
  device?: string;
  data: Record<string, unknown>;
}

export const SyncPutRequest = v.object({
  /** Version the client last saw; 0 for the first upload. */
  baseVersion: v.number({ min: 0, int: true }),
  device: v.optional(v.string({ max: 64 })),
  data: v.record(),
});
export type SyncPutRequest = Infer<typeof SyncPutRequest>;

/** Upper bound on a sync document (bytes of JSON). */
export const SYNC_MAX_BYTES = 256 * 1024;

// --- /v1/releases, /v1/news ---------------------------------------------------
export interface ReleaseInfo {
  version: string;
  date: string;
  notes: string;
  /** Windows installer download URL. */
  downloadUrl?: string;
}

export interface Announcement {
  id: string;
  title: string;
  body: string;
  date: string;
  url?: string;
  /** Show only to Premium users (early-access notes). */
  premiumOnly?: boolean;
}

// --- errors -------------------------------------------------------------------
export interface ApiErrorBody {
  error: { code: string; message: string };
}

/** Minecraft UUIDs: Mojang returns them without dashes. */
export function normalizeUuid(id: string): string {
  return id.replace(/-/g, "").toLowerCase();
}
