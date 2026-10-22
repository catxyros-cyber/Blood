#!/usr/bin/env python3
"""Original launcher background art for MOTION: a blocky crimson forest under a blood moon,
one palette per theme.

Run: python3 branding/tools/gen_backgrounds.py
Writes apps/launcher/public/backgrounds/<theme>.jpg (1920x1080) and <theme>-thumb.jpg.
Procedural and deterministic; contains no third-party artwork.
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "apps/launcher/public/backgrounds")
W, H = 1920, 1080
S = 4                      # pixel-art scale
LW, LH = W // S, H // S    # low-res canvas 480x270

THEMES = {
    "motion": dict(sky=["#020403", "#06120b", "#0b2a18", "#0f4a2a"], moon="#e6fff0", moon_glow="#39ff88", far="#082014", mid="#05130c",
                   ground="#020604", rim="#2be07a", cap=["#14a85a", "#39ff88", "#0d7a41", "#c6ff3d"], stem="#0b2a19", stem_hi="#155c35",
                   light="#d6ff5c", ember=["#39ff88", "#c6ff3d", "#eafff2"], haze="#0a3a20"),
    "crimson": dict(sky=["#050203", "#1c0509", "#4a0a14", "#8c1424"], moon="#ffc9c0", moon_glow="#ff2a3c", far="#2b0910", mid="#1a060a",
                    ground="#0c0405", rim="#8a1020", cap=["#b3142a", "#d41c33", "#86101f", "#e8324a"], stem="#4a0d16", stem_hi="#7a1826",
                    light="#ff9a4a", ember=["#ff3b3b", "#ff6a3d", "#ffb070"], haze="#5c0a16"),
    "obsidian": dict(sky=["#020202", "#09060a", "#1d0a10", "#3f0b16"], moon="#f3e6e8", moon_glow="#c8102a", far="#140a0e", mid="#0c0709",
                     ground="#050405", rim="#6b0c19", cap=["#7d0f1f", "#a3132a", "#5a0b17", "#c0182f"], stem="#241015", stem_hi="#3d1820",
                     light="#ff7a5c", ember=["#e8192f", "#ff4757", "#ffd0d0"], haze="#2a060d"),
    "ash": dict(sky=["#0b0c0d", "#1a1b1d", "#33302f", "#5b4a48"], moon="#f0ecea", moon_glow="#c9333f", far="#262728", mid="#1a1b1c",
                ground="#0f1010", rim="#6e2a2f", cap=["#7a2a31", "#963238", "#5e2228", "#b23a42"], stem="#2c2b2b", stem_hi="#43403f",
                light="#ff8a6a", ember=["#d9d4d2", "#e0484f", "#a8a2a0"], haze="#2b2627"),
    "ember": dict(sky=["#060203", "#230806", "#5c160a", "#b3330f"], moon="#ffd9a8", moon_glow="#ff4a1c", far="#331009", mid="#1f0906",
                  ground="#0e0504", rim="#a8320f", cap=["#c2361a", "#e04a1f", "#952514", "#ff6a2a"], stem="#4a1a0e", stem_hi="#7a2c14",
                  light="#ffc15c", ember=["#ff7a2a", "#ffb347", "#ffe0a0"], haze="#6b1c0a"),
    "mono": dict(sky=["#030303", "#0f0f0f", "#2a2a2a", "#555555"], moon="#f4f4f4", moon_glow="#bbbbbb", far="#202020", mid="#141414",
                 ground="#090909", rim="#5a5a5a", cap=["#6e6e6e", "#8a8a8a", "#525252", "#a6a6a6"], stem="#262626", stem_hi="#3a3a3a",
                 light="#e6e6e6", ember=["#d6d6d6", "#9a9a9a", "#ffffff"], haze="#262626"),
}

MOON_X, MOON_Y, MOON_R = 0.8, 0.3, 24  # low-res radius


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def lerp(a, b, t):
    return a + (b - a) * t


def sky(p):
    """Full-resolution sky gradient with a soft glow around the moon."""
    y = np.linspace(0, 1, H)[:, None]
    stops = [hexc(c) for c in p["sky"]]
    pos = [0.0, 0.35, 0.7, 1.0]
    col = np.zeros((H, 1, 3), np.float32)
    for i in range(3):
        t = np.clip((y - pos[i]) / (pos[i + 1] - pos[i]), 0, 1)[..., None]
        m = ((y >= pos[i]) & (y <= pos[i + 1]))[..., None]
        col = np.where(m, lerp(stops[i], stops[i + 1], t), col)
    img = np.repeat(col, W, axis=1)
    yy, xx = np.mgrid[0:H, 0:W]
    cx, cy = W * MOON_X, H * MOON_Y
    d = np.sqrt(((xx - cx) / W) ** 2 + ((yy - cy) / H * 1.25) ** 2)
    glow = np.clip(1 - d / 0.5, 0, 1) ** 2.4
    img = img + glow[..., None] * (hexc(p["moon_glow"]) - img) * 0.5
    return img


def moon(canvas, p, rng):
    """A pixelated moon with a few darker 'maria' blocks."""
    cx, cy = int(LW * MOON_X), int(LH * MOON_Y)
    base, dark = hexc(p["moon"]), hexc(p["moon"]) * 0.9
    for y in range(cy - MOON_R, cy + MOON_R + 1):
        for x in range(cx - MOON_R, cx + MOON_R + 1):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= MOON_R:
                shade = 1 - 0.12 * max(0, (x - cx + y - cy) / (2 * MOON_R))
                canvas[y, x, :3] = base * shade
                canvas[y, x, 3] = 255
    for _ in range(9):
        mx, my = cx + int(rng.normal(0, MOON_R * 0.45)), cy + int(rng.normal(0, MOON_R * 0.45))
        r = int(rng.integers(2, 6))
        for y in range(my - r, my + r):
            for x in range(mx - r, mx + r):
                if ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 < MOON_R - 1 and ((x - mx) ** 2 + (y - my) ** 2) < r * r:
                    canvas[y, x, :3] = dark
    return canvas


def ridge(rng, base, amp, rough, step):
    """1D blocky ridge heights (low-res) quantised to `step` pixels."""
    x = np.arange(LW)
    h = np.full(LW, base, np.float32)
    for k, a in ((1, 1.0), (2.3, 0.5), (5.1, 0.25), (11.7, rough)):
        ph = rng.uniform(0, 6.28)
        h += amp * a * np.sin(x / LW * 6.28 * k + ph)
    return (np.round(h / step) * step).astype(int)


def draw_layer(canvas, heights, color, rng, shade=0.06, texture=True, rim=None):
    c = hexc(color)
    block = 3
    for x in range(LW):
        top = max(0, heights[x])
        if top >= LH:
            continue
        canvas[top:, x, :3] = c
        canvas[top:, x, 3] = 255
        canvas[top:top + 1, x, :3] = hexc(rim) if rim else np.minimum(255, c * 1.25)
    if texture:
        for by in range(0, LH, block):
            for bx in range(0, LW, block):
                if rng.random() < 0.22:
                    region = canvas[by:by + block, bx:bx + block]
                    mask = (region[..., 3] > 0) & (np.arange(block)[:len(region), None] >= 0)
                    region[mask, :3] *= (1 - shade * 1.5) if rng.random() < 0.6 else (1 + shade)


def fungus(canvas, rng, x, ground_y, size, p, glow=True):
    """A tall crimson fungus: striped stem, wide warty cap, hanging vines, a few glowing blocks."""
    stem, hi = hexc(p["stem"]), hexc(p["stem_hi"])
    h = int(size * 1.8)
    for y in range(ground_y - h, ground_y):
        if 0 <= y < LH:
            canvas[y, x:x + 3, :3] = stem
            canvas[y, x, :3] = hi if (y // 3) % 2 == 0 else stem
            canvas[y, x:x + 3, 3] = 255
    caps = [hexc(c) for c in p["cap"]]
    top = ground_y - h
    cw = int(size * 1.2)
    rows = max(3, int(size * 0.55))
    for r in range(rows):
        # dome: narrow at the top, widest at the bottom, with a ragged lower edge
        t = (r + 1) / rows
        half = int(cw * (0.35 + 0.65 * t ** 0.5))
        y = top - rows + r + 2
        for dx in range(-half, half + 3):
            if r == rows - 1 and rng.random() < 0.3:
                continue
            px = x + dx
            if 0 <= px < LW and 0 <= y < LH:
                c = caps[rng.integers(0, len(caps))]
                light = 1.1 - t * 0.35 - (dx / (half + 2)) * 0.08
                canvas[y, px, :3] = np.clip(c * light, 0, 255)
                canvas[y, px, 3] = 255
    # weeping vines
    for _ in range(max(2, size // 4)):
        vx = x + int(rng.integers(-cw + 1, cw + 2))
        ln = int(rng.integers(3, 3 + size // 2))
        vy = top + 2
        for k in range(ln):
            if 0 <= vx < LW and 0 <= vy + k < LH:
                canvas[vy + k, vx, :3] = caps[min(2, len(caps) - 1)]
                canvas[vy + k, vx, 3] = 255
    # glowing blocks inside the cap
    if glow:
        for _ in range(max(1, size // 6)):
            gx = x + int(rng.integers(-cw + 2, cw))
            gy = top - rows + 3 + int(rng.integers(0, max(1, rows - 2)))
            if 0 <= gx < LW - 2 and 0 <= gy < LH - 2:
                canvas[gy:gy + 2, gx:gx + 2, :3] = hexc(p["light"])


def scene(p, seed=13):
    rng = np.random.default_rng(seed)
    low = np.zeros((LH, LW, 4), np.float32)
    moon(low, p, rng)
    far = ridge(rng, LH * 0.62, 16, 0.14, 2)
    draw_layer(low, far, p["far"], rng, 0.03, False)
    for x in range(14, LW - 14, 26):
        if rng.random() < 0.45:
            fungus(low, rng, x, far[x] + 1, int(rng.integers(4, 7)), dict(p, cap=p["cap"][:2], stem=p["far"], stem_hi=p["far"]), glow=False)
    mid = ridge(rng, LH * 0.74, 9, 0.2, 2)
    draw_layer(low, mid, p["mid"], rng, 0.04)
    near = ridge(rng, LH * 0.86, 5, 0.25, 3)
    xs = np.arange(LW)
    near = near - (np.clip(1 - xs / (LW * 0.3), 0, 1) ** 1.5 * 26).astype(int) - (np.clip((xs - LW * 0.74) / (LW * 0.26), 0, 1) ** 1.5 * 30).astype(int)
    draw_layer(low, np.minimum(near + 3, LH), p["ground"], rng, 0.05, rim=p["rim"])
    rim = hexc(p["rim"])
    for x in range(LW):
        y0 = near[x]
        if 0 <= y0 < LH:
            low[y0:y0 + 2, x, :3] = rim * (1 + rng.uniform(-0.12, 0.12))
            low[y0:y0 + 2, x, 3] = 255
    for x, size in ((24, 34), (78, 22), (122, 13), (int(LW * 0.78), 14), (int(LW * 0.87), 24), (LW - 26, 36)):
        x += int(rng.integers(-3, 3))
        if 0 < x < LW - 4:
            fungus(low, rng, x, near[x] + 1, size, p)
    for x in range(26, LW - 26, 29):
        if rng.random() < 0.5:
            fungus(low, rng, x, mid[x] + 1, int(rng.integers(6, 9)), dict(p, stem=p["mid"], stem_hi=p["stem"]), glow=rng.random() < 0.4)
    return low


def haze(img, p):
    """Low red mist that settles in the valley."""
    y = np.linspace(0, 1, H)[:, None]
    band = np.exp(-((y - 0.8) / 0.09) ** 2)[..., None]
    return img + band * (hexc(p["haze"]) - img) * 0.35


def embers(img, p, rng, n=90):
    cols = [hexc(c) for c in p["ember"]]
    for _ in range(n):
        x, y = rng.uniform(0, W), rng.uniform(H * 0.12, H * 0.92)
        s = int(rng.choice([2, 3, 3, 4, 5]))
        a = rng.uniform(0.25, 0.85) * (0.6 + 0.4 * y / H)
        c = cols[rng.integers(0, len(cols))]
        x0, y0 = int(x), int(y)
        x1, y1 = min(W, x0 + s), min(H, y0 + s)
        img[y0:y1, x0:x1] = img[y0:y1, x0:x1] * (1 - a) + c * a


def render(name, p):
    rng = np.random.default_rng(11)
    img = sky(p)
    low = scene(p)
    up = np.repeat(np.repeat(low, S, axis=0), S, axis=1)
    alpha = up[..., 3:4] / 255.0
    img = img * (1 - alpha) + up[..., :3] * alpha
    img = haze(img, p)
    embers(img, p, rng)
    yy, xx = np.mgrid[0:H, 0:W]
    v = 1 - 0.4 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) / 2
    img = img * v[..., None]
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    os.makedirs(OUT, exist_ok=True)
    out.save(os.path.join(OUT, name + ".jpg"), quality=86, optimize=True, progressive=True)
    out.resize((384, 216), Image.LANCZOS).save(os.path.join(OUT, name + "-thumb.jpg"), quality=85)
    print(name, os.path.getsize(os.path.join(OUT, name + ".jpg")) // 1024, "KB")


if __name__ == "__main__":
    for n, p in THEMES.items():
        render(n, p)
