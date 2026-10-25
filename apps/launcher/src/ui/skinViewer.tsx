/**
 * Interactive 3D Minecraft character, drawn with CSS 3D boxes: each face shows its piece of the
 * skin texture (pixel-sharp), the outer layer (hat, jacket, sleeves, pants) sits slightly
 * larger on top. Drag to turn, scroll to zoom. Works with any image URL (data: or https:).
 */

import { useEffect, useRef, useState, type CSSProperties } from "react";
import { faceRect, guessModel, overlayInflate, PARTS, partBox, partOffset, type FaceId, type PartBox, type PartId, type SkinModel } from "../core/skins/skin";

/**
 * The model (classic or slim arms) a skin image uses, read from its pixels. Images from another
 * site that doesn't allow reading them keep `fallback`; they still display normally.
 */
export function useSkinModel(src: string | null, fallback: SkinModel): SkinModel {
  const [found, setFound] = useState<{ src: string; model: SkinModel } | null>(null);
  useEffect(() => {
    if (!src) return;
    let live = true;
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      try {
        if (img.naturalWidth !== 64 || img.naturalHeight !== 64) return;
        const c = document.createElement("canvas");
        c.width = 64;
        c.height = 64;
        const g = c.getContext("2d");
        if (!g) return;
        g.drawImage(img, 0, 0);
        const d = g.getImageData(0, 0, 64, 64).data;
        if (live) setFound({ src, model: guessModel((x, y) => d[(y * 64 + x) * 4 + 3]!) });
      } catch {
        // A cross-origin image without CORS headers can't be read: keep the fallback.
      }
    };
    img.src = src;
    return () => {
      live = false;
    };
  }, [src]);
  return found && found.src === src ? found.model : fallback;
}

const SHADE: Record<FaceId, number> = { front: 1, back: 0.72, right: 0.84, left: 0.84, top: 1.08, bottom: 0.6 };

function Face({ src, box, face, s }: { src: string; box: PartBox; face: FaceId; s: number }) {
  const r = faceRect(box, face);
  const { w, h, d } = box;
  const fw = (face === "right" || face === "left" ? d : w) * s;
  const fh = (face === "top" || face === "bottom" ? d : h) * s;
  const t: Record<FaceId, string> = {
    front: `translateZ(${(d * s) / 2}px)`,
    back: `rotateY(180deg) translateZ(${(d * s) / 2}px)`,
    right: `rotateY(-90deg) translateZ(${(w * s) / 2}px)`,
    left: `rotateY(90deg) translateZ(${(w * s) / 2}px)`,
    top: `rotateX(90deg) translateZ(${(h * s) / 2}px)`,
    bottom: `rotateX(-90deg) translateZ(${(h * s) / 2}px)`,
  };
  const style: CSSProperties = {
    position: "absolute",
    left: -fw / 2,
    top: -fh / 2,
    width: fw,
    height: fh,
    transform: t[face],
    backgroundImage: `url("${src}")`,
    backgroundSize: `${64 * s}px ${64 * s}px`,
    backgroundPosition: `${-r.x * s}px ${-r.y * s}px`,
    imageRendering: "pixelated",
    filter: SHADE[face] === 1 ? undefined : `brightness(${SHADE[face]})`,
  };
  return <div style={style} />;
}

function Box({ src, box, s, inflate = 0 }: { src: string; box: PartBox; s: number; inflate?: number }) {
  const scale = inflate ? `scale3d(${(box.w + inflate * 2) / box.w}, ${(box.h + inflate * 2) / box.h}, ${(box.d + inflate * 2) / box.d})` : undefined;
  return (
    <div style={{ position: "absolute", transformStyle: "preserve-3d", transform: scale }}>
      {(["back", "bottom", "right", "left", "top", "front"] as FaceId[]).map((f) => (
        <Face key={f} src={src} box={box} face={f} s={s} />
      ))}
    </div>
  );
}

/** How far each limb swings (degrees); arms and legs on opposite sides move in opposite directions. */
const SWING: Record<PartId, number> = { head: 0, body: 0, rightArm: 5, leftArm: -5, rightLeg: -4, leftLeg: 4 };

/**
 * Arms and legs swing around their top edge. The swing is a CSS animation, so the browser runs it
 * on the compositor: React doesn't re-render the ~70 faces every frame.
 */
function Part({ src, part, model, s, overlay, animate }: { src: string; part: PartId; model: SkinModel; s: number; overlay: boolean; animate: boolean }) {
  const o = partOffset(part, model);
  const base = partBox(part, "base", model);
  const limb = part !== "head" && part !== "body";
  const pivotY = limb ? o.y - base.h / 2 : o.y;
  const inner = limb ? base.h / 2 : 0;
  const swing: CSSProperties = limb && animate ? ({ "--amp": `${SWING[part]}deg` } as CSSProperties) : {};
  return (
    <div style={{ position: "absolute", transformStyle: "preserve-3d", transform: `translate3d(${o.x * s}px, ${pivotY * s}px, ${o.z * s}px)` }}>
      <div className={limb && animate ? "skin-swing" : undefined} style={{ position: "absolute", transformStyle: "preserve-3d", ...swing }}>
        <div style={{ position: "absolute", transformStyle: "preserve-3d", transform: `translateY(${inner * s}px)` }}>
          <Box src={src} box={base} s={s} />
          {overlay && <Box src={src} box={partBox(part, "overlay", model)} s={s} inflate={overlayInflate(part)} />}
        </div>
      </div>
    </div>
  );
}

export function SkinViewer({ src, model, overlay = true, height = 420, animate = true, sway = false, fill = 0.7, initial = { x: -10, y: 28 }, shadow = true, className }: {
  src: string | null;
  model: SkinModel;
  overlay?: boolean;
  height?: number;
  animate?: boolean;
  /** A slow idle turn from side to side (Home). */
  sway?: boolean;
  /** How much of the height the character fills. */
  fill?: number;
  initial?: { x: number; y: number };
  shadow?: boolean;
  className?: string;
}) {
  const [rot, setRot] = useState(initial);
  const [zoom, setZoom] = useState(1);
  const drag = useRef<{ x: number; y: number } | null>(null);
  useEffect(() => setRot(initial), [initial.x, initial.y]);

  // The whole character is 32 texels tall; fit it into `fill` of the height.
  const s = ((height * fill) / 32) * zoom;

  return (
    <div
      className={["relative select-none overflow-hidden cursor-grab active:cursor-grabbing touch-none", className].filter(Boolean).join(" ")}
      style={{ height, perspective: 1100 }}
      onPointerDown={(e) => {
        drag.current = { x: e.clientX, y: e.clientY };
        (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
      }}
      onPointerMove={(e) => {
        if (!drag.current) return;
        const dx = e.clientX - drag.current.x, dy = e.clientY - drag.current.y;
        drag.current = { x: e.clientX, y: e.clientY };
        setRot((r) => ({ x: Math.max(-60, Math.min(60, r.x - dy * 0.45)), y: r.y + dx * 0.55 }));
      }}
      onPointerUp={() => (drag.current = null)}
      onWheel={(e) => setZoom((z) => Math.max(0.6, Math.min(2.2, z * (e.deltaY > 0 ? 0.92 : 1.08))))}
      onDoubleClick={() => {
        setRot(initial);
        setZoom(1);
      }}
      title="Drag to turn · scroll to zoom · double-click to reset"
    >
      {shadow && <div className="absolute left-1/2 h-5 -translate-x-1/2 rounded-[50%] bg-black/45 blur-md" style={{ width: 16 * s * 1.1, top: `calc(50% + ${16 * s - 6}px)` }} />}
      {src && (
        <div style={{ position: "absolute", left: "50%", top: "50%", transformStyle: "preserve-3d", transform: `rotateX(${rot.x}deg) rotateY(${rot.y}deg)` }}>
          <div className={sway && animate ? "skin-sway" : undefined} style={{ position: "absolute", transformStyle: "preserve-3d" }}>
            {/* Centre the 32-texel-tall figure on its middle (y 0 = neck, head above). */}
            <div style={{ position: "absolute", transformStyle: "preserve-3d", transform: `translateY(${-8 * s}px)` }}>
              {PARTS.map((p) => (
                <Part key={p.id} src={src} part={p.id} model={model} s={s} overlay={overlay} animate={animate} />
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
