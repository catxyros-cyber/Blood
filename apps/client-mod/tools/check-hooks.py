#!/usr/bin/env python3
"""Report which optional MOTION Client mixin hooks resolved against this Minecraft version.

Hooks marked require = 0 (zoom, swing animation, hit effects…) are skipped silently in game when
their target method doesn't exist. Loom only writes resolved targets into the refmap, so reading
the refmap from the built jar tells us which of them will work. Prints GitHub annotations.

Usage: check-hooks.py <jar> [<jar>…]
"""
import json
import sys
import zipfile

HOOKS = {
    "Zoom (field of view)": ("GameRendererMixin", "getFov"),
    "Zoom (scroll)": ("MouseHandlerMixin", "onScroll"),
    "Swing animation": ("ItemInHandRendererMixin", "applyItemArmAttackTransform"),
    "Hit effects": ("MultiPlayerGameModeMixin", "attack"),
}


def main():
    missing_any = False
    for jar in sys.argv[1:]:
        with zipfile.ZipFile(jar) as z:
            refmaps = [n for n in z.namelist() if n.endswith("refmap.json")]
            if not refmaps:
                print(f"::warning title=Hook check::{jar}: no refmap found")
                continue
            data = json.loads(z.read(refmaps[0]))
        mappings = data.get("mappings", {})
        ok, missing = [], []
        for label, (mixin, method) in HOOKS.items():
            entries = next((v for k, v in mappings.items() if k.endswith("/" + mixin)), {})
            (ok if any(key.startswith(method) for key in entries) else missing).append(label)
        name = jar.rsplit("/", 1)[-1]
        print(f"::notice title=Hooks in {name}::resolved: {', '.join(ok) or 'none'}")
        if missing:
            missing_any = True
            print(f"::warning title=Hooks missing in {name}::{', '.join(missing)} won't run on this Minecraft version")
    return 0


if __name__ == "__main__":
    sys.exit(main())
