/**
 * The window background: the theme's art (or the player's own image), dimmed and blurred
 * behind regular pages and shown clearly on Home, plus optional rising embers.
 */

import { useEffect, useRef, useState } from "react";
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

export function Backdrop() {
  const settings = useStore(app, (s) => s.settings);
  const page = useStore(app, (s) => s.route.page);
  const custom = useCustomBackground(settings.background.mode === "custom" ? settings.background.custom : null);
  const bg = settings.background;
  const src = bg.mode === "none" ? null : bg.mode === "custom" ? custom : `/backgrounds/${settings.theme}.jpg`;
  const home = page === "home";
  const dim = home ? 0 : bg.dim;
  const blur = home || settings.reduceMotion ? (home ? 0 : Math.min(bg.blur, 6)) : bg.blur;
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none" aria-hidden>
      {src && (
        <img
          src={src}
          alt=""
          className={cx("backdrop-art absolute inset-x-0 top-0 w-full object-cover", home ? "h-[max(560px,68%)] object-bottom" : "h-full")}
          style={{ filter: blur ? `blur(${blur}px)` : undefined, transform: blur ? "scale(1.04)" : undefined }}
          draggable={false}
        />
      )}
      <div className="absolute inset-0 transition-colors duration-500" style={{ background: `color-mix(in srgb, var(--bg) ${Math.round(dim * 100)}%, transparent)` }} />
      {home && <div className="absolute inset-x-0 top-0 h-[max(560px,68%)] hero-fade" />}
      {home && <div className="absolute inset-y-0 left-0 w-[70%] bg-gradient-to-r from-bg/55 via-bg/20 to-transparent" />}
      {home && settings.petals && !settings.reduceMotion && <Embers color={effectiveAccent(settings)} />}
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

/** Falling petals/snow, used by the Visuals page preview of BLOOD Client's in-game effects. */
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
