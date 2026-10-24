#!/usr/bin/env python3
"""Original pixel-art community skins (64x64, classic arms) for MOTION, next to Tung Tung Tung Sahur.

Run: python3 apps/api/tools/generate-meme-skins.py
Writes apps/api/content/skins/<slug>.png. The API seeds them as community skins on start.
"""
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "content", "skins")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < 64 and 0 <= y < 64:
            self.img.putpixel((x, y), c)

    def rect(self, x, y, w, h, c, noise=0.08):
        for yy in range(h):
            for xx in range(w):
                f = 1 + self.rng.uniform(-noise, noise)
                self.px(x + xx, y + yy, shade(c, f))

    def box(self, u, v, w, h, d, faces):
        """faces: dict of top/bottom/right/front/left/back -> colour or callable(skin, x, y, w, h)."""
        rects = {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + d + w + d, v + d, w, h),
        }
        for name, (x, y, ww, hh) in rects.items():
            f = faces.get(name, faces.get("all"))
            if f is None:
                continue
            if callable(f):
                f(self, x, y, ww, hh)
            else:
                self.rect(x, y, ww, hh, f)

    # Standard parts (classic model)
    def head(self, faces): self.box(0, 0, 8, 8, 8, faces)
    def hat(self, faces): self.box(32, 0, 8, 8, 8, faces)
    def body(self, faces): self.box(16, 16, 8, 12, 4, faces)
    def right_arm(self, faces): self.box(40, 16, 4, 12, 4, faces)
    def left_arm(self, faces): self.box(32, 48, 4, 12, 4, faces)
    def right_leg(self, faces): self.box(0, 16, 4, 12, 4, faces)
    def left_leg(self, faces): self.box(16, 48, 4, 12, 4, faces)

    def save(self, slug):
        os.makedirs(OUT, exist_ok=True)
        self.img.save(os.path.join(OUT, slug + ".png"), optimize=True)
        print("wrote", slug)


def vgrad(top, bottom):
    def paint(s, x, y, w, h):
        for yy in range(h):
            t = yy / max(1, h - 1)
            c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
            s.rect(x, y + yy, w, 1, c, 0.05)
    return paint


# ---------------------------------------------------------------------------- Tralalero Tralala
def tralalero():
    s = Skin(11)
    blue, dark, belly = hexc("#3d7fc4"), hexc("#2a5a92"), hexc("#e9eef2")

    def face(s, x, y, w, h):
        s.rect(x, y, w, h, blue)
        s.rect(x, y + 5, w, 3, belly)                         # white jaw
        for i in range(w):                                    # teeth
            if i % 2 == 0:
                s.px(x + i, y + 5, hexc("#ffffff"))
        s.rect(x + 1, y + 2, 2, 2, hexc("#ffffff"), 0)        # eyes
        s.rect(x + 5, y + 2, 2, 2, hexc("#ffffff"), 0)
        s.px(x + 2, y + 3, hexc("#101418"))
        s.px(x + 5, y + 3, hexc("#101418"))
        s.rect(x + 1, y + 6, 6, 1, hexc("#7a1f2b"), 0)          # mouth line

    s.head({"all": blue, "top": dark, "bottom": belly, "front": face})
    # Dorsal fin on the hat layer (back/top)
    s.hat({"top": lambda s, x, y, w, h: [s.px(x + 3 + i, y + 2 + j, dark) for i in range(2) for j in range(4)]})
    s.body({"all": blue, "front": lambda s, x, y, w, h: (s.rect(x, y, w, h, blue), s.rect(x + 2, y + 1, 4, 10, belly)),
            "back": lambda s, x, y, w, h: (s.rect(x, y, w, h, blue), s.rect(x + 3, y, 2, 4, dark))})
    fin = {"all": blue, "front": vgrad(blue, dark), "bottom": belly}
    s.right_arm(fin)
    s.left_arm(fin)

    def sneaker(s, x, y, w, h):
        s.rect(x, y, w, h - 4, blue)
        s.rect(x, y + h - 4, w, 3, hexc("#1f6fe5"))
        s.rect(x, y + h - 1, w, 1, hexc("#f4f4f4"), 0)        # white sole
        s.px(x + 1, y + h - 3, hexc("#ffffff"))                 # laces
        s.px(x + 2, y + h - 3, hexc("#ffffff"))

    legs = {"all": sneaker, "top": blue, "bottom": hexc("#f4f4f4")}
    s.right_leg(legs)
    s.left_leg(legs)
    s.save("tralalero-tralala")


# ---------------------------------------------------------------------------- Bombardiro Crocodilo
def bombardiro():
    s = Skin(23)
    green, dgreen, grey, dgrey = hexc("#4f8a3a"), hexc("#355f27"), hexc("#7d8790"), hexc("#4e565d")

    def croc(s, x, y, w, h):
        s.rect(x, y, w, h, green)
        s.rect(x, y + 4, w, 4, hexc("#d9d2a0"))                  # pale snout underside
        for i in range(w):
            if i % 2 == 1:
                s.px(x + i, y + 4, hexc("#ffffff"))               # teeth
        s.rect(x + 1, y + 1, 2, 2, hexc("#f2c22b"), 0)          # yellow eyes
        s.rect(x + 5, y + 1, 2, 2, hexc("#f2c22b"), 0)
        s.px(x + 2, y + 1, hexc("#141414"))
        s.px(x + 5, y + 1, hexc("#141414"))
        s.px(x + 3, y + 3, dgreen)                               # nostrils
        s.px(x + 4, y + 3, dgreen)

    def scales(s, x, y, w, h):
        s.rect(x, y, w, h, green)
        for yy in range(0, h, 2):
            for xx in range((yy // 2) % 2, w, 2):
                s.px(x + xx, y + yy, dgreen)

    s.head({"all": scales, "front": croc, "bottom": hexc("#d9d2a0")})

    def fuselage(s, x, y, w, h):
        s.rect(x, y, w, h, grey)
        s.rect(x, y + 3, w, 1, dgrey)                            # panel lines
        s.rect(x, y + 8, w, 1, dgrey)
        if w >= 8:
            s.rect(x + 2, y + 4, 4, 3, hexc("#c43b2e"))          # red bomb bay marking
            s.rect(x + 3, y + 5, 2, 1, hexc("#f0f0f0"), 0)

    s.body({"all": fuselage, "top": grey, "bottom": dgrey})

    def wing(s, x, y, w, h):
        s.rect(x, y, w, h, grey)
        s.rect(x, y + h - 3, w, 3, green)                        # croc claws poking out
        s.rect(x, y + 2, w, 1, dgrey)
        s.px(x, y + 5, hexc("#c43b2e"))

    s.right_arm({"all": wing, "top": grey, "bottom": green})
    s.left_arm({"all": wing, "top": grey, "bottom": green})
    s.right_leg({"all": vgrad(green, dgreen), "bottom": hexc("#2a2a2a")})
    s.left_leg({"all": vgrad(green, dgreen), "bottom": hexc("#2a2a2a")})
    s.save("bombardiro-crocodilo")


# ---------------------------------------------------------------------------- Ballerina Cappuccina
def ballerina():
    s = Skin(37)
    cup, cupd = hexc("#f2ede4"), hexc("#cfc6b6")
    coffee, foam = hexc("#6b4026"), hexc("#d8b98f")
    pink, dpink, skin = hexc("#f4a6c6"), hexc("#d97aa6"), hexc("#f1c7a4")

    def cupface(s, x, y, w, h):
        s.rect(x, y, w, h, cup)
        s.rect(x, y, w, 1, cupd, 0)                              # rim
        s.rect(x + 1, y + 3, 2, 2, hexc("#2b1c14"), 0)          # eyes
        s.rect(x + 5, y + 3, 2, 2, hexc("#2b1c14"), 0)
        s.px(x + 1, y + 3, hexc("#ffffff"))
        s.px(x + 5, y + 3, hexc("#ffffff"))
        s.rect(x + 3, y + 6, 2, 1, hexc("#d9668a"), 0)          # smile
        s.px(x, y + 5, hexc("#f5b3c8"))                         # blush
        s.px(x + 7, y + 5, hexc("#f5b3c8"))

    def latte(s, x, y, w, h):
        s.rect(x, y, w, h, coffee)
        for (dx, dy) in [(3, 2), (4, 2), (2, 3), (5, 3), (3, 4), (4, 4), (3, 5), (4, 5)]:
            s.px(x + dx, y + dy, foam)                           # latte-art heart

    def handle(s, x, y, w, h):
        s.rect(x, y, w, h, cup)
        s.rect(x + 2, y + 2, 4, 4, cupd)
        s.rect(x + 3, y + 3, 2, 2, cup)

    s.head({"all": cup, "front": cupface, "top": latte, "right": handle, "bottom": cupd})
    # A bun of foam on the hat layer
    s.hat({"top": lambda s, x, y, w, h: s.rect(x + 2, y + 2, 4, 4, foam)})

    def dress(s, x, y, w, h):
        s.rect(x, y, w, h, pink)
        s.rect(x, y + 7, w, 2, dpink)                            # waist band
        for i in range(w):                                       # tutu ruffle
            s.px(x + i, y + 9 + (i % 2), hexc("#ffd3e6"))
        s.rect(x, y + 10, w, 2, hexc("#ffc3dc"))

    s.body({"all": dress})
    s.right_arm({"all": vgrad(skin, shade(skin, 0.9)), "top": pink})
    s.left_arm({"all": vgrad(skin, shade(skin, 0.9)), "top": pink})

    def tights(s, x, y, w, h):
        s.rect(x, y, w, h, hexc("#f7c9da"))
        s.rect(x, y + h - 3, w, 3, dpink)                        # ballet shoes
        s.px(x + 1, y + h - 5, dpink)
        s.px(x + 2, y + h - 4, dpink)

    s.right_leg({"all": tights, "bottom": dpink})
    s.left_leg({"all": tights, "bottom": dpink})
    s.save("ballerina-cappuccina")


if __name__ == "__main__":
    tralalero()
    bombardiro()
    ballerina()
