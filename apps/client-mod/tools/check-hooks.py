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
    # Control: Free Look already works in game; if this shows as missing, the check itself is wrong.
    "Free look (control)": ("MouseHandlerMixin", "turnPlayer"),
    "Zoom (field of view)": ("GameRendererMixin", "getFov"),
    "Zoom (scroll)": ("MouseHandlerMixin", "onScroll"),
    "Swing animation": ("ItemInHandRendererMixin", "applyItemArmAttackTransform"),
    "Hit effects": ("MultiPlayerGameModeMixin", "attack"),
}


def main():
    missing_any = False
    for jar in sys.argv[1:]:
        with zipfile.ZipFile(jar) as z:
            meta = json.loads(z.read("fabric.mod.json")) if "fabric.mod.json" in z.namelist() else {}
            mc = str(meta.get("depends", {}).get("minecraft", ""))
            if mc.lstrip(">=~^ ")[:2].isdigit() and int(mc.lstrip(">=~^ ")[:2]) >= 26:
                # 26.x ships unobfuscated: nothing is remapped, so there is nothing to read here.
                print(f"::notice title=Hooks in {jar.rsplit('/', 1)[-1]}::not checked statically (unobfuscated Minecraft)")
                continue
            refmaps = [n for n in z.namelist() if n.endswith("refmap.json")]
            mappings = json.loads(z.read(refmaps[0])).get("mappings", {}) if refmaps else None
            classes = {n.rsplit("/", 1)[-1][:-6]: z.read(n) for n in z.namelist() if "/mixin/" in n and n.endswith(".class")}
        ok, missing = [], []
        for label, (mixin, method) in HOOKS.items():
            if mappings is not None:
                entries = next((v for k, v in mappings.items() if k.endswith("/" + mixin)), {})
                resolved = any(key.startswith(method) for key in entries)
            else:
                # No refmap: Loom rewrote the annotation in the class itself. A resolved target is
                # renamed to its intermediary name, so the Mojang name only survives when unresolved.
                data = classes.get(mixin, b"")
                resolved = bool(data) and (method + "(").encode() not in data
            (ok if resolved else missing).append(label)
        name = jar.rsplit("/", 1)[-1]
        print(f"::notice title=Hooks in {name}::resolved: {', '.join(ok) or 'none'}")
        if missing:
            missing_any = True
            print(f"::warning title=Hooks missing in {name}::{', '.join(missing)} won't run on this Minecraft version")
    return 0


if __name__ == "__main__":
    sys.exit(main())
