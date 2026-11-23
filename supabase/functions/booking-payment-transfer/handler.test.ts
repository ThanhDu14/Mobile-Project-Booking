// T035 (spec 050 US4): unit test `booking-payment-transfer` với Firestore giả trong bộ nhớ.
//   cd supabase/functions && deno test --allow-env --allow-read booking-payment-transfer/handler.test.ts
import { assert, assertEquals, assertMatch } from "jsr:@std/assert@1.0.19";
import { HttpError } from "../_shared/auth.ts";
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";
import { handleBookingPaymentTransfer, type TransferDeps } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const MIN = 60_000;
const NOW = new Date("2026-11-20T03:00:00Z");
const HOLD_UNTIL = new Date(NOW.getTime() + 4 * MIN);
const TRANSFER_UNTIL = new Date(NOW.getTime() + 15 * MIN);
const RID = "3b9e2c1a-1111-4222-8333-444455556666";
const GID = "9a8b7c6d-0000-4111-8222-333344445555";
const UID = "player1";
const S1 = "c1_20261120_1800";
const S2 = "c1_20261120_1830";
const PAYOUT = {
  bankBin: "970422",
  bankShortName: "MBBank",
  accountNumber: "0123456789",
  accountHolderName: "NGUYEN VAN A",
};

interface Env {
  fs: FakeFirestore;
  deps: TransferDeps;
  codes: string[];
}

const heldSlot = (bookingId = RID, extra: Doc = {}): Doc => ({
  venueId: "v1",
  courtId: "c1",
  status: "HELD",
  holdBy: UID,
  bookingId,
  holdExpiresAt: HOLD_UNTIL,
  ...extra,
});

function setup(
  opts: { venue?: Doc; slots?: Record<string, Doc>; seed?: Record<string, Doc>; codes?: string[] } = {},
): Env {
  const fs = new FakeFirestore(() => NOW);
  fs.seed("venues/v1", {
    ownerId: "owner1",
    name: "Sân Ánh Dương",
    address: "12 Nguyễn Trãi",
    status: "ACTIVE",
    payoutAccount: PAYOUT,
    ...opts.venue,
  });
  fs.seed("venues/v1/courts/c1", { name: "Sân 1", surfaceType: "Thảm PVC" });
  fs.seed("venues/v1/priceRules/r1", { daysOfWeek: [], startTime: "0600", endTime: "2200", pricePerHour: 120_000 });
  for (const [id, doc] of Object.entries(opts.slots ?? { [S1]: heldSlot(), [S2]: heldSlot() })) {
    fs.seed(`slots/${id}`, doc);
  }
  for (const [path, doc] of Object.entries(opts.seed ?? {})) fs.seed(path, doc);
  const codes = [...(opts.codes ?? ["SNPAAAA2222", "SNPBBBB3333", "SNPCCCC4444", "SNPDDDD5555"])];
  const env: Env = {
    fs,
    codes,
    deps: {
      firestore: fs,
      now: () => NOW,
      sleep: () => Promise.resolve(),
      newCode: () => codes.shift() ?? "SNPZZZZ9999",
      authenticate: (req) => {
        const token = /^Bearer (\S+)$/.exec(req.headers.get("Authorization") ?? "")?.[1];
        if (!token) return Promise.reject(new HttpError(401, "UNAUTHENTICATED", "no token"));
        if (token === "locked") return Promise.reject(new HttpError(403, "ACCOUNT_LOCKED", "locked"));
        return Promise.resolve({ uid: token });
      },
    },
  };
  return env;
}

const body = (extra: Record<string, unknown> = {}) => ({
  requestId: RID,
  recurringGroupId: null,
  venueId: "v1",
  slotIds: [S1, S2],
  voucherCode: null,
  option: "TRANSFER_DEPOSIT",
  expectedTotal: 120_000,
  ...extra,
});

async function call(env: Env, payload: unknown, uid: string | null = UID) {
  const res = await handleBookingPaymentTransfer(
    new Request("http://localhost/functions/v1/booking-payment-transfer", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(uid ? { Authorization: `Bearer ${uid}` } : {}) },
      body: JSON.stringify(payload),
    }),
    env.deps,
  );
  return { status: res.status, json: await res.json() };
}

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

Deno.test("không đăng nhập → 401; tài khoản bị khóa → 403", async () => {
  const env = setup();
  assertEquals((await call(env, body(), null)).status, 401);
  assertEquals((await call(env, body(), "locked")).status, 403);
});

Deno.test("option phải là chuyển khoản", async () => {
  const env = setup();
  const res = await call(env, body({ option: "AT_VENUE" }));
  assertEquals(res.status, 400);
  assertEquals(res.json.error.code, "INVALID_ARGUMENT");
});

Deno.test("cơ sở thiếu payoutAccount → 422 TRANSFER_NOT_SUPPORTED", async () => {
  const env = setup({ venue: { payoutAccount: { bankBin: "970422", accountNumber: "0123" } } });
  const res = await call(env, body());
  assertEquals(res.status, 422);
  assertEquals(res.json.error.code, "TRANSFER_NOT_SUPPORTED");
  assertEquals(env.fs.commits, 0);
});

Deno.test("tổng 0đ → 409 INVALID_STATE (phải dùng booking-payment-confirm)", async () => {
  const env = setup({ seed: { "systemVouchers/FREE": voucher({ type: "FIXED", value: 500_000, maxDiscount: 0 }) } });
  const res = await call(env, body({ voucherCode: "FREE", expectedTotal: 0 }));
  assertEquals(res.status, 409);
  assertEquals(res.json.error.code, "INVALID_STATE");
  assertEquals(env.fs.commits, 0);
});

Deno.test("cọc 30%: đơn HOLD chờ chuyển khoản, mã thanh toán, gia hạn 15 phút ở đơn và mọi slot", async () => {
  const env = setup();
  const res = await call(env, body());
  assertEquals(res.status, 200);
  assertMatch(res.json.paymentCode, /^SNP[A-HJ-NP-Z2-9]{8}$/);
  assertEquals(res.json.bookingId, RID);
  assertEquals(res.json.amountDue, 36_000);
  assertEquals(res.json.holdExpiresAt, TRANSFER_UNTIL.toISOString());
  assertEquals(res.json.sandbox, true);
  assertEquals(res.json.payoutAccount, {
    bankShortName: "MBBank",
    accountNumber: "0123456789",
    accountHolderName: "NGUYEN VAN A",
  });
  assertEquals(res.json.qrUrl, "https://qr.sepay.vn/img?acc=0123456789&bank=970422&amount=36000&des=SNPAAAA2222");

  const booking = env.fs.read(`bookings/${RID}`)!;
  assertEquals(booking.status, "HOLD");
  assertEquals(booking.paymentStatus, "AWAITING_TRANSFER");
  assertEquals(booking.paymentMethod, "TRANSFER_DEPOSIT");
  assertEquals(booking.amountDue, 36_000);
  assertEquals(booking.depositAmount, 36_000);
  assertEquals(booking.total, 120_000);
  assertEquals(booking.paidAmount, 0);
  assertEquals(booking.remainingAmount, 120_000);
  assertEquals(booking.paymentCode, "SNPAAAA2222");
  assertEquals((booking.holdExpiresAt as Date).getTime(), TRANSFER_UNTIL.getTime());
  for (const s of [S1, S2]) {
    const slot = env.fs.read(`slots/${s}`)!;
    assertEquals(slot.status, "HELD");
    assertEquals(slot.bookingId, RID);
    assertEquals((slot.holdExpiresAt as Date).getTime(), TRANSFER_UNTIL.getTime());
  }
  const code = env.fs.read("paymentCodes/SNPAAAA2222")!;
  assertEquals(code.active, true);
  assertEquals(code.amount, 36_000);
  assertEquals(code.bookingId, RID);
  assertEquals(code.recurringGroupId, null);
  assertEquals(code.userId, UID);
  assertEquals(code.venueId, "v1");
});

Deno.test("trả đủ: amountDue = total", async () => {
  const env = setup();
  const res = await call(env, body({ option: "TRANSFER_FULL" }));
  assertEquals(res.status, 200);
  assertEquals(res.json.amountDue, 120_000);
  assertEquals(env.fs.read(`bookings/${RID}`)!.depositAmount, 0);
});

Deno.test("voucher được giữ tạm (RESERVED), đếm lượt ngay", async () => {
  const env = setup({ seed: { "systemVouchers/SALE20": voucher() } });
  const res = await call(env, body({ voucherCode: "sale20", expectedTotal: 96_000 }));
  assertEquals(res.status, 200);
  assertEquals(res.json.amountDue, 29_000);
  assertEquals(env.fs.read("systemVouchers/SALE20")!.usedCount, 1);
  assertEquals(env.fs.read(`systemVouchers/SALE20/redemptions/${RID}`)!.status, "RESERVED");
  assertEquals(env.fs.read(`systemVouchers/SALE20/userUsage/${UID}`)!.count, 1);
  const booking = env.fs.read(`bookings/${RID}`)!;
  assertEquals(booking.voucherCode, "SALE20");
  assertEquals(booking.voucherScope, "SYSTEM");
});

Deno.test("gọi lại cùng requestId: cùng mã, cùng hạn, không ghi thêm", async () => {
  const env = setup({ seed: { "systemVouchers/SALE20": voucher() } });
  const first = await call(env, body({ voucherCode: "SALE20", expectedTotal: 96_000 }));
  const commits = env.fs.commits;
  const later = { ...env.deps, now: () => new Date(NOW.getTime() + 3 * MIN) };
  const second = await handleBookingPaymentTransfer(
    new Request("http://localhost", {
      method: "POST",
      headers: { Authorization: `Bearer ${UID}` },
      body: JSON.stringify(body({ voucherCode: "SALE20", expectedTotal: 96_000 })),
    }),
    later,
  );
  const json = await second.json();
  assertEquals(second.status, 200);
  assertEquals(json.paymentCode, first.json.paymentCode);
  assertEquals(json.holdExpiresAt, first.json.holdExpiresAt);
  assertEquals(env.fs.commits, commits);
  assertEquals(env.fs.read("systemVouchers/SALE20")!.usedCount, 1);
});

Deno.test("mã trùng thì sinh lại; trùng quá 3 lần → 500", async () => {
  const env = setup({ seed: { "paymentCodes/SNPAAAA2222": { active: false } } });
  const res = await call(env, body());
  assertEquals(res.status, 200);
  assertEquals(res.json.paymentCode, "SNPBBBB3333");

  const all = setup({
    codes: ["SNPAAAA2222", "SNPAAAA2222", "SNPAAAA2222", "SNPAAAA2222"],
    seed: { "paymentCodes/SNPAAAA2222": { active: false } },
  });
  assertEquals((await call(all, body())).status, 500);
});

Deno.test("giữ chỗ đã hết hạn → 409 HOLD_EXPIRED", async () => {
  const env = setup({
    slots: { [S1]: heldSlot(RID, { holdExpiresAt: new Date(NOW.getTime() - 1) }), [S2]: heldSlot() },
  });
  const res = await call(env, body());
  assertEquals(res.status, 409);
  assertEquals(res.json.error.code, "HOLD_EXPIRED");
});

Deno.test("giá khác app gửi → 409 PRICE_CHANGED kèm breakdown", async () => {
  const env = setup();
  const res = await call(env, body({ expectedTotal: 100_000 }));
  assertEquals(res.status, 409);
  assertEquals(res.json.error.code, "PRICE_CHANGED");
  assertEquals(res.json.error.breakdown.total, 120_000);
});

Deno.test("đơn của người khác → 403", async () => {
  const env = setup({ seed: { [`bookings/${RID}`]: { userId: "other", status: "HOLD" } } });
  assertEquals((await call(env, body())).status, 403);
});

Deno.test("đơn đã PENDING → 409 INVALID_STATE", async () => {
  const env = setup({ seed: { [`bookings/${RID}`]: { userId: UID, status: "PENDING", paymentStatus: "UNPAID" } } });
  const res = await call(env, body());
  assertEquals(res.status, 409);
  assertEquals(res.json.error.code, "INVALID_STATE");
});

Deno.test("đơn định kỳ: nhóm, các buổi và slot cùng gia hạn; mã trỏ về nhóm", async () => {
  const env = setup({
    slots: {
      "c1_20261127_1800": heldSlot(GID, { holdExpiresAt: HOLD_UNTIL }),
      "c1_20261204_1800": heldSlot(GID, { holdExpiresAt: HOLD_UNTIL }),
    },
    seed: {
      [`recurringGroups/${GID}`]: { userId: UID, venueId: "v1", status: "HOLD", holdExpiresAt: HOLD_UNTIL },
      "bookings/b1": {
        userId: UID,
        recurringGroupId: GID,
        status: "HOLD",
        date: "20261127",
        startTime: "1800",
        slotIds: ["c1_20261127_1800"],
        holdExpiresAt: HOLD_UNTIL,
      },
      "bookings/b2": {
        userId: UID,
        recurringGroupId: GID,
        status: "HOLD",
        date: "20261204",
        startTime: "1800",
        slotIds: ["c1_20261204_1800"],
        holdExpiresAt: HOLD_UNTIL,
      },
    },
  });
  const res = await call(
    env,
    body({ requestId: GID, recurringGroupId: GID, slotIds: [], option: "TRANSFER_FULL", expectedTotal: 120_000 }),
  );
  assertEquals(res.status, 200);
  assertEquals(res.json.bookingId, GID);
  assertEquals(res.json.recurringGroupId, GID);
  assertEquals(res.json.amountDue, 120_000);
  const group = env.fs.read(`recurringGroups/${GID}`)!;
  assertEquals(group.status, "HOLD");
  assertEquals(group.paymentStatus, "AWAITING_TRANSFER");
  assertEquals(group.paymentCode, res.json.paymentCode);
  assertEquals((group.holdExpiresAt as Date).getTime(), TRANSFER_UNTIL.getTime());
  for (const b of ["b1", "b2"]) {
    const s = env.fs.read(`bookings/${b}`)!;
    assertEquals(s.status, "HOLD");
    assertEquals(s.paymentStatus, "AWAITING_TRANSFER");
    assertEquals((s.holdExpiresAt as Date).getTime(), TRANSFER_UNTIL.getTime());
  }
  for (const s of ["c1_20261127_1800", "c1_20261204_1800"]) {
    assertEquals((env.fs.read(`slots/${s}`)!.holdExpiresAt as Date).getTime(), TRANSFER_UNTIL.getTime());
  }
  const code = env.fs.read(`paymentCodes/${res.json.paymentCode}`)!;
  assertEquals(code.recurringGroupId, GID);
  assertEquals(code.bookingId, null);
  assert(code.active);
});
