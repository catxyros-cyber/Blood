# MOTION MOTION

**Built for frames.** A black-and-electric-green Minecraft: Java Edition launcher for Windows, focused on
performance, with **MOTION Client**: an in-game mod that adds a customizable HUD, a custom
crosshair, a Right Shift settings menu, a Right Ctrl visuals menu with real shader effects, and optional visuals.

> MOTION is not affiliated with Mojang Studios or Microsoft. It ships no Minecraft game
> files. Players sign in with their own Microsoft account and must own Minecraft.

## What's in the repo

| Path | What it is |
| --- | --- |
| `apps/launcher` | The desktop launcher (Tauri 2 + React + TypeScript, Rust backend). Builds the Windows `.exe`/`.msi`. |
| `apps/client-mod` | **MOTION Client**, the in-game mod (Fabric/Quilt + NeoForge, Minecraft 1.21.1). It adds the HUD modules, the custom crosshair, the Right Shift menu, the Right Ctrl visuals menu, shader effects (colour grading, glow, sharpening), 36 skyboxes, decorative weather and visual profiles. |
| `apps/api` | Node API: Minecraft-verified sign-in, Stripe Premium (server-side only), settings sync, news and releases. It also serves the website. |
| `apps/website` | Static marketing site: home, pricing, download, account, legal. |
| `packages/shared` | Shared API types, plan definitions and validation. |
| `branding` | Logo/icon generator and the original background art generator. |
| `docs` | Setup and reference docs (below). |

## Launcher features

- **Microsoft sign-in.** Microsoft's own sign-in page opens in a window inside the launcher (a code-in-browser option is the fallback), so MOTION never sees your password. Tokens are kept in Windows Credential Manager. Several accounts are supported.
- **My Skin.** Upload skins (file picker or drag and drop, 64×64 and old 64×32), keep a library with favourites and recents, preview them on an interactive 3D character (drag to turn, scroll to zoom, classic/slim, outer layer), edit them in a pixel editor (per part and face, brush/eraser/fill/picker, undo/redo, import/export) and **apply one to your real Minecraft account**. Mojang's skin API takes the skin by URL: the MOTION server serves the PNG for ten minutes and the launcher asks Mojang to use it with your own token. A skin only shows as applied once Mojang confirms it.
- **Capes, wings and auras.** Pick a cape, wings and an aura (Visuals). The owner publishes them on the admin page; MOTION Client shows them in game to every MOTION player (Minecraft 1.21.11). Capes move smoothly: they hang still when you stand and drift back gently as you move.
- **In-game background.** Replace the title-screen panorama, pause/option menu backgrounds and loading screens with fully red, fully black, fully white, Motion green or your own image (Visuals → In-game background; Minecraft 1.21.11).
- **Installations.** Vanilla, Fabric, Quilt, Forge and NeoForge. Each gets its own folder, Java, memory and JVM arguments, and can be duplicated.
- **Mods and content.**
  - Browse Modrinth for mods, modpacks (`.mrpack`), resource packs and shaders, with dependency resolution.
  - Add your own `.jar`/`.zip` files by drag and drop.
  - Enable and disable items, update them, and get compatibility warnings.
  - Removed files go to the Recycle Bin. Nothing is overwritten silently.
- **Performance.**
  - Reversible presets that edit `options.txt`. A backup is made before every change.
  - Recommended performance mods.
  - Stats measured from the running game (memory, CPU, launch time; GC pauses from the game's GC log). MOTION never estimates or invents numbers.
- **Customize.**
  - Five themes (Crimson, Obsidian, Ash, Ember, Monochrome) with original crimson-forest art, an **accent intensity** slider (flat to vivid), your own background image (also used on the loading screen), rising embers and Reduce motion.
  - MOTION Client setup: install or update it, then pick a profile, skybox, weather, menu theme and menu key. You see a live preview first.
- **Premium (optional).**
  - Stripe Checkout runs on the server. Premium is unlocked **only** by verified webhooks, never by the client saying it paid.
  - Adds cloud sync, the Competitive and Extreme FPS presets, advanced analytics (heap, GC pauses, stutter) and any accent colour. Launching, mods, themes and MOTION Client are free.
- **Crash Doctor.** When Minecraft crashes, MOTION reads the game's output and explains the cause in plain words. It recognises a missing or incompatible mod, a mod built for another Minecraft version, a mixin failure, running out of memory, the wrong Java version, damaged files, the GPU driver, the shader pack, and the mods the crash report suspects. Where it can, it offers a one-click fix: more RAM, turn off the culprit, get the missing mod, repair, use MOTION's Java, or open the shader packs.
- **Worlds & servers.** Each installation lists your real worlds (name, icon, game mode, last played) and the servers from Minecraft's multiplayer list. **Play** opens a world and **Join** connects to a server straight from the launcher (Quick Play on 1.20+). The Home screen shows the servers and worlds you opened recently under **Jump back in**.
- **Command palette.** Press **Ctrl+K** anywhere to play any installation, rejoin a server or world, jump to any page, repair, or check for updates.
- **Automatic updates.** New versions download in the background and install by themselves when nobody is playing (15-second countdown with "Later"). Releases are one command: `npm run release -- x.y.z "notes"` then `git push --follow-tags`. See [docs/RELEASE.md](docs/RELEASE.md).

- **MOTION Mods page.** Every built-in module in one place, per installation:
  - **HUD:** FPS, frame time, memory, coordinates, direction, keystrokes, CPS, armor, potion effects, clock & session timer, held item, sprint & sneak status, info panel (ping, server, day, time), and a custom crosshair with a live preview.
  - **Performance:** Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling and ModernFix. Switching one on installs the best compatible version from Modrinth (with its required libraries); switching it off renames the file to `.disabled`.
  - **Visuals** and **Utility:** block outline, low fire, skybox, ambient particles, Minecraft's Toggle Sprint/Sneak, widget backgrounds and more.
  - Categories, search, compatibility labels, and **Performance**, **Minimal HUD** and **Full HUD** presets. MOTION Client's settings are backed up before each preset and can be restored from **Backups**.
- **Dashboard.** Play button, quick version switching, allocated RAM, Java version, game-file status, installed-mods overview (with the core performance mods), recently played, quick access, launcher update status and news.

## MOTION Client features (in game)

- **Right Ctrl visuals menu.** Presets (Blood Red, Cinematic, Clean PvP, Performance, Vanilla), skybox picker, real GLSL shader effects, fog and performance controls, docked beside the world for live preview. See `docs/VISUALS.md`.
- **Right Shift menu.** Ten categories, search, every kind of control, per-setting and per-page reset, autosave, and five crimson menu themes.
- **Skyboxes.**
  - Eleven built-ins, including the default Crimson Dusk and Blood Moon. You can also import your own: 3×2 atlases, 360° panoramas, or OptiFine sky packs.
  - Day/night crossfade.
- **Decorative weather.** Rain, snow, petals (crimson by default), leaves, fireflies and dust, all moved by wind. Includes presets, distance culling and an adaptive particle budget. Effects are client-side visuals only.
- **HUD editor.** Thirteen widgets: FPS, frame time, memory, coordinates (with facing, dimension, biome), direction, keystrokes, CPS, armor (numbers, % or bars), potion effects (compact/expanded, hide by type or one by one), clock & session, held item, sprint & sneak, and an info panel. Drag them into place, snap them to guides, scale them and save layouts. A failing widget turns itself off without affecting the others.
- **Hit effects.** Whatever you hit gets a glowing shockwave ring, a slash trail and sparks in your colour (or rainbow). Swords **spin** when you swing in first person (Spin, Flip or Whirl). It's all visual; hits, reach and damage are unchanged. Settings are on the **Combat** page.
- **Health indicators and name tags.** Hearts, a number or a bar above players you can see (never through walls), and your own name tag in third person.
- **Waypoints.** Press **B** to mark where you stand. Markers show the name and distance, with arrows at the screen edge when a waypoint is behind you. A death waypoint is added automatically and clears when you reach it. Waypoints are saved per server and world.
- **Zoom.** Hold **C** to zoom (4× by default). Scroll while zoomed to zoom further. Smooth easing, an optional cinematic camera, and toggle mode are under General → Zoom.
- **Custom crosshair.** Cross, cross + dot, T, dot or square; size, gap, thickness, colour, opacity and outline.
- **Profiles.** Eight built-in looks. You can also make your own, import and export them as files or `BLOOM1:` share codes, and switch profiles automatically by biome.
- **Accessibility and performance.** Reduced motion and a performance mode. The low-FPS advisor *suggests* changes but never makes them.
- **Compatibility.** Sodium and Iris are detected. While a shader pack is active, custom sky and fog are skipped, and the menu explains why.

See [docs/BLOOM_CLIENT.md](docs/BLOOM_CLIENT.md) for the full reference.

## Quick start (development)

Requirements: Node 22.12+, Rust stable, and the Tauri 2 prerequisites for Windows (WebView2, MSVC build tools). The mod also needs JDK 21.

```bash
npm install
cp apps/launcher/.env.example apps/launcher/.env   # set VITE_MS_CLIENT_ID (docs/MICROSOFT_AUTH.md)
npm run dev                                        # launcher in dev mode

cp apps/api/.env.example apps/api/.env             # Stripe test keys etc. (docs/STRIPE.md)
npm run dev:api                                    # API + website on http://localhost:8787

cd apps/client-mod && ./gradlew build              # MOTION Client jars + core tests
node tools/bundle.mjs                              # put them into the launcher bundle
```

Tests:

```bash
npm run typecheck && npm test                      # TypeScript (shared, api, launcher)
npm run test:rust                                  # Rust backend
cd apps/client-mod && ./gradlew :common:test       # MOTION Client core
```

## Building the Windows installer

Builds run in GitHub Actions (`.github/workflows/ci.yml` for unsigned CI builds, `release.yml` for signed releases):

1. **Build the mod.** An Ubuntu job builds the MOTION Client jars and runs their tests.
2. **Bundle it.** The Windows job runs `tools/bundle.mjs` to copy the jars and a `manifest.json` (with sha1 hashes) into `apps/launcher/src-tauri/resources/bloomclient/`.
3. **Build the installer.** The Windows job then builds the NSIS `.exe` and the `.msi`.

Release signing, the updater keys and the code-signing certificate are covered in [docs/RELEASE.md](docs/RELEASE.md).

## Docs

- [BLOOD_CHANGES.md](docs/BLOOD_CHANGES.md): what changed from Bloom, what was tested, and how to build
- [ARCHITECTURE.md](docs/ARCHITECTURE.md): how the pieces fit together and the security model
- [MICROSOFT_AUTH.md](docs/MICROSOFT_AUTH.md): Azure app registration and Minecraft API approval
- [STRIPE.md](docs/STRIPE.md): products, webhooks, environments
- [MODRINTH.md](docs/MODRINTH.md): API use, User-Agent, rate limits
- [RELEASE.md](docs/RELEASE.md): versioning, signing, auto-update
- [BLOOM_CLIENT.md](docs/BLOOM_CLIENT.md): the in-game mod, settings file format, compatibility matrix
- [CUSTOM_SKYBOXES.md](docs/CUSTOM_SKYBOXES.md): making and importing skyboxes

## License

The MOTION Client mod is under `apps/client-mod/LICENSE`. Minecraft is a trademark of Mojang Studios.
