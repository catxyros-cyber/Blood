// BLOOD website behaviour. Talks only to this server's /v1 API (same origin, cookie session).
"use strict";

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    credentials: "same-origin",
    headers: body !== undefined ? { "Content-Type": "application/json" } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try {
    data = res.status === 204 ? null : await res.json();
  } catch {
    data = null;
  }
  if (!res.ok) {
    const err = new Error((data && data.error && data.error.message) || `Request failed (${res.status})`);
    err.status = res.status;
    err.code = data && data.error && data.error.code;
    throw err;
  }
  return data;
}

function formatPrice(amount, currency) {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency: currency.toUpperCase() }).format(amount / 100);
  } catch {
    return `${(amount / 100).toFixed(2)} ${currency.toUpperCase()}`;
  }
}

function text(el, value) {
  if (el) el.textContent = value;
}

// --- Pricing ------------------------------------------------------------------
async function initPricing() {
  const root = $("[data-plans]");
  if (!root) return;
  try {
    const { plans, billingEnabled } = await api("GET", "/v1/plans");
    for (const plan of plans) {
      const card = $(`[data-plan="${plan.id}"]`, root);
      if (!card) continue;
      if (!plan.available) {
        // Hide plans the server doesn't sell; while billing is off, show them as coming soon.
        if (billingEnabled) card.classList.add("hidden");
        else text($(".price", card), "Soon");
        continue;
      }
      const suffix = plan.interval === "month" ? "/month" : plan.interval === "year" ? "/year" : " once";
      $(".price", card).innerHTML = "";
      $(".price", card).append(formatPrice(plan.amount, plan.currency));
      const small = document.createElement("small");
      small.textContent = ` ${suffix}`;
      $(".price", card).append(small);
    }
    if (!billingEnabled) text($("[data-billing-note]"), "Purchases aren't open yet on this server. Check back soon.");
  } catch {
    text($("[data-billing-note]"), "Prices couldn't be loaded right now. They're always shown in the launcher before you pay.");
  }
}

// --- Account --------------------------------------------------------------------
const PLAN_NAMES = { monthly: "Premium Monthly", yearly: "Premium Yearly", lifetime: "Premium Lifetime" };

function describe(ent) {
  if (ent.premium) {
    if (ent.source === "lifetime") return { badge: "Premium · Lifetime", on: true, detail: "You own BLOOD Premium for life. Thank you!" };
    if (ent.source === "grant") return { badge: "Premium", on: true, detail: ent.currentPeriodEnd ? `Complimentary Premium until ${new Date(ent.currentPeriodEnd).toLocaleDateString()}.` : "Complimentary Premium." };
    const end = ent.currentPeriodEnd ? new Date(ent.currentPeriodEnd).toLocaleDateString() : "";
    if (ent.status === "past_due") return { badge: "Premium · payment due", on: false, bad: true, detail: "Your last payment failed. Update your card in the billing portal to keep Premium." };
    return {
      badge: PLAN_NAMES[ent.plan] || "Premium",
      on: true,
      detail: ent.cancelAtPeriodEnd ? `Cancelled — Premium stays active until ${end}.` : `Renews on ${end}.`,
    };
  }
  if (ent.status === "canceled") return { badge: "Free", on: false, detail: "Your subscription has ended. You can resubscribe any time." };
  if (ent.status === "unpaid" || ent.status === "incomplete") return { badge: "Free", on: false, bad: true, detail: "The last payment didn't go through." };
  return { badge: "Free", on: false, detail: "You're on BLOOD Free. Everything you need to play is included." };
}

async function initAccount() {
  const root = $("[data-account]");
  if (!root) return;
  const status = $("[data-status]", root);
  const signedOut = $("[data-signed-out]", root);
  const signedIn = $("[data-signed-in]", root);

  const show = (el) => {
    for (const x of [signedOut, signedIn, $("[data-loading]", root)]) x && x.classList.add("hidden");
    el && el.classList.remove("hidden");
  };

  // A one-time code from the launcher arrives in the URL fragment (never sent to the server in logs).
  const m = /code=([A-Za-z0-9_-]+)/.exec(location.hash);
  if (m) {
    history.replaceState(null, "", location.pathname);
    try {
      await api("POST", "/v1/auth/web-exchange", { code: m[1] });
    } catch (e) {
      text(status, e.message);
    }
  }

  let me;
  try {
    me = await api("GET", "/v1/me");
  } catch (e) {
    if (e.status === 401) return show(signedOut);
    text(status, `Couldn't load your account: ${e.message}`);
    return show(signedOut);
  }
  render(me);
  show(signedIn);

  function render(data) {
    text($("[data-username]", root), data.user.username);
    $("[data-avatar]", root).src = `https://mc-heads.net/avatar/${encodeURIComponent(data.user.minecraftUuid)}/64`;
    text($("[data-since]", root), new Date(data.user.createdAt).toLocaleDateString());
    const d = describe(data.entitlement);
    const badge = $("[data-badge]", root);
    badge.textContent = d.badge;
    badge.className = `badge${d.on ? " on" : ""}${d.bad ? " bad" : ""}`;
    text($("[data-detail]", root), d.detail);
    $("[data-upgrade]", root).classList.toggle("hidden", data.entitlement.premium);
  }

  async function run(button, fn) {
    button.disabled = true;
    text(status, "");
    try {
      await fn();
    } catch (e) {
      status.classList.add("error");
      text(status, e.message);
    } finally {
      button.disabled = false;
    }
  }

  $("[data-portal]", root).addEventListener("click", (e) =>
    run(e.currentTarget, async () => {
      const { url } = await api("POST", "/v1/billing/portal");
      location.href = url;
    }),
  );
  $("[data-refresh]", root).addEventListener("click", (e) =>
    run(e.currentTarget, async () => {
      const fresh = await api("POST", "/v1/billing/refresh");
      render(fresh);
      status.classList.remove("error");
      text(status, fresh.entitlement.premium ? "Premium is active." : "No purchases were found for this account.");
    }),
  );
  for (const b of $$("[data-checkout]", root)) {
    b.addEventListener("click", (e) =>
      run(e.currentTarget, async () => {
        const { url } = await api("POST", "/v1/billing/checkout", { plan: b.dataset.checkout });
        location.href = url;
      }),
    );
  }
  $("[data-logout]", root).addEventListener("click", (e) =>
    run(e.currentTarget, async () => {
      await api("POST", "/v1/auth/logout");
      show(signedOut);
    }),
  );
  $("[data-delete]", root).addEventListener("click", (e) =>
    run(e.currentTarget, async () => {
      if (!confirm("Delete your BLOOD account? Synced settings are removed permanently. Your Minecraft account is not affected.")) return;
      await api("DELETE", "/v1/me");
      show(signedOut);
      text(status, "Your BLOOD account was deleted.");
    }),
  );
}

// --- Checkout success: wait for the webhook to land -------------------------------
async function initSuccess() {
  const el = $("[data-success-status]");
  if (!el) return;
  for (let i = 0; i < 20; i++) {
    try {
      const me = await api("GET", "/v1/me");
      if (me.entitlement.premium) {
        text(el, `Premium is active for ${me.user.username}. BLOOD picks it up automatically — or press “Refresh” on the Premium page.`);
        return;
      }
    } catch (e) {
      if (e.status === 401) {
        text(el, "Return to BLOOD — your Premium status refreshes automatically within a few seconds.");
        return;
      }
    }
    await new Promise((r) => setTimeout(r, 3000));
  }
  text(el, "Payment received. It can take a minute to activate; use “Restore purchases” in BLOOD if it doesn't appear.");
}

// --- Releases -----------------------------------------------------------------------
async function initReleases() {
  const root = $("[data-releases]");
  if (!root) return;
  try {
    const { releases } = await api("GET", "/v1/releases");
    root.innerHTML = "";
    if (!releases.length) {
      root.textContent = "No releases yet.";
      return;
    }
    for (const r of releases) {
      const item = document.createElement("article");
      item.className = "release";
      const h = document.createElement("h2");
      h.textContent = `BLOOD ${r.version}`;
      const date = document.createElement("div");
      date.className = "faint small";
      date.textContent = new Date(r.date).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
      const notes = document.createElement("pre");
      notes.textContent = r.notes;
      item.append(h, date, notes);
      root.append(item);
    }
  } catch {
    root.textContent = "Release notes couldn't be loaded right now.";
  }
}

document.addEventListener("DOMContentLoaded", () => {
  const here = location.pathname.replace(/\.html$/, "").replace(/\/$/, "") || "/index";
  for (const a of $$(".nav a")) {
    const target = new URL(a.href).pathname.replace(/\.html$/, "").replace(/\/$/, "") || "/index";
    if (target === here) a.setAttribute("aria-current", "page");
  }
  const year = $("[data-year]");
  if (year) year.textContent = String(new Date().getFullYear());
  void initPricing();
  void initAccount();
  void initSuccess();
  void initReleases();
});
