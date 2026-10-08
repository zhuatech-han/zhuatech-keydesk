// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { filterRows, utc, stateName, language, errorMessage } from "./ui.js";
test("filter combines state and search without changing records", () => {
  const rows = [
    { id: 1, state: "ISSUED", keyName: "TEST Office" },
    { id: 2, state: "RETURNED", keyName: "TEST Office" },
  ];
  assert.deepEqual(filterRows(rows, "office", "ISSUED"), [rows[0]]);
  assert.equal(rows.length, 2);
});
test("invalid local date cannot be submitted", () => {
  assert.throws(() => utc("invalid"), /INVALID_INPUT/);
  assert.equal(utc("2026-10-08T08:00:00Z"), "2026-10-08T08:00:00.000Z");
});
test("recovery is not displayed as a completed return", () => {
  language.value = "en";
  assert.equal(stateName("RETURN_REVIEW"), "Recovery in review");
  assert.match(errorMessage(new Error("RESULT_UNKNOWN")), /unknown/);
  language.value = "zh";
});
