// Temporary (launcher redesign), with mock.ts and index.html: renders pages for CI screenshots.
// node preview/shoot.mjs <outdir> [name=pageJSON ...]
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
for (const spec of pages.length ? pages : ['home={"page":"home"}']) {
  const at = spec.indexOf("={");
  const name = spec.slice(0, at);
  const json = spec.slice(at + 1);
  const extra = name.includes("@") ? name.split("@")[1] : "";
  await page.goto(`http://localhost:1420/preview/index.html?page=${encodeURIComponent(json)}${extra ? `&${extra}` : ""}`);
  await page.waitForTimeout(2500);
  await page.screenshot({ path: `${out}/${name.split("@")[0]}.png` });
  if (name.includes("full")) await page.locator("#bloom-main").evaluate((m) => m.scrollTo(0, 900));
  if (name.includes("full")) await page.screenshot({ path: `${out}/${name.split("@")[0]}-2.png` });
}
console.log(errors.filter((e) => !/preview:|503|Failed to load resource/.test(e)).slice(0, 15).join("\n"));
await browser.close();
