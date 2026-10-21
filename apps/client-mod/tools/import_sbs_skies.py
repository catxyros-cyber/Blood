#!/usr/bin/env python3
"""
Turns Screaming Brain Studios' "Cloudy Skyboxes" (CC0, 25 equirectangular 2:1 panoramas) into
BLOOD Client built-in skyboxes:

    common/src/main/resources/assets/bloomclient/textures/skybox/<id>.png        3x2 cube atlas
    common/src/main/resources/assets/bloomclient/textures/skybox/<id>.png.mcmeta  linear + clamp
    common/src/main/resources/assets/bloomclient/textures/gui/sky_preview/<id>.png  128x72 card
    ../launcher/public/bloomclient/sky/<id>.png                                   384x216 card

The mapping is the one the mod uses at runtime (core/sky/Panorama.java + CubeLayout.java):
longitude 0 = north (-Z) increasing towards east (+X), latitude +90 = straight up. Sampling is
bilinear with horizontal wrap-around, so the panorama's left/right edge leaves no seam, and every
face pixel is computed from its true direction, so neighbouring faces meet exactly.

Usage:
    python3 tools/import_sbs_skies.py <folder with Panorama_Sky_NN-*.png> [--face 512] [--check]
"""
import argparse
import glob
import os
import re

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "common/src/main/resources/assets/bloomclient/textures")
LAUNCHER_SKY = os.path.join(ROOT, "..", "launcher", "public", "bloomclient", "sky")

# number -> (id, name). Ids are stable: they're stored in players' settings and profiles.
SKIES = {
    1: ("sbs_silver_morning", "Silver Morning"),
    2: ("sbs_golden_haze", "Golden Haze"),
    3: ("sbs_amber_dusk", "Amber Dusk"),
    4: ("sbs_summer_blue", "Summer Blue"),
    5: ("sbs_fair_weather", "Fair Weather"),
    6: ("sbs_clear_noon", "Clear Noon"),
    7: ("sbs_lavender_clouds", "Lavender Clouds"),
    8: ("sbs_soft_daylight", "Soft Daylight"),
    9: ("sbs_storm_gold", "Storm Gold"),
    10: ("sbs_overcast", "Overcast"),
    11: ("sbs_violet_night", "Violet Night"),
    12: ("sbs_slate_dusk", "Slate Dusk"),
    13: ("sbs_sand_fog", "Sand Fog"),
    14: ("sbs_emerald_haze", "Emerald Haze"),
    15: ("sbs_midnight_glow", "Midnight Glow"),
    16: ("sbs_cloud_tide", "Cloud Tide"),
    17: ("sbs_plum_sunset", "Plum Sunset"),
    18: ("sbs_steel_horizon", "Steel Horizon"),
    19: ("sbs_crimson_storm", "Crimson Storm"),
    20: ("sbs_whiteout", "Whiteout"),
    21: ("sbs_fire_sunset", "Fire Sunset"),
    22: ("sbs_rust_sunset", "Rust Sunset"),
    23: ("sbs_rose_swirl", "Rose Swirl"),
    24: ("sbs_pale_clouds", "Pale Clouds"),
    25: ("sbs_dream_mist", "Dream Mist"),
}

# Face order and UV rectangles exactly as CubeLayout.UV: {minU, minV, maxU, maxV}.
BOTTOM, NORTH, SOUTH, TOP, EAST, WEST = range(6)
UV = [(0, 0, 1 / 3, 0.5), (1 / 3, 0.5, 2 / 3, 1), (2 / 3, 0, 1, 0.5), (1 / 3, 0, 2 / 3, 0.5), (2 / 3, 0.5, 1, 1), (0, 0.5, 1 / 3, 1)]


def direction(face, s, t):
    """CubeLayout.direction (unnormalised). Minecraft axes: +X east, +Y up, +Z south."""
    a, b = 2 * s - 1, 2 * t - 1
    one = np.ones_like(a)
    if face == BOTTOM:
        return a, -one, b
    if face == NORTH:
        return a, -b, -one
    if face == SOUTH:
        return -a, -b, one
    if face == TOP:
        return a, one, -b
    if face == EAST:
        return one, -b, a
    if face == WEST:
        return -one, -b, -a
    raise ValueError(face)


class Pano:
    """Bilinear sampling of an equirectangular image by direction (Panorama.java's mapping)."""

    def __init__(self, img):
        self.a = np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0
        self.h, self.w = self.a.shape[:2]

    def sample(self, x, y, z):
        l = np.sqrt(x * x + y * y + z * z)
        x, y, z = x / l, y / l, z / l
        lon = np.arctan2(x, -z)
        lat = np.arcsin(np.clip(y, -1, 1))
        u = (lon / (2 * np.pi) + 0.5) * self.w - 0.5
        v = (0.5 - lat / np.pi) * self.h - 0.5
        u0 = np.floor(u).astype(int)
        v0 = np.floor(v).astype(int)
        fu, fv = (u - u0)[..., None], (v - v0)[..., None]
        u1 = u0 + 1
        v1 = v0 + 1
        u0 %= self.w
        u1 %= self.w
        v0 = np.clip(v0, 0, self.h - 1)
        v1 = np.clip(v1, 0, self.h - 1)
        a = self.a
        top = a[v0, u0] * (1 - fu) + a[v0, u1] * fu
        bot = a[v1, u0] * (1 - fu) + a[v1, u1] * fu
        return top * (1 - fv) + bot * fv


def to_atlas(pano, face):
    """Edge texels sit exactly on the cube edge (s = i / (face - 1)). The renderer insets each face
    by half a texel, so both faces sharing an edge show the same colours there: no seam."""
    out = np.zeros((face * 2, face * 3, 3), dtype=np.float32)
    idx = np.arange(face) / (face - 1)
    S, T = np.meshgrid(idx, idx)
    for f in range(6):
        x0, y0 = round(UV[f][0] * face * 3), round(UV[f][1] * face * 2)
        out[y0:y0 + face, x0:x0 + face] = pano.sample(*direction(f, S, T))
    return out


class Atlas:
    """Samples an atlas by direction, the way the GPU sees the drawn cube (for the seam check)."""

    def __init__(self, arr):
        self.a = arr
        self.face = arr.shape[0] // 2

    def sample(self, x, y, z):
        ax, ay, az = np.abs(x), np.abs(y), np.abs(z)
        out = np.zeros(x.shape + (3,), dtype=np.float32)
        F = self.face
        # Invert CubeLayout.direction per dominant axis.
        cases = [
            ((ay >= ax) & (ay >= az) & (y < 0), BOTTOM, lambda: (x / ay, z / ay)),
            ((ay >= ax) & (ay >= az) & (y >= 0), TOP, lambda: (x / ay, -z / ay)),
            ((az > ax) & (az > ay) & (z < 0), NORTH, lambda: (x / az, -y / az)),
            ((az > ax) & (az > ay) & (z >= 0), SOUTH, lambda: (-x / az, -y / az)),
            ((ax > ay) & (ax >= az) & (x >= 0), EAST, lambda: (z / ax, -y / ax)),
            ((ax > ay) & (ax >= az) & (x < 0), WEST, lambda: (-z / ax, -y / ax)),
        ]
        for mask, f, ab in cases:
            if not mask.any():
                continue
            a, b = ab()
            s, t = (a[mask] + 1) / 2, (b[mask] + 1) / 2
            # SkyRenderer insets each face by half a texel, so s=0..1 lands on texel centres 0..F-1.
            px = np.clip(s * (F - 1), 0, F - 1)
            py = np.clip(t * (F - 1), 0, F - 1)
            x0, y0 = np.floor(px).astype(int), np.floor(py).astype(int)
            x1, y1 = np.minimum(x0 + 1, F - 1), np.minimum(y0 + 1, F - 1)
            fx, fy = (px - x0)[:, None], (py - y0)[:, None]
            ox, oy = round(UV[f][0] * F * 3), round(UV[f][1] * F * 2)
            A = self.a
            top = A[oy + y0, ox + x0] * (1 - fx) + A[oy + y0, ox + x1] * fx
            bot = A[oy + y1, ox + x0] * (1 - fx) + A[oy + y1, ox + x1] * fx
            out[mask] = top * (1 - fy) + bot * fy
        return out


def view(fn, w, h, yaw_deg=-30.0, pitch_up=0.25, fov_deg=80.0):
    """Same camera as tools/gen_skyboxes.py preview(): looking north-west, slightly up."""
    yaw, fov = np.radians(yaw_deg), np.radians(fov_deg)
    px = (np.arange(w) + 0.5) / w * 2 - 1
    py = (np.arange(h) + 0.5) / h * 2 - 1
    PX, PY = np.meshgrid(px, py)
    tan = np.tan(fov / 2)
    dx, dy, dz = PX * tan, -PY * tan * h / w + pitch_up, -np.ones_like(PX)
    cx, sx = np.cos(yaw), np.sin(yaw)
    rx, rz = dx * cx - dz * sx, dx * sx + dz * cx
    return np.clip(fn(rx, dy, rz), 0, 1)


def save(arr, path, dither=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if dither:
        # Optional noise against banding. Off by default: the sources are 8-bit already, and noise
        # makes the PNGs far larger.
        rng = np.random.default_rng(len(path))
        arr = arr + (rng.random(arr.shape[:2] + (1,)) - 0.5) / 255.0
    Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB").save(path, optimize=True)


def seam_error(pano, atlas):
    """Max colour difference between the cube (as drawn) and the panorama, over views that
    cross every face edge and corner. 8-bit quantisation alone gives ~2/255."""
    a = Atlas(atlas)
    worst = 0.0
    for yaw in range(-180, 180, 45):
        for pitch in (-1.2, -0.5, 0.0, 0.5, 1.2):
            ref = view(pano.sample, 160, 90, yaw, pitch, 100)
            got = view(a.sample, 160, 90, yaw, pitch, 100)
            worst = max(worst, float(np.abs(ref - got).max()))
    return worst


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("--face", type=int, default=512)
    ap.add_argument("--check", action="store_true", help="measure seams against the source panorama")
    args = ap.parse_args()
    files = {}
    for p in glob.glob(os.path.join(args.folder, "Panorama_Sky_*.png")):
        m = re.search(r"Panorama_Sky_(\d+)", os.path.basename(p))
        if m:
            files[int(m.group(1))] = p
    missing = sorted(set(SKIES) - set(files))
    if missing:
        raise SystemExit(f"missing panoramas: {missing}")
    for n, (sid, name) in SKIES.items():
        img = Image.open(files[n])
        if img.width != img.height * 2:
            raise SystemExit(f"{files[n]} is not a 2:1 panorama ({img.width}x{img.height})")
        pano = Pano(img)
        atlas = to_atlas(pano, args.face)
        save(atlas, os.path.join(RES, "skybox", sid + ".png"))
        with open(os.path.join(RES, "skybox", sid + ".png.mcmeta"), "w") as f:
            f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
        save(view(pano.sample, 128, 72), os.path.join(RES, "gui", "sky_preview", sid + ".png"))
        save(view(pano.sample, 384, 216), os.path.join(LAUNCHER_SKY, sid + ".png"))
        line = f"{sid:24s} {name}"
        if args.check:
            line += f"  seam/max error {seam_error(pano, atlas) * 255:.1f}/255"
        print(line)


if __name__ == "__main__":
    main()
