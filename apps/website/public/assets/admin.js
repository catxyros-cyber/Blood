// MOTION key admin page. Talks to /v1/admin/* on this server with the ADMIN_TOKEN as a bearer
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
        if (c.width > 64) img.className = "hd";
        if (c.frames > 1) {
          // Show the first frame of the strip.
          img.style.objectFit = "cover";
          img.style.objectPosition = "top";
        }
        const name = document.createElement("div");
        name.className = "admin-cape-name";
        name.textContent = c.name;
        const meta = document.createElement("div");
        meta.className = "muted";
        const anim = c.frames > 1 ? ` · animated, ${c.frames} frames` : "";
        meta.textContent = `${c.width}×${c.width / 2}${anim} · ${c.access === "premium" ? "Premium only" : "Everyone"}${c.enabled ? "" : " · hidden"}`;
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

  // ---------------------------------------------------------------- cape images

  const CAPE_WIDTHS = [64, 128, 256, 512, 1024, 2048];
  const MAX_FRAMES = 64;

  /**
   * Turns any image into a cape PNG. Animated GIF/WebP/APNG frames (decoded by the browser) become
   * a vertical strip, one frame under the other. Images in the cape layout but at an odd size are
   * scaled (pixel-sharp) to the nearest valid width.
   */
  async function capeFromFile(file) {
    let frames = [];
    let frameMs = 100;
    if (typeof ImageDecoder === "function" && /gif|webp|apng|png/.test(file.type)) {
      try {
        const dec = new ImageDecoder({ data: await file.arrayBuffer(), type: file.type });
        await dec.tracks.ready;
        const count = dec.tracks.selectedTrack ? dec.tracks.selectedTrack.frameCount : 1;
        if (count > 1) {
          const step = Math.max(1, Math.ceil(count / MAX_FRAMES));
          let total = 0;
          for (let i = 0; i < count; i += step) {
            const { image } = await dec.decode({ frameIndex: i });
            const bmp = await createImageBitmap(image);
            total += (image.duration || 100000) / 1000;
            image.close();
            frames.push(bmp);
          }
          frameMs = Math.max(20, Math.round((total / frames.length) * step));
        }
        dec.close();
      } catch {
        frames = [];
      }
    }
    if (!frames.length) {
      if (file.type === "image/gif" && typeof ImageDecoder !== "function") {
        throw new Error("This browser can't read animated GIFs. Use Chrome or Edge, or upload a PNG strip.");
      }
      frames = [await createImageBitmap(file)];
    }
    let fw = frames[0].width, fh = frames[0].height;
    // One tall image with frames stacked vertically (a "strip").
    let stripFrames = 1;
    if (frames.length === 1 && fh > fw / 2 && Number.isInteger(fw / 2) && fh % (fw / 2) === 0) {
      stripFrames = fh / (fw / 2);
      fh = fw / 2;
    }
    const n = frames.length > 1 ? frames.length : stripFrames;
    if (n > MAX_FRAMES) throw new Error(`An animated cape can have at most ${MAX_FRAMES} frames. This one has ${n}.`);
    // Not a cape layout (2:1)? Treat it as artwork and wrap it onto an HD cape.
    const artwork = Math.abs(fw / fh - 2) > 0.02;
    const width = artwork ? artworkWidth(fw, fh, n) : CAPE_WIDTHS.find((w) => w >= Math.min(fw, 2048)) ?? 2048;
    const height = width / 2;
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height * n;
    const g = canvas.getContext("2d");
    // Pixel art (64×32, or whole-number upscales of it) stays crisp; HD art is resampled smoothly.
    const pixelArt = !artwork && (fw <= 64 || (width % fw === 0 && fw % 64 === 0));
    g.imageSmoothingEnabled = !pixelArt;
    g.imageSmoothingQuality = "high";
    for (let i = 0; i < n; i++) {
      const src = frames.length > 1 ? frames[i] : frames[0];
      const sy = frames.length > 1 ? 0 : i * fh;
      if (artwork) wrapArtwork(g, src, 0, sy, fw, fh, i * height, width);
      else g.drawImage(src, 0, sy, fw, fh, 0, i * height, width, height);
    }
    const dataUrl = canvas.toDataURL("image/png");
    return { dataUrl, frames: n, frameMs };
  }


  /** Largest cape width that keeps an artwork animation under the server's size limits. */
  function artworkWidth(fw, fh, n) {
    // Roughly one byte per pixel after PNG compression; the server takes up to 4 MB.
    for (const w of [2048, 1024, 512, 256, 128]) if (w * (w / 2) * n <= (n === 1 ? 2_100_000 : 3_400_000) && w <= Math.max(128, fw * 2)) return w;
    return 128;
  }

  /**
   * Draws any picture onto one cape frame (Minecraft's 64×32 layout, scaled to `width`):
   * the artwork covers the outside of the cape and the elytra, the inside gets a darker copy,
   * and the thin edges take the artwork's colours so no gaps show in game.
   */
  function wrapArtwork(g, src, sx, sy, sw, sh, top, width) {
    const u = width / 64;
    const cover = (dx, dy, dw, dh) => {
      const s = Math.max(dw / sw, dh / sh);
      const cw = dw / s, ch = dh / s;
      g.drawImage(src, sx + (sw - cw) / 2, sy + (sh - ch) / 2, cw, ch, dx, top + dy, dw, dh);
    };
    g.save();
    g.clearRect(0, top, width, width / 2);
    // Outside (seen by other players), the edges and top/bottom strips.
    cover(1 * u, 1 * u, 10 * u, 16 * u);
    cover(0, 1 * u, 1 * u, 16 * u);
    cover(11 * u, 1 * u, 1 * u, 16 * u);
    cover(1 * u, 0, 10 * u, 1 * u);
    cover(11 * u, 0, 10 * u, 1 * u);
    // Inside (against the player's back): the same art, darkened.
    cover(12 * u, 1 * u, 10 * u, 16 * u);
    g.fillStyle = "rgba(0,0,0,0.55)";
    g.fillRect(12 * u, top + 1 * u, 10 * u, 16 * u);
    // Elytra wings.
    cover(22 * u, 0, 24 * u, 22 * u);
    g.restore();
  }

  $("[data-cape-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const f = new FormData(ev.target);
    const file = f.get("file");
    const capeMsg = $("[data-cape-msg]");
    if (!file || !file.size) return msg(capeMsg, "Choose an image.", true);
    if (file.size > 20 * 1024 * 1024) return msg(capeMsg, "The file is too big (max 20 MB before conversion).", true);
    let cape;
    try {
      msg(capeMsg, "Preparing the image…");
      cape = await capeFromFile(file);
    } catch (e) {
      return msg(capeMsg, e.message, true);
    }
    const typedMs = Number(f.get("frameMs"));
    const frameMs = typedMs >= 20 ? Math.round(typedMs) : cape.frameMs;
    const data = cape.dataUrl;
    if (data.length * 0.75 > 4 * 1024 * 1024) return msg(capeMsg, "That cape is over 4 MB after conversion. Use fewer frames or a smaller size.", true);
    const submit = ev.target.querySelector("button[type=submit]");
    submit.disabled = true;
    msg(capeMsg, "Publishing…");
    try {
      await api("POST", "/v1/admin/capes", { name: String(f.get("name")).trim(), access: f.get("access"), png: data, ...(cape.frames > 1 ? { frameMs } : {}) });
      msg(capeMsg, cape.frames > 1 ? `Published an animated cape (${cape.frames} frames). Players can pick it in the MOTION launcher.` : "Published. Players can pick it in the MOTION launcher.");
      ev.target.reset();
      await loadCapes();
    } catch (e) {
      msg(capeMsg, e.message, true);
    } finally {
      submit.disabled = false;
    }
  });

  // ---------------------------------------------------------------- tags

  async function loadTags() {
    const box = $("[data-tags]");
    try {
      const { team } = await api("GET", "/v1/admin/tags");
      box.replaceChildren();
      if (!team.length) {
        box.textContent = "No tags yet.";
        return;
      }
      for (const t of team) {
        const row = document.createElement("div");
        row.className = "admin-tag";
        const tag = document.createElement("span");
        tag.className = "admin-tag-pill";
        tag.style.color = t.color;
        tag.textContent = `[${t.text}]`;
        const name = document.createElement("span");
        name.textContent = ` ${t.username} `;
        const state = document.createElement("span");
        state.className = "muted";
        state.textContent = t.playing ? "● playing" : t.online ? "● online" : "offline";
        if (t.online) state.style.color = "#3ecf6e";
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "btn";
        edit.textContent = "Edit";
        edit.addEventListener("click", () => {
          const form = $("[data-tag-form]");
          form.username.value = t.username;
          form.text.value = t.text;
          form.color.value = t.color;
          form.text.focus();
        });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-danger";
        remove.textContent = "Remove";
        remove.addEventListener("click", async () => {
          try {
            await api("POST", "/v1/admin/tags", { username: t.username, text: "", color: t.color });
            await loadTags();
          } catch (e) {
            msg($("[data-tag-msg]"), e.message, true);
          }
        });
        const actions = document.createElement("div");
        actions.className = "admin-actions";
        actions.append(edit, remove);
        row.append(tag, name, state, actions);
        box.append(row);
      }
    } catch (e) {
      msg($("[data-tag-msg]"), e.message, true);
    }
  }

  $("[data-tag-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const f = new FormData(ev.target);
    const tagMsg = $("[data-tag-msg]");
    const text = String(f.get("text") || "").trim();
    try {
      const r = await api("POST", "/v1/admin/tags", { username: String(f.get("username")).trim(), text, color: f.get("color") });
      msg(tagMsg, text ? `Saved. ${r.username} now shows [${text}] to MOTION players.` : `Removed ${r.username}'s tag.`);
      ev.target.reset();
      await loadTags();
    } catch (e) {
      msg(tagMsg, e.message, true);
    }
  });

  // ---------------------------------------------------------------- community skins

  async function loadCommunitySkins() {
    const box = $("[data-skins]");
    try {
      const { skins } = await api("GET", "/v1/admin/community-skins");
      box.replaceChildren();
      if (!skins.length) { box.textContent = "No community skins yet."; return; }
      for (const skin of skins) {
        const card = document.createElement("div");
        card.className = "admin-cape" + (skin.enabled ? "" : " off");
        const preview = document.createElement("img"); preview.src = skin.image; preview.alt = ""; preview.className = "admin-skin-image";
        const name = document.createElement("div"); name.className = "admin-cape-name"; name.textContent = skin.name;
        const meta = document.createElement("div"); meta.className = "muted";
        meta.textContent = (skin.model === "slim" ? "Slim" : "Classic") + " · 64×64 · " + (skin.enabled ? "Available to MOTION users" : "Hidden");
        const actions = document.createElement("div"); actions.className = "admin-actions";
        const actionButton = (label, action, danger = false) => {
          const b = document.createElement("button"); b.type = "button"; b.className = danger ? "btn btn-danger" : "btn"; b.textContent = label;
          b.addEventListener("click", async () => {
            if (action === "delete" && !confirm("Delete " + skin.name + " from the MOTION catalog?")) return;
            b.disabled = true;
            try { await api("POST", "/v1/admin/community-skins/update", { id: skin.id, action }); await loadCommunitySkins(); }
            catch (e) { msg($("[data-skin-msg]"), e.message, true); b.disabled = false; }
          });
          return b;
        };
        actions.append(actionButton(skin.enabled ? "Hide" : "Show", skin.enabled ? "disable" : "enable"));
        actions.append(actionButton("Delete", "delete", true));
        card.append(preview, name, meta, actions); box.append(card);
      }
    } catch (e) { msg($("[data-skin-msg]"), e.message, true); }
  }

  async function skinPng(file) {
    const bmp = await createImageBitmap(file);
    if (bmp.width !== 64 || bmp.height !== 64) throw new Error("Community skins must be exactly 64×64 pixels.");
    const c = document.createElement("canvas"); c.width = 64; c.height = 64;
    const g = c.getContext("2d"); g.imageSmoothingEnabled = false; g.drawImage(bmp, 0, 0);
    return c.toDataURL("image/png");
  }

  $("[data-skin-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault(); const f = new FormData(ev.target); const skinMsg = $("[data-skin-msg]"); const file = f.get("file");
    if (!file || !file.size) return msg(skinMsg, "Choose a Minecraft skin PNG.", true);
    const submit = ev.target.querySelector("button[type=submit]"); submit.disabled = true;
    try {
      msg(skinMsg, "Preparing skin…"); const png = await skinPng(file);
      if (png.length * 0.75 > 64 * 1024) throw new Error("That skin is over 64 KB after conversion.");
      await api("POST", "/v1/admin/community-skins", { name: String(f.get("name")).trim(), model: f.get("model"), png });
      msg(skinMsg, "Published. MOTION users can find it in My Skin → Community."); ev.target.reset(); await loadCommunitySkins();
    } catch (e) { msg(skinMsg, e.message, true); }
    finally { submit.disabled = false; }
  });

  // ---------------------------------------------------------------- accessories

  const kindSelect = $("[data-acc-kind]");
  const syncKind = () => {
    const aura = kindSelect.value === "aura";
    $("[data-acc-style]").classList.toggle("hidden", !aura);
    $("[data-acc-file]").classList.toggle("hidden", aura);
  };
  kindSelect.addEventListener("change", syncKind);
  syncKind();

  /** One wing as a PNG (kept smooth; large images are scaled down to 1024 px on the long side). */
  async function wingPng(file) {
    const bmp = await createImageBitmap(file);
    const scale = Math.min(1, 1024 / Math.max(bmp.width, bmp.height));
    const c = document.createElement("canvas");
    c.width = Math.max(16, Math.round(bmp.width * scale));
    c.height = Math.max(16, Math.round(bmp.height * scale));
    const g = c.getContext("2d");
    g.imageSmoothingEnabled = true;
    g.imageSmoothingQuality = "high";
    g.drawImage(bmp, 0, 0, c.width, c.height);
    return c.toDataURL("image/png");
  }

  async function loadAccessories() {
    const box = $("[data-accs]");
    try {
      const { accessories } = await api("GET", "/v1/admin/accessories");
      box.replaceChildren();
      if (!accessories.length) {
        box.textContent = "No accessories yet.";
        return;
      }
      for (const a of accessories) {
        const card = document.createElement("div");
        card.className = "admin-cape" + (a.enabled ? "" : " off");
        let preview;
        if (a.kind === "wings") {
          preview = document.createElement("img");
          preview.src = a.image;
          preview.alt = "";
          preview.className = "hd admin-wing";
        } else {
          preview = document.createElement("div");
          preview.className = "admin-aura";
          preview.style.setProperty("--aura", a.color);
          preview.textContent = a.style;
        }
        const name = document.createElement("div");
        name.className = "admin-cape-name";
        name.textContent = a.name;
        const meta = document.createElement("div");
        meta.className = "muted";
        meta.textContent = `${a.kind === "wings" ? `Wings · ${a.width}×${a.height}` : `Aura · ${a.style} · ${a.color}`} · ${a.access === "premium" ? "Premium only" : "Everyone"}${a.enabled ? "" : " · hidden"}`;
        const actions = document.createElement("div");
        actions.className = "admin-actions";
        const act = (label, action, danger) => {
          const b = document.createElement("button");
          b.type = "button";
          b.className = danger ? "btn btn-danger" : "btn";
          b.textContent = label;
          b.addEventListener("click", async () => {
            if (action === "delete" && !confirm(`Delete "${a.name}"? Players wearing it lose it.`)) return;
            b.disabled = true;
            try {
              await api("POST", "/v1/admin/accessories/update", { id: a.id, action });
              await loadAccessories();
            } catch (e) {
              msg($("[data-acc-msg]"), e.message, true);
              b.disabled = false;
            }
          });
          return b;
        };
        actions.append(act(a.enabled ? "Hide" : "Show", a.enabled ? "disable" : "enable"));
        actions.append(act(a.access === "premium" ? "Make free" : "Premium only", a.access === "premium" ? "everyone" : "premium"));
        actions.append(act("Delete", "delete", true));
        card.append(preview, name, meta, actions);
        box.append(card);
      }
    } catch (e) {
      msg($("[data-acc-msg]"), e.message, true);
    }
  }

  $("[data-acc-form]").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const f = new FormData(ev.target);
    const accMsg = $("[data-acc-msg]");
    const kind = f.get("kind");
    const body = { name: String(f.get("name")).trim(), kind, access: f.get("access"), color: f.get("color") };
    try {
      if (kind === "wings") {
        const file = f.get("file");
        if (!file || !file.size) return msg(accMsg, "Choose a wing image.", true);
        msg(accMsg, "Preparing the image…");
        body.png = await wingPng(file);
        if (body.png.length * 0.75 > 4 * 1024 * 1024) return msg(accMsg, "That image is over 4 MB after conversion. Use a smaller one.", true);
      } else {
        body.style = f.get("style");
      }
      msg(accMsg, "Publishing…");
      await api("POST", "/v1/admin/accessories", body);
      msg(accMsg, "Published. Players can pick it in the launcher (Customize).");
      ev.target.reset();
      syncKind();
      await loadAccessories();
    } catch (e) {
      msg(accMsg, e.message, true);
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
    await loadAccessories();
    await loadCommunitySkins();
    await loadTags();
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
    void loadAccessories();
    void loadCommunitySkins();
    void loadTags();
  }
  setInterval(() => { if (token) void loadTags(); }, 30_000);
})();
