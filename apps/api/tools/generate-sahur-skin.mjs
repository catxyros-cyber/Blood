import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { deflateSync } from "node:zlib";

const W = 64, H = 64;
const rgba = Buffer.alloc(W * H * 4);
const at = (x, y) => (y * W + x) * 4;
function pixel(x, y, color) {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const i = at(x, y), [r, g, b, a = 255] = color;
  rgba[i] = r; rgba[i + 1] = g; rgba[i + 2] = b; rgba[i + 3] = a;
}
function rect(x, y, w, h, color) {
  for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) pixel(xx, yy, color);
}
function color(hex, a = 255) {
  const n = Number.parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a];
}
const bark = ["#301812", "#452419", "#5b2c1b", "#713a20", "#8a4c29", "#a56535", "#c1844b"];
function face(x, y, w, h, seed, palette = bark) {
  for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) {
    let n = (xx * 13 + yy * 7 + seed * 11) % 29;
    let idx = n < 3 ? 0 : n < 8 ? 1 : n < 16 ? 2 : n < 22 ? 3 : n < 26 ? 4 : n < 28 ? 5 : 6;
    if (((xx + seed) % 4) === 0) idx = Math.max(0, idx - 1);
    if (yy % 5 === 1 && n > 17) idx = Math.min(6, idx + 1);
    pixel(x + xx, y + yy, color(palette[idx]));
  }
  // A few broken bark grooves and warm grain highlights.
  for (let xx = 1 + seed % 3; xx < w; xx += 4) {
    const end = Math.min(h, 3 + ((xx * 3 + seed) % Math.max(3, h - 1)));
    for (let yy = 0; yy < end; yy++) {
      if ((yy + xx + seed) % 4 !== 0) pixel(x + xx, y + yy, color(yy % 3 === 0 ? "#382017" : "#301812"));
    }
    if (xx + 1 < w && seed % 2 === 0) pixel(x + xx + 1, y + Math.max(0, end - 1), color("#bd7a43"));
  }
}
function box(w, h, d, u, v, seed, palette = bark) {
  face(u + d, v + d, w, h, seed, palette); // front
  face(u + d + w + d, v + d, w, h, seed + 1, palette); // back
  face(u, v + d, d, h, seed + 2, palette); // right
  face(u + d + w, v + d, d, h, seed + 3, palette); // left
  face(u + d, v, w, d, seed + 4, palette); // top
  face(u + d + w, v, w, d, seed + 5, palette); // bottom
}
// Bark-built body, limbs and a hollow knot pattern on the top of the head.
box(8, 8, 8, 0, 0, 1);
box(8, 12, 4, 16, 16, 3);
box(4, 12, 4, 40, 16, 7);
box(4, 12, 4, 32, 48, 9);
box(4, 12, 4, 0, 16, 11);
box(4, 12, 4, 16, 48, 13);
// Pale growth rings on the top plane of the wooden head.
rect(10, 2, 4, 1, color("#c1844b"));
rect(9, 3, 1, 2, color("#402117")); rect(14, 3, 1, 2, color("#402117"));
rect(10, 5, 4, 1, color("#402117")); rect(11, 3, 2, 2, color("#7c4326"));
// Face: deep carved sockets, ember eyes, heavy brows, and a crooked toothy grin.
rect(9, 9, 3, 1, color("#25140f")); rect(13, 9, 3, 1, color("#25140f"));
rect(9, 10, 2, 2, color("#1b1210")); rect(13, 10, 2, 2, color("#1b1210"));
pixel(10, 10, color("#f5bd49")); pixel(14, 10, color("#f5bd49"));
pixel(10, 11, color("#fff0b0")); pixel(14, 11, color("#fff0b0"));
rect(11, 12, 2, 1, color("#3a1e14"));
pixel(10, 13, color("#28140f")); rect(11, 13, 3, 1, color("#21110e")); pixel(14, 13, color("#28140f"));
pixel(11, 14, color("#f1d1a1")); pixel(12, 14, color("#fff0ce")); pixel(13, 14, color("#f1d1a1"));
pixel(12, 15, color("#4b2619"));
// Head overlay: a red woven sahur band with a small brass knot and tiny crown crest.
const red = color("#8c2424"), redHi = color("#c54a36"), gold = color("#edbd63"), dark = color("#482018");
rect(40, 8, 8, 1, dark); rect(40, 9, 8, 1, red);
for (let x = 40; x < 48; x += 2) pixel(x, 9, redHi);
rect(43, 8, 2, 2, gold); pixel(44, 8, color("#fff0a0"));
rect(32, 8, 4, 1, dark); rect(32, 9, 4, 1, red);
rect(48, 8, 4, 1, dark); rect(48, 9, 4, 1, red);
rect(40, 0, 8, 8, color("#68321f"));
rect(42, 1, 4, 1, gold); rect(41, 2, 1, 3, red); rect(46, 2, 1, 3, red);
rect(42, 5, 4, 1, dark); pixel(44, 3, color("#ffdd88"));
// Outer body layer: weathered crimson shawl, diagonal sash and brass fasteners.
rect(20, 36, 8, 2, color("#55201e")); rect(20, 36, 8, 1, color("#a6382d"));
for (let x = 20; x < 28; x++) if ((x % 2) === 0) pixel(x, 37, color("#d56d47"));
for (let i = 0; i < 6; i++) { pixel(21 + i, 39 + i, color(i % 2 ? "#a6382d" : "#d1a252")); pixel(22 + i, 39 + i, color("#55201e")); }
rect(20, 47, 8, 1, color("#271611")); rect(22, 47, 4, 1, gold); pixel(24, 47, color("#fff0a0"));
// Shawl edging and shoulder tabs on both sleeves.
rect(44, 20, 4, 2, color("#8c2424")); rect(44, 20, 4, 1, gold);
rect(36, 52, 4, 2, color("#8c2424")); rect(36, 52, 4, 1, gold);
// Right arm becomes a carved walking-drum baton: dark grip, lacquered shaft, bright head.
rect(44, 22, 4, 6, color("#9b5d31"));
rect(44, 22, 1, 6, color("#d3924e")); rect(47, 22, 1, 6, color("#61351f"));
rect(45, 25, 1, 3, color("#e4b86d"));
rect(44, 28, 4, 2, color("#321a14")); rect(45, 28, 1, 2, color("#be8d58"));
rect(44, 30, 4, 2, color("#51301e")); rect(45, 30, 2, 1, color("#c1834b"));
// Cuff on the opposite arm, wrapping thread, and wrist highlights.
rect(36, 60, 4, 1, color("#301812")); rect(36, 59, 4, 1, gold);
// Wrapped wooden legs with dark sandal-like feet and split gold seams.
rect(4, 29, 4, 2, color("#332018")); rect(4, 29, 4, 1, color("#191312")); pixel(5, 30, gold); pixel(7, 30, gold);
rect(20, 61, 4, 2, color("#332018")); rect(20, 61, 4, 1, color("#191312")); pixel(21, 62, gold); pixel(23, 62, gold);
rect(1, 25, 2, 1, color("#ad7041")); rect(17, 57, 2, 1, color("#ad7041"));
// Keep the whole 64x64 atlas opaque only where a face exists (overlay slots remain transparent).
const crcTable = new Uint32Array(256);
for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcTable[n] = c >>> 0; }
function crc32(buf) { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(name, data) { const type = Buffer.from(name); const out = Buffer.alloc(12 + data.length); out.writeUInt32BE(data.length, 0); type.copy(out, 4); data.copy(out, 8); out.writeUInt32BE(crc32(Buffer.concat([type, data])), 8 + data.length); return out; }
const raw = Buffer.alloc((W * 4 + 1) * H);
for (let y = 0; y < H; y++) rgba.copy(raw, y * (W * 4 + 1) + 1, y * W * 4, (y + 1) * W * 4);
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
const png = Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk("IHDR", ihdr), chunk("IDAT", deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
const out = resolve(import.meta.dirname, "../content/skins/tung-tung-tung-sahur.png");
mkdirSync(resolve(import.meta.dirname, "../content/skins"), { recursive: true });
writeFileSync(out, png);
console.log(`${out} (${png.length} bytes)`);
