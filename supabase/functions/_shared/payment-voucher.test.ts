import { assertEquals } from "jsr:@std/assert@1.0.19";
import type { Write } from "./firestore.ts";
import {
  checkVoucher,
  commitWrites,
  type LoadedVoucher,
  loadRedemption,
  loadUserUsageCount,
  loadVoucher,
  releaseWrites,
  reserveWrites,
  voucherPath,
} from "./payment-voucher.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

const NOW = new Date("2026-11-16T10:00:00Z");

function fakeTx(docs: Record<string, Record<string, unknown>>) {
  return { get: async (path: string) => docs[path] ?? null };
}

const pathOf = (w: Write) => (w.update?.name ?? w.delete ?? "").split("/documents/")[1];
const increments = (w: Write) =>
  Object.fromEntries(
    (w.updateTransforms ?? [])
      .filter((t): t is { fieldPath: string; increment: { integerValue: string } } => "increment" in t)
      .map((t) => [t.fieldPath, Number(t.increment.integerValue)]),
  );
const serverTimes = (w: Write) =>
  (w.updateTransforms ?? []).filter((t) => "setToServerValue" in t).map((t) => t.fieldPath).sort();
const stringField = (w: Write, key: string) => (w.update?.fields[key] as { stringValue?: string } | undefined)?.stringValue;

function voucherDoc(overrides: Record<string, unknown> = {}) {
  return {
    code: "GIAM20",
    type: "PERCENT",
    value: 20,
    maxDiscount: 30000,
    minOrderTotal: 100000,
    validFrom: "2026-11-01T00:00:00.000Z",
    validTo: "2026-11-30T23:59:59.000Z",
    usageLimit: 10,
    perUserLimit: 1,
    usedCount: 3,
    isActive: true,
    ...overrides,
  };
}

function loaded(overrides: Partial<LoadedVoucher> = {}): LoadedVoucher {
  return {
    scope: "VENUE",
    venueId: "v1",
    code: "GIAM20",
    type: "PERCENT",
    value: 20,
    maxDiscount: 30000,
    minOrderTotal: 100000,
    validFromMs: Date.parse("2026-11-01T00:00:00Z"),
    validToMs: Date.parse("2026-11-30T23:59:59Z"),
    usageLimit: 10,
    usedCount: 3,
    perUserLimit: 1,
    isActive: true,
    ...overrides,
  };
}

// ---------- loadVoucher ----------

Deno.test("payment-voucher: loadVoucher ưu tiên voucher cơ sở khi trùng mã với hệ thống", async () => {
  const tx = fakeTx({
    "venues/v1/vouchers/GIAM20": voucherDoc({ value: 20 }),
    "systemVouchers/GIAM20": voucherDoc({ value: 50 }),
  });
  const v = await loadVoucher(tx, "v1", "GIAM20");
  assertEquals(v?.scope, "VENUE");
  assertEquals(v?.value, 20);
  assertEquals(voucherPath(v!), "venues/v1/vouchers/GIAM20");
});

Deno.test("payment-voucher: loadVoucher rơi về systemVouchers và chuẩn hóa mã chữ thường", async () => {
  const tx = fakeTx({ "systemVouchers/HE2026": voucherDoc({ code: "HE2026", type: "FIXED", value: 20000 }) });
  const v = await loadVoucher(tx, "v1", " he2026 ");
  assertEquals(v?.scope, "SYSTEM");
  assertEquals(v?.code, "HE2026");
  assertEquals(voucherPath(v!), "systemVouchers/HE2026");
});

Deno.test("payment-voucher: loadVoucher trả null khi không có ở cả hai nơi hoặc mã rỗng", async () => {
  assertEquals(await loadVoucher(fakeTx({}), "v1", "KHONGCO"), null);
  assertEquals(await loadVoucher(fakeTx({}), "v1", "  "), null);
});

Deno.test("payment-voucher: loadVoucher hiểu maxDiscount/usageLimit/perUserLimit = 0 là không giới hạn, ngày dạng Date", async () => {
  const tx = fakeTx({
    "venues/v1/vouchers/X": voucherDoc({
      maxDiscount: 0,
      usageLimit: 0,
      perUserLimit: 0,
      validFrom: new Date("2026-11-01T00:00:00Z"),
      validTo: new Date("2026-12-01T00:00:00Z"),
    }),
  });
  const v = await loadVoucher(tx, "v1", "X");
  assertEquals(v?.maxDiscount, null);
  assertEquals(v?.usageLimit, null);
  assertEquals(v?.perUserLimit, null);
  assertEquals(v?.validFromMs, Date.parse("2026-11-01T00:00:00Z"));
  assertEquals(v?.validToMs, Date.parse("2026-12-01T00:00:00Z"));
});

Deno.test("payment-voucher: loadUserUsageCount và loadRedemption đọc dưới voucher", async () => {
  const v = loaded();
  const tx = fakeTx({
    "venues/v1/vouchers/GIAM20/userUsage/u1": { count: 2 },
    "venues/v1/vouchers/GIAM20/redemptions/b1": { userId: "u1", status: "RESERVED" },
  });
  assertEquals(await loadUserUsageCount(tx, v, "u1"), 2);
  assertEquals(await loadUserUsageCount(tx, v, "u2"), 0);
  assertEquals((await loadRedemption(tx, v, "b1"))?.status, "RESERVED");
  assertEquals(await loadRedemption(tx, v, "b2"), null);
});

// ---------- checkVoucher ----------

Deno.test("payment-voucher: checkVoucher hợp lệ", () => {
  assertEquals(checkVoucher(loaded(), 0, 200000, NOW), { ok: true });
});

Deno.test("payment-voucher: checkVoucher trả đúng lý do từ chối", () => {
  assertEquals(checkVoucher(null, 0, 200000, NOW), { ok: false, reason: "NOT_FOUND" });
  assertEquals(checkVoucher(loaded({ isActive: false }), 0, 200000, NOW), { ok: false, reason: "EXPIRED" });
  assertEquals(
    checkVoucher(loaded({ validToMs: Date.parse("2026-11-15T00:00:00Z") }), 0, 200000, NOW),
    { ok: false, reason: "EXPIRED" },
  );
  assertEquals(
    checkVoucher(loaded({ validFromMs: Date.parse("2026-11-17T00:00:00Z") }), 0, 200000, NOW),
    { ok: false, reason: "EXPIRED" },
  );
  assertEquals(
    checkVoucher(loaded({ minOrderTotal: 300000 }), 0, 200000, NOW),
    { ok: false, reason: "MIN_ORDER", minOrderTotal: 300000 },
  );
  assertEquals(checkVoucher(loaded({ usedCount: 10 }), 0, 200000, NOW), { ok: false, reason: "OUT_OF_USES" });
  assertEquals(checkVoucher(loaded({ perUserLimit: 1 }), 1, 200000, NOW), { ok: false, reason: "PER_USER_LIMIT" });
});

Deno.test("payment-voucher: checkVoucher biên: đúng bằng đơn tối thiểu và đúng giờ hết hạn vẫn hợp lệ", () => {
  assertEquals(checkVoucher(loaded({ minOrderTotal: 200000 }), 0, 200000, NOW), { ok: true });
  assertEquals(checkVoucher(loaded({ validToMs: NOW.getTime() }), 0, 200000, NOW), { ok: true });
  assertEquals(
    checkVoucher(loaded({ usageLimit: null, perUserLimit: null, usedCount: 999 }), 99, 200000, NOW),
    { ok: true },
  );
});

// ---------- reserve / commit / release ----------

Deno.test("payment-voucher: reserveWrites tăng usedCount, userUsage.count và tạo redemption RESERVED", () => {
  const writes = reserveWrites(loaded(), "u1", "b1");
  assertEquals(writes.map(pathOf), [
    "venues/v1/vouchers/GIAM20",
    "venues/v1/vouchers/GIAM20/userUsage/u1",
    "venues/v1/vouchers/GIAM20/redemptions/b1",
  ]);
  const [voucher, usage, redemption] = writes;
  assertEquals(increments(voucher), { usedCount: 1 });
  assertEquals(voucher.currentDocument, { exists: true });
  assertEquals(increments(usage), { count: 1 });
  assertEquals(usage.currentDocument, undefined); // tạo mới nếu chưa có
  assertEquals(redemption.currentDocument, { exists: false });
  assertEquals(stringField(redemption, "status"), "RESERVED");
  assertEquals(stringField(redemption, "userId"), "u1");
  assertEquals(serverTimes(redemption), ["reservedAt"]);
});

Deno.test("payment-voucher: reserveWrites với status USED dùng cho đơn chốt ngay (tại sân)", () => {
  const redemption = reserveWrites(loaded({ scope: "SYSTEM" }), "u1", "b1", "USED")[2];
  assertEquals(pathOf(redemption), "systemVouchers/GIAM20/redemptions/b1");
  assertEquals(stringField(redemption, "status"), "USED");
  assertEquals(serverTimes(redemption), ["reservedAt", "usedAt"]);
});

Deno.test("payment-voucher: commitWrites đổi RESERVED → USED kèm usedAt; đã USED hoặc không có thì không ghi", () => {
  const v = loaded();
  const writes = commitWrites(v, "b1", { userId: "u1", status: "RESERVED" });
  assertEquals(writes.length, 1);
  assertEquals(pathOf(writes[0]), "venues/v1/vouchers/GIAM20/redemptions/b1");
  assertEquals(stringField(writes[0], "status"), "USED");
  assertEquals(writes[0].updateMask, { fieldPaths: ["status"] });
  assertEquals(serverTimes(writes[0]), ["usedAt"]);
  assertEquals(commitWrites(v, "b1", { userId: "u1", status: "USED" }), []);
  assertEquals(commitWrites(v, "b1", null), []);
});

Deno.test("payment-voucher: releaseWrites giảm hai bộ đếm và xóa redemption RESERVED; USED thì giữ nguyên", () => {
  const v = loaded();
  const writes = releaseWrites(v, "b1", { userId: "u1", status: "RESERVED" });
  assertEquals(writes.map(pathOf), [
    "venues/v1/vouchers/GIAM20",
    "venues/v1/vouchers/GIAM20/userUsage/u1",
    "venues/v1/vouchers/GIAM20/redemptions/b1",
  ]);
  assertEquals(increments(writes[0]), { usedCount: -1 });
  assertEquals(increments(writes[1]), { count: -1 });
  assertEquals(writes[1].currentDocument, { exists: true });
  assertEquals(writes[2].delete !== undefined, true);
  assertEquals(releaseWrites(v, "b1", { userId: "u1", status: "USED" }), []);
  assertEquals(releaseWrites(v, "b1", null), []);
});

Deno.test("payment-voucher: release/commit nhận VoucherRef (scope, venueId, code) đọc từ đơn", () => {
  const ref = { scope: "SYSTEM" as const, venueId: "v9", code: "HE2026" };
  assertEquals(pathOf(releaseWrites(ref, "g1", { userId: "u2", status: "RESERVED" })[1]), "systemVouchers/HE2026/userUsage/u2");
  assertEquals(pathOf(commitWrites(ref, "g1", { userId: "u2", status: "RESERVED" })[0]), "systemVouchers/HE2026/redemptions/g1");
});
