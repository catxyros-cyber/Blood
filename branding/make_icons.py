"""Generate the BLOOD mark, wordmark (SVG) and the app icon set (PNG + ICO).

The mark is a pixel-art blood drop on a 13x16 grid, and the wordmark is "BLOOD" set in
blocky 5x7 letters with a slight forward slant and one crimson drip. Both are built from
squares so they stay crisp and read as Minecraft-adjacent without copying any game art.
The PNG/ICO icon set is rasterised from the SVG with a headless browser.

Run:  python3 branding/make_icons.py       (needs Playwright's Chromium for the raster step)
      python3 branding/make_icons.py --svg-only
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

# ------------------------------------------------------------------ palette
HIGHLIGHT = "#ff8a8f"
SPECULAR = "#ffd6d8"
BRIGHT = "#f2283b"
MAIN = "#d31428"
MID = "#a50e1f"
SHADE = "#6e0813"
TILE_TOP, TILE_BOTTOM = "#1c0c10", "#070405"
LETTER = "#f4eeee"
LETTER_SHADE = "#c9bcbc"

# ------------------------------------------------------------------ the drop (13 x 16)
DROP = [
    "......X......",
    "......X......",
    ".....XXX.....",
    ".....XXX.....",
    "....XXXXX....",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    ".XXXXXXXXXXX.",
    ".XXXXXXXXXXX.",
    "XXXXXXXXXXXXX",
    "XXXXXXXXXXXXX",
    "XXXXXXXXXXXXX",
    ".XXXXXXXXXXX.",
    ".XXXXXXXXXXX.",
    "..XXXXXXXXX..",
    "....XXXXX....",
]
DW, DH = len(DROP[0]), len(DROP)
HIGHLIGHT_PX = {(3, 8), (2, 9), (2, 10), (3, 11), (4, 6), (5, 5)}
SPECULAR_PX = {(3, 9), (3, 10)}


def filled(x: int, y: int) -> bool:
    return 0 <= y < DH and 0 <= x < DW and DROP[y][x] == "X"


def drop_color(x: int, y: int) -> str:
    if (x, y) in SPECULAR_PX:
        return SPECULAR
    if (x, y) in HIGHLIGHT_PX:
        return HIGHLIGHT
    # Light comes from the top-left: the lower-right rim is darkest.
    if not filled(x + 1, y) or not filled(x, y + 1):
        return SHADE if y > 4 else MID
    if not filled(x + 2, y) or not filled(x, y + 2):
        return MID
    if y < 7 and (not filled(x - 1, y) or not filled(x, y - 1)):
        return BRIGHT
    return MAIN


def drop_rects(ox: float, oy: float, px: float) -> str:
    out = []
    for y in range(DH):
        for x in range(DW):
            if filled(x, y):
                # +0.04 overlap hides hairline seams when the browser antialiases.
                out.append(f'<rect x="{ox + x * px:.2f}" y="{oy + y * px:.2f}" width="{px + 0.04:.2f}" height="{px + 0.04:.2f}" fill="{drop_color(x, y)}"/>')
    return "".join(out)


def mark_svg(size: int = 512, tile: bool = False) -> str:
    """Square mark. With tile=True it sits on the dark rounded app-icon tile."""
    p = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="{size}" height="{size}" shape-rendering="crispEdges">']
    if tile:
        p.append('<defs><linearGradient id="t" x1="0" y1="0" x2="0" y2="1">'
                 f'<stop offset="0" stop-color="{TILE_TOP}"/><stop offset="1" stop-color="{TILE_BOTTOM}"/></linearGradient>'
                 '<radialGradient id="g" cx=".5" cy=".62" r=".5"><stop offset="0" stop-color="#d31428" stop-opacity=".38"/>'
                 '<stop offset="1" stop-color="#d31428" stop-opacity="0"/></radialGradient></defs>')
        p.append('<rect x="0" y="0" width="256" height="256" rx="56" fill="url(#t)" shape-rendering="geometricPrecision"/>')
        p.append('<rect x="0" y="0" width="256" height="256" rx="56" fill="url(#g)" shape-rendering="geometricPrecision"/>')
        p.append('<rect x="1.5" y="1.5" width="253" height="253" rx="54.5" fill="none" stroke="#ffffff" stroke-opacity=".06" stroke-width="3" shape-rendering="geometricPrecision"/>')
        px = 11.0
    else:
        px = 15.0
    ox = (256 - DW * px) / 2
    oy = (256 - DH * px) / 2
    p.append(drop_rects(ox, oy, px))
    p.append("</svg>")
    return "".join(p)


# ------------------------------------------------------------------ the wordmark
LETTERS = {
    "B": ["1111.", "11.11", "11.11", "1111.", "11.11", "11.11", "1111."],
    "L": ["11...", "11...", "11...", "11...", "11...", "11...", "11111"],
    "O": [".111.", "11.11", "11.11", "11.11", "11.11", "11.11", ".111."],
    "D": ["1111.", "11.11", "11.11", "11.11", "11.11", "11.11", "1111."],
}
WORD = "BLOOD"
# The drip hangs from the bottom of the second O (column offsets are within that letter).
DRIP = [(3, 7), (3, 8), (3, 9)]


def wordmark_svg(height: int = 64, color: str = LETTER, shade: str = LETTER_SHADE) -> str:
    px = 10
    gap = 1
    cols = len(WORD) * 5 + (len(WORD) - 1) * gap
    rows = 7 + 3  # letters + drip
    skew = 0.2  # forward slant (x shift per unit of height)
    w = cols * px + rows * px * skew + 4
    h = rows * px
    p = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.0f} {h}" height="{height}" width="{height * w / h:.0f}">',
         f'<g transform="translate({rows * px * skew + 2:.1f} 0) skewX(-{11.3:.1f})">']
    # One <path> per colour: cells of the same colour are filled as a single shape, so the
    # slant doesn't leave antialiasing seams between neighbouring squares.
    cells: dict[str, list[tuple[int, int]]] = {}
    x0 = 0
    for i, ch in enumerate(WORD):
        for y, row in enumerate(LETTERS[ch]):
            for x, c in enumerate(row):
                if c != "1":
                    continue
                fill = shade if y == 6 else color
                if i == 3 and (x, y) == (DRIP[0][0], 6):
                    fill = MAIN  # where the drip leaves the letter
                cells.setdefault(fill, []).append((x0 + x * px, y * px))
        if i == 3:
            for (dx, dy) in DRIP:
                cells.setdefault(MAIN if dy < 9 else BRIGHT, []).append((x0 + dx * px, dy * px))
        x0 += (5 + gap) * px
    for fill, pts in cells.items():
        d = "".join(f"M{x} {y}h{px}v{px}h-{px}z" for x, y in pts)
        p.append(f'<path d="{d}" fill="{fill}"/>')
    p.append("</g></svg>")
    return "".join(p)


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


def pixel_drop(scale: int, pad: int = 0) -> Image.Image:
    """Exact pixel-art drop for in-game textures (no browser needed)."""
    img = Image.new("RGBA", ((DW + 2 * pad) * scale, (DH + 2 * pad) * scale), (0, 0, 0, 0))
    for y in range(DH):
        for x in range(DW):
            if filled(x, y):
                c = drop_color(x, y).lstrip("#")
                rgb = tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
                for yy in range(scale):
                    for xx in range(scale):
                        img.putpixel(((x + pad) * scale + xx, (y + pad) * scale + yy), rgb)
    return img


def write(path: str, text: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def main():
    write(os.path.join(ROOT, "blood-mark.svg"), mark_svg(512))
    write(os.path.join(ROOT, "blood-logo.svg"), mark_svg(512, tile=True))
    write(os.path.join(ROOT, "blood-wordmark.svg"), wordmark_svg(128))
    write(os.path.join(PUBLIC_DIR, "blood-mark.svg"), mark_svg(512))
    write(os.path.join(PUBLIC_DIR, "blood-wordmark.svg"), wordmark_svg(128))
    if os.path.isdir(WEBSITE_DIR):
        write(os.path.join(WEBSITE_DIR, "blood-mark.svg"), mark_svg(512))
        write(os.path.join(WEBSITE_DIR, "img", "blood-logo.svg"), mark_svg(512, tile=True))
        write(os.path.join(WEBSITE_DIR, "img", "blood-wordmark.svg"), wordmark_svg(128))
    # In-game logo sprite and mod icon (pure pixels, no browser).
    if os.path.isdir(MOD_DIR):
        sprite = Image.new("RGBA", (32, 32), (0, 0, 0, 0))  # drawn at 16x16 in menus and toasts
        d2 = pixel_drop(2)
        sprite.alpha_composite(d2, ((32 - d2.width) // 2, (32 - d2.height) // 2))
        sprite.save(os.path.join(MOD_DIR, "textures", "gui", "sprites", "logo.png"))
        icon = Image.new("RGBA", (128, 128), (10, 6, 8, 255))
        d = pixel_drop(6)
        icon.alpha_composite(d, ((128 - d.width) // 2, (128 - d.height) // 2))
        icon.save(os.path.join(MOD_DIR, "icon.png"))
    if "--svg-only" in sys.argv:
        return
    big = os.path.join(ROOT, "blood-icon-1024.png")
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
        src.resize((512, 512), Image.LANCZOS).save(os.path.join(WEBSITE_DIR, "img", "blood-icon-512.png"))
    print("icons written")


if __name__ == "__main__":
    main()
