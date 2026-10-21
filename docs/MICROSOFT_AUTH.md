# Microsoft sign-in

BLOOD signs players in on **Microsoft's own sign-in page**. There are two ways:

1. **Sign in.**
   - **In-app window (default).** "Add Microsoft account" opens Microsoft's sign-in page in a small window inside the launcher (OAuth 2.0 authorization code + PKCE). When you finish, Microsoft redirects to `https://login.microsoftonline.com/common/oauth2/nativeclient`. BLOOD catches that redirect, closes the window and exchanges the one-time code for tokens. The window has no access to any BLOOD command.
   - **Code in your browser (fallback).** "Sign in with a code instead" uses the device code flow: BLOOD shows a short code and opens Microsoft's page in your browser.
2. **BLOOD never sees your password.** Either way you type it (and do 2FA or passkeys) on Microsoft's page. BLOOD never asks for, sees or stores it.
3. **Exchange tokens.** The launcher trades Microsoft's token for Xbox Live → XSTS → Minecraft tokens, then loads your Java Edition profile, which only exists for accounts that own the game (`/entitlements/mcstore` is used to tell "doesn't own it" apart from "owns it but has no profile yet"). An account without Minecraft can't launch the game; BLOOD has no offline or "cracked" mode.
4. **Store safely.** The refresh token goes to **Windows Credential Manager** (service "BLOOD Launcher"). Tokens are never written to settings files or logs. Signing out deletes them.

The code is in `apps/launcher/src/core/auth/microsoft.ts`, with tests in `microsoft.test.ts`.

## Registering your own Azure app

Every launcher build needs its own Azure app registration (client id):

1. Go to <https://entra.microsoft.com> → **App registrations** → **New registration**.
   - Name: *BLOOD* (or your fork's name).
   - Supported account types: **Personal Microsoft accounts only**.
   - Redirect URI: leave it empty here (it's added in the next step).
2. Under **Authentication**:
   - **Add a platform → Mobile and desktop applications**, tick or enter `https://login.microsoftonline.com/common/oauth2/nativeclient`, and save. This is needed for the in-app sign-in window.
   - Set **Allow public client flows** to **Yes**. This is needed for the code fallback.
3. Copy the **Application (client) ID** into `VITE_MS_CLIENT_ID`. Use `apps/launcher/.env` locally and a GitHub repository *variable* for CI. The client id is public, not a secret, and no client secret is needed or used.
4. **Request Minecraft API access.** New app ids can't call `api.minecraftservices.com` until Mojang approves them. Submit the form at <https://aka.ms/mce-reviewappid> and wait for approval; until then, sign-in fails at the Minecraft step with a clear error.

## Errors you may see

| Message | Cause |
| --- | --- |
| "The Azure app isn't set up for in-app sign-in yet…" | The nativeclient redirect URI is missing (step 2). The code fallback still works. |
| "…doesn't own Minecraft: Java Edition" | No Java profile and no game entitlement. |
| "This account owns Minecraft but has no Java Edition profile yet…" | Pick a username at minecraft.net first. |
| "This Microsoft account has no Xbox profile…" (XSTS 2148916233) | Sign in once at minecraft.net to create one. |
| "This is a child account…" (XSTS 2148916238) | The account must be added to a Microsoft Family by an adult. |
| "Minecraft rejected this launcher's sign-in…" | The Azure app id hasn't been approved by Mojang (step 4). |
