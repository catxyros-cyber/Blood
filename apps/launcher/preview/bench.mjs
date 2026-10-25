// Temporary (launcher redesign): measures the launcher in Chromium against the mock backend.
// node bench.mjs <baseUrl> <out.json> <label>
import { writeFileSync } from "node:fs";
import { chromium } from "playwright";

const [base, outFile, label] = process.argv.slice(2);
const RUNS = 5;
const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; };
const browser = await chromium.launch();

async function fresh(query = "") {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await ctx.newPage();
  const bytes = { backgrounds: 0, files: [] };
  page.on("response", async (r) => {
    if (!r.url().includes("/backgrounds/")) return;
    const b = await r.body().catch(() => null);
    if (b) { bytes.backgrounds += b.length; bytes.files.push(`${r.url().split("/backgrounds/")[1]} ${b.length}`); }
  });
  await page.goto(`${base}/preview/index.html?${query}`);
  return { ctx, page, bytes };
}

const result = { label, boot: [], bootIpc: null, homeIpc: [], bgBytes: null, bgFiles: null, decode: [], scroll: [], idle: [], heap: [], domNodes: [] };

// Warm the dev server: visit every page so it has compiled (and pre-bundled) everything the app
// loads lazily; it reloads the page when it finds new dependencies, which mustn't land mid-run.
{
  const { ctx, page } = await fresh("many&lat=2");
  await page.waitForSelector("#bloom-main", { timeout: 60000 });
  for (const p of ["library", "settings", "mods", "performance", "customize", "skins", "browse", "accounts", "premium", "home"]) {
    await page.evaluate((p) => import("/src/services/app.ts").then(({ navigate }) => navigate({ page: p })), p).catch(() => undefined);
    await page.waitForTimeout(1200);
  }
  await page.waitForTimeout(6000);
  await ctx.close();
}

for (let i = 0, attempts = 0; i < RUNS; i++) {
 try {
  // Startup: from navigation to the app shell, with a 2 ms delay per backend call.
  const { ctx, page, bytes } = await fresh("many&lat=2");
  await page.waitForSelector("#bloom-main", { timeout: 60000 });
  result.boot.push(await page.evaluate(() => performance.now()));
  await page.waitForTimeout(2500);
  if (i === 0) {
    result.bootIpc = await page.evaluate(() => ({ ...window.__ipc }));
    result.bgBytes = bytes.backgrounds;
    result.bgFiles = bytes.files;
  }
  // A visit to Home from another page: backend calls it makes.
  await page.evaluate(() => import("/src/services/app.ts").then(({ navigate }) => navigate({ page: "library" })));
  await page.waitForTimeout(800);
  await page.evaluate(() => { for (const k of Object.keys(window.__ipc)) delete window.__ipc[k]; });
  await page.evaluate(() => import("/src/services/app.ts").then(({ navigate }) => navigate({ page: "home" })));
  await page.waitForTimeout(1500);
  const ipc = await page.evaluate(() => ({ ...window.__ipc }));
  result.homeIpc.push(Object.values(ipc).reduce((a, b) => a + b, 0));
  if (i === 0) result.homeIpcByCommand = ipc;
  // Idle on Home: main-thread work over 5 s.
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("Performance.enable");
  const m0 = Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map((m) => [m.name, m.value]));
  await page.waitForTimeout(5000);
  const m1 = Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map((m) => [m.name, m.value]));
  result.idle.push(Math.round((m1.TaskDuration - m0.TaskDuration) * 1000));
  result.heap.push(Math.round(m1.JSHeapUsedSize / 1024 / 1024 * 10) / 10);
  result.domNodes.push(m1.Nodes);
  // Scrolling a long page over the background: frame intervals from requestAnimationFrame.
  await page.evaluate(() => import("/src/services/app.ts").then(({ navigate }) => navigate({ page: "settings" })));
  await page.waitForTimeout(1500);
  const frames = await page.evaluate(async () => {
    const main = document.getElementById("bloom-main");
    const times = [];
    let last = performance.now();
    const end = last + 3000;
    await new Promise((done) => {
      const step = (t) => {
        times.push(t - last); last = t;
        main.scrollTop = (Math.sin(t / 300) * 0.5 + 0.5) * (main.scrollHeight - main.clientHeight);
        if (t < end) requestAnimationFrame(step); else done();
      };
      requestAnimationFrame(step);
    });
    times.shift();
    const s = [...times].sort((a, b) => a - b);
    return { avg: times.reduce((a, b) => a + b, 0) / times.length, p95: s[Math.floor(s.length * 0.95)], frames: times.length };
  });
  result.scroll.push(frames);
  await ctx.close();
 } catch (e) {
  // A dev-server reload during a run: discard the run and do it again.
  if (++attempts > 6) throw e;
  console.error(`run ${i} retried: ${e.message.split("\n")[0]}`);
  for (const k of ["boot", "homeIpc", "idle", "heap", "domNodes", "scroll"]) result[k] = result[k].slice(0, i);
  i--;
 }
}

// Decoding the background image once (fetch + decode), as the app does on Home.
{
  const { ctx, page } = await fresh();
  await page.waitForSelector("#bloom-main", { timeout: 60000 });
  for (let i = 0; i < RUNS; i++) {
    result.decode.push(await page.evaluate(async (i) => {
      const exts = ["webp", "jpg"];
      for (const ext of exts) {
        const url = `/backgrounds/motion.${ext}?r=${i}`;
        const head = await fetch(url, { method: "HEAD" }).catch(() => null);
        if (!head || !head.ok || !(head.headers.get("content-type") ?? "").startsWith("image")) continue;
        const img = new Image();
        const t = performance.now();
        img.src = url;
        await img.decode();
        return { ms: Math.round(performance.now() - t), size: `${img.naturalWidth}x${img.naturalHeight}`, decodedMB: Math.round((img.naturalWidth * img.naturalHeight * 4) / 1048576) };
      }
      return null;
    }, i));
  }
  await ctx.close();
}

const summary = {
  label,
  bootMs: Math.round(median(result.boot)),
  bootIpcCalls: Object.values(result.bootIpc ?? {}).reduce((a, b) => a + b, 0),
  homeVisitIpcCalls: median(result.homeIpc),
  homeVisitByCommand: result.homeIpcByCommand,
  backgroundBytesKB: Math.round((result.bgBytes ?? 0) / 1024),
  backgroundFiles: result.bgFiles,
  decodeMs: median(result.decode.map((d) => d?.ms ?? NaN)),
  decodedImage: result.decode[0],
  scrollAvgFrameMs: Math.round(median(result.scroll.map((s) => s.avg)) * 10) / 10,
  scrollP95FrameMs: Math.round(median(result.scroll.map((s) => s.p95)) * 10) / 10,
  idleMainThreadMsPer5s: median(result.idle),
  heapMB: median(result.heap),
  domNodes: median(result.domNodes),
  raw: result,
};
writeFileSync(outFile, JSON.stringify(summary, null, 2));
console.log(JSON.stringify({ ...summary, raw: undefined }, null, 2));
await browser.close();
