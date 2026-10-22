import assert from "node:assert/strict";
import test from "node:test";
import { faceRect, guessModel, legacyMirrors, partBox, skinSizeProblem } from "./skin";

test("skin UV layout matches Minecraft's", () => {
  // Head front is the familiar 8×8 at (8, 8); the hat front at (40, 8).
  assert.deepEqual(faceRect(partBox("head", "base", "classic"), "front"), { x: 8, y: 8, w: 8, h: 8 });
  assert.deepEqual(faceRect(partBox("head", "overlay", "classic"), "front"), { x: 40, y: 8, w: 8, h: 8 });
  // Body front (20, 20) 8×12, back (32, 20).
  assert.deepEqual(faceRect(partBox("body", "base", "classic"), "front"), { x: 20, y: 20, w: 8, h: 12 });
  assert.deepEqual(faceRect(partBox("body", "base", "classic"), "back"), { x: 32, y: 20, w: 8, h: 12 });
  // Left leg (64×64 only) front at (20, 52); left arm front at (36, 52).
  assert.deepEqual(faceRect(partBox("leftLeg", "base", "classic"), "front"), { x: 20, y: 52, w: 4, h: 12 });
  assert.deepEqual(faceRect(partBox("leftArm", "base", "classic"), "front"), { x: 36, y: 52, w: 4, h: 12 });
  // Slim arms are 3 wide, so the back face shifts left by 2.
  assert.deepEqual(faceRect(partBox("rightArm", "base", "slim"), "back"), { x: 51, y: 20, w: 3, h: 12 });
  assert.deepEqual(faceRect(partBox("rightArm", "base", "classic"), "back"), { x: 52, y: 20, w: 4, h: 12 });
});

test("skin sizes and legacy conversion", () => {
  assert.equal(skinSizeProblem(64, 64), null);
  assert.equal(skinSizeProblem(64, 32), null);
  assert.match(skinSizeProblem(128, 128) ?? "", /64×64/);
  const m = legacyMirrors();
  assert.equal(m.length, 12);
  // Every target lies in the bottom half that 64×32 skins lack.
  assert.ok(m.every(({ to }) => to.y >= 48 && to.y + to.h <= 64));
  // Mirroring swaps the side faces.
  const leftLegLeft = m.find(({ to }) => to.x === 24 && to.y === 52)!; // left leg, "left" face
  assert.deepEqual(leftLegLeft.from, { x: 0, y: 20, w: 4, h: 12 }); // right leg, "right" face
});

test("model guess", () => {
  assert.equal(guessModel(() => 255), "classic");
  assert.equal(guessModel((x) => (x === 47 ? 0 : 255)), "slim");
});
