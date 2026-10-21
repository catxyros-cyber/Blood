import { test } from "node:test";
import assert from "node:assert/strict";
import { CheckoutRequest, FEATURES, SyncPutRequest, VerifyRequest, formatPrice, normalizeUuid, v } from "./index";

test("validators accept good input and explain bad input", () => {
  assert.deepEqual(VerifyRequest.parse({ challengeId: "abcdefghijklmnop1234", username: "Steve_1" }), {
    ok: true,
    value: { challengeId: "abcdefghijklmnop1234", username: "Steve_1" },
  });
  const bad = VerifyRequest.parse({ challengeId: "short", username: "Steve" });
  assert.equal(bad.ok, false);
  assert.match(!bad.ok ? bad.error : "", /challengeId must be at least 16/);
  assert.equal(VerifyRequest.parse({ challengeId: "abcdefghijklmnop1234", username: "bad name!" }).ok, false);
  assert.equal(CheckoutRequest.parse({ plan: "yearly" }).ok, true);
  assert.equal(CheckoutRequest.parse({ plan: "forever" }).ok, false);
  assert.equal(SyncPutRequest.parse({ baseVersion: 1.5, data: {} }).ok, false);
  assert.equal(SyncPutRequest.parse({ baseVersion: 0, data: [] }).ok, false);
  assert.equal(v.array(v.number(), { max: 2 }).parse([1, 2, 3]).ok, false);
  assert.deepEqual(v.object({ a: v.optional(v.string()) }).parse({ a: null }), { ok: true, value: {} });
});

test("plans and helpers", () => {
  assert.ok(FEATURES.filter((f) => f.free).every((f) => f.premium), "Premium includes everything in Free");
  assert.ok(FEATURES.some((f) => f.key === "launcher" && f.free), "launching is free");
  assert.equal(formatPrice(499, "usd", "en-US"), "$4.99");
  assert.equal(normalizeUuid("069A79F4-44E9-4726-A5BE-FCA90E38AAF5"), "069a79f444e94726a5befca90e38aaf5");
});
