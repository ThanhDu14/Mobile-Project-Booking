// T037 (spec 050 US4): chủ sân bấm "Đã hoàn tiền".
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { authedRequest, fakeAuthenticate } from "../_shared/payment-test-auth.ts";
import { handleOwnerMarkRefunded } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const NOW = new Date("2026-11-22T03:00:00Z");

function setup(refund: Doc = {}, booking: Doc | null = { userId: "p1", paymentStatus: "REFUND_PENDING" }) {
  const fs = new FakeFirestore(() => NOW);
  fs.seed("venues/v1", { ownerId: "owner1" });
  fs.seed("refunds/refund_b1", {
    bookingId: "b1",
    recurringGroupId: null,
    venueId: "v1",
    userId: "p1",
    amount: 36_000,
    status: "PENDING",
    ...refund,
  });
  if (booking) fs.seed("bookings/b1", booking);
  return {
    fs,
    deps: { firestore: fs, authenticate: fakeAuthenticate, now: () => NOW, sleep: () => Promise.resolve() },
  };
}

async function call(env: ReturnType<typeof setup>, uid: string | null, body: unknown = { refundId: "refund_b1" }) {
  const res = await handleOwnerMarkRefunded(authedRequest(uid, body), env.deps);
  return { status: res.status, json: await res.json() };
}

Deno.test("không đăng nhập → 401; không phải chủ cơ sở → 403; không có refund → 404", async () => {
  const env = setup();
  assertEquals((await call(env, null)).status, 401);
  assertEquals((await call(env, "owner2")).status, 403);
  assertEquals((await call(env, "owner1", { refundId: "refund_x" })).status, 404);
  assertEquals((await call(env, "owner1", {})).status, 400);
  assertEquals(env.fs.read("refunds/refund_b1")!.status, "PENDING");
});

Deno.test("PENDING → DONE, đơn REFUND_PENDING → REFUNDED", async () => {
  const env = setup();
  const res = await call(env, "owner1");
  assertEquals(res.status, 200);
  assertEquals(res.json, { refundId: "refund_b1", status: "DONE", bookingPaymentStatus: "REFUNDED" });
  const refund = env.fs.read("refunds/refund_b1")!;
  assertEquals(refund.status, "DONE");
  assertEquals((refund.doneAt as Date).getTime(), NOW.getTime());
  assertEquals(env.fs.read("bookings/b1")!.paymentStatus, "REFUNDED");
});

Deno.test("tiền về muộn: đơn không ở REFUND_PENDING thì giữ nguyên, trả null", async () => {
  const env = setup({}, { userId: "p1", paymentStatus: "UNPAID" });
  const res = await call(env, "owner1");
  assertEquals(res.json.bookingPaymentStatus, null);
  assertEquals(env.fs.read("bookings/b1")!.paymentStatus, "UNPAID");
});

Deno.test("gọi lại khi đã DONE trả kết quả cũ, không ghi thêm", async () => {
  const env = setup();
  await call(env, "owner1");
  const commits = env.fs.commits;
  const res = await call(env, "owner1");
  assertEquals(res.status, 200);
  assertEquals(res.json.status, "DONE");
  assert(res.json.bookingPaymentStatus === "REFUNDED");
  assertEquals(env.fs.commits, commits);
});
