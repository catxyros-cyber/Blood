
## Capes (1.0.5)
- The owner uploads capes on the website admin page (name, PNG 64×32 up to 1024×512, free or premium-only), and can hide, re-price or delete them.
- Players pick a cape in the launcher (Customize → Cape). The launcher tells the game the MOTION server address.
- In game, MOTION looks up nearby players' capes in batches (cached 5 min) and draws them with Minecraft's own cape and elytra renderers. Only MOTION users see them. Toggle: General → Capes → "MOTION capes".

## Tags & online status
- Admin page → Tags: give any MOTION player a tag (text + colour), edit or remove it. Shows who is online/playing.
- In game, MOTION users see "[TAG] name" above heads and in the Tab list. Toggle: General → Capes → "MOTION tags".
- The launcher checks in once a minute while open; its Home page shows the "MOTION team" with Online / Playing / Offline.

## Minecraft 1.21.11 + quality update
- **MOTION Client for Minecraft 1.21.11 (Fabric/Quilt)** in `mc1.21.11/`: its own Gradle build (Loom 1.18, Gradle 9) that compiles the shared `common/.../core` sources and resources from the 1.21.1 build, with the Minecraft-facing code ported to 1.21.11's GPU API (render pipelines, render passes, 2D GUI transforms, input events, Identifier).
  - Sky, skyboxes, stars, glow, horizon and haze draw with MOTION's own pipelines (`shaders/core/bloom_*`) at the end of Minecraft's sky pass.
  - Shader effects are MOTION's own full-screen passes (`shaders/post/bloom_*`); the slider values travel in Minecraft's per-draw uniform block, so they still change live.
  - Fog, clouds, weather, block outline (camera-facing strips), particles, HUD, menus, capes and tags are all ported. Not on 1.21.11 yet: the lowered fire overlay and the sky colour tint.
  - The launcher bundles every Minecraft version's jar (`manifest.json` lists the version per file) and installs the matching one; "Create Fabric installation" now uses the newest version.
  - CI builds it in its own job; a failure there never blocks a release (that release just ships without 1.21.11).
- Skies stay 4K (download size); the generator can render 8K (`--face 2048`) and 8K sources aren't kept in memory.
- **Sharper menus**: GUI sprites and icons are stored at 4x (`tools/gen_textures.py`), and sky thumbnails are 512×288 (`tools/gen_sky_thumbnails.py`).
- **Stronger shader effects**: contrast-adaptive sharpening, screen-blended glow (no blown-out whites), vibrance-style saturation, and a third, wider blur round at High quality.

## New built-in skies
- Removed the 20 skies MOTION generated itself (11 procedural + 9 Halloween) and their generators.
- Added 33 skies from the "Fantasy Skybox FREE" pack (supplied by the owner), converted by `tools/import_fantasy_skies.py`: 9 classic cubemaps and 4 panorama sets (day, night, sunrise, sunset, rain, snow). Sun-less/moon-less versions are used so MOTION's own sun and moon don't double up; sunrise/sunset skies hide MOTION's sun.
- Old sky ids in settings and profiles are mapped to the closest new sky (`SkyboxCatalog.migrate`, launcher `migrateSky`). New defaults: Crimson Sunset by day, Crimson Night at night.

## Animated capes
- Admin page takes PNG, JPG, WebP and animated GIF/WebP (converted in the browser into a vertical PNG strip, up to 64 frames, 4 MB), or a ready-made PNG strip with a frame time.
- Server stores `frame_ms` (migration 005) and sends it as `X-Cape-Frame-Ms`; frames come from the image size (frame = width × width/2).
- MOTION Client (1.21.1 and 1.21.11) cuts the strip into one texture per frame and plays it on the cape and elytra; the launcher preview animates too.
