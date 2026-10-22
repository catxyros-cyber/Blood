#!/usr/bin/env python3
"""4K launcher backgrounds for BLOOD: a misty crimson fungus forest under a blood moon, painted
smoothly at 3840x2160 (the older gen_backgrounds.py makes the 1080p pixel-art versions).

Run: python3 branding/tools/gen_backgrounds_4k.py [theme ...]
Writes apps/launcher/public/backgrounds/<theme>.jpg (3840x2160) and <theme>-thumb.jpg (768x432).
Uses the same five palettes. Procedural and deterministic; contains no third-party artwork.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import gaussian_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_backgrounds import THEMES  # noqa: E402  (same palettes as the pixel-art set)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "apps/launcher/public/backgrounds")
W, H = 3840, 2160
SS = 2  # silhouettes are drawn at 2x and filtered down for clean edges
MOON = (0.78, 0.28, 0.105)  # x, y (fractions), radius (fraction of H)


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


def noise(w, h, scale, seed, octaves=6, persistence=0.5):
    """Fractal value noise in [0,1]."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp = total = 0.0
    amp = 1.0
    for o in range(octaves):
        cx = max(2, int(scale * 2 ** o))
        cy = max(2, int(scale * 2 ** o * h / w))
        g = rng.random((cy + 1, cx + 1)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray(g, "F").resize((w, h), Image.BICUBIC), np.float32)
        total += amp
        amp *= persistence
    return np.clip(out / total, 0, 1)


def blur(a, r):
    sig = (r, r) if a.ndim == 2 else (r, r, 0)
    return gaussian_filter(a.astype(np.float32), sig, mode="nearest", truncate=3.0)


def mask_from(img):
    """2x-drawn 'L' image -> float mask at W x H."""
    return np.asarray(img.resize((W, H), Image.LANCZOS), np.float32) / 255.0


def over(img, color, alpha):
    a = np.clip(alpha, 0, 1)[..., None]
    return img * (1 - a) + color * a


def facing_light(m, k, light=(1, -1)):
    """Edges of mask m that face the moon (up and to the right), as a soft 0..1 rim."""
    dx, dy = light[0] * k, light[1] * k
    shifted = np.roll(np.roll(m, -dy, axis=0), -dx, axis=1)
    return blur(np.clip(m - shifted, 0, 1), k * 0.6)


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


# ----------------------------------------------------------------------------- sky

def sky(p, yy, xx):
    stops = [hexc(c) for c in p["sky"]]
    pos = [0.0, 0.38, 0.72, 1.0]
    y = yy / H
    img = np.zeros((H, W, 3), np.float32)
    for i in range(3):
        t = smooth((y - pos[i]) / (pos[i + 1] - pos[i]))[..., None]
        m = ((y >= pos[i]) & (y <= pos[i + 1]))[..., None]
        img = np.where(m, stops[i] + (stops[i + 1] - stops[i]) * t, img)
    # Faint stars, mostly in the dark upper sky
    rng = np.random.default_rng(3)
    stars = np.zeros((H, W), np.float32)
    n = 1400
    sx, sy = rng.integers(0, W, n), (rng.random(n) ** 1.6 * H * 0.6).astype(int)
    stars[sy, sx] = rng.random(n) ** 3
    stars = np.maximum(stars, blur(stars, 1.2) * 3)
    img += stars[..., None] * (1 - y[..., None]) * 0.8
    # Nebula / high cloud
    neb = noise(W // 4, H // 4, 3, 5, 6)
    neb = np.asarray(Image.fromarray(neb, "F").resize((W, H), Image.BICUBIC), np.float32)
    img += hexc(p["moon_glow"]) * (smooth((neb - 0.48) * 3) * 0.10 * (1 - y))[..., None]
    return img


def moon(img, p, yy, xx):
    mx, my, mr = MOON[0] * W, MOON[1] * H, MOON[2] * H
    d = np.sqrt((xx - mx) ** 2 + (yy - my) ** 2) / mr
    glowc = hexc(p["moon_glow"])
    # Wide halo + tight bloom
    img += glowc * (np.exp(-np.maximum(d - 1, 0) * 0.9) * 0.55)[..., None]
    img += glowc * (np.exp(-np.maximum(d - 1, 0) * 4.0) * 0.6)[..., None]
    img += hexc(p["moon"]) * (np.exp(-np.maximum(d - 1, 0) * 9.0) * 0.35)[..., None]
    # God rays: angular streaks fading with distance
    ang = np.arctan2(yy - my, xx - mx)
    r9 = np.random.default_rng(9)
    ray = np.zeros_like(ang)
    for k, a in ((7, 0.5), (13, 0.35), (23, 0.25), (41, 0.15)):
        ray += a * np.sin(ang * k + r9.uniform(0, 6.28))
    ray = smooth((ray + 0.2) * 1.2) * np.exp(-np.maximum(d - 1, 0) * 0.35) * (d > 1)
    img += glowc * (ray * 0.12)[..., None]
    # Surface
    surf = noise(W // 2, H // 2, 10, 11, 6)
    surf = np.asarray(Image.fromarray(surf, "F").resize((W, H), Image.BICUBIC), np.float32)
    maria = smooth((noise(W // 4, H // 4, 3, 13, 4) - 0.47) * 3.5)
    maria = np.asarray(Image.fromarray(maria, "F").resize((W, H), Image.BICUBIC), np.float32)
    # Small craters
    rng = np.random.default_rng(17)
    crater = np.zeros((H, W), np.float32)
    for _ in range(34):
        a, r = rng.random() * 2 * math.pi, math.sqrt(rng.random()) * 0.9
        cx, cy, cr = mx + math.cos(a) * r * mr, my + math.sin(a) * r * mr, mr * rng.uniform(0.02, 0.09)
        x0, x1, y0, y1 = int(cx - cr * 1.6), int(cx + cr * 1.6), int(cy - cr * 1.6), int(cy + cr * 1.6)
        sub_d = np.sqrt((xx[y0:y1, x0:x1] - cx) ** 2 + (yy[y0:y1, x0:x1] - cy) ** 2) / cr
        crater[y0:y1, x0:x1] += np.where(sub_d < 1, -0.10 * np.sqrt(np.clip(1 - sub_d ** 2, 0, 1)), 0.07 * np.exp(-((sub_d - 1.0) / 0.15) ** 2))
    base = hexc(p["moon"])
    limb = np.clip(d, 0, 1)
    face = base * ((0.66 + 0.46 * surf) * (1 - 0.5 * maria) + crater)[..., None]
    face = face * (1 - 0.45 * limb ** 3)[..., None] + glowc * (0.25 * limb ** 2.5)[..., None]
    edge = smooth((1 - d) * mr / 2.0)  # ~2px antialiased limb
    return over(img, face, edge)


def clouds(img, p, yy, xx, seed, y0, y1, alpha):
    """Thin cloud bands, lit from the moon side."""
    n = noise(W // 3, H // 3, 2.2, seed, 6)
    n = np.asarray(Image.fromarray(n, "F").resize((W, H), Image.BICUBIC), np.float32)
    band = smooth((yy / H - y0) / 0.05) * smooth((y1 - yy / H) / 0.08)
    c = smooth((n - 0.52) * 4) * band
    mx, my = MOON[0] * W, MOON[1] * H
    near = np.exp(-np.sqrt(((xx - mx) / W) ** 2 + ((yy - my) / H) ** 2) * 4)
    col = hexc(p["far"])[None, None] * (1 - near[..., None]) + hexc(p["moon_glow"]) * near[..., None] * 0.8
    return over(img, col, c * alpha)


# ----------------------------------------------------------------------------- terrain

def ridge_line(rng, base, amp, roughness, w):
    x = np.linspace(0, 1, w)
    h = np.full(w, base, np.float64)
    a = amp
    for k in (1.0, 2.1, 4.3, 8.9, 17.2, 35.0, 71.0):
        h += a * np.sin(x * 2 * math.pi * k + rng.uniform(0, 6.28)) * rng.uniform(0.6, 1.0)
        a *= roughness
    return h


def layer_mask(heights):
    img = Image.new("L", (W * SS, H * SS), 0)
    pts = [(0, H * SS)] + [(i * SS, float(heights[i]) * SS) for i in range(0, W, 2)] + [(W * SS, H * SS)]
    ImageDraw.Draw(img).polygon(pts, fill=255)
    return img


def fungus(draw, rng, x, ground, size, vines=True, glow_spots=None):
    """A giant crimson fungus silhouette (stem, domed cap with ragged rim, hanging vines)."""
    s = SS
    stem_h = size * rng.uniform(1.5, 2.1)
    lean = rng.uniform(-0.18, 0.18) * size
    top = (x + lean, ground - stem_h)
    # Tapered, slightly curved stem
    pts_l, pts_r = [], []
    for i in range(25):
        t = i / 24
        cx = x + lean * t ** 1.5
        cy = ground - stem_h * t
        wdt = size * (0.13 - 0.06 * t) + size * 0.05 * max(0, 0.15 - t) * 6
        pts_l.append(((cx - wdt) * s, cy * s))
        pts_r.append(((cx + wdt) * s, cy * s))
    draw.polygon(pts_l + pts_r[::-1], fill=255)
    # Roots
    for k in (-1, 1):
        draw.line([(x * s, ground * s), ((x + k * size * 0.35) * s, (ground + size * 0.05) * s)], fill=255, width=int(size * 0.05 * s))
    # Cap: dome + ragged underside
    cw = size * rng.uniform(1.0, 1.35)
    ch = size * rng.uniform(0.55, 0.75)
    cx, cy = top
    dome = []
    ph = rng.uniform(0, 6.28)
    for i in range(121):
        a = math.pi + math.pi * i / 120
        r = 1 + 0.035 * math.sin(i / 120 * 9 + ph) + 0.015 * math.sin(i / 120 * 23 + ph)
        dome.append(((cx + math.cos(a) * cw * r) * s, (cy + math.sin(a) * ch * r) * s))
    under = []
    for i in range(121):
        t = i / 120
        ux = cx + cw - 2 * cw * t
        uy = cy + ch * 0.10 + (abs(math.sin(t * 14 + ph)) ** 0.6) * ch * 0.09
        under.append((ux * s, uy * s))
    draw.polygon(dome + under, fill=255)
    if vines:
        for _ in range(int(rng.integers(3, 7))):
            vx = cx + rng.uniform(-0.9, 0.9) * cw
            ln = rng.uniform(0.2, 0.9) * size
            wob = rng.uniform(0, 6)
            pts = [((vx + math.sin(wob + j * 0.6) * size * 0.015) * s, (cy + ch * 0.1 + ln * j / 10) * s) for j in range(11)]
            draw.line(pts, fill=255, width=max(2, int(size * 0.007 * s)), joint="curve")
    if glow_spots is not None:
        for _ in range(int(rng.integers(3, 8))):
            a = rng.uniform(math.pi * 1.1, math.pi * 1.9)
            r = rng.uniform(0.3, 0.9)
            glow_spots.append((cx + math.cos(a) * cw * r, cy + math.sin(a) * ch * r * 0.9, size * rng.uniform(0.025, 0.05)))
    return top, cw, ch


def render(name, p):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    rng = np.random.default_rng(29)
    img = sky(p, yy, xx)
    img = moon(img, p, yy, xx)
    img = clouds(img, p, yy, xx, 41, 0.18, 0.40, 0.55)

    haze_c = hexc(p["haze"])
    glow_c = hexc(p["moon_glow"])
    mx = MOON[0] * W

    def fog(img, y_center, spread, amount, seed):
        n = noise(W // 4, H // 4, 2.5, seed, 5)
        n = np.asarray(Image.fromarray(n, "F").resize((W, H), Image.BICUBIC), np.float32)
        band = np.exp(-((yy / H - y_center) / spread) ** 2) * (0.6 + 0.6 * n)
        lit = np.exp(-np.abs(xx - mx) / W * 2.2)
        col = haze_c * (1 - lit[..., None] * 0.5) + glow_c * lit[..., None] * 0.5
        return over(img, col, band * amount)

    # Distant mountains (3 layers, fading into the haze)
    for i, (base, amp, rough, seed) in enumerate([(0.56, 0.06, 0.55, 51), (0.63, 0.045, 0.55, 52), (0.70, 0.035, 0.6, 53)]):
        r = np.random.default_rng(seed)
        hgt = ridge_line(r, H * base, H * amp, rough, W)
        m = mask_from(layer_mask(hgt))
        depth = 0.62 - i * 0.2
        col = hexc(p["far"]) * (1 - depth) + haze_c * depth
        # Moonlit rim on each ridge
        rim = facing_light(m, 4, (0, -1)) * np.exp(-np.abs(xx - mx) / W * 2.5)
        img = over(img, col, m)
        img += glow_c * (rim * (0.35 - 0.08 * i))[..., None]
        img = fog(img, base + 0.06, 0.05, 0.30, 60 + i)

    # Mid forest of fungi on a ridge
    hgt = ridge_line(np.random.default_rng(71), H * 0.775, H * 0.02, 0.55, W)
    mid = Image.new("L", (W * SS, H * SS), 0)
    md = ImageDraw.Draw(mid)
    md.polygon([(0, H * SS)] + [(i * SS, float(hgt[i]) * SS) for i in range(0, W, 2)] + [(W * SS, H * SS)], fill=255)
    for x in np.arange(60, W, 150):
        if rng.random() < 0.6:
            xi = int(x + rng.uniform(-50, 50)) % W
            fungus(md, rng, xi, hgt[xi] + 4, rng.uniform(70, 130), vines=False)
    m = mask_from(mid)
    img = over(img, hexc(p["mid"]) * 0.75 + haze_c * 0.25, m)
    img += glow_c * (facing_light(m, 5) * 0.5 * np.exp(-np.abs(xx - mx) / W * 2))[..., None]
    img = fog(img, 0.83, 0.05, 0.35, 81)

    # Foreground: ground and giant fungi framing the left and right edges
    xs = np.arange(W)
    gh = ridge_line(np.random.default_rng(91), H * 0.885, H * 0.012, 0.55, W)
    gh -= (np.clip(1 - xs / (W * 0.28), 0, 1) ** 1.6) * H * 0.05 + (np.clip((xs - W * 0.76) / (W * 0.24), 0, 1) ** 1.6) * H * 0.06
    near = Image.new("L", (W * SS, H * SS), 0)
    nd = ImageDraw.Draw(near)
    nd.polygon([(0, H * SS)] + [(i * SS, float(gh[i]) * SS) for i in range(0, W, 2)] + [(W * SS, H * SS)], fill=255)
    spots = []
    caps = []
    for fx, size in [(0.05, 520), (0.17, 330), (0.27, 170), (0.80, 190), (0.885, 360), (0.975, 560)]:
        xi = int(fx * W)
        caps.append(fungus(nd, rng, xi, gh[min(W - 1, xi)] + 40, size, glow_spots=spots))
    m = mask_from(near)
    # Silhouette colour with a slight vertical gradient, then crimson rim light from the moon side
    ground_c = hexc(p["ground"])
    img = over(img, ground_c * (1 + 0.25 * (1 - yy / H))[..., None], m)
    rim = facing_light(m, 7)
    side = np.clip(1.15 - np.abs(xx - mx) / W * 1.2, 0.25, 1.0)
    img += hexc(p["rim"]) * (rim * 1.1 * side)[..., None]
    # Caps glow faintly from inside (subsurface), strongest near their underside
    capglow = np.zeros((H, W), np.float32)
    capc = [hexc(c) for c in p["cap"]]
    cap_img = Image.new("L", (W, H), 0)
    cd = ImageDraw.Draw(cap_img)
    for (cx, cy), cw, ch in caps:
        cd.ellipse([cx - cw * 0.85, cy - ch * 0.8, cx + cw * 0.85, cy + ch * 0.1], fill=255)
    capglow = blur(np.asarray(cap_img, np.float32) / 255.0, 60) * m
    tex = noise(W // 2, H // 2, 24, 97, 4)
    tex = np.asarray(Image.fromarray(tex, "F").resize((W, H), Image.BICUBIC), np.float32)
    img += (capc[0] * 0.55)[None, None] * (capglow * (0.5 + 0.8 * tex))[..., None]
    # Glowing spots (shroomlights)
    sp = Image.new("L", (W, H), 0)
    sd = ImageDraw.Draw(sp)
    for sx, sy, r in spots:
        sd.ellipse([sx - r, sy - r, sx + r, sy + r], fill=255)
    spa = np.asarray(sp, np.float32) / 255.0
    light = hexc(p["light"])
    img = over(img, light, spa)
    img += light * (blur(spa, 18) * 1.2 + blur(spa, 60) * 0.8)[..., None]

    # Low valley mist in front of everything, then embers with bokeh
    img = fog(img, 0.95, 0.045, 0.22, 101)
    er = np.random.default_rng(7)
    sharp = Image.new("RGB", (W, H), 0)
    soft = Image.new("RGB", (W, H), 0)
    ds, df = ImageDraw.Draw(sharp), ImageDraw.Draw(soft)
    ecols = [tuple(int(v * 255) for v in hexc(c)) for c in p["ember"]]
    for _ in range(260):
        x, y = er.uniform(0, W), er.uniform(H * 0.15, H * 0.97)
        c = ecols[er.integers(0, len(ecols))]
        if er.random() < 0.12:  # big out-of-focus foreground embers
            r = er.uniform(10, 26)
            df.ellipse([x - r, y - r, x + r, y + r], fill=tuple(int(v * 0.5) for v in c))
        else:
            r = er.uniform(1.5, 4.5) * (0.6 + y / H)
            ds.ellipse([x - r, y - r, x + r, y + r], fill=c)
    s_arr = np.asarray(sharp, np.float32) / 255.0
    f_arr = np.asarray(soft.filter(ImageFilter.GaussianBlur(9)), np.float32) / 255.0
    img += s_arr * 0.9 + blur(s_arr, 8) * 2.2 + f_arr * 0.9

    # Grade: gentle filmic curve, vignette, fine grain
    img = img / (1 + img * 0.18) * 1.12
    v = 1 - 0.55 * (((xx / W - 0.55) * 1.3) ** 2 + ((yy / H - 0.45) * 1.5) ** 2)
    img *= np.clip(v, 0.25, 1)[..., None]
    grain = np.random.default_rng(1).normal(0, 0.012, (H, W)).astype(np.float32)
    img += grain[..., None]
    out = Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".jpg")
    out.save(path, quality=92, optimize=True, progressive=True, subsampling=0)
    out.resize((768, 432), Image.LANCZOS).save(os.path.join(OUT, name + "-thumb.jpg"), quality=88)
    print(name, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    names = sys.argv[1:] or list(THEMES)
    for n in names:
        render(n, THEMES[n])
