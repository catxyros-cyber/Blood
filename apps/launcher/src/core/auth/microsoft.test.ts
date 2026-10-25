import { test } from "node:test";
import assert from "node:assert/strict";
import { minecraftSessionFromMs, pollDeviceCode, publicSkinUrl, refreshMsToken, requestDeviceCode, skinUrlFromSessionProfile } from "./microsoft";
import { FakePlatform, json } from "../test/fakePlatform";

function authRoutes(p: FakePlatform, opts: { xstsError?: number; noProfile?: boolean; notApproved?: boolean } = {}) {
  let polls = 0;
  p.route("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", (req) => {
    assert.equal(req.form?.client_id, "client-123");
    assert.equal(req.form?.scope, "XboxLive.signin offline_access");
    return json({ device_code: "dc", user_code: "ABCD-1234", verification_uri: "https://www.microsoft.com/link", expires_in: 900, interval: 5, message: "Go" });
  });
  p.route("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", (req) => {
    if (req.form?.grant_type === "refresh_token") {
      return req.form.refresh_token === "good"
        ? json({ access_token: "ms2", refresh_token: "good2", expires_in: 3600 })
        : json({ error: "invalid_grant" }, 400);
    }
    polls++;
    return polls < 2 ? json({ error: "authorization_pending" }, 400) : json({ access_token: "ms", refresh_token: "rt", expires_in: 3600 });
  });
  p.route("https://user.auth.xboxlive.com/user/authenticate", (req) => {
    const body = req.json as { Properties: { RpsTicket: string } };
    assert.equal(body.Properties.RpsTicket, "d=ms");
    return json({ Token: "xbl", DisplayClaims: { xui: [{ uhs: "uhs1" }] } });
  });
  p.route("https://xsts.auth.xboxlive.com/xsts/authorize", () =>
    opts.xstsError ? json({ XErr: opts.xstsError }, 401) : json({ Token: "xsts", DisplayClaims: { xui: [{ uhs: "uhs1", xid: "2535" }] } }),
  );
  p.route("https://api.minecraftservices.com/authentication/login_with_xbox", (req) => {
    assert.equal((req.json as { identityToken: string }).identityToken, "XBL3.0 x=uhs1;xsts");
    return opts.notApproved ? json({}, 403) : json({ access_token: "mc", expires_in: 86400 });
  });
  p.route("https://api.minecraftservices.com/minecraft/profile", (req) => {
    assert.equal(req.headers?.Authorization, "Bearer mc");
    return opts.noProfile
      ? json({ error: "NOT_FOUND" }, 404)
      : json({ id: "069a79f444e94726a5befca90e38aaf5", name: "Notch", skins: [{ url: "https://textures.minecraft.net/x", state: "ACTIVE" }] });
  });
}

test("device code flow → verified Minecraft session", async () => {
  const p = new FakePlatform();
  authRoutes(p);
  const http = p.request.bind(p);
  const code = await requestDeviceCode(http, "client-123");
  assert.equal(code.userCode, "ABCD-1234");
  const first = await pollDeviceCode(http, "client-123", code);
  assert.ok("pending" in first);
  const second = await pollDeviceCode(http, "client-123", code);
  assert.ok("tokens" in second);
  const session = await minecraftSessionFromMs(http, second.tokens.accessToken);
  assert.equal(session.username, "Notch");
  assert.equal(session.uuid, "069a79f444e94726a5befca90e38aaf5");
  assert.equal(session.xuid, "2535");
  assert.ok(session.expiresAt > Date.now());
});

test("ownership and Xbox errors are explained", async () => {
  const p1 = new FakePlatform();
  authRoutes(p1, { noProfile: true });
  await assert.rejects(minecraftSessionFromMs(p1.request.bind(p1), "ms"), (e: { code: string }) => e.code === "not_owned");

  const p2 = new FakePlatform();
  authRoutes(p2, { xstsError: 2148916238 });
  await assert.rejects(minecraftSessionFromMs(p2.request.bind(p2), "ms"), /child account/);

  const p3 = new FakePlatform();
  authRoutes(p3, { notApproved: true });
  await assert.rejects(minecraftSessionFromMs(p3.request.bind(p3), "ms"), (e: { code: string }) => e.code === "app_not_approved");

  // Owns the game (entitlement present) but has no Java profile yet.
  const p4 = new FakePlatform();
  authRoutes(p4, { noProfile: true });
  p4.route("https://api.minecraftservices.com/entitlements/mcstore", { items: [{ name: "product_minecraft" }, { name: "game_minecraft" }] });
  await assert.rejects(minecraftSessionFromMs(p4.request.bind(p4), "ms"), (e: { code: string }) => e.code === "no_profile");
});

test("refresh tokens: rotation and expiry", async () => {
  const p = new FakePlatform();
  authRoutes(p);
  const t = await refreshMsToken(p.request.bind(p), "client-123", "good");
  assert.equal(t.refreshToken, "good2");
  await assert.rejects(refreshMsToken(p.request.bind(p), "client-123", "revoked"), (e: { code: string }) => e.code === "session_expired");
  await assert.rejects(requestDeviceCode(p.request.bind(p), ""), (e: { code: string }) => e.code === "auth_not_configured");
});

test("in-app sign-in: authorize URL uses PKCE S256 and the native redirect", async () => {
  const { createAuthRequest, pkceChallenge, NATIVE_REDIRECT_URI } = await import("./microsoft");
  const req = await createAuthRequest("client-123");
  const u = new URL(req.url);
  assert.equal(u.origin + u.pathname, "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize");
  assert.equal(u.searchParams.get("client_id"), "client-123");
  assert.equal(u.searchParams.get("redirect_uri"), NATIVE_REDIRECT_URI);
  assert.equal(u.searchParams.get("code_challenge_method"), "S256");
  assert.equal(u.searchParams.get("code_challenge"), await pkceChallenge(req.verifier));
  assert.equal(u.searchParams.get("state"), req.state);
  assert.match(req.verifier, /^[A-Za-z0-9_-]{43,128}$/);
  // RFC 7636 appendix B test vector
  assert.equal(await pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"), "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  await assert.rejects(createAuthRequest(""), /not configured/);
});

test("in-app sign-in: redirect parsing checks state and reports errors", async () => {
  const { codeFromRedirect, NATIVE_REDIRECT_URI } = await import("./microsoft");
  assert.equal(codeFromRedirect(`${NATIVE_REDIRECT_URI}?code=abc&state=s1`, "s1"), "abc");
  assert.throws(() => codeFromRedirect(`${NATIVE_REDIRECT_URI}?code=abc&state=other`, "s1"), /didn't match/);
  assert.throws(() => codeFromRedirect(`${NATIVE_REDIRECT_URI}?error=access_denied&state=s1`, "s1"), /cancelled/);
  assert.throws(
    () => codeFromRedirect(`${NATIVE_REDIRECT_URI}?error=invalid_request&error_description=The+provided+value+for+redirect_uri+is+not+valid&state=s1`, "s1"),
    /redirect URI/,
  );
  assert.throws(() => codeFromRedirect("https://evil.example/?code=abc&state=s1", "s1"), /unexpected address/);
});

test("in-app sign-in: code is exchanged with the verifier", async () => {
  const { exchangeAuthCode, NATIVE_REDIRECT_URI } = await import("./microsoft");
  const p = new FakePlatform();
  p.route("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", (req) => {
    assert.equal(req.form?.grant_type, "authorization_code");
    assert.equal(req.form?.code, "abc");
    assert.equal(req.form?.code_verifier, "ver");
    assert.equal(req.form?.redirect_uri, NATIVE_REDIRECT_URI);
    return json({ access_token: "ms", refresh_token: "rt", expires_in: 3600 });
  });
  const t = await exchangeAuthCode((r) => p.request(r), "client-123", "abc", "ver");
  assert.equal(t.accessToken, "ms");
  assert.equal(t.refreshToken, "rt");
});

test("skin URLs are served over https (the launcher only loads https images)", async () => {
  const { secureSkinUrl } = await import("./microsoft");
  assert.equal(secureSkinUrl("http://textures.minecraft.net/texture/abc"), "https://textures.minecraft.net/texture/abc");
  assert.equal(secureSkinUrl("https://textures.minecraft.net/texture/abc"), "https://textures.minecraft.net/texture/abc");
  assert.equal(secureSkinUrl(undefined), undefined);
});

const texturesProperty = (textures: unknown) => ({ properties: [{ name: "textures", value: btoa(JSON.stringify({ textures })) }] });

test("skinUrlFromSessionProfile reads the skin from a public profile", () => {
  const url = skinUrlFromSessionProfile(texturesProperty({ SKIN: { url: "http://textures.minecraft.net/texture/abc" } }));
  assert.equal(url, "https://textures.minecraft.net/texture/abc");
  assert.equal(skinUrlFromSessionProfile(texturesProperty({})), undefined, "default skin");
  assert.equal(skinUrlFromSessionProfile(texturesProperty({ SKIN: { url: "https://evil.example/x.png" } })), undefined, "other hosts");
  assert.equal(skinUrlFromSessionProfile({ properties: [{ name: "textures", value: "not base64 json" }] }), undefined);
  assert.equal(skinUrlFromSessionProfile(null), undefined);
});

test("publicSkinUrl asks the session server by undashed UUID", async () => {
  const p = new FakePlatform();
  p.route("https://sessionserver.mojang.com/session/minecraft/profile/0123456789abcdef0123456789abcdef", () =>
    json(texturesProperty({ SKIN: { url: "http://textures.minecraft.net/texture/face" } })),
  );
  const http = (req: Parameters<typeof p.request>[0]) => p.request(req);
  assert.equal(await publicSkinUrl(http, "01234567-89ab-cdef-0123-456789abcdef"), "https://textures.minecraft.net/texture/face");
  assert.equal(await publicSkinUrl(http, "not-a-uuid"), undefined);
});
