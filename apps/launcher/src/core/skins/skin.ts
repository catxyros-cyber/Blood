/**
 * Minecraft skin layout: where each body part and face lives on the 64×64 texture, for the
 * classic (4-pixel arms) and slim (3-pixel arms) models. Pure data and maths; used by the 3D
 * preview and the editor.
 */

export type SkinModel = "classic" | "slim";
export type PartId = "head" | "body" | "rightArm" | "leftArm" | "rightLeg" | "leftLeg";
export type FaceId = "front" | "back" | "right" | "left" | "top" | "bottom";
export type Layer = "base" | "overlay";

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface PartBox {
  /** Size in texels: width (x), height (y), depth (z). */
  w: number;
  h: number;
  d: number;
  /** Texture origin of the box's UV block. */
  u: number;
  v: number;
}

export const PARTS: { id: PartId; name: string }[] = [
  { id: "head", name: "Head" },
  { id: "body", name: "Body" },
  { id: "rightArm", name: "Right arm" },
  { id: "leftArm", name: "Left arm" },
  { id: "rightLeg", name: "Right leg" },
  { id: "leftLeg", name: "Left leg" },
];

export const FACES: { id: FaceId; name: string }[] = [
  { id: "front", name: "Front" },
  { id: "back", name: "Back" },
  { id: "right", name: "Right" },
  { id: "left", name: "Left" },
  { id: "top", name: "Top" },
  { id: "bottom", name: "Bottom" },
];

/** The UV box of a part (base layer or its overlay: hat, jacket, sleeves, pants). */
export function partBox(part: PartId, layer: Layer, model: SkinModel): PartBox {
  const arm = model === "slim" ? 3 : 4;
  const t: Record<PartId, [PartBox, PartBox]> = {
    head: [{ w: 8, h: 8, d: 8, u: 0, v: 0 }, { w: 8, h: 8, d: 8, u: 32, v: 0 }],
    body: [{ w: 8, h: 12, d: 4, u: 16, v: 16 }, { w: 8, h: 12, d: 4, u: 16, v: 32 }],
    rightArm: [{ w: arm, h: 12, d: 4, u: 40, v: 16 }, { w: arm, h: 12, d: 4, u: 40, v: 32 }],
    leftArm: [{ w: arm, h: 12, d: 4, u: 32, v: 48 }, { w: arm, h: 12, d: 4, u: 48, v: 48 }],
    rightLeg: [{ w: 4, h: 12, d: 4, u: 0, v: 16 }, { w: 4, h: 12, d: 4, u: 0, v: 32 }],
    leftLeg: [{ w: 4, h: 12, d: 4, u: 16, v: 48 }, { w: 4, h: 12, d: 4, u: 0, v: 48 }],
  };
  return t[part][layer === "base" ? 0 : 1];
}

/**
 * Where one face of a box sits on the texture (Minecraft's cube unwrap):
 *   [  .  ][ top ][bottom][  .  ]
 *   [right][front][ left ][back ]
 * "right"/"left" are the character's own right and left.
 */
export function faceRect(b: PartBox, face: FaceId): Rect {
  const { w, h, d, u, v } = b;
  switch (face) {
    case "top":
      return { x: u + d, y: v, w, h: d };
    case "bottom":
      return { x: u + d + w, y: v, w, h: d };
    case "right":
      return { x: u, y: v + d, w: d, h };
    case "front":
      return { x: u + d, y: v + d, w, h };
    case "left":
      return { x: u + d + w, y: v + d, w: d, h };
    case "back":
      return { x: u + d + w + d, y: v + d, w, h };
  }
}

/** Where each part's centre sits relative to the top of the body (texels, y down). */
export function partOffset(part: PartId, model: SkinModel): { x: number; y: number; z: number } {
  const armX = model === "slim" ? 5.5 : 6;
  switch (part) {
    case "head":
      return { x: 0, y: -4, z: 0 };
    case "body":
      return { x: 0, y: 6, z: 0 };
    case "rightArm":
      return { x: -armX, y: 6, z: 0 };
    case "leftArm":
      return { x: armX, y: 6, z: 0 };
    case "rightLeg":
      return { x: -2, y: 18, z: 0 };
    case "leftLeg":
      return { x: 2, y: 18, z: 0 };
  }
}

/** How much an overlay (hat, jacket…) stands out from its part, in texels on each side. */
export function overlayInflate(part: PartId): number {
  return part === "head" ? 0.5 : 0.25;
}

/** A skin PNG must be 64×64, or the old 64×32 format (which the launcher converts). */
export function skinSizeProblem(w: number, h: number): string | null {
  if (w === 64 && (h === 64 || h === 32)) return null;
  return `A Minecraft skin is 64×64 pixels (or the old 64×32). This image is ${w}×${h}.`;
}

/**
 * The copies an old 64×32 skin needs to become 64×64: the left arm and leg reuse the right
 * ones, mirrored. Each entry copies `from` to `to` flipped horizontally (per face).
 */
export function legacyMirrors(): { from: Rect; to: Rect }[] {
  const out: { from: Rect; to: Rect }[] = [];
  const pairs: [PartId, PartId][] = [["rightLeg", "leftLeg"], ["rightArm", "leftArm"]];
  for (const [src, dst] of pairs) {
    const a = partBox(src, "base", "classic");
    const b = partBox(dst, "base", "classic");
    for (const f of FACES) {
      // Mirroring swaps the character's left and right faces.
      const fromFace = f.id === "left" ? "right" : f.id === "right" ? "left" : f.id;
      out.push({ from: faceRect(a, fromFace), to: faceRect(b, f.id) });
    }
  }
  return out;
}

/** Guess the model from the texture: slim skins leave the 4th arm column transparent. */
export function guessModel(alphaAt: (x: number, y: number) => number): SkinModel {
  // Right arm front, x = 40 + 4 + 3 = 47 (the 4th column), rows 20..31.
  for (let y = 20; y < 32; y++) if (alphaAt(47, y) > 0) return "classic";
  return "slim";
}
