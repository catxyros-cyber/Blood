# Custom skyboxes

MOTION Client draws skyboxes as a **cube**: six square faces packed into one PNG in a 3×2
grid. This is the same layout OptiFine/MCPatcher sky layers and FabricSkyBoxes'
"square-textured" skies use:

```
+--------+--------+--------+
| bottom |  top   | south  |
+--------+--------+--------+
|  west  | north  |  east  |
+--------+--------+--------+
```

- The image must be exactly **3 faces wide and 2 tall**, for example 1536×1024 (512 px faces) or 3072×2048 (1024 px faces). Faces can be 16–2048 px.
- Files must be PNG, at most 32 MB and at most 8192 px on a side.
- **360° panoramas** (equirectangular, exactly twice as wide as tall, e.g. 4096×2048) are converted to a cube automatically. Face size is a quarter of the width, at most 1024 px. The original is kept in `skyboxes/originals/`.
- Transparency is respected: transparent parts let the vanilla sky (or the night skybox) show through.

## Three ways to add one

1. **In game.** Open **Right Shift → Skyboxes → Import…** and pick a PNG, or drop the file onto the menu.
2. **In the launcher.** Use **Customize → Skybox → Import image**. It checks the size first and then copies the file into the installation's `config/bloomclient/skyboxes/`.
3. **OptiFine sky pack.** In game, use **Skyboxes → Import OptiFine pack** and choose a resource-pack `.zip` or folder.
   - **What's read:** MOTION reads `assets/minecraft/optifine/sky/world0/sky*.properties` (and the older `mcpatcher/` path). Each layer becomes one skybox, with blend mode, rotation, speed and axis.
   - **Day or night:** fade times decide whether a layer is suggested for day or night.
   - **Not supported:** `weathers`, `biomes`, `heights` and `transition`. The import tells you which options were ignored.
   - **Only text and PNG are read:** nothing in a pack is run.

You can also drop PNGs straight into `config/bloomclient/skyboxes/`. MOTION picks them up the next time the folder is scanned (at start-up and after an import). Files with the wrong size are skipped with a log message and left untouched.

## Optional settings file

Next to `my_sky.png` you can put `my_sky.json`:

```json
{
  "format": 1,
  "name": "My Sky",
  "blend": "alpha",
  "rotate": false,
  "speed": 1.0,
  "axis": [0, 0, 1],
  "suggested": "night",
  "tint": "#8899AA",
  "origin": "my_sky.png"
}
```

| Key | Meaning |
| --- | --- |
| `name` | Name shown in the menu (32 characters max; formatting codes removed). |
| `blend` | `alpha` (normal), `add`, `screen` or `dodge` (additive, good for stars/aurora overlays). Anything else falls back to normal. |
| `rotate`, `speed`, `axis` | Rotate with the time of day around `axis`, `speed` times per day. |
| `suggested` | `day`, `night` or `any`. This is only a hint in the menu. |
| `tint` | Colour used for the menu swatch. |

## Day, night and the crossfade

- **Choosing skyboxes.** Pick a **Daytime skybox** and a **Night skybox**. If both are the same, one skybox is shown all day.
- **Daylight.** MOTION follows the vanilla daylight curve: `cos(time) * 2 + 0.5`, clamped to 0–1.
- **Transition intensity.** This slider narrows or widens the smoothstep window around sunrise and sunset. 0% gives long, gentle transitions; 100% switches quickly.
- **How the blend works.**
  - The night skybox is drawn first and the day skybox on top.
  - Each one's alpha is chosen so that the combined coverage equals the opacity you set ("Daytime appearance" / "Night-time appearance"). That way the vanilla sky only shows through as much as you allow, and there's no bright or dark "seam" mid-transition.
- **Weather.** Rain dims the skybox, like the vanilla sky.
- **Profile switches.** With **Smooth transitions** on, skyboxes crossfade over a short time.

## Shaders and other sky mods

- **Shader packs.** While an Iris shader pack is active, the shader draws its own sky, so MOTION pauses its skybox, sky tint and fog. You can override this under **Skyboxes → Compatibility → Use with shader packs**, but expect odd results with some packs.
- **Other sky mods.** If FabricSkyBoxes or Nuit has a skybox active, it replaces MOTION's. Disable one of them.
