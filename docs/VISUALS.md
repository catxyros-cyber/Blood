# BLOOD Client visuals: the Right Ctrl menu, shader effects and skies

## The Right Ctrl menu

Press **Right Ctrl** in game. A compact menu opens, docked to the left side of the screen (change it
under General → Menu position), so you can watch every change live in the world. Press Right Ctrl
again, or Esc, to close it.

- **Tab / Shift+Tab** switch sections.
- Type anywhere to search every BLOOD setting.
- Rebind the key under General (or in Minecraft's Controls).
- The full menu is still on **Right Shift**. Both menus show the same settings, and changes apply
  immediately and are saved automatically.

| Section | What's in it |
| --- | --- |
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

**Shader packs:** while an Iris shader pack is on, BLOOD's sky, fog and shader effects pause (the pack
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
| **Vanilla** | All of BLOOD's world visuals off. The HUD still works. |

Click a preset to apply it, then change anything you like: your edits are saved into it (the card
shows **EDITED**). Click the active edited preset to restore its original look.
**Save as preset…** saves the current look as your own preset. The other built-in looks (Cherry
Blossom, Snowfall, …) and per-biome switching are under Right Shift → Profiles.

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

All options are normal BLOOD settings in `config/bloomclient/settings.json` (keys `post.*`,
`world.fog*`, `world.netherFog*`, `world.endFog*`, `perf.postQuality`, `perf.lite`,
`perf.skyQuality`, `ui.visualsPosition`, `ui.visualsDim`). Presets are profiles in
`config/bloomclient/profiles/`. The Right Ctrl key is a Minecraft key binding, so it's saved in
`options.txt`.
