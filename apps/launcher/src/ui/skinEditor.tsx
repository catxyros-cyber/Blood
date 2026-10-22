/**
 * Skin editor: paint the 64×64 texture one face at a time (head, body, arms, legs × front,
 * back, sides, top, bottom; base or outer layer) or the whole texture at once. Brush, eraser,
 * fill and colour picker, undo/redo, zoom. Every change updates the 3D preview live.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { FACES, faceRect, PARTS, partBox, type FaceId, type Layer, type PartId, type Rect, type SkinModel } from "../core/skins/skin";
import { Button, cx, Segmented, Select, Slider, Toggle } from "./components";
import { Icon } from "./icons";

type Tool = "brush" | "eraser" | "fill" | "picker";
const PALETTE = ["#000000", "#ffffff", "#39ff88", "#c6ff3d", "#0b2a19", "#e3192f", "#ffcc99", "#c68c53", "#6b4226", "#2b6cff", "#7c4dff", "#f2c46b"];

function hexToRgba(hex: string): [number, number, number, number] {
  const h = hex.replace("#", "");
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 255];
}

function rgbaToHex(r: number, g: number, b: number): string {
  return "#" + [r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("");
}

/** A plain starter skin (solid colours per part), so "New skin" isn't an empty texture. */
export function blankSkin(): string {
  const c = document.createElement("canvas");
  c.width = c.height = 64;
  const g = c.getContext("2d")!;
  const colours: Record<PartId, string> = { head: "#e0ac7e", body: "#16a35a", rightArm: "#e0ac7e", leftArm: "#e0ac7e", rightLeg: "#1d2b25", leftLeg: "#1d2b25" };
  for (const p of PARTS) {
    for (const f of FACES) {
      const r = faceRect(partBox(p.id, "base", "classic"), f.id);
      g.fillStyle = colours[p.id];
      g.fillRect(r.x, r.y, r.w, r.h);
    }
  }
  // Simple face: eyes and hair.
  g.fillStyle = "#3b2414";
  g.fillRect(8, 0, 8, 8);
  g.fillRect(8, 8, 8, 2);
  g.fillStyle = "#ffffff";
  g.fillRect(9, 12, 2, 1);
  g.fillRect(13, 12, 2, 1);
  g.fillStyle = "#1f6f3f";
  g.fillRect(10, 12, 1, 1);
  g.fillRect(14, 12, 1, 1);
  return c.toDataURL("image/png");
}

export function SkinEditor({ png, model, onChange, onSave, onImport }: {
  png: string;
  model: SkinModel;
  /** Live preview: called with the current texture after each change. */
  onChange: (png: string) => void;
  onSave: (png: string) => void;
  onImport: () => Promise<string | null>;
}) {
  const tex = useRef<HTMLCanvasElement>(document.createElement("canvas"));
  const view = useRef<HTMLCanvasElement>(null);
  const undo = useRef<ImageData[]>([]);
  const redo = useRef<ImageData[]>([]);
  const painting = useRef(false);
  const [, force] = useState(0);
  const [mode, setMode] = useState<"face" | "texture">("face");
  const [part, setPart] = useState<PartId>("head");
  const [face, setFace] = useState<FaceId>("front");
  const [layer, setLayer] = useState<Layer>("base");
  const [tool, setTool] = useState<Tool>("brush");
  const [color, setColor] = useState("#39ff88");
  const [zoom, setZoom] = useState(28);
  const [ready, setReady] = useState(false);

  const rect: Rect = mode === "texture" ? { x: 0, y: 0, w: 64, h: 64 } : faceRect(partBox(part, layer, model), face);
  const cell = mode === "texture" ? Math.max(4, Math.round(zoom / 3.5)) : zoom;

  // Load the texture when the source skin changes.
  useEffect(() => {
    const img = new Image();
    img.onload = () => {
      const c = tex.current;
      c.width = c.height = 64;
      const g = c.getContext("2d", { willReadFrequently: true })!;
      g.clearRect(0, 0, 64, 64);
      g.drawImage(img, 0, 0);
      undo.current = [];
      redo.current = [];
      setReady(true);
      force((n) => n + 1);
    };
    img.src = png;
  }, [png]);

  const draw = useCallback(() => {
    const v = view.current;
    if (!v || !ready) return;
    v.width = rect.w * cell;
    v.height = rect.h * cell;
    const g = v.getContext("2d")!;
    // Checkerboard shows transparency.
    for (let y = 0; y < rect.h; y++) for (let x = 0; x < rect.w; x++) {
      g.fillStyle = (x + y) % 2 ? "#1a201c" : "#121714";
      g.fillRect(x * cell, y * cell, cell, cell);
    }
    if (layer === "overlay" && mode === "face") {
      // Show the base layer faintly under the outer layer.
      g.globalAlpha = 0.35;
      const b = faceRect(partBox(part, "base", model), face);
      g.imageSmoothingEnabled = false;
      g.drawImage(tex.current, b.x, b.y, b.w, b.h, 0, 0, v.width, v.height);
      g.globalAlpha = 1;
    }
    g.imageSmoothingEnabled = false;
    g.drawImage(tex.current, rect.x, rect.y, rect.w, rect.h, 0, 0, v.width, v.height);
    if (cell >= 8) {
      g.strokeStyle = "rgba(255,255,255,0.07)";
      g.lineWidth = 1;
      for (let x = 0; x <= rect.w; x++) {
        g.beginPath();
        g.moveTo(x * cell + 0.5, 0);
        g.lineTo(x * cell + 0.5, v.height);
        g.stroke();
      }
      for (let y = 0; y <= rect.h; y++) {
        g.beginPath();
        g.moveTo(0, y * cell + 0.5);
        g.lineTo(v.width, y * cell + 0.5);
        g.stroke();
      }
    }
  }, [rect.x, rect.y, rect.w, rect.h, cell, ready, layer, mode, part, face, model]);

  useEffect(draw);

  const ctx = () => tex.current.getContext("2d", { willReadFrequently: true })!;
  const snapshot = () => {
    undo.current.push(ctx().getImageData(0, 0, 64, 64));
    if (undo.current.length > 80) undo.current.shift();
    redo.current = [];
  };
  const emit = () => onChange(tex.current.toDataURL("image/png"));

  const at = (e: React.PointerEvent): { x: number; y: number } | null => {
    const r = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect();
    const x = Math.floor(((e.clientX - r.left) / r.width) * rect.w);
    const y = Math.floor(((e.clientY - r.top) / r.height) * rect.h);
    if (x < 0 || y < 0 || x >= rect.w || y >= rect.h) return null;
    return { x: rect.x + x, y: rect.y + y };
  };

  const paintAt = (p: { x: number; y: number }) => {
    const g = ctx();
    if (tool === "brush") {
      g.fillStyle = color;
      g.clearRect(p.x, p.y, 1, 1);
      g.fillRect(p.x, p.y, 1, 1);
    } else if (tool === "eraser") {
      g.clearRect(p.x, p.y, 1, 1);
    } else if (tool === "picker") {
      const d = g.getImageData(p.x, p.y, 1, 1).data;
      if (d[3]! > 0) setColor(rgbaToHex(d[0]!, d[1]!, d[2]!));
      setTool("brush");
      return;
    } else if (tool === "fill") {
      // Flood fill inside the visible rectangle, matching the exact colour.
      const img = g.getImageData(0, 0, 64, 64);
      const d = img.data;
      const idx = (x: number, y: number) => (y * 64 + x) * 4;
      const t0 = idx(p.x, p.y);
      const target = [d[t0]!, d[t0 + 1]!, d[t0 + 2]!, d[t0 + 3]!];
      const fill = hexToRgba(color);
      if (target.every((v, i) => v === fill[i])) return;
      const stack = [[p.x, p.y]];
      while (stack.length) {
        const [x, y] = stack.pop()!;
        if (x! < rect.x || y! < rect.y || x! >= rect.x + rect.w || y! >= rect.y + rect.h) continue;
        const i = idx(x!, y!);
        if (d[i] !== target[0] || d[i + 1] !== target[1] || d[i + 2] !== target[2] || d[i + 3] !== target[3]) continue;
        d[i] = fill[0];
        d[i + 1] = fill[1];
        d[i + 2] = fill[2];
        d[i + 3] = fill[3];
        stack.push([x! + 1, y!], [x! - 1, y!], [x!, y! + 1], [x!, y! - 1]);
      }
      g.putImageData(img, 0, 0);
    }
    draw();
  };

  const doUndo = () => {
    const prev = undo.current.pop();
    if (!prev) return;
    redo.current.push(ctx().getImageData(0, 0, 64, 64));
    ctx().putImageData(prev, 0, 0);
    draw();
    emit();
    force((n) => n + 1);
  };
  const doRedo = () => {
    const next = redo.current.pop();
    if (!next) return;
    undo.current.push(ctx().getImageData(0, 0, 64, 64));
    ctx().putImageData(next, 0, 0);
    draw();
    emit();
    force((n) => n + 1);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      if (e.key.toLowerCase() === "z" && !e.shiftKey) {
        e.preventDefault();
        doUndo();
      } else if (e.key.toLowerCase() === "y" || (e.key.toLowerCase() === "z" && e.shiftKey)) {
        e.preventDefault();
        doRedo();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const exportPng = () => {
    const a = document.createElement("a");
    a.href = tex.current.toDataURL("image/png");
    a.download = "motion-skin.png";
    a.click();
  };

  const toolBtn = (t: Tool, icon: React.ReactNode, label: string) => (
    <button
      type="button"
      title={label}
      onClick={() => setTool(t)}
      className={cx("h-9 px-3 rounded-lg border flex items-center gap-1.5 text-[12.5px] cursor-pointer", tool === t ? "border-accent bg-accent/15 text-fg" : "border-line bg-panel-2 text-muted hover:text-fg")}
    >
      {icon}
      {label}
    </button>
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Segmented value={mode} onChange={setMode} options={[{ value: "face", label: "Parts" }, { value: "texture", label: "Whole texture" }]} />
        {mode === "face" && (
          <>
            <Select value={part} onChange={(v) => setPart(v as PartId)} options={PARTS.map((p) => ({ value: p.id, label: p.name }))} className="w-36" />
            <Select value={face} onChange={(v) => setFace(v as FaceId)} options={FACES.map((f) => ({ value: f.id, label: f.name }))} className="w-28" />
            <Toggle checked={layer === "overlay"} onChange={(v) => setLayer(v ? "overlay" : "base")} label="Outer layer" />
          </>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {toolBtn("brush", <Icon.Paint size={14} />, "Brush")}
        {toolBtn("eraser", <Icon.Close size={14} />, "Eraser")}
        {toolBtn("fill", <Icon.Drop size={14} />, "Fill")}
        {toolBtn("picker", <Icon.Crosshair size={14} />, "Pick colour")}
        <label className="h-9 px-2 rounded-lg border border-line bg-panel-2 flex items-center gap-2 cursor-pointer" title="Colour">
          <input type="color" value={color} onChange={(e) => setColor(e.target.value)} className="h-6 w-8 bg-transparent border-0 cursor-pointer" />
          <span className="font-mono text-[12px] text-muted">{color}</span>
        </label>
        <div className="flex gap-1">
          {PALETTE.map((c) => (
            <button key={c} type="button" title={c} onClick={() => setColor(c)} className={cx("h-6 w-6 rounded-md border cursor-pointer", color === c ? "border-fg" : "border-white/10")} style={{ background: c }} />
          ))}
        </div>
      </div>

      <div className="rounded-xl border border-line bg-[#0b0e0c] p-3 overflow-auto max-h-[460px] grid place-items-center">
        <canvas
          ref={view}
          className="touch-none cursor-crosshair"
          style={{ imageRendering: "pixelated" }}
          onPointerDown={(e) => {
            const p = at(e);
            if (!p) return;
            (e.currentTarget as HTMLCanvasElement).setPointerCapture(e.pointerId);
            if (tool !== "picker") snapshot();
            painting.current = tool === "brush" || tool === "eraser";
            paintAt(p);
            if (tool !== "picker") emit();
          }}
          onPointerMove={(e) => {
            if (!painting.current) return;
            const p = at(e);
            if (p) paintAt(p);
          }}
          onPointerUp={() => {
            if (painting.current) emit();
            painting.current = false;
            force((n) => n + 1);
          }}
        />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="w-48">
          <Slider value={zoom} min={12} max={48} step={2} format={(v) => `${Math.round((v / 28) * 100)}%`} onChange={setZoom} />
        </div>
        <Button size="sm" icon={<Icon.Undo size={14} />} disabled={!undo.current.length} onClick={doUndo}>Undo</Button>
        <Button size="sm" icon={<Icon.Refresh size={14} />} disabled={!redo.current.length} onClick={doRedo}>Redo</Button>
        <div className="flex-1" />
        <Button size="sm" icon={<Icon.Upload size={14} />} onClick={async () => {
          const imported = await onImport();
          if (imported) {
            const img = new Image();
            img.onload = () => {
              snapshot();
              ctx().clearRect(0, 0, 64, 64);
              ctx().drawImage(img, 0, 0);
              draw();
              emit();
            };
            img.src = imported;
          }
        }}>Import PNG</Button>
        <Button size="sm" icon={<Icon.Download size={14} />} onClick={exportPng}>Export PNG</Button>
        <Button size="sm" variant="primary" icon={<Icon.Check size={14} />} onClick={() => onSave(tex.current.toDataURL("image/png"))}>Save to library</Button>
      </div>
      <p className="text-[12px] text-faint">Ctrl+Z / Ctrl+Y to undo and redo. With "Outer layer" on, the base layer shows faintly underneath. Export saves to your Downloads folder.</p>
    </div>
  );
}
