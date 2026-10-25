#!/usr/bin/env python3
"""MOTION launcher background (3840x2160): ribbons of green light sweeping through a dark room,
a faint perspective floor grid and soft bokeh. Original procedural art.

Run: python3 branding/tools/gen_motion_bg.py
Writes apps/launcher/public/backgrounds/motion.jpg and motion-thumb.jpg.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "apps", "launcher", "public", "backgrounds")
W, H = 3840, 2160


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def glow(a, r):
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(img.filter(ImageFilter.GaussianBlur(r)), np.float32) / 255


def main():
    rng = np.random.default_rng(42)
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    u, v = x / W, y / H

    # Base: near-black with a deep green bloom low on the right, where the ribbons converge.
    col = np.zeros((H, W, 3), np.float32)
    col += np.array([0.012, 0.018, 0.016], np.float32)
    bloom = np.exp(-(((u - 0.72) / 0.45) ** 2 + ((v - 0.62) / 0.38) ** 2))
    col += np.array([0.02, 0.11, 0.06], np.float32) * bloom[..., None]

    # Perspective floor grid below the horizon, fading into the distance.
    horizon = 0.64
    below = v > horizon
    depth = np.where(below, (v - horizon) / (1 - horizon), 0) + 1e-4
    gz = 1 / depth
    gx = (u - 0.62) * gz
    lines = (np.exp(-((np.abs(((gx * 3) % 1) - 0.5) - 0.5) / (0.012 * gz)) ** 2) +
             np.exp(-((np.abs(((gz * 1.6) % 1) - 0.5) - 0.5) / (0.01 * gz)) ** 2))
    grid = np.where(below, np.clip(lines, 0, 1) * smooth(depth * 3) * 0.22, 0)
    col += np.array([0.10, 0.55, 0.30], np.float32) * grid[..., None]

    # Light ribbons: thin bright cores with wide soft glow, sweeping left to right.
    core = np.zeros((H, W), np.float32)
    tint = np.zeros((H, W, 3), np.float32)
    palette = [(0.78, 1.0, 0.24), (0.22, 1.0, 0.53), (0.0, 0.77, 0.42), (0.55, 1.0, 0.75)]
    for i in range(9):
        amp = rng.uniform(0.05, 0.13)
        freq = rng.uniform(1.6, 3.2)
        phase = rng.uniform(0, 6.28)
        base = 0.30 + 0.05 * i + rng.uniform(-0.03, 0.03)
        cy = base + amp * np.sin(u * freq * math.pi + phase) + (u - 0.5) * 0.18
        width = 0.0011 + 0.0016 * rng.random()
        fade = smooth((u - 0.02) / 0.35) * (0.45 + 0.55 * smooth((u - 0.3) / 0.5))
        d = np.abs(v - cy)
        c = np.exp(-(d / width) ** 2) * fade * rng.uniform(0.55, 1.0)
        core = np.maximum(core, c)
        tint += np.array(palette[i % len(palette)], np.float32) * c[..., None]
    wide = glow(core, 26)
    wider = glow(core, 90)
    tint_n = tint / np.maximum(core[..., None], 1e-3)
    tint_n = np.where(core[..., None] > 0.02, tint_n, np.array([0.22, 1.0, 0.53], np.float32))
    col += tint_n * (core * 1.0)[..., None]
    col += np.array([0.20, 0.95, 0.52], np.float32) * (wide * 1.25)[..., None]
    col += np.array([0.06, 0.45, 0.24], np.float32) * (wider * 1.6)[..., None]

    # Bokeh: soft discs of light drifting in the air.
    bok = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(bok)
    for _ in range(70):
        bx, by = rng.uniform(0.25, 1.0) * W, rng.uniform(0.05, 0.85) * H
        r = rng.uniform(3, 34)
        d.ellipse([bx - r, by - r, bx + r, by + r], fill=int(rng.uniform(18, 90)))
    bokeh = np.asarray(bok.filter(ImageFilter.GaussianBlur(5)), np.float32) / 255
    col += np.array([0.35, 1.0, 0.62], np.float32) * (bokeh * 0.55)[..., None]

    # Fine sparkles.
    sp = np.zeros((H, W), np.float32)
    for _ in range(900):
        sx, sy = int(rng.uniform(0, W)), int(rng.uniform(0, H * 0.9))
        sp[sy, sx] = rng.uniform(0.3, 1.0)
    col += np.array([0.8, 1.0, 0.9], np.float32) * glow(sp, 1.4)[..., None] * 3.0

    # Vignette, filmic tone curve, grain.
    vig = 1 - 0.6 * ((u - 0.6) ** 2 * 1.3 + (v - 0.5) ** 2 * 1.6)
    col *= np.clip(vig, 0.25, 1)[..., None]
    col = 1 - np.exp(-col * 1.35)
    arr = np.clip(col * 255 + rng.normal(0, 1.4, (H, W, 1)), 0, 255).astype(np.uint8)
    img = Image.fromarray(arr)
    img.save(os.path.join(OUT, "motion.jpg"), quality=93, optimize=True, progressive=True)
    img.resize((480, 270), Image.LANCZOS).save(os.path.join(OUT, "motion-thumb.jpg"), quality=88)
    print("wrote motion.jpg")


if __name__ == "__main__":
    main()
