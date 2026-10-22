# MOTION Client visuals: the Right Ctrl menu, shader effects and skies

## The Right Ctrl menu

Press **Right Ctrl** in game. A compact menu opens, docked to the left side of the screen (change it
under General → Menu position), so you can watch every change live in the world. Press Right Ctrl
again, or Esc, to close it.

- **Tab / Shift+Tab** switch sections.
- Type anywhere to search every MOTION setting.
- Rebind the key under General (or in Minecraft's Controls).
- The full menu is still on **Right Shift**. Both menus show the same settings, and changes apply
  immediately and are saved automatically.

| Section | What's in it |
| --- | --- |
| **Skyboxes** | The sky browser: live preview, search, filters, favourites, apply, Try in world, Restore vanilla, imports |
| **Themes** | The 12 menu themes (8 Halloween), decorations, accent |
| **Visuals** | Presets, skybox picker (day and night), sky look, shader effects (colour grading, glow, sharpen, vignette), weather visuals |
| **World** | Sky colour and horizon, fog and atmosphere, Nether/End fog tints, which dimensions get shader effects, visual time of day, smooth transitions |
| **Performance** | Shader quality, Performance-friendly mode, skybox resolution, particle quality, automatic quality |
| **General** | All visuals on/off, Save now, Save as preset, Load preset, Restore defaults, menu options and keys |

A live indicator in the header shows what the shaders are doing right now ("Colour grading",
"Grading + glow" or "Shaders off").

## Shader effects (what they really are)

These are **real GLSL post-processing passes** that run on your GPU through Minecraft's own
post-processing system (`PostChain`), right after the world and your hand are drawn and before the
HUD. The HUD and menus are never affected.

| Effect | How it works |
| --- | --- |
| Brightness | Exposure in stops (+1 doubles the light). |
| Contrast, Saturation | Around mid-grey / per pixel luminance. |
| Colour temperature | White balance: warmer (orange) or cooler (blue). |
| Colour filter | A brightness-preserving colour filter, mostly on shadows and mid-tones. |
| Soft highlights | Film-like roll-off instead of clipping bright areas to flat white. |
| Glow | Bright parts are extracted into a small buffer (320×180 or 480×270), blurred with a separable Gaussian and added back. |
| Sharpen | 5-tap unsharp mask, clamped so it doesn't create halos. |
| Vignette | Darkens the corners, corrected for aspect ratio. |
| Weather mood | Rain/thunder make the grade cooler, greyer and flatter, following the actual rain strength. |

The shader files are in `apps/client-mod/common/src/main/resources/assets/minecraft/shaders/program/bloodclient_*`
and the passes in `assets/bloomclient/shaders/post/*.json`.

**What it is not:** this isn't a shader pack like Iris/OptiFine packs. It doesn't add shadows,
reflections or volumetric light, because those need to change how every block is drawn. It grades
and enhances the finished image, which is cheap and works with Sodium.

**Performance:**
- With all values neutral the pass is skipped entirely, so it costs nothing.
- Grading alone is one full-screen pass.
- Glow uses small fixed-size buffers, so its cost doesn't grow with your resolution.
- After 10 seconds unused, the GPU buffers are released.

**Shader packs:** while an Iris shader pack is on, MOTION's sky, fog and shader effects pause (the pack
does its own). Override this under Skyboxes → Compatibility → Use with shader packs.

**If a shader can't load** (very old drivers, or a resource pack replacing it), the error is
shown under the "Shader effects" switch and in the log, and the game carries on without it.

## Presets

| Preset | Look |
| --- | --- |
| **Blood Red** | Crimson dusk sky, blood moon, darker crimson grade with glow and vignette, red Nether fog. |
| **Cinematic** | Amber Dusk / Violet Night skies, softer warm colours, stronger glow (high quality), vignette, haze. |
| **Clean PvP** | No skybox or particles, punchier colours, light sharpening, thinner fog, clear block outline. |
| **Performance** | Plain sky, no particles, glow, stars or clouds, half-size sky textures, grading off. |
| **Vanilla** | All of MOTION's world visuals off. The HUD still works. |

Click a preset to apply it, then change anything you like: your edits are saved into it (the card
shows **EDITED**). Click the active edited preset to restore its original look.
**Save as preset…** saves the current look as your own preset. The other built-in looks (Cherry
Blossom, Snowfall, …) and per-biome switching are under Right Shift → Profiles.

## The sky browser (Right Ctrl → Skyboxes)

- **Live preview.** A large view of the selected sky, drawn on the CPU from the same texture the
  game uses, with the same face mapping and your Sky look colours. It turns slowly on its own; drag
  to look around, and click to stop or start the turning. Switching skies crossfades. Fog and shader
  effects are added on top in the world, so **Try in world** shows the sky in the real world straight
  away, without saving it. It ends when you close the menu or apply something.
- **Cards** show the preview, name and **real resolution** ("4K · 1024 px faces"), DAY/NIGHT badges,
  and a star for favourites. Favourites are listed first.
- **Filters:** All, Favourites, Halloween, MOTION, Cloudy, Yours, plus search by name or description.
- **Apply:** Use day & night, By day, At night, or **Restore vanilla** (fades back to Minecraft's sky).
- A **Matches <theme>** badge shows when a sky is the current theme's recommended sky.

### Importing your own skies

| Format | What happens |
| --- | --- |
| 3×2 atlas PNG (e.g. 3072×2048) | Used as it is. |
| 360° panorama PNG (2:1, e.g. 4096×2048) | Converted to a cube. |
| Cubemap cross PNG (4:3, e.g. 4096×3072) | Converted to a cube. |
| Six face PNGs in a folder or .zip | Named px/nx/py/ny/pz/nz, posx…, or right/left/top/bottom/front/back. |

- Faces can be 16–2048 px; files can be up to 32 MB.
- Everything is checked before anything is saved: missing faces, non-square or mismatched faces,
  wrong proportions and non-PNG files each get a clear message, and nothing is imported.
- Imported skies can be renamed, favourited, **replaced** (new image, same name and settings) and
  removed (moved to `skyboxes/deleted/`, so you can get them back).
- **Load custom skyboxes** (Performance) hides them and stops them loading.

## The 4K Halloween skies

Blood Moon, Halloween Night, Pumpkin Sky, Haunted Forest, Midnight Purple, Ghostfire Sky, Eclipse,
Blood Storm and Monochrome Night.

- **What they are:** original skies **generated procedurally** by
  `apps/client-mod/tools/gen_halloween_skies.py`, not painted or photographed.
  - Moons have craters, maria, limb darkening and an anti-aliased edge.
  - Stars sit on a jittered grid, so there's no repeating pattern, and get brighter in a
    natural power-law spread.
  - Clouds are 8-octave noise, and forests are three layers of silhouettes with mist.
- **Resolution:** each is a 3072×2048 atlas with **1024 px faces**, the same detail per degree as a
  4096×2048 ("4K") panorama. They're stored at that size (no upscaling), and the menu shows the
  real size.
- **No seams or distortion:** every pixel is computed from its own view direction, with edge texels
  on the cube edges, so faces meet without seams and moons stay round.
- **Clean gradients and moons:** the images are dithered against banding. Their moon is painted in,
  so MOTION's extra sun and moon are skipped on these skies.
- **About 3.5–4.5 MB each** as PNG, and about 24 MB of video memory each at High.

## Themes

Classic MOTION, Blood Moon, Pumpkin Night, Haunted Forest, Midnight Halloween, Ghostfire, Spiderweb and
Crimson Eclipse, plus Obsidian, Ash, Ember and Monochrome.

- Each theme sets the menu palette and accent, and draws a light backdrop decoration: embers, a
  blood moon, pumpkins, mist, bats, ghostly wisps, corner webs or an eclipse.
  - Turn decorations off with **Theme decorations**. They also stop with Reduce motion or reduced
    interface effects.
- Each Halloween theme recommends a sky, which you're **asked** about after picking the theme. Your
  own sky choice is never changed unless you say yes.

## Skybox quality and memory (Performance)

| Quality | Largest face | Video memory per sky |
| --- | --- | --- |
| Performance | 256 px | about 1.5 MB |
| Balanced | 512 px | about 6 MB |
| High (default) | 1024 px (full 4K for the Halloween set) | about 24 MB |
| Ultra | up to 2048 px (your own 8K imports) | about 96 MB |

- **Shrinking:** larger skies are shrunk face by face (area average, no bleeding between faces) on a
  background thread.
- **Fallbacks:** if Minecraft has less than 2 GB of memory, or the graphics card can't take the
  texture size, MOTION lowers the size and says why on the Performance page.
- **Skybox cache** (1–6) sets how many skies stay loaded for instant switching; older ones are released.
- **Skybox switch fade** (0–5 s) sets how long sky changes crossfade. 0 switches instantly.

## The 25 cloudy skies

"Cloudy Skyboxes" by Screaming Brain Studios, released as **CC0 / public domain**
(`textures/skybox/SBS_CLOUDY_SKYBOXES_LICENSE.txt`). They're built into the mod as 3×2 cube atlases
with 512 px faces, so there's nothing to install. Pick them in the Visuals section (Day or Night).

`apps/client-mod/tools/import_sbs_skies.py` converted them from the equirectangular versions using the
same mapping the mod uses for imported panoramas:
- Each face pixel is sampled from its true direction, and edge texels sit exactly on the cube edges.
  The renderer insets every face by exactly half a texel, so neighbouring faces meet with no seam.
- Checked: the colour error along every cube edge (≈0.3/255) is no larger than in the middle of a
  face, i.e. below one 8-bit step.

## Settings file

All options are normal MOTION settings in `config/bloomclient/settings.json` (keys `post.*`,
`world.fog*`, `world.netherFog*`, `world.endFog*`, `perf.postQuality`, `perf.lite`,
`perf.skyQuality`, `ui.visualsPosition`, `ui.visualsDim`). Presets are profiles in
`config/bloomclient/profiles/`. The Right Ctrl key is a Minecraft key binding, so it's saved in
`options.txt`.
