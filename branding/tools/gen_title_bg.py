#!/usr/bin/env python3
"""MOTION Client title screen background (3840x2160): soft charcoal studio backdrop with a satin
curtain flowing down the right side. Original procedural art.

Run: python3 branding/tools/gen_title_bg.py
Writes apps/client-mod/modern/src/main/resources/assets/bloomclient/textures/gui/motion_title_bg.png
(the .png.mcmeta next to it turns on smooth filtering, so it stays sharp at any screen size).
"""
import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "apps", "client-mod", "modern", "src", "main", "resources", "assets", "bloomclient", "textures", "gui", "motion_title_bg.png")
W, H = 3840, 2160


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def noise(w, h, scale, seed, octaves=5):
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp = total = 0.0
    amp = 1.0
    for o in range(octaves):
        cells = max(2, int(scale * 2 ** o))
        g = rng.random((cells + 1, int((cells + 1) * w / h) + 1)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), np.float32) / 255
        total += amp
        amp *= 0.5
    return out / total


def main():
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    u, v = x / W, y / H

    # Studio backdrop: a soft key light behind the menu (left third), falling off to the edges.
    base = np.array([0.055, 0.060, 0.066], np.float32)
    key = np.exp(-(((u - 0.24) / 0.42) ** 2 + ((v - 0.46) / 0.62) ** 2))
    col = base[None, None] * (0.75 + 0.9 * key[..., None])
    col += np.array([0.012, 0.03, 0.02], np.float32)[None, None] * key[..., None]  # faint green cast
    clouds = noise(W, H, 2.2, 3)
    col *= (0.93 + 0.12 * clouds)[..., None]

    # Satin curtain on the right: many soft folds that sway as they fall.
    cx = 0.80 + 0.012 * np.sin(v * 5.0 + 0.6)
    region = smooth((u - (cx - 0.13)) / 0.07)
    t = (u - cx) * 150 + np.sin(v * 6.0 + u * 7) * 2.2 + np.sin(v * 2.1 + 1.3) * 3.0
    folds = 0.5 + 0.5 * np.sin(t) * (0.6 + 0.4 * np.sin(t * 0.47 + 0.8))
    sheen = np.power(np.clip(np.sin(t + 0.55), 0, 1), 6)               # narrow satin highlights
    fine = 0.5 + 0.5 * np.sin(t * 0.33 + v * 3)
    shade = 0.32 + 0.46 * folds + 0.08 * fine
    satin = np.array([0.18, 0.19, 0.205], np.float32)[None, None] * shade[..., None]
    satin += np.array([0.62, 0.66, 0.70], np.float32)[None, None] * (sheen * 0.30 * (0.7 + 0.3 * np.sin(v * 3 + 0.4)))[..., None]
    satin *= (0.8 + 0.25 * (1 - v))[..., None]                          # lit from above
    col = col * (1 - region[..., None]) + satin * region[..., None]
    # Soft shadow the curtain casts onto the backdrop.
    shadow = smooth(((cx - 0.13) - u) / 0.05) * smooth((u - (cx - 0.24)) / 0.11)
    col *= (1 - 0.35 * shadow)[..., None]

    # Vignette, then very fine film grain so gradients never band.
    vig = 1 - 0.55 * ((u - 0.45) ** 2 * 1.4 + (v - 0.5) ** 2 * 1.8)
    col *= np.clip(vig, 0.35, 1)[..., None]
    img = Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
    arr = np.asarray(img, np.float32)
    grain = np.random.default_rng(9).normal(0, 1.1, (H, W, 1)).astype(np.float32)
    arr = np.clip(arr + grain, 0, 255).astype(np.uint8)
    Image.fromarray(arr).save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
