// T038 (spec 050 US4): người chơi báo chưa nhận được tiền hoàn.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { authedRequest, fakeAuthenticate } from "../_shared/payment-test-auth.ts";
import { handleRefundReport } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const NOW = new Date("2026-11-25T03:00:00Z");
const DAY = 24 * 60 * 60_000;

function setup(refund: Doc = {}) {
  const fs = new FakeFirestore(() => NOW);
  fs.seed("refunds/refund_b1", {
    bookingId: "b1",
    recurringGroupId: null,
    venueId: "v1",
    userId: "p1",
    amount: 36_000,
    status: "PENDING",
    createdAt: new Date(NOW.getTime() - 4 * DAY),
    reportedAt: null,
    ...refund,
  });
  return {
    fs,
    deps: {
      firestore: fs,
      authenticate: fakeAuthenticate,
      now: () => NOW,
      sleep: () => Promise.resolve(),
      newId: () => "r123",
    },
  };
}

async function call(
  env: ReturnType<typeof setup>,
  uid: string | null,
  body: unknown = { refundId: "refund_b1", note: "Đã 4 ngày chưa nhận" },
) {
  const res = await handleRefundReport(authedRequest(uid, body), env.deps);
  return { status: res.status, json: await res.json() };
}

Deno.test("không phải người được hoàn → 403", async () => {
  const env = setup();
  assertEquals((await call(env, null)).status, 401);
  assertEquals((await call(env, "p2")).status, 403);
  assertEquals(env.fs.children("reports").length, 0);
});

Deno.test("PENDING chưa đủ 3 ngày → 409 INVALID_STATE", async () => {
  const env = setup({ createdAt: new Date(NOW.getTime() - 2 * DAY) });
  const res = await call(env, "p1");
  assertEquals(res.status, 409);
  assertEquals(res.json.error.code, "INVALID_STATE");
});

Deno.test("đủ điều kiện: tạo report VENUE và đặt reportedAt; lần hai → 409", async () => {
  const env = setup();
  const res = await call(env, "p1");
  assertEquals(res.status, 201);
  assertEquals(res.json, { reportId: "r123" });
  const report = env.fs.read("reports/r123")!;
  assertEquals(report.reporterId, "p1");
  assertEquals(report.targetType, "VENUE");
  assertEquals(report.targetPath, "venues/v1");
  assertEquals(report.reason, "REFUND_NOT_RECEIVED");
  assertEquals(report.detail, "Đã 4 ngày chưa nhận");
  assertEquals(report.refundId, "refund_b1");
  assertEquals(report.bookingId, "b1");
  assertEquals(report.amount, 36_000);
  assertEquals(report.status, "OPEN");
  assertEquals((env.fs.read("refunds/refund_b1")!.reportedAt as Date).getTime(), NOW.getTime());
  assertEquals((await call(env, "p1")).status, 409);
});

Deno.test("refund DONE vẫn báo được (người chơi chưa nhận)", async () => {
  const env = setup({ status: "DONE", createdAt: new Date(NOW.getTime() - 1 * DAY) });
  assertEquals((await call(env, "p1")).status, 201);
});
