# Bloom → BLOOD: what changed

This build turns the Bloom launcher and Bloom Client into **BLOOD** and **BLOOD Client**. Every
existing feature is kept (accounts, installations, the Modrinth browser, performance presets,
skyboxes, weather, profiles, Premium).

## Launcher (`apps/launcher`)

- **Identity.** BLOOD name everywhere, a pixel blood-drop mark, a blocky slanted wordmark, new app
  icons (`branding/make_icons.py`) and crimson-forest background art for every theme
  (`branding/tools/gen_backgrounds.py`). The app id is now `com.bloodlauncher.desktop`, so
  BLOOD keeps its own data folder and can be installed next to Bloom. The exe is `BLOOD.exe`.
- **Themes.** Crimson (default), Obsidian, Ash, Ember and Monochrome. Old theme ids migrate to the
  nearest new one.
- **Accent intensity** (Settings → Appearance): mixes the accent towards a muted tone and scales
  every glow, from 0% (flat) to 100% (vivid).
- **Loading screen.** Theme art, mark, wordmark and a loading bar. If you choose your own background
  image (Settings → Appearance → Background → My image), the loading screen uses it too.
- **Dashboard.** Clickable version chip for switching versions, BLOOD Mods / Mods / Optimize
  actions, allocated RAM (of total), Java version and source, a game-files status, quick access
  tiles, an installed-mods overview with the core performance mods, and launcher update status.
- **BLOOD Mods page** (new, sidebar). Per installation:
  - HUD modules (13 widgets plus the custom crosshair), each with its options, scale and a live
    crosshair preview, written to BLOOD Client's `settings.json` (applied live while playing).
  - Performance mods (Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling, ModernFix):
    on = re-enable or install the best compatible Modrinth version with dependencies; off = rename
    to `.disabled`.
  - Visual and utility switches, including Minecraft's own Toggle Sprint / Toggle Sneak.
  - Categories, search, compatibility labels, and Performance / Minimal HUD / Full HUD presets.
    BLOOD Client's settings are backed up before each preset (`<instance>/.bloom/client-backups/`)
    and can be restored from **Backups**.
- **Pre-launch check.** Play stops with a clear message if the mods folder has problems that would
  crash the game (missing dependencies, wrong loader or version, known conflicts), with a
  **Launch anyway** option.
- Settings explain which changes apply right away and which apply on the next game start.

## Halloween update

- **8 Halloween themes:** Blood Moon, Pumpkin Night, Haunted Forest, Midnight Halloween, Ghostfire,
  Spiderweb, Crimson Eclipse and Classic BLOOD, with backdrop decorations and recommended skies.
- **9 procedurally generated 4K skies** (1024 px faces).
- **Sky browser:** live rotatable preview, favourites, search, filters, Try in world, Restore vanilla.
- **Imports:** cubemap crosses and six-face sets, with validation, plus replacing an imported image.
- **Quality and transitions:** four skybox quality levels with memory/GPU fallbacks, cache size,
  adjustable fade, and turning the sky off now fades.

## Visuals update

- **Right Ctrl visuals menu**: a compact, docked crimson menu with Visuals, World, Performance and General sections.
- **Shader effects**: real GLSL post-processing (colour grading, glow, sharpening, vignette, weather mood), with quality levels.
- **25 new built-in skies** (CC0 "Cloudy Skyboxes" by Screaming Brain Studios), converted seamlessly.
- **Presets**: Blood Red, Cinematic, Clean PvP, Performance, Vanilla.
- **Fog**: master switch, smooth transitions, Nether and End fog tints.
- **Fixes**: skybox faces are now inset by exactly half a texel (no faint seams), and sky textures and
  shaders now refresh on F3+T / resource pack changes.

See `docs/VISUALS.md`.

## Premium keys (1.0.1)

- **Redeem a key** on the dashboard or the Premium page to unlock Premium on that PC. Each key works
  on one PC; the owner can move it to another PC once every 24 hours. Admin page at `/admin.html`
  to create, list, disable and reset keys. See `docs/LICENSE_KEYS.md`.

## BLOOD Client (`apps/client-mod`, version 0.2.0)

- Menu themes: Crimson (default), Obsidian, Ash, Ember, Monochrome.
- New default look: the **Blood Moon** profile with two new skyboxes, **Crimson Dusk** (day) and
  **Blood Moon** (night), crimson petals and red haze. The old pink look is the Cherry Blossom
  profile.
- New HUD widgets: **Clock & session**, **Held item**, **Sprint & sneak**, **Info panel** (ping,
  server, day, time).
- New options: coordinates (facing, dimension), armor (durability bars), potion effects (show level,
  hide beneficial/harmful/infinite, and hide individual effects).
- **Custom crosshair** (HUD → Crosshair): style, size, gap, thickness, colour, opacity, outline.
- The mod id, package names and config folder are still `bloomclient` / `dev.bloom.client`, so
  existing settings files and key bindings keep working.

## How this was checked

- Launcher unit tests: 59 passing (plus shared 2, API 13), including new tests for HUD-block editing
  and presets.
- BLOOD Client core tests (pure Java, no Minecraft): 60 passing.
- The launcher UI was built and rendered against a mocked backend, and every page was checked in
  screenshots. The BLOOD Mods toggles were checked to write the expected `settings.json`.
- **Not yet checked:** a real Gradle build of the mod against Minecraft, a real Tauri/Rust build and
  launching the game. That needs the Fabric/NeoForge Maven servers and crates.io (see below). Build
  first, then check these in game:
  - the crosshair mixin (`GuiMixin` → `Gui.renderCrosshair`; optional, so if it doesn't match, the
    vanilla crosshair stays);
  - the four new widgets on Fabric and NeoForge;
  - the Full HUD layout at your GUI scale.

## Build

```bash
npm install
cd apps/client-mod && ./gradlew build && node tools/bundle.mjs && cd ../..
npm run build            # Windows: produces BLOOD_1.0.0_x64-setup.exe and the .msi
```

Or push to GitHub and let `.github/workflows/ci.yml` build the unsigned installer.
