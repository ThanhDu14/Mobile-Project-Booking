// Thanh toán giả lập: chốt đơn chờ chuyển khoản bằng lõi của webhook SePay, không cần SePay.
//   cd supabase/functions && deno test --allow-env --allow-read booking-payment-simulate/handler.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { authedRequest, fakeAuthenticate } from "../_shared/payment-test-auth.ts";
import { handleBookingPaymentSimulate, simulatedTransactionId } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const NOW = new Date("2026-11-20T03:05:00Z");
const EXPIRES = new Date("2026-11-20T03:15:00Z");
const CODE = "SNPABCD2345";
const BID = "3b9e2c1a-1111-4222-8333-444455556666";
const UID = "player1";
const S1 = "c1_20261120_1800";

function setup(opts: { enabled?: boolean; booking?: Doc; slot?: Doc } = {}) {
  const fs = new FakeFirestore(() => NOW);
  fs.seed("venues/v1", { ownerId: "owner1", name: "Sân Ánh Dương", status: "ACTIVE" });
  fs.seed("users/owner1", { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" });
  fs.seed(`paymentCodes/${CODE}`, {
    code: CODE, bookingId: BID, recurringGroupId: null, userId: UID, venueId: "v1", amount: 120_000, active: true,
  });
  fs.seed(`bookings/${BID}`, {
    userId: UID, venueId: "v1", status: "HOLD", paymentStatus: "AWAITING_TRANSFER", paymentMethod: "TRANSFER_FULL",
    subtotal: 120_000, discount: 0, total: 120_000, amountDue: 120_000, paidAmount: 0, remainingAmount: 120_000,
    paymentCode: CODE, voucherCode: null, voucherScope: null, holdExpiresAt: EXPIRES, items: [{ slotId: S1 }],
    startAt: new Date("2026-11-20T11:00:00Z"), venueSnapshot: { name: "Sân Ánh Dương" }, ...opts.booking,
  });
  fs.seed(`slots/${S1}`, { status: "HELD", holdBy: UID, bookingId: BID, holdExpiresAt: EXPIRES, ...opts.slot });
  const deps = { firestore: fs, authenticate: fakeAuthenticate, now: () => NOW, enabled: () => opts.enabled ?? true };
  return { fs, deps };
}

async function call(env: ReturnType<typeof setup>, uid: string | null = UID, body: unknown = { paymentCode: CODE }) {
  const res = await handleBookingPaymentSimulate(authedRequest(uid, body), env.deps);
  return { status: res.status, json: await res.json() };
}

Deno.test("chưa bật PAYMENT_SIMULATOR_ENABLED → 404, không ghi gì", async () => {
  const env = setup({ enabled: false });
  const res = await call(env);
  assertEquals(res.status, 404);
  assertEquals(res.json.error.code, "SIMULATOR_DISABLED");
  assertEquals(env.fs.commits, 0);
});

Deno.test("chốt đơn như webhook: PAID, PENDING, slot BOOKED, sổ giao dịch đánh dấu giả lập", async () => {
  const env = setup();
  const res = await call(env);
  assertEquals(res.status, 200);
  assertEquals(res.json, { outcome: "CONFIRMED" });
  const b = env.fs.read(`bookings/${BID}`)!;
  assertEquals(b.status, "PENDING");
  assertEquals(b.paymentStatus, "PAID");
  assertEquals(b.paidAmount, 120_000);
  assertEquals(env.fs.read(`slots/${S1}`)!.status, "BOOKED");
  assertEquals(env.fs.read(`paymentCodes/${CODE}`)!.active, false);
  const id = await simulatedTransactionId(CODE);
  assert(id < 0);
  const ledger = env.fs.read(`sepayTransactions/${id}`)!;
  assertEquals(ledger.outcome, "CONFIRMED");
  assertEquals(ledger.simulated, true);
  assertEquals(ledger.simulatedBy, UID);
});

Deno.test("bấm lại → DUPLICATE, đơn không đổi thêm", async () => {
  const env = setup();
  await call(env);
  const commits = env.fs.commits;
  const res = await call(env);
  assertEquals(res.status, 200);
  assertEquals(res.json, { outcome: "DUPLICATE" });
  assertEquals(env.fs.commits, commits);
});

Deno.test("người khác giả lập mã của mình → 403", async () => {
  const env = setup();
  const res = await call(env, "player2");
  assertEquals(res.status, 403);
  assertEquals(env.fs.read(`bookings/${BID}`)!.status, "HOLD");
});

Deno.test("chưa đăng nhập → 401; mã sai dạng → 400; không có mã → 404", async () => {
  const env = setup();
  assertEquals((await call(env, null)).status, 401);
  assertEquals((await call(env, UID, { paymentCode: "abc" })).status, 400);
  assertEquals((await call(env, UID, { paymentCode: "SNPZZZZ2222" })).status, 404);
  assertEquals(env.fs.commits, 0);
});

Deno.test("giữ chỗ đã mất → 409 HOLD_EXPIRED, không sinh khoản hoàn", async () => {
  const env = setup({ slot: { status: "AVAILABLE", bookingId: null } });
  const res = await call(env);
  assertEquals(res.status, 409);
  assertEquals(res.json.error.code, "HOLD_EXPIRED");
  assertEquals(env.fs.children("refunds").length, 0);
  assertEquals(env.fs.children("sepayTransactions").length, 0);
});
