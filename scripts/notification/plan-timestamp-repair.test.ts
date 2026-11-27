import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { planTimestampRepair, type Snapshot } from "./plan-timestamp-repair.ts";
const name = "projects/demo-notification/databases/(default)/documents/users/a/notifications/one";
const timestamp = "2026-11-22T03:15:00.123Z";
const snapshot: Snapshot = { name, updateTime: timestamp, fields: { createdAt: { stringValue: timestamp } } };

Deno.test("repair plan changes only createdAt, preserves the instant and rejects concurrent overwrites", () => {
  const plan = planTimestampRepair([snapshot]);
  assertEquals(plan.writes, [{ update: { name, fields: { createdAt: { timestampValue: timestamp } } }, updateMask: { fieldPaths: ["createdAt"] }, currentDocument: { updateTime: timestamp } }]);
  assertEquals(plan.mode, "REVIEW_ONLY_NO_COMMIT");
});

Deno.test("repair plan rejects mixed projects and malformed runtime snapshots before producing writes", () => {
  assertThrows(() => planTimestampRepair([snapshot, { ...snapshot, name: name.replace('demo-notification', 'other-project') }]));
  for (const invalid of [null, 3, {}, { name: 42 }, { ...snapshot, fields: [] }]) {
    assertThrows(() => planTimestampRepair([snapshot, invalid] as unknown as Snapshot[]));
  }
});

Deno.test("repair plan rejects conflicting duplicate snapshots instead of choosing an arbitrary copy", () => {
  assertThrows(() => planTimestampRepair([snapshot, { ...snapshot, updateTime: "2026-11-23T01:00:00.000Z" }]));
});

Deno.test("repair plan pins the expected project and rejects an ambiguous REST Value", () => {
  assertThrows(() => planTimestampRepair([snapshot], "wrong-project"));
  assertEquals(planTimestampRepair([snapshot], "demo-notification").projectId, "demo-notification");
  assertThrows(() => planTimestampRepair([{ ...snapshot, fields: { createdAt: { stringValue: timestamp, timestampValue: timestamp } } }]));
});

Deno.test("repair plan requires a valid Firestore updateTime but preserves nanosecond precision", () => {
  const bad = planTimestampRepair([{ ...snapshot, updateTime: "not-a-timestamp" }]);
  assertEquals(bad.writes.length, 0);
  assertEquals(bad.skipped[0].reason, "invalid-concurrency-precondition");
  const precise = "2026-11-23T01:00:00.123456789Z";
  const plan = planTimestampRepair([{ ...snapshot, updateTime: precise }]);
  assertEquals((plan.writes[0] as { currentDocument: { updateTime: string } }).currentDocument.updateTime, precise);
});
Deno.test("repair plan leaves native dates, invalid or ambiguous dates, foreign paths and duplicate input untouched", () => {
  const inputs: Snapshot[] = [snapshot, snapshot,
    { ...snapshot, name: name + "2", fields: { createdAt: { timestampValue: timestamp } } },
    { ...snapshot, name: name + "3", fields: { createdAt: { stringValue: "2026-02-30T00:00:00.000Z" } } },
    { ...snapshot, name: name + "4", fields: { createdAt: { stringValue: "2026-11-22T03:15:00" } } },
    { ...snapshot, name: name.replace("notifications", "favorites") },
    { ...snapshot, name: name + "5", updateTime: undefined },
  ];
  const plan = planTimestampRepair(inputs);
  assertEquals(plan.writes.length, 1);
  assertEquals(plan.skipped.map(s => s.reason), ["duplicate-input", "already-timestamp", "manual-review-invalid-date", "manual-review-invalid-date", "outside-inbox", "missing-concurrency-precondition"]);
});
