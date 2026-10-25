// Temporary (launcher redesign), with mock.ts and index.html: renders pages for CI screenshots.
// node shoot.mjs <outdir> name[@query][~WxH][+tall]={"page":...} ...
import { chromium } from "playwright";
const [out, ...pages] = process.argv.slice(2);
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await ctx.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(String(e)));
page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
await page.goto("http://localhost:1420/preview/index.html");
await page.waitForTimeout(8000);
for (const spec of pages) {
  const at = spec.indexOf("={");
  let name = spec.slice(0, at);
  const json = spec.slice(at + 1);
  const tall = name.endsWith("+tall");
  if (tall) name = name.slice(0, -5);
  let size = { width: 1280, height: 800 };
  const sm = name.match(/~(\d+)x(\d+)$/);
  if (sm) {
    size = { width: Number(sm[1]), height: Number(sm[2]) };
    name = name.slice(0, sm.index);
  }
  const [file, extra = ""] = name.split("@");
  await page.setViewportSize(size);
  await page.goto(`http://localhost:1420/preview/index.html?page=${encodeURIComponent(json)}${extra ? `&${extra}` : ""}`);
  await page.waitForTimeout(2500);
  if (tall) {
    // Grow the window to the main column's content so one image shows the whole page.
    const h = await page.locator("#bloom-main").evaluate((m) => m.scrollHeight + 36);
    await page.setViewportSize({ width: size.width, height: Math.min(Math.max(h, 800), 4000) });
    await page.waitForTimeout(600);
  }
  await page.screenshot({ path: `${out}/${file}${sm ? `-${size.width}` : ""}${tall ? "-tall" : ""}.png` });
}
console.log(errors.filter((e) => !/preview:|503|Failed to load resource/.test(e)).slice(0, 15).join("\n"));
await browser.close();
