// T026 (spec 050 US2): unit test `booking-payment-confirm` với Firestore giả trong bộ nhớ. Không cần Emulator.
//   cd supabase/functions && deno test --allow-env booking-payment-confirm/handler.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { HttpError } from "../_shared/auth.ts";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { type ConfirmDeps, handleBookingPaymentConfirm } from "./handler.ts";

Deno.env.set(
  "FIREBASE_PROJECT_ID",
  Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow",
);

const MIN = 60_000;
// 10:00 giờ Việt Nam ngày 20/11/2026.
const NOW = new Date("2026-11-20T03:00:00Z");
const HOLD_UNTIL = new Date(NOW.getTime() + 4 * MIN);
const RID = "3b9e2c1a-1111-4222-8333-444455556666";
const RID2 = "7f1d0c2b-aaaa-4bbb-9ccc-ddddeeeeffff";
const GID = "9a8b7c6d-0000-4111-8222-333344445555";
const UID = "player1";
const OWNER = "owner1";
// Hai slot 18:00 và 18:30 (giờ VN) sân c1; bảng giá 120.000đ/giờ → 60.000đ/slot.
const S1 = "c1_20261120_1800";
const S2 = "c1_20261120_1830";
const SLOT_START_S1 = new Date("2026-11-20T11:00:00Z");

interface Env {
  fs: FakeFirestore;
  deps: ConfirmDeps;
}

function heldSlot(bookingId = RID, extra: Doc = {}): Doc {
  return {
    venueId: "v1",
    courtId: "c1",
    status: "HELD",
    holdBy: UID,
    bookingId,
    holdExpiresAt: HOLD_UNTIL,
    ...extra,
  };
}

function setup(
  opts: {
    slots?: Record<string, Doc>;
    venue?: Doc;
    vouchers?: Record<string, Doc>;
  } = {},
): Env {
  const fs = new FakeFirestore(() => NOW);
  fs.seed("venues/v1", {
    ownerId: OWNER,
    name: "Sân Ánh Dương",
    address: "12 Nguyễn Trãi, Quận 1",
    phone: "0901234567",
    photoUrl: null,
    status: "ACTIVE",
    ...opts.venue,
  });
  fs.seed("venues/v1/courts/c1", { name: "Sân 1", surfaceType: "Thảm PVC" });
  fs.seed("venues/v1/priceRules/r1", {
    daysOfWeek: [],
    startTime: "0600",
    endTime: "2200",
    pricePerHour: 120_000,
  });
  for (
    const [id, doc] of Object.entries(
      opts.slots ?? { [S1]: heldSlot(), [S2]: heldSlot() },
    )
  ) fs.seed(`slots/${id}`, doc);
  for (const [path, doc] of Object.entries(opts.vouchers ?? {})) {
    fs.seed(path, doc);
  }
  const deps: ConfirmDeps = {
    firestore: fs,
    now: () => NOW,
    authenticate: (req) => {
      const token = /^Bearer (\S+)$/.exec(
        req.headers.get("Authorization") ?? "",
      )?.[1];
      if (!token) {
        return Promise.reject(
          new HttpError(401, "UNAUTHENTICATED", "no token"),
        );
      }
      if (token === "locked") {
        return Promise.reject(
          new HttpError(403, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa"),
        );
      }
      return Promise.resolve({ uid: token });
    },
    sleep: () => Promise.resolve(),
  };
  return { fs, deps };
}

const body = (extra: Record<string, unknown> = {}) => ({
  requestId: RID,
  recurringGroupId: null,
  venueId: "v1",
  slotIds: [S1, S2],
  voucherCode: null,
  expectedTotal: 120_000,
  ...extra,
});

async function call(env: Env, payload: unknown, uid: string | null = UID) {
  const res = await handleBookingPaymentConfirm(
    new Request("http://localhost/functions/v1/booking-payment-confirm", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(uid ? { Authorization: `Bearer ${uid}` } : {}),
      },
      body: JSON.stringify(payload),
    }),
    env.deps,
  );
  return { status: res.status, json: await res.json() };
}

const SYSTEM_VOUCHER = "systemVouchers/SALE20";
const voucher = (extra: Doc = {}): Doc => ({
  type: "PERCENT",
  value: 20,
  maxDiscount: 30_000,
  minOrderTotal: 0,
  validFrom: "2026-11-01T00:00:00+07:00",
  validTo: "2026-11-30T23:59:59+07:00",
  usageLimit: 10,
  usedCount: 0,
  perUserLimit: 1,
  isActive: true,
  ...extra,
});

// ---------- Xác thực, đầu vào ----------

Deno.test("thiếu token → 401", async () => {
  const { status, json } = await call(setup(), body(), null);
  assertEquals(status, 401);
  assertEquals(json.error.code, "UNAUTHENTICATED");
});

Deno.test("tài khoản LOCKED → 403, không ghi gì", async () => {
  const env = setup();
  const { status, json } = await call(env, body(), "locked");
  assertEquals(status, 403);
  assertEquals(json.error.code, "ACCOUNT_LOCKED");
  assertEquals(env.fs.commits, 0);
});

Deno.test("body sai → 400 INVALID_ARGUMENT", async () => {
  for (
    const bad of [
      body({ requestId: "abc" }),
      body({ slotIds: [] }),
      body({ expectedTotal: -1 }),
      body({ venueId: "" }),
    ]
  ) {
    const { status, json } = await call(setup(), bad);
    assertEquals(status, 400);
    assertEquals(json.error.code, "INVALID_ARGUMENT");
  }
});

Deno.test("GET → 405", async () => {
  const res = await handleBookingPaymentConfirm(
    new Request("http://localhost/x", { method: "GET" }),
    setup().deps,
  );
  assertEquals(res.status, 405);
});

// ---------- Trả tại sân, đơn lẻ ----------

Deno.test("thành công: đơn PENDING/AT_VENUE/UNPAID, slot BOOKED, thông báo BOOKING_NEW cho chủ sân", async () => {
  const env = setup();
  const { status, json } = await call(env, body());
  assertEquals(status, 200);
  assertEquals(json.bookingId, RID);
  assertEquals(json.status, "PENDING");
  assertEquals(json.paymentStatus, "UNPAID");

  const b = env.fs.read(`bookings/${RID}`)!;
  assertEquals(b.userId, UID);
  assertEquals(b.venueId, "v1");
  assertEquals(b.status, "PENDING");
  assertEquals(b.paymentMethod, "AT_VENUE");
  assertEquals(b.paymentStatus, "UNPAID");
  assertEquals([b.subtotal, b.discount, b.total], [120_000, 0, 120_000]);
  assertEquals(
    [b.amountDue, b.depositAmount, b.paidAmount, b.remainingAmount],
    [0, 0, 0, 120_000],
  );
  assertEquals(b.voucherCode, null);
  assertEquals(b.requestId, RID);
  assertEquals((b.pendingAt as Date).getTime(), NOW.getTime());
  // min(pendingAt + 60 phút, startAt) = 11:00Z vì slot đầu lúc 18:00 VN = 11:00Z, còn now + 60 phút = 04:00Z.
  assertEquals(
    (b.approvalDeadline as Date).getTime(),
    NOW.getTime() + 60 * MIN,
  );
  assertEquals((b.startAt as Date).getTime(), SLOT_START_S1.getTime());
  assertEquals((b.endAt as Date).getTime(), SLOT_START_S1.getTime() + 60 * MIN);
  assertEquals(b.holdExpiresAt, null);
  assertEquals((b.venueSnapshot as Doc).name, "Sân Ánh Dương");
  const items = b.items as Doc[];
  assertEquals(items.map((i) => [i.slotId, i.courtId, i.courtName, i.price]), [
    [S1, "c1", "Sân 1 · Thảm PVC", 60_000],
    [S2, "c1", "Sân 1 · Thảm PVC", 60_000],
  ]);

  for (const s of [S1, S2]) {
    const slot = env.fs.read(`slots/${s}`)!;
    assertEquals(slot.status, "BOOKED");
    assertEquals(slot.holdExpiresAt, null);
    assertEquals(slot.bookingId, RID);
  }

  const notes = env.fs.children(`users/${OWNER}/notifications`);
  assertEquals(notes.length, 1);
  assertEquals(notes[0].data.type, "BOOKING_NEW");
  assertEquals((notes[0].data.data as Doc).bookingId, RID);

  assertEquals(json.receipt.bookingId, RID);
  assertEquals(json.receipt.recurringGroupId, null);
  assertEquals(json.receipt.venueName, "Sân Ánh Dương");
  assertEquals(json.receipt.option, "AT_VENUE");
  assertEquals(json.receipt.status, "UNPAID");
  assertEquals([
    json.receipt.total,
    json.receipt.paidAmount,
    json.receipt.remainingAmount,
  ], [120_000, 0, 120_000]);
  assertEquals(json.receipt.items, [
    "Sân 1 · Thảm PVC · 20/11/2026 18:00–19:00",
  ]);
});

Deno.test("approvalDeadline = startAt khi giờ chơi đến trước 60 phút", async () => {
  const near = "c1_20261120_1030"; // 10:30 VN, 30 phút nữa
  const env = setup({ slots: { [near]: heldSlot() } });
  const { status } = await call(
    env,
    body({ slotIds: [near], expectedTotal: 60_000 }),
  );
  assertEquals(status, 200);
  const b = env.fs.read(`bookings/${RID}`)!;
  assertEquals(
    (b.approvalDeadline as Date).getTime(),
    new Date("2026-11-20T03:30:00Z").getTime(),
  );
});

Deno.test("gọi lại cùng requestId trả lại kết quả cũ, không ghi thêm", async () => {
  const env = setup({ vouchers: { [SYSTEM_VOUCHER]: voucher() } });
  const first = await call(
    env,
    body({ voucherCode: "sale20", expectedTotal: 96_000 }),
  );
  assertEquals(first.status, 200);
  const commits = env.fs.commits;

  const again = await call(
    env,
    body({ voucherCode: "sale20", expectedTotal: 96_000 }),
  );
  assertEquals(again.status, 200);
  assertEquals(again.json, first.json);
  assertEquals(env.fs.commits, commits);
  assertEquals(env.fs.read(SYSTEM_VOUCHER)!.usedCount, 1);
  assertEquals(env.fs.children(`users/${OWNER}/notifications`).length, 1);
});

Deno.test("đơn cùng requestId của người khác → 403 FORBIDDEN", async () => {
  const env = setup();
  env.fs.seed(`bookings/${RID}`, { userId: "someone", status: "PENDING" });
  const { status, json } = await call(env, body());
  assertEquals(status, 403);
  assertEquals(json.error.code, "FORBIDDEN");
});

// ---------- Giữ chỗ ----------

Deno.test("slot của người khác, hết hạn, giữ cho request khác hoặc không còn HELD → 409 HOLD_EXPIRED", async () => {
  const cases: Doc[] = [
    heldSlot(RID, { holdBy: "other" }),
    heldSlot(RID, { holdExpiresAt: new Date(NOW.getTime() - 1) }),
    heldSlot(RID2),
    heldSlot(RID, { status: "BOOKED" }),
  ];
  for (const s1 of cases) {
    const env = setup({ slots: { [S1]: s1, [S2]: heldSlot() } });
    const { status, json } = await call(env, body());
    assertEquals(status, 409);
    assertEquals(json.error.code, "HOLD_EXPIRED");
    assertEquals(env.fs.commits, 0);
    assertEquals(env.fs.read(`bookings/${RID}`), null);
  }
});

Deno.test("slot chưa có document → 409 HOLD_EXPIRED", async () => {
  const env = setup({ slots: { [S1]: heldSlot() } });
  const { status, json } = await call(env, body());
  assertEquals(status, 409);
  assertEquals(json.error.code, "HOLD_EXPIRED");
});

Deno.test("slot của cơ sở khác → 409 HOLD_EXPIRED", async () => {
  const env = setup({
    slots: { [S1]: heldSlot(RID, { venueId: "v2" }), [S2]: heldSlot() },
  });
  const { status } = await call(env, body());
  assertEquals(status, 409);
});

// ---------- Giá, voucher ----------

Deno.test("expectedTotal lệch → 409 PRICE_CHANGED kèm breakdown, không ghi gì", async () => {
  const env = setup();
  const { status, json } = await call(env, body({ expectedTotal: 100_000 }));
  assertEquals(status, 409);
  assertEquals(json.error.code, "PRICE_CHANGED");
  assertEquals(json.error.breakdown.subtotal, 120_000);
  assertEquals(json.error.breakdown.total, 120_000);
  assertEquals(env.fs.commits, 0);
});

Deno.test("slot ngoài bảng giá → 409 NO_PRICE", async () => {
  const late = "c1_20261120_2230";
  const env = setup({ slots: { [late]: heldSlot() } });
  const { status, json } = await call(
    env,
    body({ slotIds: [late], expectedTotal: 0 }),
  );
  assertEquals(status, 409);
  assertEquals(json.error.code, "NO_PRICE");
});

Deno.test("voucher hợp lệ: giảm tiền, redemption USED, usedCount và userUsage tăng 1", async () => {
  const env = setup({ vouchers: { [SYSTEM_VOUCHER]: voucher() } });
  const { status, json } = await call(
    env,
    body({ voucherCode: " sale20 ", expectedTotal: 96_000 }),
  );
  assertEquals(status, 200);
  const b = env.fs.read(`bookings/${RID}`)!;
  assertEquals([b.subtotal, b.discount, b.total, b.remainingAmount], [
    120_000,
    24_000,
    96_000,
    96_000,
  ]);
  assertEquals(b.voucherCode, "SALE20");
  assertEquals(b.voucherScope, "SYSTEM");
  assertEquals(env.fs.read(SYSTEM_VOUCHER)!.usedCount, 1);
  assertEquals(env.fs.read(`${SYSTEM_VOUCHER}/userUsage/${UID}`)!.count, 1);
  assertEquals(
    env.fs.read(`${SYSTEM_VOUCHER}/redemptions/${RID}`)!.status,
    "USED",
  );
  assertEquals(json.receipt.voucherCode, "SALE20");
  assertEquals(json.receipt.discount, 24_000);
});

Deno.test("voucher cơ sở thắng voucher hệ thống trùng mã", async () => {
  const env = setup({
    vouchers: {
      [SYSTEM_VOUCHER]: voucher(),
      "venues/v1/vouchers/SALE20": voucher({
        type: "FIXED",
        value: 50_000,
        maxDiscount: 0,
      }),
    },
  });
  const { status } = await call(
    env,
    body({ voucherCode: "SALE20", expectedTotal: 70_000 }),
  );
  assertEquals(status, 200);
  assertEquals(env.fs.read(`bookings/${RID}`)!.voucherScope, "VENUE");
  assertEquals(env.fs.read("venues/v1/vouchers/SALE20")!.usedCount, 1);
  assertEquals(env.fs.read(SYSTEM_VOUCHER)!.usedCount, 0);
});

Deno.test("voucher hết lượt → 422 VOUCHER_INVALID OUT_OF_USES", async () => {
  const env = setup({
    vouchers: { [SYSTEM_VOUCHER]: voucher({ usageLimit: 3, usedCount: 3 }) },
  });
  const { status, json } = await call(
    env,
    body({ voucherCode: "SALE20", expectedTotal: 96_000 }),
  );
  assertEquals(status, 422);
  assertEquals(json.error.code, "VOUCHER_INVALID");
  assertEquals(json.error.reason, "OUT_OF_USES");
  assertEquals(env.fs.commits, 0);
});

Deno.test("voucher chưa đủ đơn tối thiểu → 422 MIN_ORDER kèm minOrderTotal; không có mã → NOT_FOUND", async () => {
  let env = setup({
    vouchers: { [SYSTEM_VOUCHER]: voucher({ minOrderTotal: 200_000 }) },
  });
  let r = await call(
    env,
    body({ voucherCode: "SALE20", expectedTotal: 96_000 }),
  );
  assertEquals(r.status, 422);
  assertEquals(r.json.error.reason, "MIN_ORDER");
  assertEquals(r.json.error.minOrderTotal, 200_000);

  env = setup();
  r = await call(env, body({ voucherCode: "NOPE", expectedTotal: 120_000 }));
  assertEquals(r.status, 422);
  assertEquals(r.json.error.reason, "NOT_FOUND");
});

Deno.test("người chơi đã dùng hết lượt của mình → 422 PER_USER_LIMIT", async () => {
  const env = setup({
    vouchers: {
      [SYSTEM_VOUCHER]: voucher(),
      [`${SYSTEM_VOUCHER}/userUsage/${UID}`]: { count: 1 },
    },
  });
  const { status, json } = await call(
    env,
    body({ voucherCode: "SALE20", expectedTotal: 96_000 }),
  );
  assertEquals(status, 422);
  assertEquals(json.error.reason, "PER_USER_LIMIT");
});

Deno.test("tổng 0đ → paymentStatus PAID, paidAmount 0, remainingAmount 0", async () => {
  const env = setup({
    vouchers: {
      [SYSTEM_VOUCHER]: voucher({
        type: "FIXED",
        value: 200_000,
        maxDiscount: 0,
      }),
    },
  });
  const { status, json } = await call(
    env,
    body({ voucherCode: "SALE20", expectedTotal: 0 }),
  );
  assertEquals(status, 200);
  assertEquals(json.paymentStatus, "PAID");
  const b = env.fs.read(`bookings/${RID}`)!;
  assertEquals([b.total, b.paidAmount, b.remainingAmount], [0, 0, 0]);
  assertEquals(b.paymentStatus, "PAID");
});

// ---------- Đổi từ chờ chuyển khoản sang trả tại sân ----------

Deno.test("đơn đang AWAITING_TRANSFER → AT_VENUE/UNPAID, mã tắt, voucher RESERVED → USED không tăng đếm lần hai", async () => {
  const env = setup({
    vouchers: {
      [SYSTEM_VOUCHER]: voucher({ usedCount: 1 }),
      [`${SYSTEM_VOUCHER}/userUsage/${UID}`]: { userId: UID, count: 1 },
      [`${SYSTEM_VOUCHER}/redemptions/${RID}`]: {
        userId: UID,
        status: "RESERVED",
      },
    },
  });
  env.fs.seed(`bookings/${RID}`, {
    userId: UID,
    venueId: "v1",
    status: "HOLD",
    paymentMethod: "TRANSFER_FULL",
    paymentStatus: "AWAITING_TRANSFER",
    paymentCode: "SNPABCDEFGH",
    amountDue: 96_000,
    voucherCode: "SALE20",
    voucherScope: "SYSTEM",
    items: [{ slotId: S1 }, { slotId: S2 }],
    holdExpiresAt: new Date(NOW.getTime() + 15 * MIN),
  });
  env.fs.seed("paymentCodes/SNPABCDEFGH", {
    code: "SNPABCDEFGH",
    bookingId: RID,
    active: true,
    amount: 96_000,
  });
  const until = new Date(NOW.getTime() + 15 * MIN);
  env.fs.seed(`slots/${S1}`, heldSlot(RID, { holdExpiresAt: until }));
  env.fs.seed(`slots/${S2}`, heldSlot(RID, { holdExpiresAt: until }));

  const { status, json } = await call(
    env,
    body({ voucherCode: "SALE20", expectedTotal: 96_000 }),
  );
  assertEquals(status, 200);
  assertEquals(json.paymentStatus, "UNPAID");
  const b = env.fs.read(`bookings/${RID}`)!;
  assertEquals(b.status, "PENDING");
  assertEquals(b.paymentMethod, "AT_VENUE");
  assertEquals(b.paymentStatus, "UNPAID");
  assertEquals(b.amountDue, 0);
  assertEquals(env.fs.read("paymentCodes/SNPABCDEFGH")!.active, false);
  assertEquals(
    env.fs.read(`${SYSTEM_VOUCHER}/redemptions/${RID}`)!.status,
    "USED",
  );
  assertEquals(env.fs.read(SYSTEM_VOUCHER)!.usedCount, 1);
  assertEquals(env.fs.read(`${SYSTEM_VOUCHER}/userUsage/${UID}`)!.count, 1);
  assertEquals(env.fs.read(`slots/${S1}`)!.status, "BOOKED");
});

Deno.test("đơn đã EXPIRED → 409 HOLD_EXPIRED", async () => {
  const env = setup();
  env.fs.seed(`bookings/${RID}`, {
    userId: UID,
    venueId: "v1",
    status: "EXPIRED",
    items: [{ slotId: S1 }],
  });
  const { status, json } = await call(env, body());
  assertEquals(status, 409);
  assertEquals(json.error.code, "HOLD_EXPIRED");
});

// ---------- Đơn định kỳ ----------

function seedRecurring(env: Env, groupExtra: Doc = {}) {
  const s1 = "c1_20261127_1800",
    s2 = "c1_20261127_1830",
    s3 = "c1_20261204_1800",
    s4 = "c1_20261204_1830";
  env.fs.seed(`recurringGroups/${GID}`, {
    userId: UID,
    venueId: "v1",
    totalSessions: 2,
    status: "HOLD",
    holdExpiresAt: HOLD_UNTIL,
    ...groupExtra,
  });
  env.fs.seed("bookings/sessA", {
    recurringGroupId: GID,
    userId: UID,
    venueId: "v1",
    courtId: "c1",
    courtName: "Sân 1",
    date: "20261127",
    startTime: "1800",
    endTime: "1900",
    slotIds: [s1, s2],
    status: "HOLD",
    price: 120_000,
  });
  env.fs.seed("bookings/sessB", {
    recurringGroupId: GID,
    userId: UID,
    venueId: "v1",
    courtId: "c1",
    courtName: "Sân 1",
    date: "20261204",
    startTime: "1800",
    endTime: "1900",
    slotIds: [s3, s4],
    status: "HOLD",
    price: 120_000,
  });
  for (const s of [s1, s2, s3, s4]) env.fs.seed(`slots/${s}`, heldSlot(GID));
  return [s1, s2, s3, s4];
}

Deno.test("đơn định kỳ: một lần chốt cho cả nhóm, nhóm DONE, mọi buổi PENDING, slot BOOKED", async () => {
  const env = setup({ slots: {}, vouchers: { [SYSTEM_VOUCHER]: voucher() } });
  const slots = seedRecurring(env);
  const { status, json } = await call(
    env,
    body({
      requestId: GID,
      recurringGroupId: GID,
      slotIds: [],
      voucherCode: "SALE20",
      expectedTotal: 210_000,
    }),
  );
  assertEquals(status, 200);
  assertEquals(json.bookingId, GID);
  assertEquals(json.receipt.recurringGroupId, GID);
  assertEquals(json.receipt.items.length, 2);

  const g = env.fs.read(`recurringGroups/${GID}`)!;
  assertEquals(g.status, "DONE");
  assertEquals([g.subtotal, g.discount, g.total], [240_000, 30_000, 210_000]);
  assertEquals(g.paymentMethod, "AT_VENUE");
  assertEquals(g.paymentStatus, "UNPAID");
  assertEquals(g.voucherCode, "SALE20");

  const a = env.fs.read("bookings/sessA")!, b = env.fs.read("bookings/sessB")!;
  for (const s of [a, b]) {
    assertEquals(s.status, "PENDING");
    assertEquals(s.paymentStatus, "UNPAID");
    assertEquals(s.paymentMethod, "AT_VENUE");
    assert(Array.isArray(s.items));
  }
  // Giảm giá chia theo tỷ lệ, phần lẻ vào buổi cuối; tổng các buổi = tổng nhóm.
  assertEquals((a.discount as number) + (b.discount as number), 30_000);
  assertEquals((a.total as number) + (b.total as number), 210_000);
  for (const s of slots) {
    assertEquals(env.fs.read(`slots/${s}`)!.status, "BOOKED");
  }
  assertEquals(
    env.fs.read(`${SYSTEM_VOUCHER}/redemptions/${GID}`)!.status,
    "USED",
  );
  assertEquals(env.fs.children(`users/${OWNER}/notifications`).length, 1);

  const again = await call(
    env,
    body({
      requestId: GID,
      recurringGroupId: GID,
      slotIds: [],
      voucherCode: "SALE20",
      expectedTotal: 210_000,
    }),
  );
  assertEquals(again.status, 200);
  assertEquals(again.json, json);
});

Deno.test("đơn định kỳ hết hạn giữ chỗ → 409 HOLD_EXPIRED; nhóm của người khác → 403", async () => {
  let env = setup({ slots: {} });
  seedRecurring(env, { holdExpiresAt: new Date(NOW.getTime() - 1) });
  let r = await call(
    env,
    body({
      requestId: GID,
      recurringGroupId: GID,
      slotIds: [],
      expectedTotal: 240_000,
    }),
  );
  assertEquals(r.status, 409);
  assertEquals(r.json.error.code, "HOLD_EXPIRED");

  env = setup({ slots: {} });
  seedRecurring(env, { userId: "someone" });
  r = await call(
    env,
    body({
      requestId: GID,
      recurringGroupId: GID,
      slotIds: [],
      expectedTotal: 240_000,
    }),
  );
  assertEquals(r.status, 403);
});

Deno.test("đơn định kỳ: requestId phải bằng recurringGroupId", async () => {
  const env = setup({ slots: {} });
  seedRecurring(env);
  const { status } = await call(
    env,
    body({ recurringGroupId: GID, slotIds: [], expectedTotal: 240_000 }),
  );
  assertEquals(status, 400);
});
