#!/usr/bin/env python3
"""Generates BLOOD Client's GUI sprites, menu icons, particle textures and logo (original art).

Run: python3 tools/gen_textures.py
Everything is drawn at 4x and downsampled for clean edges. Sprites are white/grey so the
mod can tint them with the selected theme colour.
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "common/src/main/resources/assets/bloomclient/textures")
SPR = os.path.join(TEX, "gui/sprites")
SS = 4  # supersampling


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def finish(img, w, h):
    return img.resize((w, h), Image.LANCZOS)


def save(img, path, mcmeta=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    if mcmeta is not None:
        with open(path + ".mcmeta", "w") as f:
            json.dump(mcmeta, f, indent=2)
            f.write("\n")


def nine(w, h, border):
    return {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}


def rounded(w, h, r, fill=(255, 255, 255, 255), outline=None, width=1):
    im = canvas(w, h)
    d = ImageDraw.Draw(im)
    if outline:
        d.rounded_rectangle([0, 0, w * SS - 1, h * SS - 1], r * SS, outline=outline, width=width * SS)
    else:
        d.rounded_rectangle([0, 0, w * SS - 1, h * SS - 1], r * SS, fill=fill)
    return finish(im, w, h)


W = (255, 255, 255, 255)


def sprites():
    save(rounded(16, 16, 4), f"{SPR}/panel.png", nine(16, 16, 5))
    save(rounded(16, 16, 4, outline=W), f"{SPR}/panel_border.png", nine(16, 16, 5))
    save(rounded(16, 16, 2), f"{SPR}/panel_small.png", nine(16, 16, 3))
    save(rounded(16, 16, 2, outline=W), f"{SPR}/panel_small_border.png", nine(16, 16, 3))
    save(rounded(32, 16, 8), f"{SPR}/pill.png", nine(32, 16, {"left": 8, "top": 7, "right": 8, "bottom": 7}))
    save(rounded(32, 16, 8, outline=W), f"{SPR}/pill_border.png", nine(32, 16, {"left": 8, "top": 7, "right": 8, "bottom": 7}))
    # circles
    im = canvas(16, 16)
    ImageDraw.Draw(im).ellipse([0, 0, 16 * SS - 1, 16 * SS - 1], fill=W)
    save(finish(im, 16, 16), f"{SPR}/circle.png")
    im = canvas(16, 16)
    ImageDraw.Draw(im).ellipse([SS, SS, 15 * SS - 1, 15 * SS - 1], outline=W, width=2 * SS)
    save(finish(im, 16, 16), f"{SPR}/ring.png")
    # soft shadow
    sh = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(sh)
    d.rounded_rectangle([8, 8, 23, 23], 4, fill=(0, 0, 0, 255))
    sh = sh.filter(ImageFilter.GaussianBlur(4))
    save(sh, f"{SPR}/shadow.png", nine(32, 32, 12))
    # radial glow
    g = Image.new("RGBA", (64, 64))
    for y in range(64):
        for x in range(64):
            dd = math.hypot(x - 31.5, y - 31.5) / 32
            a = max(0.0, 1 - dd) ** 2
            g.putpixel((x, y), (255, 255, 255, int(a * 255)))
    save(g, f"{SPR}/glow.png")
    # hue bar (vertical, red at top)
    hb = Image.new("RGBA", (8, 128))
    import colorsys
    for y in range(128):
        r, gg, b = colorsys.hsv_to_rgb(y / 127, 1, 1)
        for x in range(8):
            hb.putpixel((x, y), (int(r * 255), int(gg * 255), int(b * 255), 255))
    save(hb, f"{SPR}/hue_bar.png")
    # checkerboard for transparency previews (tiled)
    ch = Image.new("RGBA", (8, 8), (200, 200, 200, 255))
    d = ImageDraw.Draw(ch)
    d.rectangle([0, 0, 3, 3], fill=(150, 150, 150, 255))
    d.rectangle([4, 4, 7, 7], fill=(150, 150, 150, 255))
    save(ch, f"{SPR}/checker.png", {"gui": {"scaling": {"type": "tile", "width": 8, "height": 8}}})
    # vertical fade used under the menu header
    fd = Image.new("RGBA", (4, 32))
    for y in range(32):
        for x in range(4):
            fd.putpixel((x, y), (255, 255, 255, int(255 * (1 - y / 31))))
    save(fd, f"{SPR}/fade_down.png")


# ---------------------------------------------------------------- icons (16x16, white)
def icon(name, draw_fn, size=16):
    im = canvas(size, size)
    d = ImageDraw.Draw(im)
    draw_fn(d, SS)
    save(finish(im, size, size), f"{SPR}/icons/{name}.png")


def P(*pts):
    return [(x * SS, y * SS) for x, y in pts]


def L(d, s, pts, w=1.6):
    d.line(P(*pts), fill=W, width=int(w * s), joint="curve")


def E(d, s, x0, y0, x1, y1, fill=True, w=1.6):
    if fill:
        d.ellipse([x0 * s, y0 * s, x1 * s, y1 * s], fill=W)
    else:
        d.ellipse([x0 * s, y0 * s, x1 * s, y1 * s], outline=W, width=int(w * s))


def R(d, s, x0, y0, x1, y1, r=1.5, fill=True, w=1.6):
    if fill:
        d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], r * s, fill=W)
    else:
        d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], r * s, outline=W, width=int(w * s))


def petal_flower(d, s, cx, cy, r, pr):
    for i in range(5):
        a = math.radians(-90 + i * 72)
        px, py = cx + math.cos(a) * r, cy + math.sin(a) * r
        d.ellipse([(px - pr) * s, (py - pr) * s, (px + pr) * s, (py + pr) * s], fill=W)


def icons():
    def gear(d, s):
        E(d, s, 3, 3, 13, 13, fill=False, w=2.2)
        for i in range(8):
            a = math.radians(i * 45)
            x, y = 8 + math.cos(a) * 6.3, 8 + math.sin(a) * 6.3
            d.ellipse([(x - 1.3) * s, (y - 1.3) * s, (x + 1.3) * s, (y + 1.3) * s], fill=W)
        E(d, s, 6.3, 6.3, 9.7, 9.7)
    icon("general", gear)

    def hud(d, s):
        R(d, s, 1.5, 2.5, 14.5, 13.5, 2, fill=False)
        R(d, s, 3.5, 4.5, 7.5, 6.5, 0.6)
        R(d, s, 3.5, 8, 6, 9.6, 0.6)
        R(d, s, 10, 10, 12.5, 11.6, 0.6)
    icon("hud", hud)

    def sky(d, s):
        E(d, s, 2, 2, 8, 8)
        for cx, cy, r in ((7, 10.5, 3.2), (10.5, 9, 3.6), (13, 11, 2.4)):
            E(d, s, cx - r, cy - r, cx + r, cy + r)
        R(d, s, 4, 10.5, 15, 14.3, 1.8)
    icon("skybox", sky)

    def weather(d, s):
        for cx, cy, r in ((5.5, 7, 3), (9, 5.2, 3.7), (12, 7.4, 2.6)):
            E(d, s, cx - r, cy - r, cx + r, cy + r)
        R(d, s, 2.5, 7, 14.5, 10, 1.5)
        for x in (5, 8.5, 12):
            L(d, s, [(x, 11.8), (x - 1.2, 14.6)], 1.4)
    icon("weather", weather)

    def petals(d, s):
        petal_flower(d, s, 8, 8, 3.6, 2.9)
        d.ellipse([6.4 * s, 6.4 * s, 9.6 * s, 9.6 * s], fill=(0, 0, 0, 0))
    icon("particles", petals)

    def sparkle(d, s):
        d.polygon(P((8, 1), (9.4, 6.6), (15, 8), (9.4, 9.4), (8, 15), (6.6, 9.4), (1, 8), (6.6, 6.6)), fill=W)
        d.polygon(P((13, 1.5), (13.6, 3.4), (15.5, 4), (13.6, 4.6), (13, 6.5), (12.4, 4.6), (10.5, 4), (12.4, 3.4)), fill=W)
    icon("effects", sparkle)

    def world(d, s):
        E(d, s, 2, 2, 14, 14, fill=False, w=1.6)
        d.ellipse([5 * s, 2 * s, 11 * s, 14 * s], outline=W, width=int(1.3 * s))
        L(d, s, [(2.5, 8), (13.5, 8)], 1.3)
        L(d, s, [(3.5, 5), (12.5, 5)], 1.1)
        L(d, s, [(3.5, 11), (12.5, 11)], 1.1)
    icon("world", world)

    def gauge(d, s):
        d.arc([1.5 * s, 2.5 * s, 14.5 * s, 15.5 * s], 180, 360, fill=W, width=int(2 * s))
        L(d, s, [(8, 9.5), (11.5, 5)], 1.8)
        E(d, s, 6.6, 8.1, 9.4, 10.9)
        L(d, s, [(1.5, 9), (3.5, 9)], 1.4)
        L(d, s, [(12.5, 9), (14.5, 9)], 1.4)
    icon("performance", gauge)

    def profiles(d, s):
        E(d, s, 5, 1.5, 11, 7.5)
        d.pieslice([2 * s, 8.5 * s, 14 * s, 20 * s], 180, 360, fill=W)
    icon("profiles", profiles)

    def keyboard(d, s):
        R(d, s, 1, 4, 15, 12.5, 1.6, fill=False, w=1.4)
        for row, y in enumerate((6, 8.2)):
            for i in range(5):
                x = 3 + i * 2.2
                R(d, s, x, y, x + 1.3, y + 1.2, 0.3)
        R(d, s, 4.5, 10.3, 11.5, 11.3, 0.3)
    icon("keybinds", keyboard)

    # misc
    def search(d, s):
        E(d, s, 2, 2, 11, 11, fill=False, w=1.8)
        L(d, s, [(10, 10), (14, 14)], 2.2)
    icon("search", search)

    def reset(d, s):
        d.arc([2.5 * s, 2.5 * s, 13.5 * s, 13.5 * s], 40, 330, fill=W, width=int(1.8 * s))
        d.polygon(P((12.5, 1.5), (13.8, 6.2), (9.2, 5.2)), fill=W)
    icon("reset", reset)

    icon("close", lambda d, s: (L(d, s, [(3.5, 3.5), (12.5, 12.5)], 2), L(d, s, [(12.5, 3.5), (3.5, 12.5)], 2)))
    icon("plus", lambda d, s: (L(d, s, [(8, 3), (8, 13)], 2), L(d, s, [(3, 8), (13, 8)], 2)))
    icon("check", lambda d, s: L(d, s, [(3, 8.5), (6.5, 12), (13, 4.5)], 2.2))
    icon("chevron_down", lambda d, s: L(d, s, [(4, 6), (8, 10), (12, 6)], 2))
    icon("chevron_right", lambda d, s: L(d, s, [(6, 4), (10, 8), (6, 12)], 2))
    icon("chevron_left", lambda d, s: L(d, s, [(10, 4), (6, 8), (10, 12)], 2))

    def copy(d, s):
        R(d, s, 5, 5, 14, 14, 1.5, fill=False, w=1.5)
        L(d, s, [(3, 11), (2.5, 11), (2.5, 2.5), (11, 2.5), (11, 3)], 1.5)
    icon("copy", copy)

    def import_(d, s):
        L(d, s, [(8, 2), (8, 10)], 1.8)
        L(d, s, [(4.5, 7), (8, 10.5), (11.5, 7)], 1.8)
        L(d, s, [(2.5, 10), (2.5, 13.5), (13.5, 13.5), (13.5, 10)], 1.6)
    icon("import", import_)

    def export(d, s):
        L(d, s, [(8, 10.5), (8, 2.5)], 1.8)
        L(d, s, [(4.5, 6), (8, 2.5), (11.5, 6)], 1.8)
        L(d, s, [(2.5, 10), (2.5, 13.5), (13.5, 13.5), (13.5, 10)], 1.6)
    icon("export", export)

    def trash(d, s):
        L(d, s, [(2.5, 4.2), (13.5, 4.2)], 1.6)
        L(d, s, [(6, 4), (6.5, 2.2), (9.5, 2.2), (10, 4)], 1.4)
        d.polygon(P((4, 5.5), (12, 5.5), (11.2, 14), (4.8, 14)), outline=W, width=int(1.5 * s))
    icon("trash", trash)

    def edit(d, s):
        d.polygon(P((10.5, 2), (14, 5.5), (6, 13.5), (2.5, 13.5), (2.5, 10)), outline=W, width=int(1.5 * s))
        L(d, s, [(9, 3.5), (12.5, 7)], 1.4)
    icon("edit", edit)

    def folder(d, s):
        d.polygon(P((1.5, 3), (6, 3), (7.5, 4.8), (14.5, 4.8), (14.5, 13.5), (1.5, 13.5)), outline=W, width=int(1.5 * s))
    icon("folder", folder)

    def warning(d, s):
        d.polygon(P((8, 1.5), (15, 14), (1, 14)), fill=W)
        d.line(P((8, 6), (8, 10)), fill=(0, 0, 0, 0), width=int(1.8 * s))
        d.ellipse([7.1 * s, 11.2 * s, 8.9 * s, 13 * s], fill=(0, 0, 0, 0))
    icon("warning", warning)

    def info(d, s):
        E(d, s, 1.5, 1.5, 14.5, 14.5)
        d.line(P((8, 7), (8, 12)), fill=(0, 0, 0, 0), width=int(1.8 * s))
        d.ellipse([7.1 * s, 3.8 * s, 8.9 * s, 5.6 * s], fill=(0, 0, 0, 0))
    icon("info", info)

    def grid(d, s):
        for v in (2, 6, 10, 14):
            L(d, s, [(v, 2), (v, 14)], 1.1)
            L(d, s, [(2, v), (14, v)], 1.1)
    icon("grid", grid)

    def eye(d, s):
        d.ellipse([1 * s, 4 * s, 15 * s, 12 * s], outline=W, width=int(1.5 * s))
        E(d, s, 5.8, 5.8, 10.2, 10.2)
    icon("eye", eye)

    def eye_off(d, s):
        eye(d, s)
        L(d, s, [(2.5, 13.5), (13.5, 2.5)], 1.8)
    icon("eye_off", eye_off)

    def play(d, s):
        d.polygon(P((4, 2.5), (13.5, 8), (4, 13.5)), fill=W)
    icon("play", play)

    def star(d, s):
        pts = []
        for i in range(10):
            a = math.radians(-90 + i * 36)
            r = 6.5 if i % 2 == 0 else 2.8
            pts.append((8 + math.cos(a) * r, 8.4 + math.sin(a) * r))
        d.polygon(P(*pts), fill=W)
    icon("star", star)

    def move(d, s):
        L(d, s, [(8, 1.5), (8, 14.5)], 1.4)
        L(d, s, [(1.5, 8), (14.5, 8)], 1.4)
        for pts in (((6, 3.5), (8, 1.5), (10, 3.5)), ((6, 12.5), (8, 14.5), (10, 12.5)),
                    ((3.5, 6), (1.5, 8), (3.5, 10)), ((12.5, 6), (14.5, 8), (12.5, 10))):
            L(d, s, list(pts), 1.4)
    icon("move", move)

    def drop(d, s):
        d.polygon(P((8, 1.5), (12.5, 8.5), (3.5, 8.5)), fill=W)
        E(d, s, 3.5, 5.5, 12.5, 14.5)
    icon("color", drop)

    def link(d, s):
        R(d, s, 1.5, 5, 9, 11, 3, fill=False, w=1.5)
        R(d, s, 7, 5, 14.5, 11, 3, fill=False, w=1.5)
    icon("link", link)


# ---------------------------------------------------------------- particle atlas (128x128, 32px cells)
def particle_atlas():
    S = 32
    atlas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))

    def cell(col, row, fn):
        im = canvas(S, S)
        d = ImageDraw.Draw(im)
        fn(d, im)
        atlas.paste(finish(im, S, S), (col * S, row * S))

    k = SS

    def petal_round(d, im):
        d.ellipse([6 * k, 4 * k, 26 * k, 28 * k], fill=(255, 255, 255, 255))
        d.ellipse([12 * k, 6 * k, 20 * k, 14 * k], fill=(255, 238, 244, 255))

    def petal_notched(d, im):
        d.ellipse([5 * k, 6 * k, 27 * k, 29 * k], fill=(255, 255, 255, 255))
        d.polygon([(16 * k, 13 * k), (11 * k, 3 * k), (21 * k, 3 * k)], fill=(0, 0, 0, 0))
        d.line([(16 * k, 27 * k), (16 * k, 17 * k)], fill=(240, 220, 230, 255), width=k)

    def petal_oval(d, im):
        d.ellipse([10 * k, 2 * k, 22 * k, 30 * k], fill=(255, 255, 255, 255))
        d.line([(16 * k, 28 * k), (16 * k, 8 * k)], fill=(242, 225, 233, 255), width=k)

    def petal_curled(d, im):
        d.pieslice([4 * k, 4 * k, 28 * k, 28 * k], 200, 520, fill=(255, 255, 255, 255))
        d.ellipse([10 * k, 10 * k, 22 * k, 22 * k], fill=(236, 214, 224, 255))

    def leaf_a(d, im):
        d.polygon([(16 * k, 2 * k), (26 * k, 14 * k), (16 * k, 30 * k), (6 * k, 14 * k)], fill=(255, 255, 255, 255))
        d.line([(16 * k, 30 * k), (16 * k, 5 * k)], fill=(205, 205, 205, 255), width=int(1.2 * k))

    def leaf_b(d, im):
        d.ellipse([7 * k, 3 * k, 25 * k, 29 * k], fill=(255, 255, 255, 255))
        d.line([(16 * k, 29 * k), (16 * k, 6 * k)], fill=(205, 205, 205, 255), width=int(1.2 * k))
        for y in (12, 18, 24):
            d.line([(16 * k, y * k), (11 * k, (y - 4) * k)], fill=(215, 215, 215, 255), width=k)
            d.line([(16 * k, y * k), (21 * k, (y - 4) * k)], fill=(215, 215, 215, 255), width=k)

    def snow_soft(d, im):
        for r, a in ((14, 50), (10, 130), (7, 255)):
            d.ellipse([(16 - r) * k, (16 - r) * k, (16 + r) * k, (16 + r) * k], fill=(255, 255, 255, a))

    def snow_crystal(d, im):
        for i in range(6):
            a = math.radians(i * 60)
            x, y = 16 + math.cos(a) * 13, 16 + math.sin(a) * 13
            d.line([(16 * k, 16 * k), (x * k, y * k)], fill=(255, 255, 255, 255), width=2 * k)
            for f in (0.55,):
                bx, by = 16 + math.cos(a) * 13 * f, 16 + math.sin(a) * 13 * f
                for da in (-50, 50):
                    b = a + math.radians(da)
                    d.line([(bx * k, by * k), ((bx + math.cos(b) * 4) * k, (by + math.sin(b) * 4) * k)],
                           fill=(255, 255, 255, 255), width=int(1.5 * k))

    def snow_pixel(d, im):
        d.rectangle([10 * k, 10 * k, 22 * k, 22 * k], fill=(255, 255, 255, 255))

    def soft_dot(d, im):
        for r in range(16, 0, -1):
            a = int(255 * (1 - r / 16) ** 1.6)
            d.ellipse([(16 - r) * k, (16 - r) * k, (16 + r) * k, (16 + r) * k], fill=(255, 255, 255, a))

    def rain(d, im):
        for y in range(32):
            a = int(255 * math.sin(math.pi * y / 31) ** 0.7)
            d.line([(15 * k, y * k), (17 * k, y * k)], fill=(255, 255, 255, a), width=k)

    def splash(d, im):
        d.ellipse([10 * k, 10 * k, 22 * k, 22 * k], fill=(255, 255, 255, 255))

    def sparkle(d, im):
        d.polygon([(16 * k, 1 * k), (18 * k, 14 * k), (31 * k, 16 * k), (18 * k, 18 * k), (16 * k, 31 * k),
                   (14 * k, 18 * k), (1 * k, 16 * k), (14 * k, 14 * k)], fill=(255, 255, 255, 255))

    cell(0, 0, petal_round)
    cell(1, 0, petal_notched)
    cell(2, 0, petal_oval)
    cell(3, 0, petal_curled)
    cell(0, 1, leaf_a)
    cell(1, 1, leaf_b)
    cell(2, 1, snow_soft)
    cell(3, 1, snow_crystal)
    cell(0, 2, snow_pixel)
    cell(1, 2, soft_dot)
    cell(2, 2, rain)
    cell(3, 2, splash)
    cell(0, 3, sparkle)
    save(atlas, os.path.join(TEX, "effects/atlas.png"), {"texture": {"blur": True, "clamp": True}})

    halo = Image.new("RGBA", (64, 64))
    for y in range(64):
        for x in range(64):
            dd = math.hypot(x - 31.5, y - 31.5) / 32
            a = max(0.0, 1 - dd) ** 2.4
            halo.putpixel((x, y), (255, 255, 255, int(a * 255)))
    save(halo, os.path.join(TEX, "effects/halo.png"), {"texture": {"blur": True, "clamp": True}})


def logo():
    for size, path in ((128, os.path.join(ROOT, "common/src/main/resources/assets/bloomclient/icon.png")),
                       (32, f"{SPR}/logo.png")):
        im = canvas(size, size)
        d = ImageDraw.Draw(im)
        c = size * SS / 2
        r = size * SS * 0.24
        pr = size * SS * 0.2
        for i in range(5):
            a = math.radians(-90 + i * 72)
            px, py = c + math.cos(a) * r, c + math.sin(a) * r
            d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(255, 168, 200, 255))
            # notch
            nx, ny = c + math.cos(a) * (r + pr * 0.95), c + math.sin(a) * (r + pr * 0.95)
            d.ellipse([nx - pr * 0.28, ny - pr * 0.28, nx + pr * 0.28, ny + pr * 0.28], fill=(0, 0, 0, 0))
        for i in range(5):
            a = math.radians(-90 + 36 + i * 72)
            px, py = c + math.cos(a) * r * 0.55, c + math.sin(a) * r * 0.55
            d.line([(c, c), (px, py)], fill=(255, 120, 165, 255), width=max(1, int(size * SS * 0.02)))
        cr = size * SS * 0.1
        d.ellipse([c - cr, c - cr, c + cr, c + cr], fill=(255, 225, 120, 255))
        save(finish(im, size, size), path)


if __name__ == "__main__":
    sprites()
    icons()
    particle_atlas()
    logo()
    print("ok")
