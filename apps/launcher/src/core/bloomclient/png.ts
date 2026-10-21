/** PNG header checks for imported skyboxes (no image decoding needed). */

export interface PngSize {
  width: number;
  height: number;
}

const SIG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

export function pngSize(bytes: Uint8Array): PngSize | null {
  if (bytes.length < 33) return null;
  for (let i = 0; i < 8; i++) if (bytes[i] !== SIG[i]) return null;
  if (String.fromCharCode(bytes[12]!, bytes[13]!, bytes[14]!, bytes[15]!) !== "IHDR") return null;
  const rd = (o: number) => ((bytes[o]! << 24) | (bytes[o + 1]! << 16) | (bytes[o + 2]! << 8) | bytes[o + 3]!) >>> 0;
  const width = rd(16), height = rd(20);
  if (!width || !height || width > 8192 || height > 8192) return null;
  return { width, height };
}

export function dataUrlBytes(dataUrl: string, max = 64): Uint8Array {
  const b64 = dataUrl.slice(dataUrl.indexOf(",") + 1, dataUrl.indexOf(",") + 1 + Math.ceil((max * 4) / 3) + 4);
  const bin = atob(b64.slice(0, b64.length - (b64.length % 4)));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Same rules as the mod (CubeLayout.checkAtlasSize / Panorama.looksLikePanorama). */
export function skyboxImageProblem(size: PngSize): string | null {
  const { width: w, height: h } = size;
  if (w * 2 === h * 3 && w % 3 === 0) {
    const face = w / 3;
    if (face < 16) return "The image is too small (each face must be at least 16 pixels).";
    if (face > 2048) return "The image is too large (each face can be at most 2048 pixels).";
    return null;
  }
  if (w === h * 2 && h >= 64) return null; // 360° panorama: the game converts it
  return `A skybox must be a 3×2 cube image (e.g. 1536×1024) or a 2:1 panorama. This one is ${w}×${h}.`;
}
