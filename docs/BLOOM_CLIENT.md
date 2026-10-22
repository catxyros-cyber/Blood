# MOTION Client

MOTION Client is the in-game half of MOTION: a client-side, purely visual mod. It never
changes gameplay, sends nothing to servers, and does nothing on a dedicated server.
It isn't loaded on servers at all.

## Compatibility matrix

| | Supported | Notes |
| --- | --- | --- |
| Minecraft | **1.21.1** | Other versions: the launcher explains that MOTION Client isn't available yet and offers to create a Fabric 1.21.1 installation. |
| Fabric (Loader ≥ 0.16) | ✅ | Needs **Fabric API**. The launcher installs it from Modrinth if it's missing. |
| Quilt | ✅ | Runs the Fabric build through Quilt's Fabric compatibility (QFAPI/Fabric API required). |
| NeoForge 21.1.x | ✅ | Separate NeoForge build. |
| Forge | ❌ | Not supported. The launcher and docs say so instead of failing silently; use NeoForge or Fabric. |
| Vanilla | ❌ | Mods need a loader. The launcher offers "Create Fabric 1.21.1 installation". |
| Sodium / Embeddium | ✅ | Sky, fog, particles and HUD work alongside it. |
| Iris / Oculus (shader pack **off**) | ✅ | |
| Iris / Oculus (shader pack **on**) | ⚠️ | Shaders draw their own sky and fog, so MOTION pauses its skybox, sky tint, fog and world particles. HUD and menu keep working. Override in Skyboxes → Compatibility. |
| OptiFine via OptiFabric | ❌ | Declared incompatible (`breaks` in fabric.mod.json). Use Sodium + Iris. |
| FabricSkyBoxes / Nuit | ⚠️ | An active FSB skybox replaces MOTION's; turn one off. |
| Particle Rain | ⚠️ | Double weather. Turn off one of the two rain/snow effects. |

**Fail-safe mixins.** If another mod changes a method MOTION hooks, that single feature is skipped instead of crashing the game: `defaultRequire = 0`, and Mixin logs a warning. HUD widgets are isolated, so a widget that throws three times is turned off and marked in the HUD page.

## Controls

| Key | Default | Action |
| --- | --- | --- |
| Open MOTION menu | **Right Shift** | Opens the menu. Change it in Minecraft's Controls, the menu's Keybinds page or the launcher. |
| HUD editor | unbound | Opens the drag-and-drop HUD editor. |
| Toggle MOTION visuals | unbound | Turns all of MOTION's visuals on or off at once. |
| Next profile | unbound | Cycles through visual profiles. |
| Toggle HUD | unbound | Shows or hides MOTION's HUD widgets. |

## The menu

There are ten pages: General, HUD Editor, Skyboxes, Weather & Environment, Particles, Visual Effects, World Appearance, Performance, Profiles, Keybinds.

- **Search.** It finds any setting by name, description or keyword across all pages.
- **Controls.** Toggles, sliders, dropdowns, colour pickers (with hex input), text fields, keybind rows, image pickers.
- **Resetting.** Every setting can be reset individually (↺), and each page has "Reset page".
- **Autosave.** Changes save automatically (debounced) and when the menu closes. There is no Save button to forget.
- **Themes.** Five menu themes (Crimson, Obsidian, Ash, Ember, Monochrome), plus an accent colour and an animation level (Full, Reduced, Performance). Old Bloom theme ids fall back to Crimson.
- **Unavailable settings.** Settings that can't work right now (for example while a shader pack is active) are disabled and show the reason instead of their description. Settings that depend on a toggle are greyed out until it is on.

## Visual features

- **Skyboxes.**
  - Eleven built-ins: Crimson Dusk (default day), Blood Moon (default night), Cherry Blossom Sky, Pink Sunset, Sakura Night, Starry Night, Galaxy, Fantasy Clouds, Aurora, Dreamy Pastel, Clear Blue Sky.
  - Separate day and night choices with a crossfade, plus brightness, saturation, tint, rotation and stars.
  - You can import your own: see [CUSTOM_SKYBOXES.md](CUSTOM_SKYBOXES.md).
- **Weather and environment.** All of these are decorative client-side particles; real weather and gameplay are unchanged.
  - Rain: with the world's weather or always (decorative); density, colour, splashes, and an option to hide vanilla rain.
  - Snow: everywhere, in snowy biomes, or when it's snowing; with intensity, flake look and colour.
  - Petals (crimson by default, any two colours): in chosen biomes or everywhere; with density, speed, size, spin, shapes, and petals that settle on the ground.
  - Leaves, fireflies, dust motes.
  - A shared wind field moves all of them.
- **Quality.** Presets Low, Medium and High, plus Custom. Particles have distance culling, a hard cap and an *adaptive budget* that lowers the particle count when frame time rises.
- **Visual effects.**
  - A block outline with custom colour and width.
  - A sun/moon glow.
  - Fog distance and colour, and a sky tint.
  - Fire overlay offset (lower the on-fire overlay).
  - Visual-only time of day. This is client-side, so the server's time is untouched.
- **HUD.** Thirteen widgets (ids in brackets are used in `settings.json` and by the launcher):
  - FPS `fps`, Frame time `frametime`, Memory `memory` (used / allocated `-Xmx`), Coordinates `coords` (one line, facing, dimension, biome, Nether/Overworld equivalent), Direction `direction`, Keystrokes `keystrokes`, CPS `cps`.
  - Armor `armor`: numbers, percentages or vanilla-style durability bars; vertical or horizontal; optionally the held item.
  - Potion effects `potions`: icons, level, live countdown (ending effects flash), compact or expanded, hide beneficial/harmful/infinite effects, and hide individual effects with the **Hidden effects** setting (`hud.potions.hidden`).
  - Clock & session `clock`: the computer's clock (12/24 h, seconds) and time since you joined the world.
  - Held item `held`: main hand and off hand with durability text and bar.
  - Sprint & sneak `status`: shows sprinting, sneaking (and flying), and whether Minecraft's Toggle Sprint/Sneak is holding it.
  - Info panel `info`: ping (from the server's player list), server address, world day and in-game time.
- **Custom crosshair** (HUD → Crosshair): `crosshair.enabled`, `crosshair.style` (`cross`, `cross_dot`, `t`, `dot`, `square`), `crosshair.size`, `crosshair.gap`, `crosshair.thickness`, `crosshair.color`, `crosshair.opacity`, `crosshair.outline`. It replaces the vanilla crosshair through an optional mixin on `Gui.renderCrosshair`; the vanilla crosshair returns in spectator mode, with the F3 axis crosshair, or if another mod replaces that method. The crosshair-style attack indicator isn't drawn while it's on (Attack Indicator → Hotbar keeps one).
  - **Editing:** drag with snapping guides, resize by dragging a corner or with the mouse wheel, and set per-widget options and colours.
  - **Layouts:** several layouts (Default and PvP built in).
- **Profiles.**
  - Nine built-ins: Blood Moon (default), Cherry Blossom, Competitive, Minimal, Snowfall, Rainy Day, Fantasy, Night Sky, Cinematic. You can also save your own.
  - Import and export them as `.json` files or `BLOOM1:` share codes. Imports contain values only, never code, and are capped at 64 KB.
  - Optional automatic switching by biome group.
  - Built-ins you edit can be reset; deleted profiles go to `profiles/deleted/`.
- **Performance page.**
  - Measured FPS, 1% lows and frame-time graph.
  - The **low-FPS advisor** lists the heaviest MOTION effects that are on when FPS stays low. It only suggests; nothing changes until you click.
  - Performance mode turns off menu animations and decorative extras.

## Files

All files live in the installation's `config/bloomclient/`:

| Path | Contents |
| --- | --- |
| `settings.json` | All settings, the active profile, biome rules and HUD layouts (format below). |
| `profiles/*.json` | Your profiles and edited built-ins. |
| `profiles/deleted/` | Deleted or reset profiles, so you can recover them. |
| `skyboxes/*.png` + `*.json` | Custom skyboxes. `skyboxes/originals/` keeps converted panoramas' originals and `skyboxes/deleted/` holds removed skyboxes. |
| `settings.json.corrupt-<time>` | Created only if `settings.json` couldn't be parsed. The bad file is kept for you and defaults are used. |

## settings.json

```json
{
  "format": 1,
  "revision": 42,
  "updatedAt": "2026-10-20T09:15:00Z",
  "updatedBy": "game",
  "settings": {
    "sky.enabled": true,
    "sky.day": "crimson_dusk",
    "sky.night": "blood_moon",
    "sky.transition": 0.5,
    "petals.enabled": true,
    "perf.quality": "medium",
    "ui.theme": "crimson",
    "outline.color": "#E3192F"
  },
  "profiles": { "active": "blood_moon", "biomeRules": { "snowy": "snowfall" } },
  "hud": {
    "activeLayout": "default",
    "layouts": [
      { "id": "default", "name": "Default", "widgets": {
          "fps": { "visible": true, "anchor": "top_left", "x": 4, "y": 4, "scale": 1.0, "options": { "colorCoded": true } },
          "potions": { "visible": true, "options": { "compact": true, "hideInfinite": true } } } },
      { "id": "pvp", "name": "PvP", "widgets": {} }
    ]
  }
}
```

- **Keys.**
  - `settings` holds flat `category.name` keys with JSON values: booleans, numbers, choice ids, `#RRGGBB` colours and strings.
  - Invalid values are ignored one by one (the default is used, and the rest of the file still loads).
  - Unknown keys, for example from a newer version, are preserved.
- **`revision`** goes up on every write. Writers use a temp file plus rename, so the file is never half-written.
- **Two writers.**
  - **The game** writes `updatedBy: "game"`. It checks the file every ~2 s while running. When the file changed on disk, keys changed in-game since the last save are kept, and everything else takes the file's newer values.
  - **The launcher** writes `updatedBy: "launcher"` and bumps `revision`. To switch profile it sets `profiles.active` and `profiles.apply: true`, which tells the game to apply that profile's values once.
- **HUD block.** Widgets missing from a layout keep their defaults; unknown widget ids are ignored. The launcher's MOTION Mods page edits only the active layout (visibility, options, scale, and positions for presets), merging into what's there.
- **Running games.** The launcher refuses to change the menu key or Toggle Sprint/Sneak while the game runs, because Minecraft rewrites `options.txt` on exit. Other settings (HUD included) can be changed and are picked up live.

## Launcher integration

The launcher's **MOTION Mods** page switches HUD modules, the crosshair and other module settings, applies the Performance / Minimal HUD / Full HUD presets (backing up `settings.json` to the installation's `.bloom/client-backups/` first), and restores backups.

The launcher's **Visuals** page:

- **Install, update or remove.** It installs, updates or removes the MOTION Client jar bundled with the launcher. The jar is checked against the manifest's sha1. Files are never overwritten, and removing moves the jar to the Recycle Bin.
- **Choose the look.** Pick a profile, day and night skybox, weather toggles, quality, menu theme and menu key, with a live preview. You can import a skybox image.
- **Before each launch,** for installations with MOTION Client turned on, it makes sure the bundled version is installed (updating the jar if the launcher has a newer one). If that fails, you get a warning and the game still launches.
- **Removing** moves only the jar to the Recycle Bin; your MOTION Client settings, profiles and skyboxes stay.

## Building

```bash
cd apps/client-mod
./gradlew build                 # fabric/build/libs and neoforge/build/libs, runs :common:test
./gradlew :fabric:runClient     # dev client (Fabric)
./gradlew :neoforge:runClient   # dev client (NeoForge)
node tools/bundle.mjs           # copy jars + manifest into the launcher bundle
python3 tools/gen_skyboxes.py   # regenerate the built-in skybox art (original, procedural)
```

All skybox, petal and GUI art is original and generated by the scripts in `tools/`. No Mojang or third-party assets are included.
