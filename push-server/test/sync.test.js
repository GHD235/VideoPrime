import assert from "node:assert/strict";
import { test } from "node:test";
import {
  SYNC_MAX_ITEMS,
  SYNC_MAX_VALUE,
  deviceName,
  newSyncCode,
  normalizeSyncCode,
  parseSyncPush,
  randomToken,
} from "../src/lib.js";

test("a push keeps known kinds and turns values into JSON", () => {
  assert.deepEqual(
    parseSyncPush({ items: [{ kind: "sub", key: "0|u", value: { a: 1 } }, { kind: "hist", key: "k" }] }),
    [
      { kind: "sub", key: "0|u", value: '{"a":1}' },
      { kind: "hist", key: "k", value: null },
    ],
  );
});

test("a push with anything wrong is refused whole", () => {
  assert.equal(parseSyncPush(null), null);
  assert.equal(parseSyncPush({ items: [{ kind: "other", key: "k", value: 1 }] }), null);
  assert.equal(parseSyncPush({ items: [{ kind: "sub", key: "", value: 1 }] }), null);
  assert.equal(parseSyncPush({ items: [{ kind: "sub", key: "k".repeat(513), value: 1 }] }), null);
  assert.equal(parseSyncPush({ items: [{ kind: "sub", key: "k", value: "x".repeat(SYNC_MAX_VALUE) }] }), null);
  const many = Array.from({ length: SYNC_MAX_ITEMS + 1 }, (_, i) => ({ kind: "sub", key: "k" + i, value: 1 }));
  assert.equal(parseSyncPush({ items: many }), null);
});

test("codes are typed in any case, with spaces or dashes", () => {
  const code = newSyncCode();
  assert.match(code, /^[A-Z2-9]{6}$/);
  assert.equal(normalizeSyncCode(code.toLowerCase()), code);
  assert.equal(normalizeSyncCode("abc-d23"), "ABCD23");
  assert.equal(normalizeSyncCode("ABC D2"), null);
  assert.equal(normalizeSyncCode("ABCDE0"), null); // 0 and O are left out on purpose
  assert.equal(normalizeSyncCode(42), null);
});

test("tokens are long and device names are cleaned", () => {
  assert.equal(randomToken().length, 43);
  assert.notEqual(randomToken(), randomToken());
  assert.equal(deviceName("Tele\nsalón"), "Telesalón");
  assert.equal(deviceName(undefined), "");
  assert.equal(deviceName("x".repeat(100)).length, 60);
});
