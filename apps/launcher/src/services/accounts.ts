/**
 * Microsoft account management. Refresh tokens and Minecraft access tokens are
 * kept in the Windows Credential Manager via the Rust layer — never in
 * settings files, logs or browser storage.
 */

import {
  codeFromRedirect,
  createAuthRequest,
  exchangeAuthCode,
  minecraftSessionFromMs,
  pollDeviceCode,
  refreshMsToken,
  requestDeviceCode,
  tokenValid,
  type DeviceCode,
  type MinecraftSession,
  type MsTokens,
} from "../core/auth/microsoft";
import { BloomError, toBloomError } from "../core/platform";
import { app, msClientId, P, saveAccounts, updateSettings, type Account } from "./app";
import { native } from "./native";

const refreshKey = (id: string) => `ms-refresh:${id}`;
const mcKey = (id: string) => `mc-session:${id}`;

const http = (req: Parameters<ReturnType<typeof P>["request"]>[0]) => P().request(req);

export function activeAccount(): Account | undefined {
  const s = app.get();
  return s.accounts.find((a) => a.id === s.settings.activeAccountId) ?? s.accounts[0];
}

export interface SignInFlow {
  code: DeviceCode;
  /** Resolves with the new account once the user finishes signing in on Microsoft's page. */
  done: Promise<Account>;
  cancel(): void;
}

export async function startSignIn(): Promise<SignInFlow> {
  const clientId = msClientId();
  const code = await requestDeviceCode(http, clientId);
  let cancelled = false;
  const done = (async () => {
    let interval = code.intervalSec;
    while (!cancelled) {
      if (Date.now() > code.expiresAt) throw new BloomError("auth_expired", "The sign-in code expired. Please try again.");
      await new Promise((r) => setTimeout(r, interval * 1000));
      if (cancelled) break;
      const r = await pollDeviceCode(http, clientId, code);
      if ("pending" in r) {
        if (r.slowDown) interval += 5;
        continue;
      }
      return await finishSignIn(r.tokens);
    }
    throw new BloomError("auth_cancelled", "Sign-in cancelled.");
  })();
  return {
    code,
    done,
    cancel() {
      cancelled = true;
    },
  };
}

/** Verifies the Minecraft account behind fresh Microsoft tokens and saves it. */
async function finishSignIn(tokens: MsTokens): Promise<Account> {
  const session = await minecraftSessionFromMs(http, tokens.accessToken);
  await native.secretSet(refreshKey(session.uuid), tokens.refreshToken);
  await storeSession(session);
  const account: Account = {
    id: session.uuid,
    username: session.username,
    xuid: session.xuid,
    skinUrl: session.skinUrl,
    addedAt: Date.now(),
    lastVerifiedAt: Date.now(),
  };
  app.set((s) => ({ accounts: [...s.accounts.filter((a) => a.id !== account.id), account] }));
  await saveAccounts();
  updateSettings({ activeAccountId: account.id });
  return account;
}

/**
 * Signs in through a Microsoft sign-in window inside the launcher. Resolves with
 * the account, or null if the player closed the window.
 */
export async function signInWithWindow(): Promise<Account | null> {
  const clientId = msClientId();
  const req = await createAuthRequest(clientId);
  const redirect = await native.msLoginWindow(req.url);
  if (!redirect) return null;
  const code = codeFromRedirect(redirect, req.state);
  const tokens = await exchangeAuthCode(http, clientId, code, req.verifier);
  return finishSignIn(tokens);
}

async function storeSession(s: MinecraftSession) {
  await native.secretSet(mcKey(s.uuid), JSON.stringify({ accessToken: s.accessToken, expiresAt: s.expiresAt }));
}

export interface LaunchCredentials {
  username: string;
  uuid: string;
  accessToken: string;
  xuid: string;
  /** True when launching without a verified online session (no internet). */
  offline: boolean;
}

/**
 * Credentials for launching. Uses the cached Minecraft token if it is still
 * valid, otherwise refreshes through Microsoft. If the network is down, an
 * account that has already been verified can still play singleplayer
 * (like the official launcher's offline mode).
 */
export async function launchCredentials(account: Account): Promise<LaunchCredentials> {
  const cachedRaw = await native.secretGet(mcKey(account.id)).catch(() => null);
  if (cachedRaw) {
    try {
      const cached = JSON.parse(cachedRaw) as { accessToken: string; expiresAt: number };
      if (tokenValid(cached.expiresAt)) {
        return { username: account.username, uuid: account.id, accessToken: cached.accessToken, xuid: account.xuid, offline: false };
      }
    } catch {
      /* re-authenticate below */
    }
  }

  const refresh = await native.secretGet(refreshKey(account.id));
  if (!refresh) throw new BloomError("session_expired", `Please sign in to ${account.username} again.`);
  try {
    const ms = await refreshMsToken(http, msClientId(), refresh);
    await native.secretSet(refreshKey(account.id), ms.refreshToken);
    const session = await minecraftSessionFromMs(http, ms.accessToken);
    await storeSession(session);
    const updated: Account = { ...account, username: session.username, skinUrl: session.skinUrl, xuid: session.xuid, lastVerifiedAt: Date.now() };
    app.set((s) => ({ accounts: s.accounts.map((a) => (a.id === account.id ? updated : a)) }));
    await saveAccounts();
    return { username: session.username, uuid: session.uuid, accessToken: session.accessToken, xuid: session.xuid, offline: false };
  } catch (e) {
    const err = toBloomError(e);
    if (["offline", "timeout", "network"].includes(err.code) && account.lastVerifiedAt > 0) {
      // Ownership was verified online before; allow offline singleplayer.
      return { username: account.username, uuid: account.id, accessToken: "0", xuid: account.xuid, offline: true };
    }
    throw err;
  }
}

export async function signOut(id: string) {
  await native.secretDelete(refreshKey(id)).catch(() => undefined);
  await native.secretDelete(mcKey(id)).catch(() => undefined);
  app.set((s) => ({ accounts: s.accounts.filter((a) => a.id !== id) }));
  await saveAccounts();
  if (app.get().settings.activeAccountId === id) updateSettings({ activeAccountId: app.get().accounts[0]?.id ?? null });
}

export function setActiveAccount(id: string) {
  updateSettings({ activeAccountId: id });
}
