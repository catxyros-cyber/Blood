
## Capes (1.0.5)
- The owner uploads capes on the website admin page (name, PNG 64×32 up to 1024×512, free or premium-only), and can hide, re-price or delete them.
- Players pick a cape in the launcher (Customize → Cape). The launcher tells the game the BLOOD server address.
- In game, BLOOD looks up nearby players' capes in batches (cached 5 min) and draws them with Minecraft's own cape and elytra renderers. Only BLOOD users see them. Toggle: General → Capes → "BLOOD capes".

## Tags & online status
- Admin page → Tags: give any BLOOD player a tag (text + colour), edit or remove it. Shows who is online/playing.
- In game, BLOOD users see "[TAG] name" above heads and in the Tab list. Toggle: General → Capes → "BLOOD tags".
- The launcher checks in once a minute while open; its Home page shows the "BLOOD team" with Online / Playing / Offline.

## Minecraft 1.21.11 + quality update
- **BLOOD Client for Minecraft 1.21.11 (Fabric/Quilt)** in `mc1.21.11/`: its own Gradle build (Loom 1.18, Gradle 9) that compiles the shared `common/.../core` sources and resources from the 1.21.1 build, with the Minecraft-facing code ported to 1.21.11's GPU API (render pipelines, render passes, 2D GUI transforms, input events, Identifier).
  - Sky, skyboxes, stars, glow, horizon and haze draw with BLOOD's own pipelines (`shaders/core/bloom_*`) at the end of Minecraft's sky pass.
  - Shader effects are BLOOD's own full-screen passes (`shaders/post/bloom_*`); the slider values travel in Minecraft's per-draw uniform block, so they still change live.
  - Fog, clouds, weather, block outline (camera-facing strips), particles, HUD, menus, capes and tags are all ported. Not on 1.21.11 yet: the lowered fire overlay and the sky colour tint.
  - The launcher bundles every Minecraft version's jar (`manifest.json` lists the version per file) and installs the matching one; "Create Fabric installation" now uses the newest version.
  - CI builds it in its own job; a failure there never blocks a release (that release just ships without 1.21.11).
- Skies stay 4K (download size); the generator can render 8K (`--face 2048`) and 8K sources aren't kept in memory.
- **Sharper menus**: GUI sprites and icons are stored at 4x (`tools/gen_textures.py`), and sky thumbnails are 512×288 (`tools/gen_sky_thumbnails.py`).
- **Stronger shader effects**: contrast-adaptive sharpening, screen-blended glow (no blown-out whites), vibrance-style saturation, and a third, wider blur round at High quality.

## New built-in skies
- Removed the 20 skies BLOOD generated itself (11 procedural + 9 Halloween) and their generators.
- Added 33 skies from the "Fantasy Skybox FREE" pack (supplied by the owner), converted by `tools/import_fantasy_skies.py`: 9 classic cubemaps and 4 panorama sets (day, night, sunrise, sunset, rain, snow). Sun-less/moon-less versions are used so BLOOD's own sun and moon don't double up; sunrise/sunset skies hide BLOOD's sun.
- Old sky ids in settings and profiles are mapped to the closest new sky (`SkyboxCatalog.migrate`, launcher `migrateSky`). New defaults: Crimson Sunset by day, Crimson Night at night.
