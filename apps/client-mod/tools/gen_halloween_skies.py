#!/usr/bin/env python3
"""
BLOOD's Halloween skyboxes, generated procedurally at 4K: 3x2 cube atlases with 1024 px faces
(3072x2048 px, the same detail per degree as a 4096x2048 panorama). Everything here is original
and generated from code. No photos or third-party art are used.

Every pixel is computed from its true view direction, with edge texels exactly on the cube edges
(the renderer insets each face by half a texel), so faces meet without seams and moons are round.
Stars use a jittered hash grid (no visible lattice), moons are shaded in their own tangent plane
(craters, maria, limb darkening, an anti-aliased edge), and the output is dithered against banding.

    python3 tools/gen_halloween_skies.py              all skies
    python3 tools/gen_halloween_skies.py hw_eclipse   one sky
    python3 tools/gen_halloween_skies.py --face 256   quick low-res test

Writes the same files as tools/import_sbs_skies.py (atlas, .mcmeta, menu preview, launcher preview).
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen_skyboxes import fbm, hexc, mix, smooth, value_noise3  # noqa: E402
from import_sbs_skies import UV, direction, view  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "common/src/main/resources/assets/bloomclient/textures")
LAUNCHER_SKY = os.path.join(ROOT, "..", "launcher", "public", "bloomclient", "sky")
PX = np.pi / 2 / 1024  # angular size of one texel at 1024 px faces (anti-aliasing width)
STAR_PX = np.pi / 2 / 1024  # star sizes are defined in 4K texels


# ---------------------------------------------------------------- building blocks

def unit(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


def grad(y, stops):
    """Smooth vertical gradient. stops: [(elevation -1..1, '#rrggbb'), ...]."""
    col = np.broadcast_to(hexc(stops[0][1]), y.shape + (3,)).copy()
    for (e0, c0), (e1, c1) in zip(stops, stops[1:]):
        t = smooth(e0, e1, y)[..., None]
        col = np.where((y >= e0)[..., None], hexc(c0) * (1 - t) + hexc(c1) * t, col)
    return col


def c3(h, shape):
    return np.broadcast_to(hexc(h), shape + (3,))


def stars(x, y, z, density, seed, cells=260, bright=1.0, warm=0.35):
    """Point stars on a jittered 3D hash grid: random position inside each cell, magnitude follows
    a steep power law (few bright, many faint), slight colour temperature variation."""
    out = np.zeros(x.shape + (3,))
    for layer, (cs, dens, b) in enumerate([(cells, density, bright), (cells * 1.9, density * 1.6, bright * 0.45)]):
        gx, gy, gz = np.floor(x * cs), np.floor(y * cs), np.floor(z * cs)
        ix, iy, iz = gx.astype(np.int64), gy.astype(np.int64), gz.astype(np.int64)
        s = seed + layer * 1000
        h = value_noise3(ix + 0.0, iy + 0.0, iz + 0.0, s)  # hash at integer points = cell hash
        on = h > 1 - dens
        jx = value_noise3(ix + 0.0, iy + 0.0, iz + 0.0, s + 1) * 0.7 + 0.15
        jy = value_noise3(ix + 0.0, iy + 0.0, iz + 0.0, s + 2) * 0.7 + 0.15
        jz = value_noise3(ix + 0.0, iy + 0.0, iz + 0.0, s + 3) * 0.7 + 0.15
        mag = value_noise3(ix + 0.0, iy + 0.0, iz + 0.0, s + 4)
        tint = value_noise3(ix + 0.0, iy + 0.0, iz + 0.0, s + 5)
        fx, fy, fz = x * cs - gx - jx, y * cs - gy - jy, z * cs - gz - jz
        # Distance in 4K texels: stars keep the same size and brightness at any resolution
        # (at 8K each star simply gets more pixels).
        d = np.sqrt(fx * fx + fy * fy + fz * fz) / cs / STAR_PX
        core = np.exp(-(d / 0.75) ** 2)
        lum = np.where(on, core * b * (0.15 + 0.85 * mag ** 4), 0.0)
        col = mix(c3("#BFD3FF", x.shape), c3("#FFE2C0", x.shape), tint * warm * 2)
        out += lum[..., None] * col
    return out


def clouds(x, y, z, cover, scale, seed, soft=0.25, height=0.22, octaves=8):
    """Cloud density on a plane above the camera (perspective towards the horizon)."""
    yy = np.maximum(y, 0.015)
    px, pz = x / (yy + height), z / (yy + height)
    n = fbm(px * scale, pz * scale, np.full_like(px, 3.7 + seed * 0.01), octaves, seed)
    # fbm here has median ~0.43 and spread ~0.11: map "cover" (0..1 of the sky) to a threshold.
    thr = 0.43 + 0.24 * (0.5 - cover)
    return smooth(thr - soft * 0.25, thr + soft * 0.25, n) * smooth(-0.01, 0.1, y)


def tangent(x, y, z, center):
    """Gnomonic coordinates (u, v) around a direction: undistorted discs, in radians near the centre."""
    m = unit(center)
    up = np.array([0.0, 1.0, 0.0]) if abs(m[1]) < 0.95 else np.array([1.0, 0.0, 0.0])
    e1 = unit(np.cross(up, m))
    e2 = np.cross(m, e1)
    d = x * m[0] + y * m[1] + z * m[2]
    dd = np.maximum(d, 1e-4)
    u = (x * e1[0] + y * e1[1] + z * e1[2]) / dd
    v = (x * e2[0] + y * e2[1] + z * e2[2]) / dd
    return d, u, v


def moon(x, y, z, center, radius, lit="#EDE6DA", dark="#7C7468", seed=1, craters=90, phase=None):
    """A detailed moon in its own tangent plane (round, undistorted):
    - maria (dark plains) from low-frequency noise, fine regolith texture from high-frequency noise;
    - craters with a power-law size spread (many small, few large), each lit from one side: a bright
      sunward rim, a shadowed far wall and a softly darker floor, plus faint ejecta around big ones;
    - limb darkening and a 1-texel anti-aliased edge; optional phase terminator.
    Returns (rgb, alpha)."""
    d, u, v = tangent(x, y, z, center)
    front = d > 0
    r = np.sqrt(u * u + v * v)
    alpha = np.where(front, smooth(radius + PX * 1.2, radius - PX * 1.2, r), 0.0)
    inside = alpha > 0
    shade_full = np.zeros_like(u)
    if inside.any():
        pu, pv = u[inside] / radius, v[inside] / radius
        one = np.ones_like(pu)
        maria = smooth(0.46, 0.6, fbm(pu * 1.5 + 5, pv * 1.5, one * 1.3, 5, seed))
        fine = fbm(pu * 14, pv * 14, one * 7.7, 6, seed + 3)
        grain = fbm(pu * 60, pv * 60, one * 2.1, 3, seed + 4)
        shade = 0.9 + 0.12 * (fine - 0.5) + 0.06 * (grain - 0.5) - 0.3 * maria
        lx, ly = -0.6, -0.8  # light direction in the tangent plane (from upper left)
        rng = np.random.default_rng(seed)
        for _ in range(craters):
            cr = 0.018 * (1 - rng.random()) ** -0.9  # power law: mostly small
            cr = min(cr, 0.22)
            cu, cv = rng.uniform(-0.95, 0.95, 2)
            if cu * cu + cv * cv > 0.92:
                continue
            du, dv = pu - cu, pv - cv
            dist = np.sqrt(du * du + dv * dv) / cr
            near = dist < 2.2
            if not near.any():
                continue
            dn = dist[near]
            side = (du[near] * lx + dv[near] * ly) / (np.sqrt(du[near] ** 2 + dv[near] ** 2) + 1e-6)  # -1 sunward .. 1 away
            floor = -0.10 * np.clip(1 - dn * dn, 0, 1)
            wall = np.exp(-((dn - 0.92) / 0.14) ** 2)
            lit_rim = 0.16 * wall * np.clip(-side, 0, 1)
            shadow = -0.20 * wall * np.clip(side, 0, 1) - 0.08 * np.clip(1 - dn, 0, 1) * np.clip(side, 0, 1)
            ejecta = 0.05 * np.exp(-((dn - 1.4) / 0.35) ** 2) if cr > 0.08 else 0.0
            shade[near] += floor + lit_rim + shadow + ejecta
        rr = np.clip(r[inside] / radius, 0, 1)
        light = np.sqrt(np.clip(1 - rr * rr, 0, 1)) ** 0.4
        if phase is not None:
            lu, lv = phase
            z3 = np.sqrt(np.clip(1 - rr * rr, 0, 1))
            light = light * smooth(-0.15, 0.25, pu * lu + pv * lv + z3 * 0.35)
        shade_full[inside] = np.clip(shade * light, 0, 1.25)
    col = mix(c3(dark, x.shape), c3(lit, x.shape), shade_full)
    return col, alpha


def glow(x, y, z, center, color, width, strength):
    d, _, _ = tangent(x, y, z, center)
    ang = np.arccos(np.clip(d, -1, 1))
    return (np.exp(-ang / width) * strength)[..., None] * hexc(color)


def nebula(x, y, z, seed, colors, strength, scale=1.4):
    n1 = fbm(x * scale + 3, y * scale, z * scale, 7, seed)
    n2 = fbm(x * scale * 2.3, y * scale * 2.3 + 9, z * scale * 2.3, 6, seed + 5)
    dens = smooth(0.52, 0.8, n1) * (0.6 + 0.4 * n2)
    col = mix(c3(colors[0], x.shape), c3(colors[1], x.shape), n2)
    return dens[..., None] * col * strength


def treeline(x, y, z, seed, base, height, density, sharp=1.0):
    """Coniferous forest silhouette along the horizon, as a function of azimuth.
    Returns 0..1 coverage (1 = inside a tree), anti-aliased."""
    az = np.arctan2(x, -z)  # -pi..pi
    elev = np.arcsin(np.clip(y, -1, 1))
    prof = np.full_like(az, base)
    rng = np.random.default_rng(seed)
    n = int(density)
    centers = rng.uniform(-np.pi, np.pi, n)
    heights = base + height * (0.35 + 0.65 * rng.random(n) ** 1.6)
    widths = (heights - base + 0.02) * rng.uniform(0.28, 0.42, n) * sharp
    for c, h, w in zip(centers, heights, widths):
        da = np.abs(np.angle(np.exp(1j * (az - c))))
        tri = h - (h - base) * (da / w)
        # small jagged branches
        tri = tri - 0.004 * (np.sin(da / w * 38) > 0.3)
        prof = np.maximum(prof, np.where(da < w, tri, base))
    return smooth(PX, -PX, elev - prof)


def bat(x, y, z, center, size, flap=0.0):
    """A small bat silhouette (coverage 0..1) drawn in the tangent plane."""
    d, u, v = tangent(x, y, z, center)
    pu, pv = u / size, v / size
    body = ((pu / 0.18) ** 2 + ((pv + 0.05) / 0.32) ** 2) < 1
    head = ((pu / 0.12) ** 2 + ((pv - 0.32) / 0.12) ** 2) < 1
    ears = (np.abs(np.abs(pu) - 0.08) < 0.04) & (pv > 0.3) & (pv < 0.5)
    ax = np.abs(pu)
    top = 0.15 + 0.35 * np.sin(np.clip(ax, 0, 1) * np.pi * 0.9) * (1 - flap) - 0.1 * flap
    scallop = -0.05 - 0.22 * np.abs(np.sin(ax * np.pi * 3.0)) * (ax < 1)
    wing = (ax < 1.0) & (pv < top) & (pv > scallop + top - 0.45)
    return np.where(d > 0, (body | head | ears | wing).astype(float), 0.0)


def ground(y, col, color, start=0.0, end=-0.3):
    return mix(col, c3(color, y.shape), smooth(start, end, y))


# ---------------------------------------------------------------- the skies

MOON_DIR = unit([-0.35, 0.38, -0.86])


def blood_moon(x, y, z):
    sh = x.shape
    c = grad(y, [(-1, "#030102"), (-0.05, "#120306"), (0.02, "#2A050C"), (0.2, "#160307"), (0.45, "#080203"), (1, "#020101")])
    c += nebula(x, y, z, 11, ["#3A0610", "#16040A"], 0.55)
    c += stars(x, y, z, 0.055, 21) * smooth(0.0, 0.25, y)[..., None]
    m, a = moon(x, y, z, MOON_DIR, 0.13, lit="#E8463C", dark="#3A0509", seed=31)
    c += glow(x, y, z, MOON_DIR, "#C0141F", 0.06, 0.9) + glow(x, y, z, MOON_DIR, "#5A0610", 0.35, 0.5)
    c = mix(c, m, a)
    cl = clouds(x, y, z, 0.34, 1.7, 41, 0.3)
    c = mix(c, c3("#140306", sh), cl * 0.8 * (1 - a))
    c += (smooth(0.2, -0.01, y) * smooth(-0.1, 0.0, y))[..., None] * hexc("#5A0810") * 0.3
    return ground(y, c, "#030102")


def halloween_night(x, y, z):
    sh = x.shape
    mdir = unit([0.4, 0.32, -0.86])
    c = grad(y, [(-1, "#05040A"), (-0.03, "#2A1408"), (0.03, "#5A2A10"), (0.12, "#1A1220"), (0.4, "#0A0A18"), (1, "#05050C")])
    c += stars(x, y, z, 0.05, 22) * smooth(0.05, 0.3, y)[..., None]
    m, a = moon(x, y, z, mdir, 0.085, lit="#FFF4DC", dark="#8C8070", seed=32)
    c += glow(x, y, z, mdir, "#FFE6B0", 0.05, 0.55) + glow(x, y, z, mdir, "#6A5A80", 0.4, 0.3)
    c = mix(c, m, a)
    cl = clouds(x, y, z, 0.48, 1.4, 42, 0.32)
    d, _, _ = tangent(x, y, z, mdir)
    rim = np.clip(d, 0, 1) ** 160 * 0.8
    cloud_col = mix(c3("#141018", sh), c3("#C8B8A8", sh), rim)
    c = mix(c, cloud_col, cl * 0.88 * (1 - a * 0.9))
    for i, (b, s) in enumerate([([0.3, 0.36, -0.88], 0.012), ([0.48, 0.29, -0.83], 0.008), ([0.36, 0.25, -0.9], 0.006),
                                ([-0.5, 0.3, -0.81], 0.007), ([-0.42, 0.34, -0.84], 0.005)]):
        c = mix(c, c3("#050308", sh), bat(x, y, z, b, s, flap=(i % 3) * 0.4))
    c += (smooth(0.1, -0.01, y) * smooth(-0.06, 0.0, y))[..., None] * hexc("#E06A18") * 0.35
    return ground(y, c, "#05040A")


def pumpkin_sky(x, y, z):
    sh = x.shape
    mdir = unit([-0.6, 0.2, -0.77])
    c = grad(y, [(-1, "#120502"), (-0.04, "#5A1A06"), (0.0, "#D0581A"), (0.06, "#A8360E"), (0.2, "#4A160C"), (0.5, "#140812"), (1, "#07040A")])
    c += stars(x, y, z, 0.045, 23) * smooth(0.2, 0.45, y)[..., None]
    m, a = moon(x, y, z, mdir, 0.1, lit="#FFC27A", dark="#8A4A20", seed=33)
    c += glow(x, y, z, mdir, "#FF9A40", 0.07, 0.6)
    c = mix(c, m, a)
    cl = clouds(x, y, z, 0.52, 1.25, 43, 0.28)
    d, _, _ = tangent(x, y, z, mdir)
    lit = smooth(0.25, 0.0, y) * 0.7 + np.clip(d, 0, 1) ** 16 * 0.6
    cloud_col = mix(c3("#1E0A08", sh), c3("#FF8A3A", sh), np.clip(lit, 0, 1))
    c = mix(c, cloud_col, cl * 0.9 * (1 - a))
    tree = treeline(x, y, z, 53, 0.0, 0.05, 240, 0.9)
    c = mix(c, c3("#0A0402", sh), tree)
    return ground(y, c, "#0A0402", -0.005, -0.08)


def haunted_forest(x, y, z):
    sh = x.shape
    mdir = unit([0.15, 0.45, -0.88])
    c = grad(y, [(-1, "#040806"), (-0.02, "#1A2A26"), (0.04, "#22342E"), (0.18, "#0E1A1C"), (0.5, "#060C10"), (1, "#03060A")])
    c += stars(x, y, z, 0.05, 24) * smooth(0.15, 0.4, y)[..., None]
    m, a = moon(x, y, z, mdir, 0.07, lit="#E6F0E8", dark="#7A8A84", seed=34)
    c += glow(x, y, z, mdir, "#B8D8D0", 0.06, 0.45) + glow(x, y, z, mdir, "#3A5A58", 0.5, 0.25)
    c = mix(c, m, a)
    cl = clouds(x, y, z, 0.3, 1.9, 44, 0.35)
    c = mix(c, c3("#1A2624", sh), cl * 0.55 * (1 - a))
    # Three forest layers: distant (pale, foggy) to near (black).
    for seed, base, h, n, colr, fog in [(61, 0.0, 0.035, 300, "#26382F", 0.0), (62, -0.005, 0.06, 200, "#14201B", 0.0),
                                        (63, -0.01, 0.1, 110, "#050806", 0.0)]:
        t = treeline(x, y, z, seed, base, h, n)
        c = mix(c, c3(colr, sh), t)
        mist = smooth(base + h * 0.6, base, y) * smooth(base - 0.04, base, y) * 0.35
        c = mix(c, c3("#5A6E68", sh), mist)
    return ground(y, c, "#030504", -0.01, -0.1)


def midnight_purple(x, y, z):
    sh = x.shape
    mdir = unit([-0.25, 0.5, -0.83])
    c = grad(y, [(-1, "#05030C"), (-0.03, "#2A1440"), (0.04, "#20103A"), (0.25, "#0E0A28"), (0.6, "#06061A"), (1, "#030310")])
    c += nebula(x, y, z, 15, ["#7A2AB0", "#2A3AA0"], 0.75, 1.2)
    c += nebula(x, y, z, 16, ["#C050D0", "#4020A0"], 0.35, 2.6)
    c += stars(x, y, z, 0.07, 25, bright=1.15)
    m, a = moon(x, y, z, mdir, 0.075, lit="#F2ECFF", dark="#7A7090", seed=35, phase=(0.8, 0.3))
    c += glow(x, y, z, mdir, "#C8B8FF", 0.05, 0.5) + glow(x, y, z, mdir, "#5A3AA0", 0.45, 0.3)
    c = mix(c, m, a)
    cl = clouds(x, y, z, 0.22, 2.2, 45, 0.35)
    c = mix(c, c3("#1A1030", sh), cl * 0.6 * (1 - a))
    return ground(y, c, "#05030C")


def ghostfire(x, y, z):
    sh = x.shape
    c = grad(y, [(-1, "#030201"), (-0.03, "#2A0E04"), (0.02, "#6A2A0A"), (0.12, "#200A04"), (0.4, "#080404"), (1, "#020202")])
    c += stars(x, y, z, 0.04, 26) * smooth(0.25, 0.5, y)[..., None]
    # Ghostly wisps: stretched, domain-warped noise ribbons, pale at the core and orange at the edges.
    w = fbm(x * 2 + 1, y * 2, z * 2, 5, 71)
    n = fbm(x * 1.2 + w * 1.6, y * 5.0 + w, z * 1.2 - w, 7, 72)
    sparse = smooth(0.5, 0.62, fbm(x * 1.5 + 7, y * 1.5, z * 1.5, 4, 73))
    ribbon = np.exp(-((n - 0.5) / 0.018) ** 2) * smooth(0.04, 0.18, y) * smooth(0.7, 0.3, y) * sparse
    halo = np.exp(-((n - 0.5) / 0.07) ** 2) * smooth(0.04, 0.18, y) * smooth(0.7, 0.3, y) * sparse
    c += halo[..., None] * hexc("#C04A10") * 0.22
    c += ribbon[..., None] * mix(c3("#FFB070", sh), c3("#FFF6EC", sh), ribbon) * 0.5
    cl = clouds(x, y, z, 0.4, 1.5, 46, 0.3)
    lit = smooth(0.2, 0.0, y)
    c = mix(c, mix(c3("#0E0606", sh), c3("#C0501A", sh), lit * 0.8), cl * 0.75)
    return ground(y, c, "#030201")


def eclipse(x, y, z):
    sh = x.shape
    edir = unit([0.2, 0.42, -0.88])
    c = grad(y, [(-1, "#020203"), (-0.03, "#1A0A10"), (0.03, "#3A1A1A"), (0.15, "#0A0610"), (1, "#030306")])
    c += stars(x, y, z, 0.06, 27) * smooth(0.0, 0.2, y)[..., None]
    d, u, v = tangent(x, y, z, edir)
    r = np.sqrt(u * u + v * v)
    R = 0.07
    ang = np.arctan2(v, u)
    streak = 0.55 + 0.45 * fbm(np.cos(ang) * 6, np.sin(ang) * 6, r * 4, 6, 81)
    corona = np.where(r > R, np.exp(-(r - R) / (0.035 * streak)) * streak, 0.0) * (d > 0)
    c += corona[..., None] * mix(c3("#FFD8A8", sh), c3("#FFFFFF", sh), np.exp(-(r - R) / 0.01))[...] * 0.9
    c += glow(x, y, z, edir, "#FF6A30", 0.2, 0.25)
    # Diamond ring: one bright bead on the rim.
    bead = np.exp(-(((u - R * 0.72) ** 2 + (v - R * 0.69) ** 2) / (0.006 ** 2)))
    c += bead[..., None] * hexc("#FFFFFF") * 1.2
    disc = np.where(d > 0, smooth(R + PX, R - PX, r), 0.0)
    c = mix(c, c3("#010101", sh), disc)
    c = np.minimum(c, 1.0)
    return ground(y, c, "#020203")


def blood_storm(x, y, z):
    sh = x.shape
    c = grad(y, [(-1, "#060102"), (-0.03, "#3A0608"), (0.04, "#6A0E12"), (0.2, "#2A0508"), (0.6, "#120204"), (1, "#080102")])
    cl = clouds(x, y, z, 0.8, 1.1, 47, 0.5, height=0.15)
    yy = np.maximum(y, 0.015)
    shade = fbm(x / (yy + 0.15) * 3.0, z / (yy + 0.15) * 3.0, np.full_like(x, 1.9), 7, 48)
    lit = smooth(0.35, 0.6, shade) * (0.4 + 0.6 * smooth(0.45, 0.0, y))
    base = mix(c3("#120204", sh), c3("#8E151A", sh), lit)
    c = mix(c, base, cl * 0.95)
    # Lightning lit from inside the clouds (soft glows, no cartoon bolts).
    for lc, s in [([0.6, 0.25, -0.76], 0.9), ([-0.7, 0.18, 0.69], 0.6), ([0.1, 0.35, 0.93], 0.45)]:
        g = glow(x, y, z, lc, "#FF8A8A", 0.08, s)
        c += g * (0.35 + 0.65 * cl[..., None])
    c += (smooth(0.08, -0.01, y) * smooth(-0.06, 0.0, y))[..., None] * hexc("#A01018") * 0.4
    return ground(y, c, "#060102")


def monochrome_night(x, y, z):
    sh = x.shape
    mdir = unit([0.5, 0.4, -0.77])
    c = grad(y, [(-1, "#050505"), (-0.02, "#202020"), (0.04, "#2A2A2A"), (0.2, "#121212"), (0.6, "#070707"), (1, "#030303")])
    c += stars(x, y, z, 0.06, 28, warm=0.0)
    m, a = moon(x, y, z, mdir, 0.08, lit="#F4F4F4", dark="#6A6A6A", seed=38)
    c += glow(x, y, z, mdir, "#FFFFFF", 0.05, 0.4) + glow(x, y, z, mdir, "#808080", 0.4, 0.2)
    c = mix(c, m, a)
    cl = clouds(x, y, z, 0.32, 1.8, 49, 0.3)
    d, _, _ = tangent(x, y, z, mdir)
    cloud_col = mix(c3("#141414", sh), c3("#BDBDBD", sh), np.clip(d, 0, 1) ** 160 * 0.8)
    c = mix(c, cloud_col, cl * 0.8 * (1 - a))
    c = c.mean(axis=-1, keepdims=True).repeat(3, axis=-1)  # true greyscale
    return ground(y, c, "#050505")


SKIES = {
    "hw_blood_moon": blood_moon,
    "hw_halloween_night": halloween_night,
    "hw_pumpkin_sky": pumpkin_sky,
    "hw_haunted_forest": haunted_forest,
    "hw_midnight_purple": midnight_purple,
    "hw_ghostfire": ghostfire,
    "hw_eclipse": eclipse,
    "hw_blood_storm": blood_storm,
    "hw_monochrome_night": monochrome_night,
}


# ---------------------------------------------------------------- output

def normalized(fn):
    def f(x, y, z):
        l = np.sqrt(x * x + y * y + z * z)
        return fn(x / l, y / l, z / l)
    return f


def render_atlas(fn, face):
    out = np.zeros((face * 2, face * 3, 3), dtype=np.float32)
    idx = np.arange(face) / (face - 1)  # edge texels on the cube edges (seamless with the half-texel inset)
    S, T = np.meshgrid(idx, idx)
    for f in range(6):
        x0, y0 = round(UV[f][0] * face * 3), round(UV[f][1] * face * 2)
        dx, dy, dz = direction(f, S, T)
        l = np.sqrt(dx * dx + dy * dy + dz * dz)
        out[y0:y0 + face, x0:x0 + face] = np.clip(fn(dx / l, dy / l, dz / l), 0, 1)
    return out


def save(arr, path, seed):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rng = np.random.default_rng(seed)
    # Triangular dither (+-1 LSB): removes banding in the smooth night gradients.
    noise = (rng.random(arr.shape[:2] + (1,)) - rng.random(arr.shape[:2] + (1,))) / 255.0
    Image.fromarray((np.clip(arr + noise, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB").save(path, optimize=True)


def main():
    global PX
    args = [a for a in sys.argv[1:]]
    face = 1024
    if "--face" in args:
        i = args.index("--face")
        face = int(args[i + 1])
        del args[i:i + 2]
    PX = np.pi / 2 / face  # anti-aliasing width follows the resolution
    only = args or list(SKIES)
    for k, sid in enumerate(only):
        fn = SKIES[sid]
        atlas = render_atlas(fn, face)
        p = os.path.join(RES, "skybox", sid + ".png")
        save(atlas, p, k)
        with open(p + ".mcmeta", "w") as f:
            f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
        save(view(normalized(fn), 128, 72), os.path.join(RES, "gui", "sky_preview", sid + ".png"), k)
        save(view(normalized(fn), 384, 216), os.path.join(LAUNCHER_SKY, sid + ".png"), k)
        print(f"{sid:22s} {face * 3}x{face * 2} ({face} px faces) {os.path.getsize(p) // 1024} KB", flush=True)


if __name__ == "__main__":
    main()
