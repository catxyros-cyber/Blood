#!/usr/bin/env python3
"""Original wing images for MOTION accessories (publish them on the admin page → Accessories).

Run: python3 branding/tools/gen_wings.py
Writes branding/wings/<name>.png: ONE wing, transparent background, shoulder about halfway down
the LEFT edge, tip on the right. The game mirrors it for the other side.
Procedural; no third-party art.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "branding", "wings")
W, H = 640, 480
SS = 2


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def feather(draw, root, angle, length, width, fill, edge):
    """A long feather: tapered ellipse-ish polygon from root along angle."""
    ca, sa = math.cos(angle), math.sin(angle)
    nx, ny = -sa, ca
    pts_l, pts_r = [], []
    for i in range(25):
        t = i / 24
        w = width * math.sin(math.pi * min(1, t * 1.15)) ** 0.7 * (1 - 0.25 * t)
        x = root[0] + ca * length * t
        y = root[1] + sa * length * t
        pts_l.append((x + nx * w, y + ny * w))
        pts_r.append((x - nx * w, y - ny * w))
    poly = pts_l + pts_r[::-1]
    draw.polygon(poly, fill=fill)
    # Shaft
    draw.line([root, (root[0] + ca * length * 0.92, root[1] + sa * length * 0.92)], fill=edge, width=max(1, int(width * 0.12)))


def edge_point(t):
    """Leading edge (shoulder → wrist → tip), quadratic Bézier in 0..1 image units."""
    p0, p1, p2 = (0.0, 0.56), (0.42, -0.02), (1.0, 0.10)
    x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
    y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
    return x, y


def wing(colors, glow=None, seed=1):
    s = SS * 2  # draw large on a roomy canvas, then crop to the wing and fit W×H
    img = Image.new("RGBA", (W * s, H * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    base, tip, edge = rgb(colors[0]), rgb(colors[1]), rgb(colors[2])
    # Layers back to front: flight feathers (long), secondary row, coverts (short, near the edge).
    layers = [(22, 1.0, 0.040, 0.10), (18, 0.62, 0.046, 0.06), (15, 0.30, 0.050, 0.03)]
    for li, (count, lf, wf, inset) in enumerate(layers):
        for i in range(count):
            t = 0.04 + 0.96 * i / (count - 1)
            ex, ey = edge_point(t)
            # Direction: hangs down near the body, sweeps outward toward the tip.
            ang = math.radians(98 - 92 * t ** 1.3 + rng.normal(0, 1.5))
            length = H * (0.50 + 0.30 * t) * lf * (0.94 + rng.random() * 0.08)
            if li == 0 and t > 0.75:
                length *= 1.0 + (t - 0.75) * 0.9  # long primaries at the tip
            root = (ex * W * s * 0.5 + math.cos(ang) * inset * H * s * 0.5, (0.1 + ey * 0.5) * H * s + math.sin(ang) * inset * H * s * 0.5)
            col = mix(base, tip, min(1.0, 0.25 + 0.55 * t + 0.2 * (2 - li) / 2))
            feather(d, root, ang, length * s * 0.5, H * s * 0.5 * wf, col + (245,), mix(col, edge, 0.55) + (255,))
    # Leading edge (the "arm")
    pts = [(edge_point(i / 40)[0] * W * s * 0.5, (0.1 + edge_point(i / 40)[1] * 0.5) * H * s) for i in range(41)]
    d.line(pts, fill=edge + (255,), width=int(5 * s), joint="curve")
    img = img.filter(ImageFilter.GaussianBlur(0.25 * s))
    if glow:
        a = np.asarray(img)[..., 3].astype(np.float32) / 255
        g = Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6 * s))
        gl = np.zeros((H * s, W * s, 4), np.float32)
        gl[..., :3] = rgb(glow)
        gl[..., 3] = np.asarray(g, np.float32) * 0.7
        under = Image.fromarray(gl.astype(np.uint8), "RGBA")
        under.alpha_composite(img)
        img = under
    # Crop: keep the shoulder on the left edge, trim empty space, add a margin for the glow.
    a = np.asarray(img)[..., 3]
    ys, xs = np.nonzero(a > 4)
    pad = int(0.03 * W * s)
    box = (0, max(0, ys.min() - pad), min(img.width, xs.max() + pad), min(img.height, ys.max() + pad))
    img = img.crop(box)
    scale = W / img.width
    return img.resize((W, max(1, round(img.height * scale))), Image.LANCZOS)


WINGS = {
    "angel": (["#ffffff", "#e9eef7", "#c9d3e3"], "#ffffff", 3),
    "motion-neon": (["#0b1a12", "#39ff88", "#b6ffd6"], "#39ff88", 5),
    "crimson-demon": (["#2a0308", "#c0102a", "#ff6070"], "#ff1f3d", 7),
    "void": (["#0a0a0f", "#3a2a5c", "#9b7cff"], "#7c4dff", 11),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (cols, glow, seed) in WINGS.items():
        wing(cols, glow, seed).save(os.path.join(OUT, name + ".png"), optimize=True)
        print("wrote", name)


if __name__ == "__main__":
    main()
