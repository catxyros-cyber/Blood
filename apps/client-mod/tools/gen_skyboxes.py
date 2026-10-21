#!/usr/bin/env python3
"""Generates BLOOD Client's built-in skyboxes (original procedural art, no third-party images).

Output: common/src/main/resources/assets/bloomclient/textures/skybox/<id>.png  (3x2 atlas, see CubeLayout.java)
        common/src/main/resources/assets/bloomclient/textures/gui/sky_preview/<id>.png (menu thumbnails)

Run:  python3 tools/gen_skyboxes.py [face_size] [ids...]   (or: python3 tools/gen_skyboxes.py launcher)
Deterministic: the same seed always produces the same images.
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "common/src/main/resources/assets/bloomclient/textures")
FACE = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else 512


# ---------------------------------------------------------------- geometry (mirrors CubeLayout.java)
UV = [(0, 0), (1, 1), (2, 0), (1, 0), (2, 1), (0, 1)]  # (column, row) of faces: bottom,north,south,top,east,west


def face_dirs(face, n):
    s = (np.arange(n) + 0.5) / n
    S, T = np.meshgrid(s, s)  # T = rows (downwards)
    a, b = 2 * S - 1, 2 * T - 1
    one = np.ones_like(a)
    if face == 0:
        d = (a, -one, b)
    elif face == 1:
        d = (a, -b, -one)
    elif face == 2:
        d = (-a, -b, one)
    elif face == 3:
        d = (a, one, -b)
    elif face == 4:
        d = (one, -b, a)
    else:
        d = (-one, -b, -a)
    x, y, z = d
    l = np.sqrt(x * x + y * y + z * z)
    return x / l, y / l, z / l


# ---------------------------------------------------------------- noise
rng = np.random.default_rng(20261019)
PERM = rng.permutation(4096)


def _hash3(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = h & 0xFFFFFFFF
    return PERM[h % 4096] / 4095.0


def value_noise3(x, y, z, seed=0):
    xi, yi, zi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    xf, yf, zf = x - xi, y - yi, z - zi
    u, v, w = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf), zf * zf * (3 - 2 * zf)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (u if dx else 1 - u) * (v if dy else 1 - v) * (w if dz else 1 - w)
                out = out + wgt * _hash3(xi + dx, yi + dy, zi + dz, seed)
    return out


def fbm(x, y, z, octaves=5, seed=0, lac=2.0, gain=0.5):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * value_noise3(x, y, z, seed + o * 17)
        norm += amp
        amp *= gain
        x, y, z = x * lac, y * lac, z * lac
    return total / norm


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + b * t


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def gradient(y, stops):
    """stops: list of (elevation -1..1, color hex)."""
    col = np.zeros(y.shape + (3,))
    col[:] = hexc(stops[0][1])
    for (e0, c0), (e1, c1) in zip(stops, stops[1:]):
        t = smooth(e0, e1, y)
        m = (y >= e0)
        col = np.where(m[..., None], mix(hexc(c0), hexc(c1), t), col)
    return col


def sun_glow(x, y, z, sdir, color, power, strength):
    s = np.array(sdir, dtype=float)
    s = s / np.linalg.norm(s)
    d = np.clip(x * s[0] + y * s[1] + z * s[2], 0, 1)
    return (d ** power)[..., None] * hexc(color) * strength


def clouds(x, y, z, cover, scale, seed, soft=0.18, height=0.25):
    """Cloud density above the horizon, projected on a plane."""
    yy = np.maximum(y, 0.02)
    px, pz = x / (yy + height), z / (yy + height)
    n = fbm(px * scale, pz * scale, np.full_like(px, 3.1), 6, seed)
    dens = smooth(1 - cover, 1 - cover + soft, n)
    return dens * smooth(0.0, 0.12, y)


def stars(x, y, z, density, seed, size=1.0, color_var=0.3):
    """Sparse stars from a 3D grid hash; returns brightness."""
    cells = 120 * size
    gx, gy, gz = np.floor(x * cells), np.floor(y * cells), np.floor(z * cells)
    h = _hash3(gx.astype(np.int64), gy.astype(np.int64), gz.astype(np.int64), seed)
    h2 = _hash3(gx.astype(np.int64), gy.astype(np.int64), gz.astype(np.int64), seed + 99)
    on = h > 1 - density
    fx, fy, fz = x * cells - gx - 0.5, y * cells - gy - 0.5, z * cells - gz - 0.5
    dist = np.sqrt(fx * fx + fy * fy + fz * fz)
    b = np.where(on, np.clip(1.25 - dist * 2.0, 0, 1) * (0.55 + 0.75 * h2), 0)
    return b


def render(fn, n=FACE):
    atlas = np.zeros((2 * n, 3 * n, 3))
    for face in range(6):
        x, y, z = face_dirs(face, n)
        col, row = UV[face]
        atlas[row * n:(row + 1) * n, col * n:(col + 1) * n] = np.clip(fn(x, y, z), 0, 1)
    return atlas


def below(y, col, ground):
    """Fade into a hazy ground colour below the horizon."""
    return mix(col, np.broadcast_to(hexc(ground), col.shape), smooth(0.0, -0.25, y))


# ---------------------------------------------------------------- skies
def cherry_blossom(x, y, z):
    c = gradient(y, [(-1, "#E7B9C8"), (-0.05, "#FFD9E4"), (0.08, "#FFE1EA"), (0.35, "#F9BFD3"), (1, "#E69AB8")])
    c = c + sun_glow(x, y, z, (-0.8, 0.25, 0.3), "#FFE6C4", 6, 0.35)
    cl = clouds(x, y, z, 0.42, 1.6, 7)
    shade = 0.92 + 0.08 * fbm(x * 9, y * 9, z * 9, 3, 11)
    c = mix(c, np.stack([np.ones_like(x), 0.93 * shade, 0.95 * shade], -1), cl * 0.8)
    return below(y, c, "#D8A2B4")


def pink_sunset(x, y, z):
    c = gradient(y, [(-1, "#4B2E4C"), (-0.04, "#FF9E7A"), (0.04, "#FFB088"), (0.18, "#F37FA7"), (0.45, "#9B5FA8"), (1, "#3E3477")])
    c = c + sun_glow(x, y, z, (-1, 0.06, 0.1), "#FFD8A0", 24, 0.9) + sun_glow(x, y, z, (-1, 0.06, 0.1), "#FF8C6B", 4, 0.35)
    cl = clouds(x, y, z, 0.38, 1.3, 23, 0.25, 0.18)
    lit = mix(np.broadcast_to(hexc("#7C3F6E"), c.shape), np.broadcast_to(hexc("#FFB4A0"), c.shape),
              np.clip(-x * 0.7 + 0.4, 0, 1))
    c = mix(c, lit, cl * 0.85)
    return below(y, c, "#3A2440")


def night_base(x, y, z, top, horizon, ground, star_density, seed):
    c = gradient(y, [(-1, ground), (-0.03, horizon), (0.25, top), (1, top)])
    s = stars(x, y, z, star_density, seed) * smooth(-0.02, 0.15, y)
    return c + s[..., None] * np.array([1.0, 0.97, 1.0])


def sakura_night(x, y, z):
    c = night_base(x, y, z, "#120C2E", "#54305F", "#1A1024", 0.035, 3)
    c = c + sun_glow(x, y, z, (0.2, -0.1, -1), "#FF8FB8", 3, 0.25) * smooth(0.3, -0.05, y)[..., None]
    cl = clouds(x, y, z, 0.3, 1.5, 31, 0.3)
    c = mix(c, np.broadcast_to(hexc("#3A2550"), c.shape), cl * 0.6)
    return below(y, c, "#120A1A")


def starry_night(x, y, z):
    c = night_base(x, y, z, "#03050D", "#1B2647", "#050710", 0.05, 5)
    big = stars(x, y, z, 0.012, 77, 0.55) * smooth(0, 0.2, y)
    return below(y, c + big[..., None] * np.array([0.85, 0.9, 1.0]), "#04050B")


def galaxy(x, y, z):
    c = night_base(x, y, z, "#06040E", "#1C1433", "#05030A", 0.05, 9)
    # Galactic band: a great circle tilted across the sky.
    nrm = np.array([0.35, 0.55, 0.76])
    nrm = nrm / np.linalg.norm(nrm)
    band = 1 - np.abs(x * nrm[0] + y * nrm[1] + z * nrm[2])
    core = smooth(0.6, 1.0, band) ** 1.5
    neb = fbm(x * 2.2, y * 2.2, z * 2.2, 6, 41)
    neb2 = fbm(x * 6 + 4, y * 6, z * 6, 5, 43)
    glow = core * (0.45 + 0.8 * neb)
    col = mix(np.broadcast_to(hexc("#5B3FA8"), c.shape), np.broadcast_to(hexc("#E37BC8"), c.shape), neb2)
    col = mix(col, np.broadcast_to(hexc("#7FB8FF"), c.shape), smooth(0.6, 0.9, neb))
    dust = smooth(0.6, 0.8, fbm(x * 5, y * 5, z * 5, 4, 47)) * core * smooth(0.85, 1.0, band)
    c = c + col * glow[..., None] * 0.7 - dust[..., None] * 0.12
    dense = stars(x, y, z, 0.25, 53, 1.3) * core
    c = c + dense[..., None] * 0.8
    return below(y, np.clip(c, 0, 1), "#05030A")


def fantasy_clouds(x, y, z):
    c = gradient(y, [(-1, "#C6B8E8"), (-0.03, "#FFE9F6"), (0.15, "#DCD6FF"), (0.5, "#A9CBFF"), (1, "#86B6FA")])
    cl = clouds(x, y, z, 0.5, 1.1, 61, 0.12, 0.35)
    top = fbm(x * 5, (y + 0.05) * 5, z * 5, 4, 67)
    cloud_col = mix(np.broadcast_to(hexc("#E7D2FF"), c.shape), np.broadcast_to(hexc("#FFF8FD"), c.shape), top * 1.4 - 0.2)
    pinkish = smooth(0.0, 0.3, y)
    cloud_col = mix(cloud_col, np.broadcast_to(hexc("#FFD6EA"), c.shape), (1 - pinkish) * 0.6)
    c = mix(c, cloud_col, cl)
    return below(y, c, "#B9A8D9")


def aurora(x, y, z):
    c = night_base(x, y, z, "#030B12", "#0E2A33", "#030709", 0.05, 13)
    # Curtains in the northern sky (-Z), waving with longitude.
    lon = np.arctan2(x, -z)
    north = smooth(-0.35, 0.5, -z / np.maximum(np.sqrt(x * x + z * z), 1e-4))
    wave = 0.22 + 0.06 * np.sin(lon * 5 + 1.3) + 0.04 * np.sin(lon * 13 + 0.4)
    height = y - wave
    curtain = smooth(-0.04, 0.02, height) * (1 - smooth(0.02, 0.45, height))
    rays = 0.55 + 0.45 * fbm(lon * 18, y * 2, np.zeros_like(y), 4, 71)
    strength = curtain * rays * north
    green = np.broadcast_to(hexc("#3DFFB0"), c.shape)
    violet = np.broadcast_to(hexc("#A46BFF"), c.shape)
    col = mix(green, violet, smooth(0.05, 0.35, height))
    c = c + col * strength[..., None] * 0.9
    c = c + (smooth(0.25, -0.05, y) * north * 0.12)[..., None] * hexc("#2EE59A")
    return below(y, np.clip(c, 0, 1), "#02060A")


def dreamy_pastel(x, y, z):
    t = fbm(x * 1.5, y * 1.5, z * 1.5, 4, 81)
    c = gradient(y, [(-1, "#CDBBE6"), (-0.02, "#C8F2E0"), (0.12, "#FFDCC8"), (0.4, "#F2C9EC"), (1, "#D5C3F2")])
    c = mix(c, np.broadcast_to(hexc("#FFE0EE"), c.shape), smooth(0.45, 0.75, t) * 0.5)
    cl = clouds(x, y, z, 0.35, 1.4, 83, 0.3, 0.3)
    c = mix(c, np.broadcast_to(hexc("#FFF6FB"), c.shape), cl * 0.7)
    return below(y, c, "#BFAFD8")


def clear_blue(x, y, z):
    c = gradient(y, [(-1, "#8FB3D6"), (-0.02, "#D6ECFF"), (0.1, "#BFE0FF"), (0.45, "#6FA9EE"), (1, "#3B7BDB")])
    c = c + sun_glow(x, y, z, (0.5, 0.7, 0.2), "#FFFFFF", 12, 0.25)
    return below(y, c, "#7F9EBD")


def crimson_dusk(x, y, z):
    """BLOOD's default day sky: a low red sun under heavy crimson cloud."""
    c = gradient(y, [(-1, "#2A0B10"), (-0.04, "#C2412E"), (0.03, "#E2583A"), (0.16, "#A8243A"), (0.45, "#5A1024"), (1, "#1E0710")])
    sun = (0.9, 0.08, -0.3)
    c = c + sun_glow(x, y, z, sun, "#FFC08A", 40, 1.0) + sun_glow(x, y, z, sun, "#FF5A3A", 5, 0.45)
    cl = clouds(x, y, z, 0.45, 1.25, 101, 0.22, 0.2)
    toward = np.clip(x * 0.6 + 0.45, 0, 1)
    lit = mix(np.broadcast_to(hexc("#3B0C18"), c.shape), np.broadcast_to(hexc("#FF8A5C"), c.shape), toward * smooth(0.0, 0.35, 1 - y))
    c = mix(c, lit, cl * 0.85)
    return below(y, np.clip(c, 0, 1), "#1C070C")


def blood_moon(x, y, z):
    """BLOOD's default night sky: a huge red moon over a near-black crimson sky."""
    c = night_base(x, y, z, "#050103", "#3A0712", "#080204", 0.03, 107)
    m = np.array([-0.35, 0.42, -0.84])
    m = m / np.linalg.norm(m)
    d = x * m[0] + y * m[1] + z * m[2]
    disc = smooth(0.9915, 0.9935, d)
    craters = 0.82 + 0.18 * fbm(x * 40, y * 40, z * 40, 3, 109)
    moon_col = mix(np.broadcast_to(hexc("#9C1A22"), c.shape), np.broadcast_to(hexc("#FF6A55"), c.shape), smooth(0.9915, 1.0, d) * craters)
    c = mix(c, moon_col, disc)
    c = c + (smooth(0.9, 0.995, d) ** 2 * (1 - disc))[..., None] * hexc("#FF2A3A") * 0.55
    c = c + (smooth(0.6, 0.99, d) ** 3)[..., None] * hexc("#7A0A18") * 0.35
    haze = smooth(0.18, -0.02, y) * smooth(-0.1, 0.0, y)
    c = c + haze[..., None] * hexc("#6E0A18") * 0.6
    cl = clouds(x, y, z, 0.28, 1.6, 113, 0.3, 0.25)
    c = mix(c, np.broadcast_to(hexc("#1A0508"), c.shape), cl * 0.7 * (1 - disc))
    return below(y, np.clip(c, 0, 1), "#050103")


SKIES = {
    "crimson_dusk": crimson_dusk,
    "blood_moon": blood_moon,
    "cherry_blossom": cherry_blossom,
    "pink_sunset": pink_sunset,
    "sakura_night": sakura_night,
    "starry_night": starry_night,
    "galaxy": galaxy,
    "fantasy_clouds": fantasy_clouds,
    "aurora": aurora,
    "dreamy_pastel": dreamy_pastel,
    "clear_blue": clear_blue,
}


def preview(fn, w=128, h=72):
    """A view looking north-west, slightly up (what the menu card shows)."""
    yaw = np.radians(-30)
    fov = np.radians(80)
    px = (np.arange(w) + 0.5) / w * 2 - 1
    py = (np.arange(h) + 0.5) / h * 2 - 1
    PX, PY = np.meshgrid(px, py)
    tan = np.tan(fov / 2)
    dx, dy, dz = PX * tan, -PY * tan * h / w + 0.25, -np.ones_like(PX)
    cx, sx = np.cos(yaw), np.sin(yaw)
    rx, rz = dx * cx - dz * sx, dx * sx + dz * cx
    l = np.sqrt(rx * rx + dy * dy + rz * rz)
    return np.clip(fn(rx / l, dy / l, rz / l), 0, 1)


def save(arr, path):
    img = Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def launcher_previews():
    """Larger previews for the launcher's Customize page (apps/launcher/public/bloomclient/sky)."""
    out = os.path.join(ROOT, "..", "launcher", "public", "bloomclient", "sky")
    os.makedirs(out, exist_ok=True)
    for sid, fn in SKIES.items():
        img = preview(fn, 384, 216)
        img = img + (rng.random(img.shape) - 0.5) / 255.0
        Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB").save(os.path.join(out, sid + ".png"), optimize=True)
        print("launcher preview", sid)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "launcher":
        launcher_previews()
        return
    only = sys.argv[2:] if len(sys.argv) > 2 else list(SKIES)
    for sid in only:
        fn = SKIES[sid]
        atlas = render(fn)
        # Light dithering hides gradient banding after 8-bit quantisation.
        atlas = atlas + (rng.random(atlas.shape) - 0.5) / 255.0
        save(atlas, os.path.join(RES, "skybox", sid + ".png"))
        with open(os.path.join(RES, "skybox", sid + ".png.mcmeta"), "w") as f:
            f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
        save(preview(fn), os.path.join(RES, "gui", "sky_preview", sid + ".png"))
        print("generated", sid, os.path.getsize(os.path.join(RES, "skybox", sid + ".png")) // 1024, "KB")


if __name__ == "__main__":
    main()
