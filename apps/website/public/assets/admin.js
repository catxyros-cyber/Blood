// BLOOD key admin page. Talks to /v1/admin/* on this server with the ADMIN_TOKEN as a bearer
// token. The token is kept for this browser tab only (sessionStorage).
(() => {
  const $ = (sel) => document.querySelector(sel);
  const STORE = "blood.adminToken";
  let token = "";
  try { token = sessionStorage.getItem(STORE) || ""; } catch { /* storage blocked */ }

  const fmtDate = (ms) => (ms ? new Date(ms).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : "—");
  const msg = (el, text, bad = false) => { el.textContent = text; el.classList.toggle("bad", bad); };

  async function api(method, path, body) {
    const res = await fetch(path, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const err = new Error(data?.error?.message || `Request failed (${res.status})`);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  function show(unlocked) {
    $("[data-login]").classList.toggle("hidden", unlocked);
    $("[data-panel]").classList.toggle("hidden", !unlocked);
  }

  function lock(text) {
    token = "";
    try { sessionStorage.removeItem(STORE); } catch { /* ignore */ }
    show(false);
    if (text) msg($("[data-login-msg]"), text, true);
  }

  function statusOf(k) {
    if (k.revoked) return ["Disabled", "bad"];
    if (k.expiresAt && k.expiresAt <= Date.now()) return ["Expired", "bad"];
    if (!k.redeemedAt) return ["Not used yet", ""];
    if (!k.bound) return ["Activated · PC reset", "on"];
    return ["Active on a PC", "on"];
  }

  function button(label, action, id, danger = false) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = danger ? "btn btn-danger" : "btn";
    b.textContent = label;
    b.addEventListener("click", async () => {
      b.disabled = true;
      try {
        await api("POST", "/v1/admin/keys/update", { id, action });
        await load();
      } catch (e) {
        msg($("[data-list-msg]"), e.message, true);
        b.disabled = false;
      }
    });
    return b;
  }

  async function load() {
    const listMsg = $("[data-list-msg]");
    msg(listMsg, "Loading…");
    try {
      const { keys } = await api("GET", "/v1/admin/keys");
      const rows = $("[data-rows]");
      rows.replaceChildren();
      for (const k of keys) {
        const tr = document.createElement("tr");
        const [status, cls] = statusOf(k);
        const cells = [`…${k.hint}`, k.label || "—", k.durationDays ? `${k.durationDays} days` : "Lifetime", fmtDate(k.createdAt), fmtDate(k.redeemedAt), k.redeemedAt ? (k.expiresAt ? fmtDate(k.expiresAt) : "Never") : "—"];
        for (const c of cells) {
          const td = document.createElement("td");
          td.textContent = c;
          tr.append(td);
        }
        const st = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = `badge ${cls}`;
        badge.textContent = status;
        st.append(badge);
        tr.append(st);
        const td = document.createElement("td");
        const actions = document.createElement("div");
        actions.className = "admin-actions";
        if (k.bound && !k.revoked) actions.append(button("Reset PC", "unbind", k.id));
        actions.append(k.revoked ? button("Enable", "restore", k.id) : button("Disable", "revoke", k.id, true));
        td.append(actions);
        tr.append(td);
        rows.append(tr);
      }
      msg(listMsg, keys.length ? `${keys.length} key${keys.length === 1 ? "" : "s"}.` : "No keys yet.");
    } catch (e) {
      if (e.status === 401 || e.status === 404) lock(e.status === 404 ? "The admin page is off. Set ADMIN_TOKEN on the server." : "Wrong admin password.");
      else msg(listMsg, e.message, true);
    }
  }

  // ---------------------------------------------------------------- capes

  async function loadCapes() {
    const box = $("[data-capes]");
    try {
      const { capes } = await api("GET", "/v1/admin/capes");
      box.replaceChildren();
      if (!capes.length) {
        box.textContent = "No capes yet.";
        return;
      }
      for (const c of capes) {
        const card = document.createElement("div");
        card.className = "admin-cape" + (c.enabled ? "" : " off");
        const img = document.createElement("img");
        img.src = c.image;
        img.alt = "";
        img.width = 128;
        img.height = 64;
        const name = document.createElement("div");
        name.className = "admin-cape-name";
        name.textContent = c.name;
        const meta = document.createElement("div");
        meta.className = "muted";
        meta.textContent = `${c.width}×${c.height} · ${c.access === "premium" ? "Premium only" : "Everyone"}${c.enabled ? "" : " · hidden"}`;
        const actions = document.createElement("div");
        actions.className = "admin-actions";
        const act = (label, action, danger) => {
          const b = document.createElement("button");
          b.type = "button";
          b.className = danger ? "btn btn-danger" : "btn";
          b.textContent = label;
          b.addEventListener("click", async () => {
            if (action === "delete" && !confirm(`Delete "${c.name}"? Players wearing it lose it.`)) return;
            b.disabled = true;
            try {
              await api("POST", "/v1/admin/capes/update", { id: c.id, action });
              await loadCapes();
            } catch (e) {
              msg($("[data-cape-msg]"), e.message, true);
              b.disabled = false;
            }
          });
          return b;
        };
        actions.append(act(c.enabled ? "Hide" : "Show", c.enabled ? "disable" : "enable"));
        actions.append(act(c.access === "premium" ? "Make free" : "Premium only", c.access === "premium" ? "everyone" : "premium"));
        actions.append(act("Delete", "delete", true));
        card.append(img, name, meta, actions);
        box.append(card);
      }
    } catch (e) {
      msg($("[data-cape-msg]"), e.message, true);
    }
  }

  $("[data-cape-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const f = new FormData(ev.target);
    const file = f.get("file");
    const capeMsg = $("[data-cape-msg]");
    if (!file || !file.size) return msg(capeMsg, "Choose a PNG file.", true);
    if (file.size > 512 * 1024) return msg(capeMsg, "The image must be at most 512 KB.", true);
    const data = await new Promise((res, rej) => {
      const r = new FileReader();
      r.onload = () => res(String(r.result));
      r.onerror = () => rej(new Error("Couldn't read the file."));
      r.readAsDataURL(file);
    });
    const submit = ev.target.querySelector("button[type=submit]");
    submit.disabled = true;
    msg(capeMsg, "Publishing…");
    try {
      await api("POST", "/v1/admin/capes", { name: String(f.get("name")).trim(), access: f.get("access"), png: data });
      msg(capeMsg, "Published. Players can pick it in the BLOOD launcher.");
      ev.target.reset();
      await loadCapes();
    } catch (e) {
      msg(capeMsg, e.message, true);
    } finally {
      submit.disabled = false;
    }
  });

  $("[data-login-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    token = new FormData(ev.target).get("token").trim();
    try { sessionStorage.setItem(STORE, token); } catch { /* ignore */ }
    msg($("[data-login-msg]"), "");
    show(true);
    await load();
    await loadCapes();
  });

  $("[data-create-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const f = new FormData(ev.target);
    const body = { count: Number(f.get("count")) };
    if (f.get("days")) body.days = Number(f.get("days"));
    const label = String(f.get("label") || "").trim();
    if (label) body.label = label;
    const createMsg = $("[data-create-msg]");
    const submit = ev.target.querySelector("button[type=submit]");
    submit.disabled = true;
    msg(createMsg, "Creating…");
    try {
      const { keys } = await api("POST", "/v1/admin/keys", body);
      $("[data-new-keys]").value = keys.join("\n");
      $("[data-new]").classList.remove("hidden");
      msg(createMsg, `Created ${keys.length} key${keys.length === 1 ? "" : "s"}.`);
      await load();
    } catch (e) {
      msg(createMsg, e.message, true);
    } finally {
      submit.disabled = false;
    }
  });

  $("[data-copy]").addEventListener("click", async () => {
    const text = $("[data-new-keys]").value;
    try {
      await navigator.clipboard.writeText(text);
      msg($("[data-create-msg]"), "Copied.");
    } catch {
      $("[data-new-keys]").select();
    }
  });

  $("[data-reload]").addEventListener("click", () => void load());
  $("[data-logout]").addEventListener("click", () => lock(""));

  if (token) {
    show(true);
    void load();
    void loadCapes();
  }
})();
