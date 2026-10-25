// Renders MOTION's theme backgrounds from scene.html (a WebGL voxel ray tracer) in headless
// Chromium and writes the app's image assets. Run from a folder where `playwright` resolves:
//
//   node render.mjs [--themes motion,crimson] [--width 2560] [--samples 2] [--out ../../public/backgrounds]
//   node render.mjs --preview          # quick 640px, 1 sample, every theme, into ./preview
//
// For each theme it writes:
//   <theme>.webp        the full scene (Home, splash)
//   <theme>-soft.webp   a small pre-blurred copy for pages other than Home (no CSS blur needed)
//   <theme>-thumb.webp  the Settings theme picker thumbnail
import { createRequire } from "node:module";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");
const here = dirname(fileURLToPath(import.meta.url));

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => {
    if (a.startsWith("--")) acc.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith("--") ? all[i + 1] : true]);
    return acc;
  }, []),
);
const preview = Boolean(args.preview);
const width = Number(args.width ?? (preview ? 640 : 2560));
const height = Math.round((width * 9) / 16);
const samples = Number(args.samples ?? (preview ? 1 : 2));
const out = resolve(args.out ?? (preview ? join(here, "preview") : join(here, "../../public/backgrounds")));

const deg = (d) => (d * Math.PI) / 180;
const dirFrom = (azimuth, elevation) => [Math.sin(deg(azimuth)) * Math.cos(deg(elevation)), Math.sin(deg(elevation)), Math.cos(deg(azimuth)) * Math.cos(deg(elevation))];
const scale = (v, k) => v.map((x) => x * k);

// One world, one camera; each theme is a time of day and a grade.
const WORLD = { ox: -768, oz: -128, w: 1536, h: 1536, seed: 7, camZ: 0, lakeStart: 34, lakeEnd: 170, mountZ: 300 };
const CAMERA = { uCamPos: [0, 81, -20], uYaw: deg(4), uPitch: deg(-3), uFov: deg(62) };

const THEMES = {
  // Emerald night: moonlight raking across the blocks, a faint aurora over the mountains.
  motion: {
    uSunDir: dirFrom(21, 12), uSunCol: [0.24, 0.336, 0.304], uAmbTop: [0.024, 0.042, 0.0408], uAmbBottom: [0.006, 0.0108, 0.0096],
    uZenith: [0.002, 0.007, 0.008], uHorizon: [0.02, 0.06, 0.05], uGroundSky: [0.006, 0.014, 0.012],
    uFogCol: [0.018, 0.048, 0.04], uFogDensity: 0.0019, uDiscSize: 0.028, uDiscCol: [0.75, 0.9, 0.825], uDiscMoon: 1, uHalo: 0.35,
    uStars: 0.9, uAurora: 0, uAuroraCol: [0.12, 0.8, 0.45], uCloudCover: 0.2, uCloudCol: [0.035, 0.065, 0.058],
    uExposure: 1.25, uSaturation: 1.0, uGrade: [0.94, 1.03, 1.0],
  },
  // Red sunset.
  crimson: {
    uSunDir: dirFrom(21, 7), uSunCol: [1.5, 0.5, 0.34], uAmbTop: [0.09, 0.035, 0.04], uAmbBottom: [0.025, 0.01, 0.012],
    uZenith: [0.018, 0.004, 0.01], uHorizon: [0.26, 0.05, 0.045], uGroundSky: [0.03, 0.008, 0.01],
    uFogCol: [0.14, 0.03, 0.03], uFogDensity: 0.0018, uDiscSize: 0.045, uDiscCol: [2.8, 1.4, 0.9], uDiscMoon: 0, uHalo: 1.0,
    uStars: 0.15, uAurora: 0, uAuroraCol: [0, 0, 0], uCloudCover: 0.3, uCloudCol: [0.3, 0.07, 0.065],
    uExposure: 1.0, uSaturation: 1.0, uGrade: [1.0, 0.95, 0.95],
  },
  // Near-black night under a blood moon.
  obsidian: {
    uSunDir: dirFrom(21, 12), uSunCol: [0.272, 0.072, 0.068], uAmbTop: [0.0132, 0.0084, 0.0102], uAmbBottom: [0.0036, 0.0018, 0.0024],
    uZenith: [0.0015, 0.001, 0.0015], uHorizon: [0.032, 0.008, 0.01], uGroundSky: [0.004, 0.002, 0.003],
    uFogCol: [0.024, 0.007, 0.008], uFogDensity: 0.0019, uDiscSize: 0.028, uDiscCol: [0.85, 0.18, 0.15], uDiscMoon: 1, uHalo: 0.35,
    uStars: 0.55, uAurora: 0, uAuroraCol: [0, 0, 0], uCloudCover: 0.14, uCloudCol: [0.026, 0.012, 0.013],
    uExposure: 1.25, uSaturation: 1.0, uGrade: [1.0, 0.95, 0.95],
  },
  // Overcast dusk: soft grey light, low cloud.
  ash: {
    uSunDir: dirFrom(21, 22), uSunCol: [0.16, 0.155, 0.155], uAmbTop: [0.11, 0.11, 0.115], uAmbBottom: [0.03, 0.03, 0.03],
    uZenith: [0.05, 0.05, 0.055], uHorizon: [0.13, 0.125, 0.125], uGroundSky: [0.03, 0.03, 0.03],
    uFogCol: [0.11, 0.105, 0.105], uFogDensity: 0.003, uDiscSize: 0.0, uDiscCol: [0, 0, 0], uDiscMoon: 0, uHalo: 0.0,
    uStars: 0, uAurora: 0, uAuroraCol: [0, 0, 0], uCloudCover: 0.6, uCloudCol: [0.14, 0.135, 0.135],
    uExposure: 1.0, uSaturation: 0.5, uGrade: [1.0, 0.97, 0.97],
  },
  // Golden-orange dusk.
  ember: {
    uSunDir: dirFrom(21, 8), uSunCol: [1.9, 0.95, 0.42], uAmbTop: [0.1, 0.06, 0.04], uAmbBottom: [0.03, 0.018, 0.01],
    uZenith: [0.025, 0.014, 0.014], uHorizon: [0.32, 0.13, 0.05], uGroundSky: [0.03, 0.017, 0.01],
    uFogCol: [0.17, 0.08, 0.035], uFogDensity: 0.0018, uDiscSize: 0.045, uDiscCol: [3.0, 2.0, 1.1], uDiscMoon: 0, uHalo: 1.0,
    uStars: 0, uAurora: 0, uAuroraCol: [0, 0, 0], uCloudCover: 0.34, uCloudCol: [0.42, 0.2, 0.1],
    uExposure: 1.0, uSaturation: 1.0, uGrade: [1.0, 0.97, 0.94],
  },
  // Moonlit, black and white.
  mono: {
    uSunDir: dirFrom(21, 12), uSunCol: [0.32, 0.32, 0.336], uAmbTop: [0.027, 0.027, 0.03], uAmbBottom: [0.0072, 0.0072, 0.0078],
    uZenith: [0.003, 0.003, 0.004], uHorizon: [0.045, 0.045, 0.05], uGroundSky: [0.009, 0.009, 0.01],
    uFogCol: [0.035, 0.035, 0.04], uFogDensity: 0.0019, uDiscSize: 0.028, uDiscCol: [0.95, 0.95, 0.95], uDiscMoon: 1, uHalo: 0.35,
    uStars: 0.8, uAurora: 0, uAuroraCol: [0, 0, 0], uCloudCover: 0.2, uCloudCol: [0.06, 0.06, 0.065],
    uExposure: 1.25, uSaturation: 0.0, uGrade: [1, 1, 1],
  },
};

const themes = args.themes ? String(args.themes).split(",") : Object.keys(THEMES);
mkdirSync(out, { recursive: true });

const browser = await chromium.launch({ args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"] });
const page = await browser.newPage();
page.on("console", (m) => m.type() === "error" && console.error("page:", m.text()));
page.on("pageerror", (e) => console.error("page error:", e.message));
await page.goto(pathToFileURL(join(here, "scene.html")).href);
const TILE = 160;
await page.evaluate((cfg) => window.setup(cfg), { tile: TILE, world: WORLD });

async function renderImage(theme) {
  const uniforms = { ...CAMERA, ...THEMES[theme], uRes: [width, height], uSamples: samples, uMode: 0, uSplit: 0, uWater: 62.88, uGlowWide: THEMES[theme].uDiscMoon ? 0.15 : 1 };
  await page.evaluate((dir) => window.setLight(dir), THEMES[theme].uSunDir);
  // The image is assembled in a 2D canvas in the page, then encoded there.
  await page.evaluate(({ w, h }) => {
    const c = document.createElement("canvas");
    c.width = w; c.height = h;
    window.__out = c;
  }, { w: width, h: height });
  const started = Date.now();
  for (let y = 0; y < height; y += TILE) {
    for (let x = 0; x < width; x += TILE) {
      const tw = Math.min(TILE, width - x), th = Math.min(TILE, height - y);
      await page.evaluate(({ x, y, tw, th, uniforms, height }) => {
        const b64 = window.renderTile({ x, y: height - y - th, w: tw, h: th, uniforms });
        const raw = Uint8ClampedArray.from(atob(b64), (c) => c.charCodeAt(0));
        // readPixels is bottom-up.
        const img = new ImageData(tw, th);
        for (let r = 0; r < th; r++) img.data.set(raw.subarray((th - 1 - r) * tw * 4, (th - r) * tw * 4), r * tw * 4);
        window.__out.getContext("2d").putImageData(img, x, y);
      }, { x, y, tw, th, uniforms, height });
    }
    process.stdout.write(`\r  ${theme} ${Math.min(100, Math.round(((y + TILE) / height) * 100))}%  `);
  }
  process.stdout.write(`(${((Date.now() - started) / 1000).toFixed(0)} s)\n`);
}

async function save(name, opts = {}) {
  const b64 = await page.evaluate(async ({ w, blur, quality, type }) => {
    const src = window.__out;
    let c = src;
    if (w && w !== src.width) {
      c = document.createElement("canvas");
      c.width = w; c.height = Math.round((w * src.height) / src.width);
      const g = c.getContext("2d");
      g.imageSmoothingQuality = "high";
      if (blur) g.filter = `blur(${blur}px)`;
      // Draw a little oversized so the blur doesn't pull in transparent edges.
      const pad = blur ? blur * 3 : 0;
      g.drawImage(src, -pad, -pad, c.width + pad * 2, c.height + pad * 2);
    }
    const blob = await new Promise((r) => c.toBlob(r, type, quality));
    const buf = new Uint8Array(await blob.arrayBuffer());
    let s = "";
    for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode.apply(null, buf.subarray(i, i + 0x8000));
    return btoa(s);
  }, { w: opts.width, blur: opts.blur, quality: opts.quality ?? 0.86, type: opts.type ?? "image/webp" });
  const file = join(out, name);
  writeFileSync(file, Buffer.from(b64, "base64"));
  console.log(`  wrote ${name} (${Math.round(Buffer.from(b64, "base64").length / 1024)} KB)`);
}

for (const theme of themes) {
  if (!THEMES[theme]) throw new Error(`Unknown theme ${theme}`);
  await renderImage(theme);
  await save(`${theme}.webp`);
  await save(`${theme}-soft.webp`, { width: 960, blur: 6, quality: 0.8 });
  await save(`${theme}-thumb.webp`, { width: 480, quality: 0.82 });
}
await browser.close();
