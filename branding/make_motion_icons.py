"""Generate the MOTION MOTION mark, wordmark (SVG) and the app icon set (PNG + ICO).

The mark is a forward-leaning dollar sign ($) in electric green with motion streaks trailing
behind it; the app icon puts it on a deep-black rounded tile with a soft green glow. The
wordmark is "MOTION MOTION" in blocky, slanted letters (white, then green), built from squares
so it stays crisp and reads as Minecraft-adjacent without copying any game art. The in-game
icon is a pixel-art version of the mark.

Run:  python3 branding/make_motion_icons.py       (needs Playwright's Chromium for the raster step)
      python3 branding/make_motion_icons.py --svg-only
"""
from __future__ import annotations

import os
import subprocess
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(ROOT)
ICON_DIR = os.path.join(REPO, "apps", "launcher", "src-tauri", "icons")
PUBLIC_DIR = os.path.join(REPO, "apps", "launcher", "public")
WEBSITE_DIR = os.path.join(REPO, "apps", "website", "public")
MOD_DIR = os.path.join(REPO, "apps", "client-mod", "common", "src", "main", "resources", "assets", "bloomclient")

LIME = "#c6ff3d"
GREEN = "#39ff88"
DEEP = "#00c46a"
TILE_TOP, TILE_BOTTOM = "#0d1410", "#030504"
WHITE = "#f4fbf7"
WHITE_SHADE = "#c3d1c9"

# ------------------------------------------------------------------ the mark


def mark_svg(size: int = 512, tile: bool = False) -> str:
    """Square mark; tile=True puts it on the dark rounded app-icon tile."""
    p = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="{size}" height="{size}">',
         '<defs>',
         f'<linearGradient id="m" gradientUnits="userSpaceOnUse" x1="120" y1="30" x2="170" y2="226"><stop offset="0" stop-color="{LIME}"/>'
         f'<stop offset=".5" stop-color="{GREEN}"/><stop offset="1" stop-color="{DEEP}"/></linearGradient>',
         f'<linearGradient id="s" gradientUnits="userSpaceOnUse" x1="96" y1="0" x2="0" y2="0"><stop offset="0" stop-color="{GREEN}"/>'
         f'<stop offset="1" stop-color="{GREEN}" stop-opacity="0"/></linearGradient>',
         '<filter id="glow" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="9" result="b"/>'
         '<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>']
    if tile:
        p.append(f'<linearGradient id="t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{TILE_TOP}"/>'
                 f'<stop offset="1" stop-color="{TILE_BOTTOM}"/></linearGradient>'
                 f'<radialGradient id="g" cx=".55" cy=".5" r=".55"><stop offset="0" stop-color="{GREEN}" stop-opacity=".30"/>'
                 f'<stop offset="1" stop-color="{GREEN}" stop-opacity="0"/></radialGradient>')
    p.append('</defs>')
    if tile:
        p.append('<rect width="256" height="256" rx="56" fill="url(#t)"/><rect width="256" height="256" rx="56" fill="url(#g)"/>'
                 '<rect x="1.5" y="1.5" width="253" height="253" rx="54.5" fill="none" stroke="#ffffff" stroke-opacity=".07" stroke-width="3"/>')
        scale, dx = 0.82, 26
    else:
        scale, dx = 1.0, 0
    p.append(f'<g transform="translate({dx + 128 * (1 - scale):.1f} {128 * (1 - scale):.1f}) scale({scale})">')
    # Motion streaks trailing to the left.
    for y, x0, x1, op in ((96, 22, 92, 0.9), (128, 6, 84, 1.0), (160, 30, 92, 0.75)):
        p.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="url(#s)" stroke-width="13" stroke-linecap="round" opacity="{op}"/>')
    # The $: an S curve with a bar through it, leaning forward.
    p.append('<g transform="translate(150 128) skewX(-14) translate(-150 -128)" filter="url(#glow)" fill="none" '
             'stroke="url(#m)" stroke-linecap="round" stroke-linejoin="round">')
    p.append('<path d="M196 70 C184 50 124 46 116 76 C108 106 190 106 192 146 C194 186 128 196 104 176" stroke-width="28"/>')
    p.append('<line x1="152" y1="30" x2="152" y2="226" stroke-width="18"/>')
    p.append('</g></g></svg>')
    return "".join(p)


# ------------------------------------------------------------------ the wordmark
LETTERS = {
    "M": ["11...11", "111.111", "1111111", "11.1.11", "11...11", "11...11", "11...11"],
    "O": [".1111.", "11..11", "11..11", "11..11", "11..11", "11..11", ".1111."],
    "T": ["111111", "111111", "..11..", "..11..", "..11..", "..11..", "..11.."],
    "I": ["11", "11", "11", "11", "11", "11", "11"],
    "N": ["11..11", "111.11", "111111", "11.111", "11..11", "11..11", "11..11"],
}
WORDS = ["MOTION", "MOTION"]


def wordmark_svg(height: int = 64) -> str:
    px, gap, space = 10, 1, 4
    widths = [sum(len(LETTERS[c][0]) for c in w) + gap * (len(w) - 1) for w in WORDS]
    cols = sum(widths) + space
    rows = 7
    skew = 0.2
    w = cols * px + rows * px * skew + 4
    h = rows * px
    p = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.0f} {h}" height="{height}" width="{height * w / h:.0f}">',
         f'<defs><linearGradient id="wg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{LIME}"/>'
         f'<stop offset="1" stop-color="{GREEN}"/></linearGradient></defs>',
         f'<g transform="translate({rows * px * skew + 2:.1f} 0) skewX(-{11.3:.1f})">']
    x0 = 0
    for wi, word in enumerate(WORDS):
        cells: dict[str, list[tuple[int, int]]] = {}
        for ch in word:
            glyph = LETTERS[ch]
            for y, row in enumerate(glyph):
                for x, c in enumerate(row):
                    if c == "1":
                        fill = (WHITE_SHADE if y == 6 else WHITE) if wi == 0 else "url(#wg)"
                        cells.setdefault(fill, []).append((x0 + x * px, y * px))
            x0 += (len(glyph[0]) + gap) * px
        x0 += (space - gap) * px
        for fill, pts in cells.items():
            d = "".join(f"M{x} {y}h{px}v{px}h-{px}z" for x, y in pts)
            p.append(f'<path d="{d}" fill="{fill}"/>')
    p.append("</g></svg>")
    return "".join(p)


# ------------------------------------------------------------------ pixel mark (in-game)
PIX = [
    ".......X.....",
    "....XXXXXXX..",
    "...XXXXXXXXX.",
    "..XXX..X..XX.",
    "..XXX..X.....",
    "...XXXXX.....",
    "....XXXXXXX..",
    ".......XXXXX.",
    ".......X.XXX.",
    "..XX...X.XXX.",
    "..XXXXXXXXX..",
    "...XXXXXXX...",
    ".......X.....",
]
PIX_STREAKS = [(0, 5, 3), (0, 8, 2)]  # (x, y, length) streak cells to the left


def pixel_mark(scale: int) -> Image.Image:
    h, w = len(PIX), len(PIX[0])
    img = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))

    def put(x, y, rgb):
        for yy in range(scale):
            for xx in range(scale):
                img.putpixel((x * scale + xx, y * scale + yy), rgb)

    for y, row in enumerate(PIX):
        t = y / (h - 1)
        c = tuple(int(a + (b - a) * t) for a, b in zip((198, 255, 61), (0, 196, 106))) + (255,)
        for x, ch in enumerate(row):
            if ch == "X":
                put(x, y, c)
    for x, y, n in PIX_STREAKS:
        for i in range(n):
            put(x + i, y, (57, 255, 136, 110 + 50 * i))
    return img


RASTER_JS = r"""
const { chromium } = require(process.env.PW || "playwright");
const fs = require("fs");
(async () => {
  const [svgPath, out, size] = process.argv.slice(2);
  const b = await chromium.launch();
  const page = await b.newPage({ viewport: { width: +size, height: +size } });
  const svg = fs.readFileSync(svgPath, "utf8");
  await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace(/width="\d+" height="\d+"/, `width="${size}" height="${size}"`)}</body></html>`);
  await page.screenshot({ path: out, omitBackground: true, clip: { x: 0, y: 0, width: +size, height: +size } });
  await b.close();
})();
"""


def raster(svg_text: str, size: int, out: str):
    tmp_svg = out + ".svg"
    tmp_js = os.path.join(os.path.dirname(out), "_raster.js")
    with open(tmp_svg, "w") as f:
        f.write(svg_text)
    with open(tmp_js, "w") as f:
        f.write(RASTER_JS)
    env = dict(os.environ)
    env.setdefault("PW", "/opt/node-tools/node_modules/playwright")
    subprocess.run(["node", tmp_js, tmp_svg, out, str(size)], check=True, env=env)
    os.remove(tmp_svg)
    os.remove(tmp_js)


def write(path: str, text: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def main():
    write(os.path.join(ROOT, "motion-mark.svg"), mark_svg(512))
    write(os.path.join(ROOT, "motion-logo.svg"), mark_svg(512, tile=True))
    write(os.path.join(ROOT, "motion-wordmark.svg"), wordmark_svg(128))
    write(os.path.join(PUBLIC_DIR, "motion-mark.svg"), mark_svg(512))
    write(os.path.join(PUBLIC_DIR, "motion-wordmark.svg"), wordmark_svg(128))
    if os.path.isdir(WEBSITE_DIR):
        write(os.path.join(WEBSITE_DIR, "motion-mark.svg"), mark_svg(512))
        write(os.path.join(WEBSITE_DIR, "img", "motion-mark.svg"), mark_svg(512))
        write(os.path.join(WEBSITE_DIR, "img", "motion-logo.svg"), mark_svg(512, tile=True))
        write(os.path.join(WEBSITE_DIR, "img", "motion-wordmark.svg"), wordmark_svg(128))
    if os.path.isdir(MOD_DIR):
        sprite = Image.new("RGBA", (32, 32), (0, 0, 0, 0))  # drawn at 16x16 in menus and toasts
        pm = pixel_mark(2)
        sprite.alpha_composite(pm, ((32 - pm.width) // 2, (32 - pm.height) // 2))
        sprite.save(os.path.join(MOD_DIR, "textures", "gui", "sprites", "logo.png"))
        icon = Image.new("RGBA", (128, 128), (5, 8, 6, 255))
        pm = pixel_mark(8)
        icon.alpha_composite(pm, ((128 - pm.width) // 2, (128 - pm.height) // 2))
        icon.save(os.path.join(MOD_DIR, "icon.png"))
    if "--svg-only" in sys.argv:
        return
    big = os.path.join(ROOT, "motion-icon-1024.png")
    raster(mark_svg(1024, tile=True), 1024, big)
    src = Image.open(big).convert("RGBA")
    sizes = {"32x32.png": 32, "64x64.png": 64, "128x128.png": 128, "128x128@2x.png": 256, "icon.png": 512,
             "Square30x30Logo.png": 30, "Square44x44Logo.png": 44, "Square71x71Logo.png": 71, "Square89x89Logo.png": 89,
             "Square107x107Logo.png": 107, "Square142x142Logo.png": 142, "Square150x150Logo.png": 150,
             "Square284x284Logo.png": 284, "Square310x310Logo.png": 310, "StoreLogo.png": 50}
    for name, s in sizes.items():
        src.resize((s, s), Image.LANCZOS).save(os.path.join(ICON_DIR, name))
    src.save(os.path.join(ICON_DIR, "icon.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    src.resize((64, 64), Image.LANCZOS).save(os.path.join(PUBLIC_DIR, "favicon.png"))
    if os.path.isdir(WEBSITE_DIR):
        src.resize((64, 64), Image.LANCZOS).save(os.path.join(WEBSITE_DIR, "favicon.png"))
        src.resize((512, 512), Image.LANCZOS).save(os.path.join(WEBSITE_DIR, "img", "motion-icon-512.png"))
    print("icons written")


if __name__ == "__main__":
    main()
