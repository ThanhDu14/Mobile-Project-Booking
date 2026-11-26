// T036 (spec 050 US4): webhook "Tiền vào" SePay Test mode → chốt đơn chờ chuyển khoản (research R4).
//   cd supabase/functions && deno test --allow-env --allow-read sepay-webhook/handler.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { hmacSha256Hex } from "../_shared/sepay.ts";
import { handleSepayWebhook } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const SECRET = "test-sepay-secret";
const NOW = new Date("2026-11-20T03:05:00Z");
const NOW_S = Math.floor(NOW.getTime() / 1000);
const EXPIRES = new Date("2026-11-20T03:15:00Z");
const CODE = "SNPABCD2345";
const BID = "3b9e2c1a-1111-4222-8333-444455556666";
const GID = "9a8b7c6d-0000-4111-8222-333344445555";
const UID = "player1";
const S1 = "c1_20261120_1800";
const S2 = "c1_20261120_1830";

function payload(overrides: Record<string, unknown> = {}) {
  return {
    id: 92704,
    gateway: "MBBank",
    transactionDate: "2026-11-20 10:05:00",
    accountNumber: "0123456789",
    code: CODE,
    content: `${CODE} dat san`,
    transferType: "in",
    transferAmount: 36_000,
    accumulated: 0,
    referenceCode: "SB123",
    ...overrides,
  };
}

async function signed(body: unknown, opts: { secret?: string; timestamp?: number; noSig?: boolean } = {}) {
  const raw = JSON.stringify(body);
  const ts = String(opts.timestamp ?? NOW_S);
  const sig = "sha256=" + await hmacSha256Hex(opts.secret ?? SECRET, `${ts}.${raw}`);
  return new Request("https://localhost/sepay-webhook", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(opts.noSig ? {} : { "x-sepay-signature": sig }),
      "x-sepay-timestamp": ts,
    },
    body: raw,
  });
}

const booking = (extra: Doc = {}): Doc => ({
  userId: UID,
  venueId: "v1",
  status: "HOLD",
  paymentStatus: "AWAITING_TRANSFER",
  paymentMethod: "TRANSFER_DEPOSIT",
  subtotal: 120_000,
  discount: 0,
  total: 120_000,
  amountDue: 36_000,
  depositAmount: 36_000,
  paidAmount: 0,
  remainingAmount: 120_000,
  paymentCode: CODE,
  voucherCode: null,
  voucherScope: null,
  holdExpiresAt: EXPIRES,
  items: [{ slotId: S1 }, { slotId: S2 }],
  startAt: new Date("2026-11-20T11:00:00Z"),
  venueSnapshot: { name: "Sân Ánh Dương" },
  ...extra,
});

const slot = (bookingId = BID, extra: Doc = {}): Doc => ({
  status: "HELD",
  holdBy: UID,
  bookingId,
  holdExpiresAt: EXPIRES,
  ...extra,
});

function setup(seed: Record<string, Doc> = {}, opts: { noDefault?: boolean; optimistic?: boolean } = {}) {
  const fs = new FakeFirestore(() => NOW, { optimistic: opts.optimistic });
  fs.seed("venues/v1", { ownerId: "owner1", name: "Sân Ánh Dương", status: "ACTIVE" });
  fs.seed("users/owner1", { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" });
  if (!opts.noDefault) {
    fs.seed(`paymentCodes/${CODE}`, {
      code: CODE,
      bookingId: BID,
      recurringGroupId: null,
      userId: UID,
      venueId: "v1",
      amount: 36_000,
      active: true,
    });
    fs.seed(`bookings/${BID}`, booking());
    fs.seed(`slots/${S1}`, slot());
    fs.seed(`slots/${S2}`, slot());
  }
  for (const [p, d] of Object.entries(seed)) fs.seed(p, d);
  const deps = { firestore: fs, secret: SECRET, nowSeconds: () => NOW_S, now: () => NOW };
  return { fs, deps };
}

async function send(env: ReturnType<typeof setup>, body: unknown = payload(), opts = {}) {
  const res = await handleSepayWebhook(await signed(body, opts), env.deps);
  return { status: res.status, json: await res.json() };
}

Deno.test("chữ ký sai, thiếu, lệch giờ → 401 và không ghi gì", async () => {
  const env = setup();
  assertEquals((await send(env, payload(), { secret: "wrong" })).status, 401);
  assertEquals((await send(env, payload(), { noSig: true })).status, 401);
  assertEquals((await send(env, payload(), { timestamp: NOW_S - 301 })).status, 401);
  assertEquals(env.fs.commits, 0);
});

Deno.test("CONFIRMED: đơn PENDING, DEPOSITED, slot BOOKED, mã tắt, thông báo chủ sân, sổ giao dịch", async () => {
  const env = setup();
  const res = await send(env);
  assertEquals(res.status, 200);
  assertEquals(res.json, { success: true, outcome: "CONFIRMED" });
  const b = env.fs.read(`bookings/${BID}`)!;
  assertEquals(b.status, "PENDING");
  assertEquals(b.paymentStatus, "DEPOSITED");
  assertEquals(b.paidAmount, 36_000);
  assertEquals(b.remainingAmount, 84_000);
  assertEquals(b.sepayTransactionId, 92704);
  assertEquals((b.paidAt as Date).getTime(), NOW.getTime());
  assertEquals(b.holdExpiresAt, null);
  assert(b.approvalDeadline instanceof Date);
  for (const s of [S1, S2]) {
    assertEquals(env.fs.read(`slots/${s}`)!.status, "BOOKED");
    assertEquals(env.fs.read(`slots/${s}`)!.holdExpiresAt, null);
  }
  assertEquals(env.fs.read(`paymentCodes/${CODE}`)!.active, false);
  assertEquals(env.fs.read(`users/owner1/notifications/booking_new_${BID}`)!.type, "BOOKING_NEW");
  const ledger = env.fs.read("sepayTransactions/92704")!;
  assertEquals(ledger.outcome, "CONFIRMED");
  assertEquals(ledger.bookingId, BID);
  assertEquals(ledger.paymentCode, CODE);
});

Deno.test("CONFIRMED trả đủ → PAID, còn lại 0", async () => {
  const env = setup({
    [`bookings/${BID}`]: booking({ paymentMethod: "TRANSFER_FULL", amountDue: 120_000, depositAmount: 0 }),
    [`paymentCodes/${CODE}`]: {
      code: CODE,
      bookingId: BID,
      recurringGroupId: null,
      userId: UID,
      venueId: "v1",
      amount: 120_000,
      active: true,
    },
  });
  const res = await send(env, payload({ transferAmount: 120_000 }));
  assertEquals(res.json.outcome, "CONFIRMED");
  const b = env.fs.read(`bookings/${BID}`)!;
  assertEquals(b.paymentStatus, "PAID");
  assertEquals(b.paidAmount, 120_000);
  assertEquals(b.remainingAmount, 0);
});

Deno.test("CONFIRMED chốt voucher RESERVED → USED", async () => {
  const env = setup({
    [`bookings/${BID}`]: booking({ voucherCode: "SALE20", voucherScope: "SYSTEM" }),
    "systemVouchers/SALE20": { type: "PERCENT", value: 20, usedCount: 1 },
    [`systemVouchers/SALE20/redemptions/${BID}`]: { userId: UID, status: "RESERVED" },
  });
  assertEquals((await send(env)).json.outcome, "CONFIRMED");
  assertEquals(env.fs.read(`systemVouchers/SALE20/redemptions/${BID}`)!.status, "USED");
  assertEquals(env.fs.read("systemVouchers/SALE20")!.usedCount, 1);
});

Deno.test("DUPLICATE: cùng id lần hai không ghi gì", async () => {
  const env = setup();
  await send(env);
  const commits = env.fs.commits;
  const res = await send(env);
  assertEquals(res.json.outcome, "DUPLICATE");
  assertEquals(env.fs.commits, commits);
});

Deno.test("NOT_INCOMING, NO_CODE, CODE_NOT_FOUND chỉ ghi sổ", async () => {
  const env = setup();
  assertEquals((await send(env, payload({ id: 1, transferType: "out" }))).json.outcome, "NOT_INCOMING");
  assertEquals((await send(env, payload({ id: 2, code: null, content: "chuyen tien" }))).json.outcome, "NO_CODE");
  assertEquals((await send(env, payload({ id: 3, code: "SNPZZZZ9999", content: "" }))).json.outcome, "CODE_NOT_FOUND");
  assertEquals(env.fs.read("sepayTransactions/3")!.outcome, "CODE_NOT_FOUND");
  assertEquals(env.fs.read(`bookings/${BID}`)!.status, "HOLD");
  assertEquals(env.fs.children("refunds").length, 0);
});

Deno.test("LATE_TRANSFER khi đơn đã EXPIRED: tạo refund, đơn không đổi", async () => {
  const env = setup({ [`bookings/${BID}`]: booking({ status: "EXPIRED" }) });
  const res = await send(env);
  assertEquals(res.json.outcome, "LATE_TRANSFER");
  const refund = env.fs.read("refunds/refund_sepay_92704")!;
  assertEquals(refund.reason, "LATE_TRANSFER");
  assertEquals(refund.amount, 36_000);
  assertEquals(refund.status, "PENDING");
  assertEquals(refund.bookingId, BID);
  assertEquals(refund.venueId, "v1");
  assertEquals(refund.userId, UID);
  assertEquals(env.fs.read(`bookings/${BID}`)!.status, "EXPIRED");
  assertEquals(env.fs.read("sepayTransactions/92704")!.refundId, "refund_sepay_92704");
});

Deno.test("LATE_TRANSFER khi mã đã tắt (đổi sang tại sân) hoặc slot không còn giữ cho đơn", async () => {
  const off = setup({
    [`paymentCodes/${CODE}`]: { code: CODE, bookingId: BID, userId: UID, venueId: "v1", amount: 36_000, active: false },
  });
  assertEquals((await send(off)).json.outcome, "LATE_TRANSFER");

  const taken = setup({ [`slots/${S2}`]: slot("someone-else", { holdBy: "other" }) });
  assertEquals((await send(taken)).json.outcome, "LATE_TRANSFER");
  assertEquals(taken.fs.read(`bookings/${BID}`)!.status, "HOLD");
  assert(taken.fs.read("refunds/refund_sepay_92704"));
});

Deno.test("AMOUNT_MISMATCH: tạo refund số tiền đã chuyển, đơn vẫn chờ", async () => {
  const env = setup();
  const res = await send(env, payload({ transferAmount: 30_000 }));
  assertEquals(res.json.outcome, "AMOUNT_MISMATCH");
  assertEquals(env.fs.read("refunds/refund_sepay_92704")!.reason, "AMOUNT_MISMATCH");
  assertEquals(env.fs.read("refunds/refund_sepay_92704")!.amount, 30_000);
  const b = env.fs.read(`bookings/${BID}`)!;
  assertEquals(b.status, "HOLD");
  assertEquals(b.paymentStatus, "AWAITING_TRANSFER");
  assertEquals(env.fs.read(`paymentCodes/${CODE}`)!.active, true);
  // Chuyển lại đúng số tiền vẫn chốt được.
  assertEquals((await send(env, payload({ id: 92705 }))).json.outcome, "CONFIRMED");
});

Deno.test("đơn định kỳ: nhóm DONE, mọi buổi PENDING, slot BOOKED", async () => {
  const env = setup({
    [`paymentCodes/${CODE}`]: {
      code: CODE,
      bookingId: null,
      recurringGroupId: GID,
      userId: UID,
      venueId: "v1",
      amount: 120_000,
      active: true,
    },
    [`recurringGroups/${GID}`]: {
      userId: UID,
      venueId: "v1",
      status: "HOLD",
      paymentStatus: "AWAITING_TRANSFER",
      paymentMethod: "TRANSFER_FULL",
      total: 120_000,
      amountDue: 120_000,
      paidAmount: 0,
      remainingAmount: 120_000,
      paymentCode: CODE,
      holdExpiresAt: EXPIRES,
    },
    "bookings/b1": {
      userId: UID,
      recurringGroupId: GID,
      status: "HOLD",
      paymentStatus: "AWAITING_TRANSFER",
      total: 60_000,
      slotIds: ["c1_20261127_1800"],
      startAt: new Date("2026-11-27T11:00:00Z"),
      holdExpiresAt: EXPIRES,
    },
    "bookings/b2": {
      userId: UID,
      recurringGroupId: GID,
      status: "HOLD",
      paymentStatus: "AWAITING_TRANSFER",
      total: 60_000,
      slotIds: ["c1_20261204_1800"],
      startAt: new Date("2026-12-04T11:00:00Z"),
      holdExpiresAt: EXPIRES,
    },
    "slots/c1_20261127_1800": slot(GID),
    "slots/c1_20261204_1800": slot(GID),
  });
  const res = await send(env, payload({ transferAmount: 120_000 }));
  assertEquals(res.json.outcome, "CONFIRMED");
  const g = env.fs.read(`recurringGroups/${GID}`)!;
  assertEquals(g.status, "DONE");
  assertEquals(g.paymentStatus, "PAID");
  assertEquals(g.paidAmount, 120_000);
  for (const b of ["b1", "b2"]) {
    const s = env.fs.read(`bookings/${b}`)!;
    assertEquals(s.status, "PENDING");
    assertEquals(s.paymentStatus, "PAID");
    assertEquals(s.paidAmount, 60_000);
    assertEquals(s.remainingAmount, 0);
  }
  assertEquals(env.fs.read("slots/c1_20261127_1800")!.status, "BOOKED");
  assertEquals(env.fs.read(`users/owner1/notifications/booking_new_${GID}`)!.type, "BOOKING_NEW");
});

Deno.test("lỗi Firestore → 500 để SePay gửi lại", async () => {
  const env = setup();
  env.deps.firestore = { runTransaction: () => Promise.reject(new Error("boom")) } as unknown as FakeFirestore;
  assertEquals((await send(env)).status, 500);
});

// T055 (spec 050 US5): SePay gửi lại cùng giao dịch 3 lần song song.
Deno.test("cùng id gửi 3 lần song song: một CONFIRMED, hai DUPLICATE, đơn chỉ cập nhật một lần", async () => {
  const env = setup({}, { optimistic: true });
  const results = await Promise.all([send(env), send(env), send(env)]);
  assertEquals(results.map((r) => r.status), [200, 200, 200]);
  const outcomes = results.map((r) => r.json.outcome).sort();
  assertEquals(outcomes, ["CONFIRMED", "DUPLICATE", "DUPLICATE"]);
  assertEquals(env.fs.read(`bookings/${BID}`)!.status, "PENDING");
  assertEquals(env.fs.read(`bookings/${BID}`)!.paidAmount, 36_000);
  assertEquals(env.fs.children("sepayTransactions").length, 1);
  assertEquals(env.fs.children(`users/owner1/notifications`).length, 1);
  assert(env.fs.aborts > 0);
});
