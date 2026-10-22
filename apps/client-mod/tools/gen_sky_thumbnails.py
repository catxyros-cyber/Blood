#!/usr/bin/env python3
"""Re-renders the in-game skybox thumbnails (textures/gui/sky_preview/<id>.png) from the bundled
cube atlases, at 512x288 (4x the GUI size) so they stay sharp at high GUI scales.

Same camera as the other generators (tools/import_sbs_skies.py view()): looking north-west,
slightly up. The menu draws them with UVs in fractions, so the size can change freely.

Run: python3 tools/gen_sky_thumbnails.py [ids...]
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from import_sbs_skies import UV, view  # noqa: E402

ROOT = os.path.dirname(HERE)
TEX = os.path.join(ROOT, "common/src/main/resources/assets/bloomclient/textures")
W, H = 512, 288


class Atlas:
    """Bilinear sampling of a 3x2 cube atlas by direction (inverse of import_sbs_skies.direction)."""

    def __init__(self, img):
        self.a = np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0
        self.face = self.a.shape[1] // 3

    def sample(self, x, y, z):
        ax, ay, az = np.abs(x), np.abs(y), np.abs(z)
        out = np.zeros(x.shape + (3,), dtype=np.float32)
        n = self.face
        cases = [
            (0, (ay >= ax) & (ay >= az) & (y < 0), lambda: (x / ay, z / ay)),       # bottom: (a, -1, b)
            (3, (ay >= ax) & (ay >= az) & (y >= 0), lambda: (x / ay, -z / ay)),     # top: (a, 1, -b)
            (1, (az > ax) & (az > ay) & (z < 0), lambda: (x / az, -y / az)),        # north: (a, -b, -1)
            (2, (az > ax) & (az > ay) & (z >= 0), lambda: (-x / az, -y / az)),      # south: (-a, -b, 1)
            (4, (ax > ay) & (ax >= az) & (x >= 0), lambda: (z / ax, -y / ax)),      # east: (1, -b, a)
            (5, (ax > ay) & (ax >= az) & (x < 0), lambda: (-z / ax, -y / ax)),      # west: (-1, -b, -a)
        ]
        for f, mask, ab in cases:
            if not mask.any():
                continue
            a, b = ab()
            s = np.clip((a[mask] + 1) / 2, 0, 1) * (n - 1)
            t = np.clip((b[mask] + 1) / 2, 0, 1) * (n - 1)
            x0, y0 = round(UV[f][0] * n * 3), round(UV[f][1] * n * 2)
            s0 = np.minimum(np.floor(s).astype(int), n - 2)
            t0 = np.minimum(np.floor(t).astype(int), n - 2)
            fs, ft = (s - s0)[:, None], (t - t0)[:, None]
            p = self.a
            top = p[y0 + t0, x0 + s0] * (1 - fs) + p[y0 + t0, x0 + s0 + 1] * fs
            bot = p[y0 + t0 + 1, x0 + s0] * (1 - fs) + p[y0 + t0 + 1, x0 + s0 + 1] * fs
            out[mask] = top * (1 - ft) + bot * ft
        return out


def main():
    sky_dir = os.path.join(TEX, "skybox")
    ids = sys.argv[1:] or sorted(f[:-4] for f in os.listdir(sky_dir) if f.endswith(".png"))
    for sid in ids:
        atlas = Atlas(Image.open(os.path.join(sky_dir, sid + ".png")))
        # Render at 2x and downsample: smooth, sharp thumbnails.
        arr = view(atlas.sample, W * 2, H * 2)
        img = Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB").resize((W, H), Image.LANCZOS)
        p = os.path.join(TEX, "gui", "sky_preview", sid + ".png")
        img.save(p, optimize=True)
        print(f"{sid:24s} {W}x{H} {os.path.getsize(p) // 1024} KB", flush=True)


if __name__ == "__main__":
    main()
