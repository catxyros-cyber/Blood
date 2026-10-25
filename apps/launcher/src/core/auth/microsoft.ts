/**
 * Microsoft → Xbox Live → XSTS → Minecraft authentication. The user signs in
 * on Microsoft's own page — in a sign-in window inside the launcher
 * (authorization code + PKCE) or, as a fallback, in their browser with the
 * device code flow. BLOOD never sees their password.
 *
 * Requires an Azure app registration (public client, "Allow public client
 * flows" enabled) that Mojang has approved for the Minecraft services API.
 * See docs/MICROSOFT_AUTH.md.
 */

import { BloomError, type HttpRequest, type HttpResponse } from "../platform";

export type Http = (req: HttpRequest) => Promise<HttpResponse>;

const MS_AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
const SCOPE = "XboxLive.signin offline_access";

export interface DeviceCode {
  deviceCode: string;
  userCode: string;
  verificationUri: string;
  expiresAt: number;
  intervalSec: number;
  message: string;
}

export interface MsTokens {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export interface MinecraftSession {
  accessToken: string;
  expiresAt: number;
  uuid: string;
  username: string;
  xuid: string;
  skinUrl?: string;
}

function parse<T>(res: HttpResponse): T {
  try {
    return JSON.parse(res.body) as T;
  } catch {
    throw new BloomError("auth_failed", `Unexpected response (HTTP ${res.status}) during sign-in`);
  }
}

export async function requestDeviceCode(http: Http, clientId: string): Promise<DeviceCode> {
  if (!clientId) throw new BloomError("auth_not_configured", "Microsoft sign-in is not configured for this build of MOTION.");
  const res = await http({
    method: "POST",
    url: `${MS_AUTHORITY}/devicecode`,
    form: { client_id: clientId, scope: SCOPE },
  });
  const body = parse<{
    device_code: string;
    user_code: string;
    verification_uri: string;
    expires_in: number;
    interval: number;
    message: string;
    error?: string;
    error_description?: string;
  }>(res);
  if (res.status !== 200 || body.error) {
    throw new BloomError("auth_failed", body.error_description ?? "Could not start Microsoft sign-in");
  }
  return {
    deviceCode: body.device_code,
    userCode: body.user_code,
    verificationUri: body.verification_uri,
    expiresAt: Date.now() + body.expires_in * 1000,
    intervalSec: body.interval || 5,
    message: body.message,
  };
}

/** One poll of the token endpoint. Returns null while the user hasn't finished signing in. */
export async function pollDeviceCode(
  http: Http,
  clientId: string,
  code: DeviceCode,
): Promise<{ tokens: MsTokens } | { pending: true; slowDown: boolean }> {
  const res = await http({
    method: "POST",
    url: `${MS_AUTHORITY}/token`,
    form: {
      grant_type: "urn:ietf:params:oauth:grant-type:device_code",
      client_id: clientId,
      device_code: code.deviceCode,
    },
  });
  const body = parse<{
    access_token?: string;
    refresh_token?: string;
    expires_in?: number;
    error?: string;
    error_description?: string;
  }>(res);
  if (body.access_token && body.refresh_token) {
    return {
      tokens: {
        accessToken: body.access_token,
        refreshToken: body.refresh_token,
        expiresAt: Date.now() + (body.expires_in ?? 3600) * 1000,
      },
    };
  }
  switch (body.error) {
    case "authorization_pending":
      return { pending: true, slowDown: false };
    case "slow_down":
      return { pending: true, slowDown: true };
    case "authorization_declined":
      throw new BloomError("auth_cancelled", "Sign-in was cancelled.");
    case "expired_token":
      throw new BloomError("auth_expired", "The sign-in code expired. Please try again.");
    default:
      throw new BloomError("auth_failed", body.error_description ?? "Microsoft sign-in failed.");
  }
}

// --- In-app sign-in window (authorization code + PKCE) -------------------------
//
// The launcher opens Microsoft's own sign-in page in a separate window. When
// Microsoft redirects to the native-client URL below, the Rust side catches the
// navigation, closes the window and hands the URL back. BLOOD never sees the
// password: only a one-time code, which is exchanged for tokens with PKCE.

/** Microsoft's standard redirect URI for desktop apps (register it in Azure). */
export const NATIVE_REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient";

export interface PkceRequest {
  url: string;
  state: string;
  verifier: string;
}

function base64Url(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomString(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  globalThis.crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

export async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

/** Builds the authorize URL for the sign-in window, with a fresh PKCE verifier and state. */
export async function createAuthRequest(clientId: string): Promise<PkceRequest> {
  if (!clientId) throw new BloomError("auth_not_configured", "Microsoft sign-in is not configured for this build of MOTION.");
  const verifier = randomString(48);
  const state = randomString(18);
  const params = new URLSearchParams({
    client_id: clientId,
    response_type: "code",
    redirect_uri: NATIVE_REDIRECT_URI,
    response_mode: "query",
    scope: SCOPE,
    state,
    code_challenge: await pkceChallenge(verifier),
    code_challenge_method: "S256",
    prompt: "select_account",
  });
  return { url: `${MS_AUTHORITY}/authorize?${params.toString()}`, state, verifier };
}

/** Reads the code from the redirect URL the sign-in window ended on. */
export function codeFromRedirect(redirectUrl: string, expectedState: string): string {
  let u: URL;
  try {
    u = new URL(redirectUrl);
  } catch {
    throw new BloomError("auth_failed", "Microsoft sign-in returned an invalid address.");
  }
  if (`${u.origin}${u.pathname}` !== NATIVE_REDIRECT_URI) throw new BloomError("auth_failed", "Microsoft sign-in returned an unexpected address.");
  const error = u.searchParams.get("error");
  if (error) {
    if (error === "access_denied") throw new BloomError("auth_cancelled", "Sign-in was cancelled.");
    const desc = u.searchParams.get("error_description");
    if (error === "invalid_request" && desc?.includes("redirect_uri")) {
      throw new BloomError(
        "auth_not_configured",
        `The Azure app isn't set up for in-app sign-in yet. Add the redirect URI ${NATIVE_REDIRECT_URI} under Authentication → Mobile and desktop applications (see docs/MICROSOFT_AUTH.md), or use "Sign in with a code".`,
      );
    }
    throw new BloomError("auth_failed", desc?.split("\r\n")[0] || `Microsoft sign-in failed (${error}).`);
  }
  if (u.searchParams.get("state") !== expectedState) throw new BloomError("auth_failed", "The sign-in response didn't match this request. Please try again.");
  const code = u.searchParams.get("code");
  if (!code) throw new BloomError("auth_failed", "Microsoft didn't return a sign-in code.");
  return code;
}

export async function exchangeAuthCode(http: Http, clientId: string, code: string, verifier: string): Promise<MsTokens> {
  const res = await http({
    method: "POST",
    url: `${MS_AUTHORITY}/token`,
    form: {
      grant_type: "authorization_code",
      client_id: clientId,
      code,
      redirect_uri: NATIVE_REDIRECT_URI,
      code_verifier: verifier,
      scope: SCOPE,
    },
  });
  const body = parse<{ access_token?: string; refresh_token?: string; expires_in?: number; error?: string; error_description?: string }>(res);
  if (!body.access_token || !body.refresh_token) {
    throw new BloomError("auth_failed", body.error_description?.split("\r\n")[0] ?? "Microsoft sign-in failed.");
  }
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    expiresAt: Date.now() + (body.expires_in ?? 3600) * 1000,
  };
}

export async function refreshMsToken(http: Http, clientId: string, refreshToken: string): Promise<MsTokens> {
  const res = await http({
    method: "POST",
    url: `${MS_AUTHORITY}/token`,
    form: { grant_type: "refresh_token", client_id: clientId, refresh_token: refreshToken, scope: SCOPE },
  });
  const body = parse<{ access_token?: string; refresh_token?: string; expires_in?: number; error?: string }>(res);
  if (!body.access_token) {
    // invalid_grant: the refresh token was revoked or expired — the user must sign in again.
    throw new BloomError("session_expired", "Your Microsoft session has expired. Please sign in again.");
  }
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token ?? refreshToken,
    expiresAt: Date.now() + (body.expires_in ?? 3600) * 1000,
  };
}

const XSTS_ERRORS: Record<string, string> = {
  "2148916227": "This Xbox account is banned.",
  "2148916229": "This account is restricted by parental controls. A parent must allow multiplayer/online play.",
  "2148916233": "This Microsoft account has no Xbox profile. Sign in once at minecraft.net to create one, then try again.",
  "2148916234": "Please accept the Xbox Terms of Service at xbox.com, then try again.",
  "2148916235": "Xbox Live is not available in your country or region.",
  "2148916236": "This account needs adult verification on xbox.com (South Korea).",
  "2148916237": "This account needs adult verification on xbox.com (South Korea).",
  "2148916238": "This is a child account. It must be added to a Microsoft family by an adult before it can sign in.",
};

async function xboxUserToken(http: Http, msAccessToken: string): Promise<{ token: string; uhs: string }> {
  const res = await http({
    method: "POST",
    url: "https://user.auth.xboxlive.com/user/authenticate",
    headers: { Accept: "application/json" },
    json: {
      Properties: { AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com", RpsTicket: `d=${msAccessToken}` },
      RelyingParty: "http://auth.xboxlive.com",
      TokenType: "JWT",
    },
  });
  if (res.status !== 200) throw new BloomError("auth_failed", `Xbox Live sign-in failed (HTTP ${res.status}).`);
  const body = parse<{ Token: string; DisplayClaims: { xui: { uhs: string }[] } }>(res);
  const uhs = body.DisplayClaims?.xui?.[0]?.uhs;
  if (!body.Token || !uhs) throw new BloomError("auth_failed", "Xbox Live returned an incomplete response.");
  return { token: body.Token, uhs };
}

async function xstsToken(http: Http, userToken: string): Promise<{ token: string; uhs: string; xuid: string }> {
  const res = await http({
    method: "POST",
    url: "https://xsts.auth.xboxlive.com/xsts/authorize",
    headers: { Accept: "application/json" },
    json: {
      Properties: { SandboxId: "RETAIL", UserTokens: [userToken] },
      RelyingParty: "rp://api.minecraftservices.com/",
      TokenType: "JWT",
    },
  });
  if (res.status === 401) {
    let xerr = "";
    try {
      xerr = String((JSON.parse(res.body) as { XErr?: number }).XErr ?? "");
    } catch {
      /* fall through */
    }
    throw new BloomError("xbox_account", XSTS_ERRORS[xerr] ?? `Xbox Live refused the sign-in (error ${xerr || "unknown"}).`);
  }
  if (res.status !== 200) throw new BloomError("auth_failed", `Xbox authorization failed (HTTP ${res.status}).`);
  const body = parse<{ Token: string; DisplayClaims: { xui: { uhs: string; xid?: string }[] } }>(res);
  const claim = body.DisplayClaims?.xui?.[0];
  if (!body.Token || !claim) throw new BloomError("auth_failed", "Xbox authorization returned an incomplete response.");
  return { token: body.Token, uhs: claim.uhs, xuid: claim.xid ?? "" };
}

async function minecraftLogin(http: Http, uhs: string, xsts: string): Promise<{ accessToken: string; expiresIn: number }> {
  const res = await http({
    method: "POST",
    url: "https://api.minecraftservices.com/authentication/login_with_xbox",
    json: { identityToken: `XBL3.0 x=${uhs};${xsts}` },
  });
  if (res.status === 403) {
    throw new BloomError(
      "app_not_approved",
      "Minecraft rejected this launcher's sign-in. The Azure app used by this build must be approved by Mojang for Minecraft services (see docs/MICROSOFT_AUTH.md).",
    );
  }
  if (res.status === 429) throw new BloomError("rate_limited", "Too many sign-in attempts. Wait a minute and try again.");
  if (res.status !== 200) throw new BloomError("auth_failed", `Minecraft sign-in failed (HTTP ${res.status}).`);
  const body = parse<{ access_token: string; expires_in: number }>(res);
  return { accessToken: body.access_token, expiresIn: body.expires_in ?? 86400 };
}

export interface MinecraftProfile {
  id: string;
  name: string;
  skins?: { url: string; state: string }[];
}

/**
 * Fetch the Java Edition profile. A 404 means the account does not own the game
 * (Game Pass accounts have a profile once they've launched it once). This is the
 * ownership check BLOOD relies on: no profile → no launch.
 */
export async function minecraftProfile(http: Http, mcAccessToken: string): Promise<MinecraftProfile> {
  const res = await http({
    method: "GET",
    url: "https://api.minecraftservices.com/minecraft/profile",
    headers: { Authorization: `Bearer ${mcAccessToken}` },
  });
  if (res.status === 404) {
    throw new BloomError(
      "not_owned",
      "This Microsoft account doesn't own Minecraft: Java Edition (or hasn't set up a Java profile yet). Buy the game or launch it once from the official launcher, then try again.",
    );
  }
  if (res.status === 401) throw new BloomError("session_expired", "Your Minecraft session has expired. Please sign in again.");
  if (res.status !== 200) throw new BloomError("auth_failed", `Could not load your Minecraft profile (HTTP ${res.status}).`);
  return parse<MinecraftProfile>(res);
}

/** Entitlements — used to tell "doesn't own the game" apart from "no profile yet". */
export async function ownsMinecraft(http: Http, mcAccessToken: string): Promise<boolean> {
  const res = await http({
    method: "GET",
    url: "https://api.minecraftservices.com/entitlements/mcstore",
    headers: { Authorization: `Bearer ${mcAccessToken}` },
  });
  if (res.status !== 200) return false;
  const body = parse<{ items?: { name: string }[] }>(res);
  return (body.items ?? []).some((i) => i.name === "game_minecraft" || i.name === "product_minecraft");
}

/** Full chain from a Microsoft access token to a verified Minecraft session. */
export async function minecraftSessionFromMs(http: Http, msAccessToken: string): Promise<MinecraftSession> {
  const user = await xboxUserToken(http, msAccessToken);
  const xsts = await xstsToken(http, user.token);
  const mc = await minecraftLogin(http, xsts.uhs, xsts.token);
  let profile: MinecraftProfile;
  try {
    profile = await minecraftProfile(http, mc.accessToken);
  } catch (e) {
    // Tell "doesn't own the game" apart from "owns it but never created a Java profile".
    if (e instanceof BloomError && e.code === "not_owned" && (await ownsMinecraft(http, mc.accessToken).catch(() => false))) {
      throw new BloomError(
        "no_profile",
        "This account owns Minecraft but has no Java Edition profile yet. Choose a username at minecraft.net (or launch the game once from the official launcher), then sign in again.",
      );
    }
    throw e;
  }
  const skin = profile.skins?.find((s) => s.state === "ACTIVE")?.url;
  return {
    accessToken: mc.accessToken,
    expiresAt: Date.now() + mc.expiresIn * 1000,
    uuid: profile.id,
    username: profile.name,
    xuid: xsts.xuid,
    skinUrl: secureSkinUrl(skin),
  };
}

/** Is a cached token still usable for at least `marginMs`? */
export function tokenValid(expiresAt: number | undefined, marginMs = 5 * 60_000): boolean {
  return typeof expiresAt === "number" && expiresAt - marginMs > Date.now();
}

/**
 * Mojang hands out skin URLs as http://textures.minecraft.net/…, which the launcher's content policy
 * (https images only) blocks. The same files are served over https.
 */
export function secureSkinUrl(url: string | undefined | null): string | undefined {
  if (!url) return undefined;
  return url.replace(/^http:\/\/textures\.minecraft\.net\//i, "https://textures.minecraft.net/");
}

/**
 * The skin URL in a public session-server profile
 * (sessionserver.mojang.com/session/minecraft/profile/<uuid>), whose "textures" property is
 * base64 JSON. Returns undefined for a missing/default skin or anything malformed.
 */
export function skinUrlFromSessionProfile(profile: unknown): string | undefined {
  const props = (profile as { properties?: { name?: string; value?: string }[] } | null)?.properties;
  const raw = Array.isArray(props) ? props.find((p) => p?.name === "textures")?.value : undefined;
  if (typeof raw !== "string") return undefined;
  try {
    const json = typeof atob === "function" ? atob(raw) : Buffer.from(raw, "base64").toString("utf8");
    const url = (JSON.parse(json) as { textures?: { SKIN?: { url?: unknown } } }).textures?.SKIN?.url;
    return typeof url === "string" && /^https?:\/\/textures\.minecraft\.net\//i.test(url) ? secureSkinUrl(url) : undefined;
  } catch {
    return undefined;
  }
}

/** A player's current skin from Mojang's public profile service: no sign-in or token needed. */
export async function publicSkinUrl(http: Http, uuid: string): Promise<string | undefined> {
  const id = uuid.replace(/-/g, "").toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(id)) return undefined;
  const res = await http({ method: "GET", url: `https://sessionserver.mojang.com/session/minecraft/profile/${id}`, timeoutMs: 10_000 });
  if (res.status !== 200 || !res.body) return undefined;
  return skinUrlFromSessionProfile(JSON.parse(res.body));
}

