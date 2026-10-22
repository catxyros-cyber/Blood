#!/usr/bin/env python3
"""Original HD capes for BLOOD (1024x512, Minecraft's 64x32 cape layout at 16x detail).

Run: python3 branding/tools/gen_capes.py
Writes branding/capes/<name>.png (still capes) and <name>-animated.png (vertical frame strips).
Publish them on the website's admin page (Capes). Procedural and deterministic; no third-party art.

Layout (in 64x32 units, scaled by U): outside of the cape (what other players see) at (1,1) 10x16,
inside at (12,1) 10x16, edges at (0,1) and (11,1) 1x16, top (1,0) and bottom (11,0) 10x1,
elytra at (22,0) 24x22.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "branding", "capes")
SS = 2  # supersampling for clean edges


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def value_noise(w, h, scale, seed, octaves=5):
    """Fractal value noise in [0,1], shape (h, w)."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cells = max(2, int(scale * 2 ** o))
        g = rng.random((cells + 1, cells + 1)).astype(np.float32)
        img = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += amp * np.asarray(img, np.float32) / 255.0
        total += amp
        amp *= 0.5
    return out / total


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x / w, y / h


# ------------------------------------------------------------------ faces (portrait, 10:16)

def face_blood_moon(w, h, t=0.0):
    x, y = grid(w, h)
    top, mid, bot = hexc("#12030a"), hexc("#5a0814"), hexc("#b0142b")
    k = y[..., None]
    col = np.where(k < 0.55, top + (mid - top) * (k / 0.55), mid + (bot - mid) * ((k - 0.55) / 0.45))
    clouds = value_noise(w, h, 3, 7)
    col *= (0.75 + 0.5 * clouds[..., None])
    # Moon
    mx, my, mr = 0.5, 0.33, 0.27
    ar = w / h
    d = np.sqrt(((x - mx) * ar) ** 2 + (y - my) ** 2) / (mr * ar)
    glow = np.exp(-np.maximum(d - 1, 0) * (3.2 - 0.6 * math.sin(t * 2 * math.pi)))
    col += hexc("#ff2a3c")[None, None] * (glow * 0.55)[..., None]
    craters = value_noise(w, h, 9, 11, 5)
    maria = smooth((value_noise(w, h, 2.5, 13, 3) - 0.45) * 4)
    moon = np.clip((1 - d) * 40, 0, 1)
    face = hexc("#ffd8cc") * (0.62 + 0.45 * craters[..., None]) * (1 - 0.28 * maria[..., None])
    face = face - 0.35 * (np.clip(d, 0, 1) ** 2.5)[..., None] + hexc("#ff4050") * 0.12 * (np.clip(d, 0, 1) ** 2)[..., None]
    col = col * (1 - moon[..., None]) + face * moon[..., None]
    col = forest(col, w, h, seed=3, base=0.78, color="#0a0204", rim="#ff3a4c")
    col = embers(col, w, h, seed=5, n=70, t=t)
    return vignette(col, x, y, 0.55)


def forest(col, w, h, seed, base, color, rim):
    """Silhouetted thorny trees along the bottom, with a glowing rim."""
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    # Ground ridge
    pts = [(0, h)]
    for i in range(0, w + 8, 8):
        pts.append((i, h * base + math.sin(i / w * 7 + seed) * h * 0.02 + rng.normal() * h * 0.004))
    pts.append((w, h))
    d.polygon(pts, fill=255)

    def branch(x0, y0, ang, length, width, depth):
        if depth == 0 or length < 3:
            return
        x1 = x0 + math.cos(ang) * length
        y1 = y0 + math.sin(ang) * length
        d.line([(x0, y0), (x1, y1)], fill=255, width=max(1, int(width)))
        n = 2 if depth > 2 else 3
        for _ in range(n):
            branch(x1, y1, ang + rng.normal(0, 0.55), length * rng.uniform(0.55, 0.78), width * 0.68, depth - 1)

    for tx in np.linspace(0.08, 0.92, 5):
        bx = tx * w + rng.normal() * w * 0.03
        branch(bx, h * base + h * 0.02, -math.pi / 2 + rng.normal(0, 0.12), h * rng.uniform(0.12, 0.2), w * 0.035, 7)
    mask = np.asarray(img, np.float32) / 255.0
    edge = np.asarray(img.filter(ImageFilter.GaussianBlur(w * 0.012)), np.float32) / 255.0
    rimglow = np.clip(edge - mask, 0, 1)
    col = col + hexc(rim)[None, None] * (rimglow * 0.9)[..., None]
    return col * (1 - mask[..., None]) + hexc(color)[None, None] * mask[..., None]


def embers(col, w, h, seed, n, t=0.0):
    rng = np.random.default_rng(seed)
    layer = Image.new("RGB", (w, h), 0)
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        ex = rng.random() * w + math.sin(t * 2 * math.pi + rng.random() * 6) * w * 0.02
        ey = (rng.random() - t * rng.uniform(0.2, 0.6)) % 1.0 * h
        r = rng.uniform(0.5, 1.6) * w / 160
        c = tuple(int(v) for v in rng.choice([(255, 80, 60), (255, 150, 90), (255, 210, 170)]))
        d.ellipse([ex - r, ey - r, ex + r, ey + r], fill=c)
    a = np.asarray(layer, np.float32) / 255.0
    b = np.asarray(layer.filter(ImageFilter.GaussianBlur(w / 90)), np.float32) / 255.0
    return col + a * 0.85 + b * 0.8


def vignette(col, x, y, strength):
    v = 1 - strength * (((x - 0.5) * 1.6) ** 2 + ((y - 0.5) * 1.2) ** 2)
    return col * np.clip(v, 0.2, 1)[..., None]


def face_sigil(w, h, t=0.0):
    x, y = grid(w, h)
    ar = w / h
    n = value_noise(w, h, 4, 21)
    col = hexc("#070506")[None, None] + (n[..., None] - 0.5) * 0.06
    # Fine woven texture
    weave = 0.5 + 0.5 * np.sin(x * w * 0.9) * np.sin(y * h * 0.9)
    col += (weave[..., None] - 0.5) * 0.025
    cx, cy = 0.5, 0.45
    dx, dy = (x - cx) * ar, (y - cy)
    r = np.sqrt(dx * dx + dy * dy)
    th = np.arctan2(dy, dx)
    pulse = 0.75 + 0.25 * math.sin(t * 2 * math.pi)
    lines = np.zeros_like(r)
    for rr, wdt in [(0.30, 0.004), (0.27, 0.0025), (0.17, 0.003), (0.08, 0.004)]:
        lines = np.maximum(lines, np.exp(-((r - rr) / wdt) ** 2))
    # Rotating runic ticks between the outer rings
    rot = t * 2 * math.pi / 12
    ticks = (np.abs(np.sin((th + rot) * 12)) > 0.94) & (r > 0.275) & (r < 0.298)
    lines = np.maximum(lines, ticks.astype(np.float32))
    # Star (heptagram) inside
    for k in range(7):
        a = k * 2 * math.pi / 7 * 3 - math.pi / 2 - rot
        b = (k + 1) * 2 * math.pi / 7 * 3 - math.pi / 2 - rot
        p0 = np.array([math.cos(a), math.sin(a)]) * 0.17
        p1 = np.array([math.cos(b), math.sin(b)]) * 0.17
        v = p1 - p0
        L = np.linalg.norm(v)
        u = v / L
        px, py = dx - p0[0], dy - p0[1]
        proj = np.clip(px * u[0] + py * u[1], 0, L)
        dist = np.sqrt((px - proj * u[0]) ** 2 + (py - proj * u[1]) ** 2)
        lines = np.maximum(lines, np.exp(-(dist / 0.003) ** 2))
    # Vertical blade below the sigil
    blade = np.exp(-((dx) / (0.006 + 0.03 * np.clip((y - 0.62) / 0.3, 0, 1) * np.clip((0.95 - y) / 0.1, 0, 1))) ** 2) * ((y > 0.62) & (y < 0.95))
    lines = np.maximum(lines, blade * 0.9)
    glow = np.asarray(Image.fromarray((np.clip(lines, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(w / 40)), np.float32) / 255.0
    red = hexc("#ff1f3d")
    col += red[None, None] * (glow * 2.2 * pulse)[..., None]
    col = col * (1 - lines[..., None]) + (hexc("#ffe0e4") * 0.6 + red * 0.6)[None, None] * lines[..., None] * pulse
    # Gold trim
    edge = np.minimum.reduce([x, 1 - x, y * ar * 1.0, (1 - y) * ar])
    trim = np.exp(-((edge - 0.045) / 0.006) ** 2)
    col = col * (1 - trim[..., None]) + hexc("#b48a4a")[None, None] * (0.7 + 0.3 * n[..., None]) * trim[..., None]
    return vignette(col, x, y, 0.35)


def face_obsidian(w, h, t=0.0):
    x, y = grid(w, h)
    rng = np.random.default_rng(31)
    pts = rng.random((26, 2)).astype(np.float32)
    px, py = x[..., None], y[..., None] * (h / w)
    ptsx, ptsy = pts[:, 0][None, None], pts[:, 1][None, None] * (h / w)
    dist = np.sqrt((px - ptsx) ** 2 + (py - ptsy) ** 2)
    s = np.sort(dist, axis=-1)
    crack = s[..., 1] - s[..., 0]
    cell = np.argmin(dist, axis=-1)
    shade = (cell * 0.618) % 1.0
    n = value_noise(w, h, 5, 33)
    col = hexc("#0b0a10")[None, None] + (shade[..., None] * 0.08) + (n[..., None] - 0.5) * 0.05
    # Glassy highlights
    spec = np.clip(np.sin((x * 3 + y * 5 + shade * 4) * math.pi) ** 24, 0, 1)
    col += hexc("#5a4a7a")[None, None] * (spec * 0.25)[..., None]
    flow = 0.6 + 0.4 * np.sin(t * 2 * math.pi + y * 8 + shade * 6)
    lava = np.exp(-(crack / 0.006) ** 2) * flow
    glow = np.exp(-(crack / 0.03) ** 2) * flow
    col += hexc("#ff3a10")[None, None] * (glow * 0.6)[..., None]
    col = col * (1 - lava[..., None]) + hexc("#ffb040")[None, None] * lava[..., None]
    col = embers(col, w, h, seed=37, n=35, t=t)
    return vignette(col, x, y, 0.45)


def face_ashfall(w, h, t=0.0):
    x, y = grid(w, h)
    n1 = value_noise(w, h, 2.5, 41)
    n2 = value_noise(w, h, 5, 43)
    smoke = smooth((n1 * 0.7 + n2 * 0.3 - 0.3 + 0.05 * math.sin(t * 2 * math.pi)) * 1.8)
    col = hexc("#151617")[None, None] * (1 - smoke[..., None]) + hexc("#8a8684")[None, None] * smoke[..., None]
    # Diagonal red slashes
    for off, wd in [(0.0, 0.012), (0.08, 0.006), (-0.07, 0.004)]:
        d = (x - 0.5) * 1.0 + (y - 0.5) * 0.45 - off
        s = np.exp(-(d / wd) ** 2) * smooth((0.9 - np.abs(y - 0.5) * 1.9) * 2)
        col = col * (1 - s[..., None]) + hexc("#e0283c")[None, None] * s[..., None]
        g = np.exp(-(d / (wd * 6)) ** 2) * smooth((0.9 - np.abs(y - 0.5) * 1.9) * 2)
        col += hexc("#c0102a")[None, None] * (g * 0.35)[..., None]
    col = embers(col, w, h, seed=47, n=90, t=t) * 0.98
    return vignette(col, x, y, 0.5)


# ------------------------------------------------------------------ assembly

def render_face(fn, w, h, t=0.0):
    col = fn(w * SS, h * SS, t)
    img = Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8))
    return img.resize((w, h), Image.LANCZOS)


def cape(fn, width=1024, t=0.0):
    U = width // 64
    canvas = Image.new("RGBA", (width, width // 2), (0, 0, 0, 0))
    face = render_face(fn, 10 * U, 16 * U, t)
    canvas.paste(face, (1 * U, 1 * U))
    # Inside: mirrored and darkened, so the art lines up through the cloth.
    inside = face.transpose(Image.FLIP_LEFT_RIGHT).point(lambda v: int(v * 0.42))
    canvas.paste(inside, (12 * U, 1 * U))
    # Edges and top/bottom take the neighbouring colours of the outside.
    canvas.paste(face.crop((0, 0, 1, 16 * U)).resize((U, 16 * U)), (0, 1 * U))
    canvas.paste(face.crop((10 * U - 1, 0, 10 * U, 16 * U)).resize((U, 16 * U)), (11 * U, 1 * U))
    canvas.paste(face.crop((0, 0, 10 * U, 1)).resize((10 * U, U)), (1 * U, 0))
    canvas.paste(face.crop((0, 16 * U - 1, 10 * U, 16 * U)).resize((10 * U, U)), (11 * U, 0))
    # Elytra: the same art across both wings.
    wings = render_face(fn, 24 * U, 22 * U, t)
    canvas.paste(wings, (22 * U, 0))
    return canvas


CAPES = {
    "blood-moon": face_blood_moon,
    "crimson-sigil": face_sigil,
    "obsidian-ember": face_obsidian,
    "ashfall": face_ashfall,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in CAPES.items():
        cape(fn, 1024).save(os.path.join(OUT, name + ".png"), optimize=True)
        print("wrote", name)
    # Animated: 12 frames at 512 wide (keeps the file well under the server's 4 MB limit).
    for name in ["crimson-sigil", "blood-moon"]:
        frames = [cape(CAPES[name], 512, i / 12) for i in range(12)]
        strip = Image.new("RGBA", (512, 256 * len(frames)))
        for i, f in enumerate(frames):
            strip.paste(f, (0, 256 * i))
        strip.save(os.path.join(OUT, name + "-animated.png"), optimize=True)
        print("wrote", name, "animated")


if __name__ == "__main__":
    main()
