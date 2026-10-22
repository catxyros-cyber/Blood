#!/usr/bin/env python3
"""
Turns the "Fantasy Skybox FREE" pack (cubemap crosses in Materials/Classic, 2:1 panoramas in
Materials/FS0xx) into BLOOD Client built-in skyboxes, the same way tools/import_sbs_skies.py does:

    common/src/main/resources/assets/bloomclient/textures/skybox/<id>.png          3x2 cube atlas
    common/src/main/resources/assets/bloomclient/textures/gui/sky_preview/<id>.png  512x288 card
    ../launcher/public/bloomclient/sky/<id>.png                                     384x216 card

The sun-less / moon-less versions are used where the pack has them: BLOOD draws its own sun and
moon over skyboxes, so the painted ones would show twice.

Usage: python3 tools/import_fantasy_skies.py "<Fantasy Skybox FREE folder>"
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from import_sbs_skies import LAUNCHER_SKY, RES, Pano, save, to_atlas, view  # noqa: E402

FACE = 512
SETS = {"FS002": "002", "FS003": "003", "FS013": "013", "FS017": "017"}
# (file stem suffix, id suffix)
PANO_KINDS = [("Day_Sunless", "day"), ("Night_Moonless", "night"), ("Sunrise", "sunrise"), ("Sunset", "sunset"),
              ("Rainy", "rainy"), ("Snowy", "snowy")]


class Cross:
    """A 4x3 horizontal cubemap cross sampled by Minecraft direction (+X east, +Y up, +Z south), using
    the OpenGL cubemap convention BLOOD's own importer uses (core/sky/CubeFormats)."""

    POS = {"px": (2, 1), "nx": (0, 1), "py": (1, 0), "ny": (1, 2), "pz": (1, 1), "nz": (3, 1)}

    def __init__(self, img):
        self.a = np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0
        self.n = self.a.shape[1] // 4

    def sample(self, x, y, z):
        z = -z  # Minecraft z = -GL z
        ax, ay, az = np.abs(x), np.abs(y), np.abs(z)
        out = np.zeros(x.shape + (3,), dtype=np.float32)
        n = self.n
        cases = [
            ("px", (ax >= ay) & (ax >= az) & (x > 0), lambda: (-z / ax, -y / ax)),
            ("nx", (ax >= ay) & (ax >= az) & (x <= 0), lambda: (z / ax, -y / ax)),
            ("py", (ay > ax) & (ay >= az) & (y > 0), lambda: (x / ay, z / ay)),
            ("ny", (ay > ax) & (ay >= az) & (y <= 0), lambda: (x / ay, -z / ay)),
            ("pz", (az > ax) & (az > ay) & (z > 0), lambda: (x / az, -y / az)),
            ("nz", (az > ax) & (az > ay) & (z <= 0), lambda: (-x / az, -y / az)),
        ]
        for face, mask, st in cases:
            if not mask.any():
                continue
            sc, tc = st()
            u = np.clip((sc[mask] + 1) / 2 * n - 0.5, 0, n - 1)
            v = np.clip((tc[mask] + 1) / 2 * n - 0.5, 0, n - 1)
            u0, v0 = np.minimum(np.floor(u).astype(int), n - 2), np.minimum(np.floor(v).astype(int), n - 2)
            fu, fv = (u - u0)[:, None], (v - v0)[:, None]
            cx, cy = self.POS[face]
            A = self.a
            ox, oy = cx * n, cy * n
            top = A[oy + v0, ox + u0] * (1 - fu) + A[oy + v0, ox + u0 + 1] * fu
            bot = A[oy + v0 + 1, ox + u0] * (1 - fu) + A[oy + v0 + 1, ox + u0 + 1] * fu
            out[mask] = top * (1 - fv) + bot * fv
        return out


def sources(root):
    m = os.path.join(root, "Materials")
    for i in range(1, 7):
        yield f"fs_classic_day_{i}", Cross, os.path.join(m, "Classic", f"FS000_Day_0{i}_Sunless.png")
    for i in range(1, 4):
        yield f"fs_classic_night_{i}", Cross, os.path.join(m, "Classic", f"FS000_Night_0{i}_Moonless.png")
    for folder, num in SETS.items():
        for stem, kind in PANO_KINDS:
            yield f"fs_{num}_{kind}", Pano, os.path.join(m, folder, f"{folder}_{stem}.png")


def main():
    root = sys.argv[1]
    for sid, kind, path in sources(root):
        img = Image.open(path)
        src = kind(img)
        save(to_atlas(src, FACE), os.path.join(RES, "skybox", sid + ".png"))
        with open(os.path.join(RES, "skybox", sid + ".png.mcmeta"), "w") as f:
            f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
        thumb = view(src.sample, 1024, 576)
        Image.fromarray((np.clip(thumb, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB").resize((512, 288), Image.LANCZOS) \
            .save(os.path.join(RES, "gui", "sky_preview", sid + ".png"), optimize=True)
        save(view(src.sample, 384, 216), os.path.join(LAUNCHER_SKY, sid + ".png"))
        print(sid, flush=True)


if __name__ == "__main__":
    main()
