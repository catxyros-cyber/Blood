import { test } from "node:test";
import assert from "node:assert/strict";
import { fuzzyFilter, fuzzyScore } from "./fuzzy";

test("fuzzy: characters must appear in order", () => {
  assert.notEqual(fuzzyScore("perf", "Performance"), null);
  assert.notEqual(fuzzyScore("mm", "MOTION Mods"), null);
  assert.equal(fuzzyScore("xyz", "Performance"), null);
  assert.equal(fuzzyScore("ecnam", "Performance"), null);
});

test("fuzzy: substrings and word starts rank first", () => {
  const items = ["Open Settings", "Play Survival 1.21", "Performance", "Skins"];
  assert.deepEqual(fuzzyFilter("set", items, (x) => x), ["Open Settings"]);
  assert.equal(fuzzyFilter("p", items, (x) => x)[0], "Play Survival 1.21");
  assert.equal(fuzzyFilter("surv", items, (x) => x)[0], "Play Survival 1.21");
  assert.deepEqual(fuzzyFilter("", items, (x) => x), items, "an empty query keeps everything in order");
});
