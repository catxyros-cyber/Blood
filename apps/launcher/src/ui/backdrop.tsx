/**
 * The window background: the theme's art (or the player's own image), dimmed and blurred
 * behind regular pages and shown clearly on Home, plus optional rising embers.
 */

import { useEffect, useRef, useState, type CSSProperties } from "react";
import { app, effectiveAccent } from "../services/app";
import { native } from "../services/native";
import { useStore } from "../services/store";
import { cx } from "./components";

const customCache = new Map<string, string | null>();

/** Data URL for the custom background (cached for the session). */
export function useCustomBackground(path: string | null): string | null {
  const [url, setUrl] = useState<string | null>(path ? (customCache.get(path) ?? null) : null);
  useEffect(() => {
    if (!path) return setUrl(null);
    if (customCache.has(path)) return setUrl(customCache.get(path) ?? null);
    let live = true;
    native
      .readDataUrl(path, 20 * 1024 * 1024)
      .then((u) => {
        customCache.set(path, u);
        if (live) setUrl(u);
      })
      .catch(() => {
        customCache.set(path, null);
        if (live) setUrl(null);
      });
    return () => {
      live = false;
    };
  }, [path]);
  return url;
}

export function forgetCustomBackground(path: string) {
  customCache.delete(path);
}

/** The bundled art for a theme: the full scene (Home) and a small pre-blurred copy (other pages). */
export function themeArt(theme: string): { sharp: string; soft: string; thumb: string } {
  return { sharp: `/backgrounds/${theme}.webp`, soft: `/backgrounds/${theme}-soft.webp`, thumb: `/backgrounds/${theme}-thumb.webp` };
}

const decoded = new Map<string, Promise<boolean>>();
/** Load and decode once per session, so switching pages or themes never shows a half-drawn image. */
function decodeOnce(src: string): Promise<boolean> {
  let p = decoded.get(src);
  if (!p) {
    const img = new Image();
    img.decoding = "async";
    img.src = src;
    p = img.decode().then(
      () => true,
      () => false,
    );
    decoded.set(src, p);
  }
  return p;
}

/** The newest of `src` that has finished decoding (null until the first one has). */
function useDecoded(src: string | null): string | null {
  const [ready, setReady] = useState<string | null>(null);
  useEffect(() => {
    if (!src) return setReady(null);
    let live = true;
    void decodeOnce(src).then((ok) => {
      if (live && ok) setReady(src);
    });
    return () => {
      live = false;
    };
  }, [src]);
  return src ? ready : null;
}

const softCache = new Map<string, Promise<string | null>>();
/**
 * A small blurred copy of the player's own background, made once with a canvas. Drawing that
 * scaled up costs nothing per frame, unlike a CSS blur over the whole window.
 */
function softVariant(src: string, blurPx: number): Promise<string | null> {
  const key = `${blurPx}:${src}`;
  let p = softCache.get(key);
  if (!p) {
    p = (async () => {
      const img = new Image();
      img.src = src;
      await img.decode();
      const w = 960;
      const h = Math.max(1, Math.round((w * img.naturalHeight) / Math.max(1, img.naturalWidth)));
      const c = document.createElement("canvas");
      c.width = w;
      c.height = h;
      const g = c.getContext("2d");
      if (!g) return null;
      // The blur is applied at 960 px wide; scale the setting (window pixels) to match.
      const scaled = (blurPx * w) / Math.max(1280, window.innerWidth);
      g.filter = `blur(${scaled.toFixed(1)}px)`;
      const pad = scaled * 3;
      g.drawImage(img, -pad, -pad, w + pad * 2, h + pad * 2);
      const blob = await new Promise<Blob | null>((r) => c.toBlob(r, "image/webp", 0.8));
      return blob ? URL.createObjectURL(blob) : null;
    })().catch(() => null);
    softCache.set(key, p);
  }
  return p;
}

function useSoftCustom(src: string | null, blurPx: number): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!src || blurPx <= 0) return setUrl(null);
    let live = true;
    void softVariant(src, blurPx).then((u) => live && setUrl(u));
    return () => {
      live = false;
    };
  }, [src, blurPx]);
  return url;
}

/**
 * Stacked copies of an image that fade in over the previous one, then the old copy is dropped.
 * Nothing is drawn until the image has decoded, so there's no flash of a half-loaded picture.
 */
function FadeImage({ src, className, style, fadeMs }: { src: string | null; className: string; style?: CSSProperties; fadeMs: number }) {
  const [layers, setLayers] = useState<{ src: string; key: number }[]>([]);
  const counter = useRef(0);
  useEffect(() => {
    if (!src) return setLayers([]);
    setLayers((l) => (l.length && l[l.length - 1]!.src === src ? l : [...l.slice(-1), { src, key: ++counter.current }]));
    const t = setTimeout(() => setLayers((l) => l.slice(-1)), fadeMs + 50);
    return () => clearTimeout(t);
  }, [src, fadeMs]);
  return (
    <>
      {layers.map((l, i) => (
        <img
          key={l.key}
          src={l.src}
          alt=""
          draggable={false}
          decoding="async"
          className={className}
          style={{ ...style, animation: i > 0 || layers.length === 1 ? `bloom-fade-in ${fadeMs}ms ease-out both` : undefined }}
        />
      ))}
    </>
  );
}

export function Backdrop() {
  const settings = useStore(app, (s) => s.settings);
  const page = useStore(app, (s) => s.route.page);
  const home = page === "home";
  const bg = settings.background;
  const custom = useCustomBackground(bg.mode === "custom" ? bg.custom : null);
  const art = themeArt(settings.theme);
  const sharpSrc = bg.mode === "none" ? null : bg.mode === "custom" ? custom : art.sharp;
  // Pages other than Home: a pre-blurred copy (bundled for theme art, made once for your own image).
  const softCustom = useSoftCustom(bg.mode === "custom" ? custom : null, bg.blur);
  const softSrc = bg.mode === "none" ? null : bg.blur <= 0 ? sharpSrc : bg.mode === "custom" ? softCustom : art.soft;

  // The full-size image is only fetched once Home has been shown (the app may start elsewhere).
  const [wantSharp, setWantSharp] = useState(home);
  useEffect(() => {
    if (home) setWantSharp(true);
  }, [home]);
  const sharp = useDecoded(wantSharp ? sharpSrc : null);
  const soft = useDecoded(softSrc);
  const fadeMs = settings.reduceMotion || matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 320;
  // Theme art's soft copy already looks like ~12px; stronger settings add only the difference.
  const extraBlur = bg.mode === "theme" && bg.blur > 12 ? (bg.blur - 12) / 2 : 0;

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none bg-bg" aria-hidden>
      <div className="absolute inset-0 transition-opacity duration-300" style={{ opacity: home ? 0 : 1 }}>
        <FadeImage
          src={soft}
          fadeMs={fadeMs}
          className="absolute inset-0 h-full w-full object-cover"
          style={extraBlur ? { filter: `blur(${extraBlur}px)`, transform: "scale(1.04)" } : undefined}
        />
        <div className="absolute inset-0" style={{ background: `color-mix(in srgb, var(--bg) ${Math.round(bg.dim * 100)}%, transparent)` }} />
      </div>
      <div className="absolute inset-0 transition-opacity duration-300" style={{ opacity: home ? 1 : 0 }}>
        {/* A slow settle each time Home opens; one transform on one layer, so it's cheap. */}
        <div className={cx("absolute inset-x-0 top-0 h-[max(560px,74%)] overflow-hidden", home && fadeMs > 0 && "hero-settle")}>
          <FadeImage src={sharp} fadeMs={fadeMs} className="absolute inset-0 w-full h-full object-cover object-[50%_22%]" />
        </div>
        <div className="absolute inset-x-0 top-0 h-[max(560px,74%)] hero-fade" />
        <div className="absolute inset-y-0 left-0 w-[64%] bg-gradient-to-r from-bg/70 via-bg/30 to-transparent" />
        {settings.petals && fadeMs > 0 && home && <Embers color={effectiveAccent(settings)} />}
      </div>
    </div>
  );
}

interface Ember {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  phase: number;
  life: number;
  hot: number;
}

/**
 * Lightweight canvas embers: small square sparks that rise, drift and flicker out.
 * Capped at ~30 FPS and paused while the window is hidden; off with Reduce motion.
 */
export function Embers({ color, count = 34 }: { color: string; count?: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    let w = 0, h = 0, raf = 0, last = 0;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const resize = () => {
      w = canvas.clientWidth;
      h = canvas.clientHeight;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    const make = (initial: boolean): Ember => ({
      x: Math.random() * w,
      y: initial ? h * (0.35 + Math.random() * 0.65) : h + 10,
      vx: (Math.random() - 0.5) * 10,
      vy: -(14 + Math.random() * 26),
      size: Math.random() < 0.25 ? 3 : 2,
      phase: Math.random() * 6.28,
      life: initial ? Math.random() : 0,
      hot: Math.random(),
    });
    const embers = Array.from({ length: count }, () => make(true));
    const frame = (t: number) => {
      raf = requestAnimationFrame(frame);
      if (document.hidden || t - last < 33) return;
      const dt = Math.min(0.1, (t - last) / 1000);
      last = t;
      ctx.clearRect(0, 0, w, h);
      for (const e of embers) {
        e.x += (e.vx + Math.sin(t / 700 + e.phase) * 9) * dt;
        e.y += e.vy * dt;
        e.life += dt / 9;
        if (e.y < h * 0.2 || e.life >= 1) Object.assign(e, make(false));
        // Fade in near the bottom, out as they rise and burn down.
        const height = Math.min(1, Math.max(0, (e.y - h * 0.2) / (h * 0.45)));
        const flicker = 0.75 + 0.25 * Math.sin(t / 90 + e.phase * 3);
        const a = Math.min(1, e.life * 6) * (1 - e.life) * height * flicker;
        if (a <= 0.01) continue;
        const s = e.size;
        ctx.globalAlpha = a * 0.18;
        ctx.fillStyle = color;
        ctx.fillRect(e.x - s * 2, e.y - s * 2, s * 5, s * 5);
        ctx.globalAlpha = a * 0.9;
        ctx.fillStyle = e.hot > 0.7 ? "#ffd2a8" : color;
        ctx.fillRect(e.x, e.y, s, s);
      }
      ctx.globalAlpha = 1;
    };
    raf = requestAnimationFrame(frame);
    window.addEventListener("resize", resize);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
    };
  }, [color, count]);
  return <canvas ref={ref} className="absolute inset-0 h-full w-full" />;
}

/** Falling petals/snow, used by the Visuals page preview of MOTION Client's in-game effects. */
interface Petal {
  x: number;
  y: number;
  vx: number;
  vy: number;
  rot: number;
  spin: number;
  size: number;
  phase: number;
  shade: number;
}

/** Lightweight canvas petals (≈30 FPS, paused when the window is hidden). */
export function Petals({ color, count = 26 }: { color: string; count?: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    let w = 0, h = 0, raf = 0, last = 0;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const resize = () => {
      w = canvas.clientWidth;
      h = canvas.clientHeight;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    const make = (initial: boolean): Petal => ({
      x: Math.random() * w,
      y: initial ? Math.random() * h : -20,
      vx: 12 + Math.random() * 18,
      vy: 16 + Math.random() * 22,
      rot: Math.random() * 6.28,
      spin: (Math.random() - 0.5) * 1.6,
      size: 4 + Math.random() * 5,
      phase: Math.random() * 6.28,
      shade: 0.75 + Math.random() * 0.25,
    });
    const petals = Array.from({ length: count }, () => make(true));
    const frame = (t: number) => {
      raf = requestAnimationFrame(frame);
      if (document.hidden || t - last < 33) return;
      const dt = Math.min(0.1, (t - last) / 1000);
      last = t;
      ctx.clearRect(0, 0, w, h);
      for (const p of petals) {
        p.x += (p.vx + Math.sin(t / 900 + p.phase) * 14) * dt;
        p.y += p.vy * dt;
        p.rot += p.spin * dt;
        if (p.y > h + 20 || p.x > w + 20) Object.assign(p, make(false), { x: Math.random() * w * 0.9 - w * 0.2 });
        const fade = Math.min(1, Math.max(0, (h * 0.95 - p.y) / (h * 0.35)));
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.rot);
        ctx.scale(1, 0.55 + 0.45 * Math.abs(Math.sin(t / 700 + p.phase)));
        ctx.globalAlpha = 0.75 * fade * p.shade;
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.ellipse(0, 0, p.size, p.size * 0.62, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha *= 0.5;
        ctx.fillStyle = "#ffffff";
        ctx.beginPath();
        ctx.ellipse(-p.size * 0.25, -p.size * 0.15, p.size * 0.35, p.size * 0.2, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }
    };
    raf = requestAnimationFrame(frame);
    window.addEventListener("resize", resize);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
    };
  }, [color, count]);
  return <canvas ref={ref} className="absolute inset-0 h-full w-full" />;
}
