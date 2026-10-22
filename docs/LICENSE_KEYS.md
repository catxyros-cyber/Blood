# Premium keys

You can sell or give away MOTION Premium as keys instead of (or as well as) Stripe payments.

- A key looks like `MOTION-7K2QF-M9XRT-4HZ8W-PN3D6`. Players paste it into **Premium key** on the
  dashboard (or the Premium page) and press **Redeem key**.
- The first PC that redeems a key owns it. Only that PC gets Premium.
- If someone else enters the same key, they see "active on another PC" and get nothing.
- The owner can move their key to a new PC with **Move to this PC**, at most **once every 24 hours**
  (counting from activation or the last move). Moving turns Premium off on the old PC. So a shared key
  can only ever work on one PC, and switching it around is slow.
- Keys can be lifetime or last a number of days (counted from when the player redeems it).
- A key also gives cloud sync on its PC when the player is signed in with Microsoft.

## How it works

- The launcher reads Windows' `MachineGuid` and sends only a SHA-256 of it (the "HWID"). The real id
  never leaves the PC.
- The server stores only a SHA-256 of each key and of each HWID. The full keys are shown once, when
  you create them. If you lose them, create new ones.
- The launcher re-checks the key at start and every 6 hours. Offline, it trusts the last good check
  for up to 7 days.
- Reinstalling Windows changes the `MachineGuid`; the player just uses **Move to this PC** (or you
  press **Reset PC** for them).

Be aware: Premium features that run inside the launcher can't be made uncrackable, because the
launcher runs on the player's PC. Server features (cloud sync) are checked on the server and can't
be bypassed. Keys stop casual sharing, which is what matters in practice.

## Setup (once)

Keys need the MOTION server running (the same one Stripe uses: see `docs/STRIPE.md` for hosting on
Railway with PostgreSQL).

1. On the server, add the variable `ADMIN_TOKEN` with a long random password (at least 24
   characters). This is your admin password. Don't share it or put it in GitHub.
2. Redeploy. The database table is created automatically (`migrations/002_license_keys.sql`).
3. In GitHub → Settings → Secrets and variables → Actions → **Variables**, set `VITE_BLOOM_API_URL`
   to your server's address, e.g. `https://blood-api.up.railway.app`. Release builds use it to talk
   to the server.
4. Release a new version so players get a launcher that knows the server.

## Making keys

Open `https://YOUR-SERVER/admin.html`, enter the admin password, choose how many keys, Lifetime or a
number of days, and an optional note (who it's for). Press **Create** and copy the keys.

The list below shows every key by its last five characters, when it was activated and when it
expires.

- **Reset PC**: clears the PC binding so the key can be activated on any PC straight away (for a
  player who got a new PC and can't wait 24 hours).
- **Disable**: the key stops working everywhere. **Enable** turns it back on.

## API (for bots, e.g. a Discord bot that hands out keys)

All admin calls need `Authorization: Bearer <ADMIN_TOKEN>`.

| Call | Body | Result |
| --- | --- | --- |
| `POST /v1/admin/keys` | `{"count": 1, "days": 30, "label": "giveaway"}` (omit `days` for lifetime) | `{"keys": ["MOTION-…"]}` |
| `GET /v1/admin/keys` | | `{"keys": [{ id, hint, label, durationDays, redeemedAt, expiresAt, bound, revoked, … }]}` |
| `POST /v1/admin/keys/update` | `{"id": "…", "action": "revoke" \| "restore" \| "unbind"}` | the updated key |

The launcher uses `POST /v1/license/activate` and `POST /v1/license/move` with `{"key", "hwid"}`.
