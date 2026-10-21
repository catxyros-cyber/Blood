/**
 * Premium is derived only from server-side records that Stripe webhooks (or
 * an explicit sync with Stripe) wrote — never from anything the client says.
 */

import type { Entitlement, PlanId } from "@bloom/shared";
import type { Grant, PurchaseRecord, SubscriptionRecord } from "./store/types";

/** Statuses that keep Premium on. `past_due` keeps access while Stripe retries the payment. */
const ACTIVE = new Set(["active", "trialing", "past_due"]);

export function computeEntitlement(
  input: { subscriptions: SubscriptionRecord[]; purchases: PurchaseRecord[]; grant: Grant | null },
  now = Date.now(),
): Entitlement {
  const base: Entitlement = { premium: false, source: null, plan: null, status: null, currentPeriodEnd: null, cancelAtPeriodEnd: false, checkedAt: now };

  const lifetime = input.purchases.find((p) => p.status === "paid" && p.plan === "lifetime");
  if (lifetime) return { ...base, premium: true, source: "lifetime", plan: "lifetime", status: "lifetime" };

  const subs = [...input.subscriptions].sort((a, b) => {
    const aa = ACTIVE.has(a.status) ? 0 : 1;
    const bb = ACTIVE.has(b.status) ? 0 : 1;
    if (aa !== bb) return aa - bb;
    return (b.currentPeriodEnd ?? 0) - (a.currentPeriodEnd ?? 0);
  });
  const best = subs[0];
  if (best && ACTIVE.has(best.status)) {
    return {
      ...base,
      premium: true,
      source: "subscription",
      plan: best.plan,
      status: best.status as Entitlement["status"],
      currentPeriodEnd: best.currentPeriodEnd,
      cancelAtPeriodEnd: best.cancelAtPeriodEnd,
    };
  }

  if (input.grant && (input.grant.expiresAt === null || input.grant.expiresAt > now)) {
    return { ...base, premium: true, source: "grant", currentPeriodEnd: input.grant.expiresAt };
  }

  // Not premium; still report the most recent subscription so the UI can say "expired" / "payment failed".
  if (best) {
    return { ...base, plan: best.plan, status: best.status as Entitlement["status"], currentPeriodEnd: best.currentPeriodEnd, cancelAtPeriodEnd: best.cancelAtPeriodEnd };
  }
  return base;
}

export function planForPrice(prices: Partial<Record<PlanId, string>>, priceId: string | null | undefined): PlanId | null {
  if (!priceId) return null;
  for (const [plan, id] of Object.entries(prices)) if (id === priceId) return plan as PlanId;
  return null;
}
