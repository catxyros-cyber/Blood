# Architecture

```
┌──────────────────────── Windows PC ────────────────────────┐        ┌──────── BLOOD server ────────┐
│ BLOOD launcher (Tauri)                                      │        │ apps/api (Node)               │
│  React UI ── invoke() ──► Rust backend                      │ HTTPS  │  /v1/auth   Minecraft-verified│
│  (src/ui, src/services,     • paths.rs  path confinement    │◄──────►│  /v1/billing Stripe Checkout  │
│   src/core = pure TS)       • net.rs    allowlisted HTTP    │        │  /v1/stripe/webhook (signed)  │
│                             • fsops.rs  Recycle-Bin deletes │        │  /v1/sync, /v1/news, releases │
│                             • secrets.rs Credential Manager │        │  website (apps/website)       │
│                             • launch.rs  start Java, stats  │        │  PostgreSQL                   │
│                             • bloomclient.rs bundled mod    │        └───────────────────────────────┘
│        │ installs jar, edits config/bloomclient/*.json      │
│        ▼                                                    │   Microsoft / Xbox / Minecraft services
│ Minecraft + BLOOD Client mod (Fabric/Quilt/NeoForge)        │   Modrinth API, Mojang piston-meta,
└─────────────────────────────────────────────────────────────┘   Fabric/Quilt/Forge/NeoForge metadata
```

## Launcher

- **`src/core`** is plain TypeScript with no UI or Tauri imports. It holds the Mojang version and library resolution, loader installers, the Modrinth client, `.mrpack` import, mod metadata parsing, perf presets, sync merging, and BLOOD Client settings-file handling. It runs under unit tests with a fake platform (`core/test/fakePlatform.ts`).
- **`src/services`** connects core to the real platform (`native.ts` → Rust commands) and to app state.
- **`src/ui`** is React with Tailwind. Themes are CSS variables in `styles.css`; the backdrop and petals live in `ui/backdrop.tsx`.
- **The Rust backend** is deliberately small and defensive:
  - **Path confinement (`paths.rs`).** The UI can only touch BLOOD's data directory and folders the user picked in a native dialog. Files outside those can only be *read* as import sources the user chose or dropped.
  - **Deletes (`fsops.rs`).** User content goes to the Recycle Bin. Only BLOOD's own caches are hard-deleted, and existing files are never overwritten unless the UI explicitly asks.
  - **Network (`net.rs`).** Requests go only to an allowlist of hosts (Microsoft/Xbox/Minecraft, Mojang, Modrinth, loader metadata, the configured BLOOD API). Downloads are checked against their sha1/sha512.
  - **Secrets (`secrets.rs`).** Microsoft refresh tokens and the BLOOD session token live in Windows Credential Manager, never in settings files or logs.
  - **BLOOD Client (`bloomclient.rs`).** Reads the bundled manifest, checks the jar's sha1, and copies it into an instance's `mods` folder without overwriting anything.

### Things the launcher never does

- It does not modify the Windows registry, disable security features, or kill processes it didn't start. The only process it stops is a game it launched, and only when you click Stop.
- It does not bundle Minecraft files. Everything comes from Mojang's servers at install time.
- It does not handle Microsoft passwords. See [MICROSOFT_AUTH.md](MICROSOFT_AUTH.md).

## Performance presets

- **Applying.** A preset edits `options.txt`, plus mod configs it recognizes. Before that it saves a timestamped backup under the instance's `.bloom/backups`, and you can restore any backup from the Performance page.
- **Game running.** A preset applied while the game runs is queued and written before the next launch.
- **Stats.** Memory, CPU and launch time are measured by the Rust backend. GC pauses are parsed from the JVM's GC log, which BLOOD enables with a launch argument. If a value can't be measured it is shown as "—", never estimated.

## API and Premium

- **Sign-in.**
  1. The launcher asks the API for a challenge.
  2. The launcher calls Mojang's `session/minecraft/join` with that serverId.
  3. The API checks `hasJoined`.

  So an account is proven to own Minecraft without its tokens ever reaching BLOOD's server.
- **Payments.**
  - The Stripe secret key exists only on the server.
  - Checkout sessions are created server-side.
  - Entitlements change **only** when a webhook arrives whose signature verifies. "Payment succeeded" pages are informational and unlock nothing.
  - Details: [STRIPE.md](STRIPE.md).
- **Sync.** Sync is a size-limited JSON document per user, merged field by field with timestamps on the client. It holds no secrets.

## BLOOD Client (the mod)

- **`common/.../core`** is plain Java with no Minecraft imports: settings, the store and file format, profiles, the HUD model, particle simulation, sky image processing, the OptiFine importer and the advisors. It is unit-tested with JUnit.
- **`common/.../{render,gui,hud,game,mixin}`** is the Minecraft layer, written against Mojang mappings and Parchment for 1.21.1.
- **`fabric/`, `neoforge/`** are thin entry points that register keys, events and the config screen.
- **Mixins fail safely.** `defaultRequire = 0`, so if another mod changes a target method, that one injection is skipped (and Mixin logs a warning) instead of crashing the game. Known interactions with other mods (Sodium, Iris, OptiFabric, FabricSkyBoxes/Nuit, Particle Rain) are listed on the menu's General page.

Settings live in `config/bloomclient/settings.json`, shared by the game and the launcher. See [BLOOM_CLIENT.md](BLOOM_CLIENT.md#settingsjson).
