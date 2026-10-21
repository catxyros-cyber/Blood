import { useEffect, useState } from "react";
import { FEATURES, formatPrice, type PlanId, type PlanInfo } from "@bloom/shared";
import { activeAccount } from "../../services/accounts";
import { app, navigate, reportError, updateSettings } from "../../services/app";
import { loadPlans, openBillingPortal, openWebAccount, refreshPremium, restorePurchases, signOutBloom, startCheckout, stopWaitingForPayment } from "../../services/premium";
import { useStore } from "../../services/store";
import { pullSync, pushSync, resolveConflict } from "../../services/sync";
import { Badge, Button, Callout, Card, cx, Modal, PageHeader, Spinner, timeAgo, Toggle } from "../components";
import { BloodMark, Icon } from "../icons";
import { PremiumBadge } from "../premiumParts";

const PLAN_COPY: Record<PlanId, { title: string; blurb: string }> = {
  monthly: { title: "Monthly", blurb: "Cancel any time." },
  yearly: { title: "Yearly", blurb: "Best value for regular players." },
  lifetime: { title: "Lifetime", blurb: "Pay once, keep Premium." },
};

function priceLabel(p: PlanInfo): string {
  if (!p.available) return "—";
  return formatPrice(p.amount, p.currency);
}

function useBusy() {
  const [busy, setBusy] = useState<string | null>(null);
  const run = (key: string, fn: () => Promise<unknown>) => {
    setBusy(key);
    fn()
      .catch((e) => reportError("Something went wrong", e))
      .finally(() => setBusy(null));
  };
  return { busy, run };
}

export function PremiumPage() {
  const premium = useStore(app, (s) => s.premium);
  const accounts = useStore(app, (s) => s.accounts);
  const cloudSync = useStore(app, (s) => s.settings.cloudSync);
  const { busy, run } = useBusy();
  void accounts;
  const account = activeAccount();
  const ent = premium.entitlement;

  useEffect(() => {
    if (!premium.plans) void loadPlans();
    if (premium.status === "signed_out" && account) void refreshPremium({ quiet: true });
  }, []);

  const plans = premium.plans?.plans ?? [];
  const billing = premium.plans?.billingEnabled ?? false;
  const yearly = plans.find((p) => p.id === "yearly");
  const monthly = plans.find((p) => p.id === "monthly");
  const saving = yearly?.available && monthly?.available && monthly.amount > 0 ? Math.round((1 - yearly.amount / (monthly.amount * 12)) * 100) : 0;

  return (
    <div className="animate-in">
      <PageHeader title={<span className="flex items-center gap-3">BLOOD Premium {ent?.premium && <PremiumBadge label="Active" />}</span>} subtitle="Support BLOOD and unlock its advanced tools. Launching and mod management stay free." />

      {!premium.configured && (
        <div className="mb-6">
          <Callout tone="warn" title="Online services aren't configured in this build">
            Premium needs BLOOD's server. Developers: set VITE_BLOOM_API_URL when building (see docs/STRIPE.md). Everything else in BLOOD works without it.
          </Callout>
        </div>
      )}

      {premium.awaitingPayment && (
        <div className="mb-6">
          <Callout
            tone="accent"
            title="Finish checkout in your browser"
            action={
              <div className="flex gap-2">
                <Button size="sm" onClick={() => run("refresh", () => refreshPremium())}>
                  I've paid — refresh
                </Button>
                <Button size="sm" variant="ghost" onClick={stopWaitingForPayment}>
                  Cancel
                </Button>
              </div>
            }
          >
            <span className="inline-flex items-center gap-2">
              <Spinner size={13} /> BLOOD is waiting for Stripe to confirm your payment. This page updates automatically.
            </span>
          </Callout>
        </div>
      )}

      <div className="grid grid-cols-[1fr_360px] gap-6 items-start">
        <div className="space-y-6 min-w-0">
          {/* Plans */}
          {!ent?.premium && (
            <div className="grid grid-cols-3 gap-3">
              {(["monthly", "yearly", "lifetime"] as PlanId[]).map((id) => {
                const p = plans.find((x) => x.id === id);
                if (p && !p.available && billing) return null;
                const featured = id === "yearly";
                const suffix = id === "monthly" ? "/ month" : id === "yearly" ? "/ year" : "once";
                return (
                  <Card key={id} className={cx("p-5 flex flex-col", featured && "border-accent/45 bg-gradient-to-b from-accent/[0.06] to-panel")}>
                    <div className="flex items-center gap-2">
                      <div className="font-display font-semibold text-[17px]">{PLAN_COPY[id].title}</div>
                      {featured && saving > 0 && <Badge tone="accent">Save {saving}%</Badge>}
                    </div>
                    <div className="mt-3 flex items-baseline gap-1.5">
                      <span className="font-display text-[30px] font-bold tracking-tight">{p ? priceLabel(p) : <Spinner />}</span>
                      {p?.available && <span className="text-muted text-[13px]">{suffix}</span>}
                    </div>
                    <div className="text-[13px] text-muted mt-1 flex-1">{PLAN_COPY[id].blurb}</div>
                    <Button
                      className="mt-4"
                      variant={featured ? "primary" : "secondary"}
                      loading={busy === id}
                      disabled={!billing || !p?.available || !account || premium.status !== "ready"}
                      onClick={() => run(id, () => startCheckout(id))}
                    >
                      {!account ? "Sign in first" : "Upgrade"}
                    </Button>
                  </Card>
                );
              })}
            </div>
          )}
          {!ent?.premium && premium.configured && !billing && premium.plans && <div className="text-[13px] text-muted">Purchases aren't open on this server yet.</div>}

          {/* Features */}
          <Card className="overflow-hidden">
            <div className="grid grid-cols-[1fr_90px_90px] px-5 py-3 text-[12px] uppercase tracking-[0.08em] text-muted font-semibold bg-panel-2 border-b border-line">
              <span>Feature</span>
              <span className="text-center">Free</span>
              <span className="text-center">Premium</span>
            </div>
            {FEATURES.map((f) => (
              <div key={f.key} className="grid grid-cols-[1fr_90px_90px] px-5 py-3 border-b border-line last:border-b-0 items-center">
                <div>
                  <div className="font-medium text-[14px]">{f.title}</div>
                  <div className="text-[12.5px] text-muted">{f.description}</div>
                </div>
                <div className="text-center">{f.free ? <Icon.Check size={17} className="inline text-accent" /> : <span className="text-faint">—</span>}</div>
                <div className="text-center">
                  <Icon.Check size={17} className="inline text-accent" />
                </div>
              </div>
            ))}
          </Card>
          <p className="text-[12px] text-faint leading-relaxed">
            Premium doesn't guarantee a higher frame rate: presets and analytics help you find good settings, but FPS depends on your hardware, settings, world and mods. Payments are processed by Stripe; BLOOD never sees your card details.
          </p>
        </div>

        {/* Status column */}
        <div className="space-y-4">
          <Card className="p-5">
            <div className="flex items-center gap-3">
              <BloodMark size={36} />
              <div className="min-w-0 flex-1">
                <div className="font-semibold">{premium.user ? premium.user.username : account ? account.username : "Not signed in"}</div>
                <div className="text-[12.5px] text-muted">
                  {premium.status === "connecting" ? "Connecting…" : premium.status === "error" ? "Couldn't reach BLOOD's server" : premium.user ? "BLOOD account" : account ? "Not connected yet" : "Sign in with Microsoft first"}
                </div>
              </div>
              {premium.status === "connecting" && <Spinner size={16} />}
            </div>
            {premium.cached && <div className="text-[12px] text-warn mt-3">Offline — showing your last known status.</div>}
            {premium.status === "error" && premium.error && <div className="text-[12px] text-danger mt-3 selectable">{premium.error}</div>}
            {ent && (
              <div className="mt-4 rounded-xl bg-panel-2 border border-line p-3.5 text-[13px] space-y-1">
                <div className="flex justify-between">
                  <span className="text-muted">Plan</span>
                  <span className="font-medium">{ent.premium ? (ent.source === "lifetime" ? "Lifetime" : ent.source === "grant" ? "Complimentary" : ent.plan ? PLAN_COPY[ent.plan].title : "Premium") : "Free"}</span>
                </div>
                {ent.status && ent.source !== "lifetime" && (
                  <div className="flex justify-between">
                    <span className="text-muted">Status</span>
                    <span className={cx("capitalize", ent.status === "past_due" && "text-warn")}>{ent.status.replace("_", " ")}</span>
                  </div>
                )}
                {ent.currentPeriodEnd && ent.source !== "lifetime" && (
                  <div className="flex justify-between">
                    <span className="text-muted">{ent.cancelAtPeriodEnd || !ent.premium ? "Ends" : "Renews"}</span>
                    <span>{new Date(ent.currentPeriodEnd).toLocaleDateString()}</span>
                  </div>
                )}
                {ent.status === "past_due" && <div className="text-[12px] text-warn pt-1">The last payment failed. Update your card in the billing portal to keep Premium.</div>}
              </div>
            )}
            <div className="flex flex-col gap-2 mt-4">
              {!account ? (
                <Button variant="primary" onClick={() => navigate({ page: "accounts" })}>
                  Sign in with Microsoft
                </Button>
              ) : (
                <>
                  {premium.user && (ent?.source === "subscription" || ent?.source === "lifetime" || ent?.status) && (
                    <Button icon={<Icon.Wrench size={15} />} loading={busy === "portal"} onClick={() => run("portal", openBillingPortal)}>
                      {ent?.source === "lifetime" ? "Invoices and billing" : "Manage subscription"}
                    </Button>
                  )}
                  <Button icon={<Icon.Refresh size={15} />} loading={busy === "restore"} disabled={!premium.configured} onClick={() => run("restore", restorePurchases)}>
                    Restore purchases
                  </Button>
                  {premium.user && (
                    <Button variant="ghost" icon={<Icon.Globe size={15} />} loading={busy === "web"} onClick={() => run("web", openWebAccount)}>
                      Manage account on the web
                    </Button>
                  )}
                  {premium.status === "error" && (
                    <Button variant="ghost" onClick={() => run("retry", () => refreshPremium())}>
                      Try again
                    </Button>
                  )}
                </>
              )}
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex items-center gap-2">
              <Icon.Cloud size={18} className="text-accent" />
              <h3 className="font-semibold">Cloud sync</h3>
              <PremiumBadge />
            </div>
            <p className="text-[13px] text-muted mt-1.5 leading-relaxed">Settings and installation profiles follow you to every PC. Mods from Modrinth are re-downloaded there; worlds and your own files stay on this PC.</p>
            {ent?.premium ? (
              <div className="mt-3 space-y-3">
                <Toggle checked={cloudSync} onChange={(v) => updateSettings({ cloudSync: v })} label="Sync automatically" />
                {premium.sync.state === "conflict" ? (
                  <Callout tone="warn" title="Changed on two PCs">
                    <div className="flex gap-2 mt-2">
                      <Button size="sm" onClick={() => run("cloud", () => resolveConflict("cloud"))}>
                        Use cloud version
                      </Button>
                      <Button size="sm" onClick={() => run("local", () => resolveConflict("local"))}>
                        Keep this PC's
                      </Button>
                    </div>
                  </Callout>
                ) : (
                  <div className="flex items-center gap-2">
                    <Button size="sm" loading={premium.sync.state === "syncing" || busy === "sync"} disabled={!cloudSync} icon={<Icon.Sync size={14} />} onClick={() => run("sync", async () => (await pullSync(), await pushSync()))}>
                      Sync now
                    </Button>
                    <span className="text-[12px] text-faint">{premium.sync.lastSyncAt ? `Last synced ${timeAgo(premium.sync.lastSyncAt)}` : "Not synced yet"}</span>
                  </div>
                )}
                {premium.sync.state === "error" && <div className="text-[12px] text-danger">{premium.sync.error}</div>}
              </div>
            ) : (
              <div className="text-[12.5px] text-faint mt-3">Available with Premium.</div>
            )}
          </Card>

          {premium.user && (
            <button type="button" className="text-[12.5px] text-faint hover:text-muted cursor-pointer" onClick={() => run("signout", signOutBloom)}>
              Disconnect BLOOD account on this PC
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export function PremiumWelcome() {
  const welcome = useStore(app, (s) => s.premium.welcome);
  const close = () => app.set((s) => ({ premium: { ...s.premium, welcome: false } }));
  return (
    <Modal
      open={Boolean(welcome)}
      onClose={close}
      title="Welcome to BLOOD Premium"
      width={460}
      footer={
        <Button variant="primary" onClick={close}>
          Let's go
        </Button>
      }
    >
      <div className="text-center py-2">
        <div className="mx-auto h-16 w-16 rounded-2xl bg-amber-300/10 text-amber-200 grid place-items-center">
          <Icon.Crown size={30} />
        </div>
        <p className="text-muted mt-4">Thank you for supporting BLOOD! Competitive and Extreme FPS presets, GC analytics, any accent colour and cloud sync are now unlocked on every PC you sign in to.</p>
      </div>
    </Modal>
  );
}
