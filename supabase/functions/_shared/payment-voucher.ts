// Đọc, kiểm, giữ tạm, chốt và nhả voucher trong transaction REST (spec 050, data-model §1.6, research R5).
// Voucher ở `venues/{venueId}/vouchers/{code}` (VENUE) hoặc `systemVouchers/{code}` (SYSTEM). Dưới mỗi voucher:
//   userUsage/{uid}      { userId, count }   tính cả lượt RESERVED
//   redemptions/{key}    { userId, status: RESERVED | USED, reservedAt, usedAt? }   key = bookingId hoặc recurringGroupId
// Bộ đếm dùng field transform `increment` nên nhả voucher (expire-holds) không cần đọc lại voucher.
import { createWrite, deleteWrite, documentName, type FirestoreTransaction, type Write } from "./firestore.ts";
import type { VoucherTerms } from "./payment-calc.ts";

export type VoucherScope = "VENUE" | "SYSTEM";

/** Đủ để tìm lại voucher từ đơn: đơn lưu `voucherCode`, `voucherScope`, `venueId`. */
export interface VoucherRef {
  scope: VoucherScope;
  venueId: string;
  code: string;
}

export interface LoadedVoucher extends VoucherRef, VoucherTerms {
  maxDiscount: number | null;
  minOrderTotal: number;
  validFromMs: number | null;
  validToMs: number | null;
  /** null = không giới hạn. */
  usageLimit: number | null;
  usedCount: number;
  /** null = không giới hạn. */
  perUserLimit: number | null;
  isActive: boolean;
}

export type VoucherRejectReason = "NOT_FOUND" | "EXPIRED" | "MIN_ORDER" | "OUT_OF_USES" | "PER_USER_LIMIT";

export type VoucherCheckResult =
  | { ok: true }
  | { ok: false; reason: Exclude<VoucherRejectReason, "MIN_ORDER"> }
  | { ok: false; reason: "MIN_ORDER"; minOrderTotal: number };

export interface Redemption {
  userId: string;
  status: "RESERVED" | "USED";
}

type TxReader = Pick<FirestoreTransaction, "get">;

export function normalizeVoucherCode(code: string): string {
  return code.trim().toUpperCase();
}

export function voucherPath(ref: VoucherRef): string {
  return ref.scope === "VENUE" ? `venues/${ref.venueId}/vouchers/${ref.code}` : `systemVouchers/${ref.code}`;
}

/** Tìm voucher cơ sở trước, không có mới tìm voucher hệ thống. */
export async function loadVoucher(tx: TxReader, venueId: string, rawCode: string): Promise<LoadedVoucher | null> {
  const code = normalizeVoucherCode(rawCode);
  if (!code) return null;
  for (const scope of ["VENUE", "SYSTEM"] as const) {
    const ref: VoucherRef = { scope, venueId, code };
    const doc = await tx.get(voucherPath(ref));
    if (doc) return toLoadedVoucher(ref, doc);
  }
  return null;
}

export async function loadUserUsageCount(tx: TxReader, ref: VoucherRef, uid: string): Promise<number> {
  const doc = await tx.get(`${voucherPath(ref)}/userUsage/${uid}`);
  return toInt(doc?.count) ?? 0;
}

export async function loadRedemption(tx: TxReader, ref: VoucherRef, key: string): Promise<Redemption | null> {
  const doc = await tx.get(`${voucherPath(ref)}/redemptions/${key}`);
  if (!doc) return null;
  const status = doc.status === "USED" ? "USED" : doc.status === "RESERVED" ? "RESERVED" : null;
  return status ? { userId: String(doc.userId ?? ""), status } : null;
}

/**
 * Kiểm voucher cho một đơn. `userUsage` là số lượt người này đã dùng hoặc đang giữ (`userUsage/{uid}.count`).
 * Thứ tự lý do theo spec: không có → hết hạn/tắt → chưa đủ đơn tối thiểu → hết lượt → hết lượt của người dùng.
 */
export function checkVoucher(
  voucher: LoadedVoucher | null,
  userUsage: number,
  subtotal: number,
  now: Date,
): VoucherCheckResult {
  if (!voucher) return { ok: false, reason: "NOT_FOUND" };
  const t = now.getTime();
  if (
    !voucher.isActive ||
    (voucher.validFromMs != null && t < voucher.validFromMs) ||
    (voucher.validToMs != null && t > voucher.validToMs)
  ) {
    return { ok: false, reason: "EXPIRED" };
  }
  if (subtotal < voucher.minOrderTotal) return { ok: false, reason: "MIN_ORDER", minOrderTotal: voucher.minOrderTotal };
  if (voucher.usageLimit != null && voucher.usedCount >= voucher.usageLimit) return { ok: false, reason: "OUT_OF_USES" };
  if (voucher.perUserLimit != null && userUsage >= voucher.perUserLimit) return { ok: false, reason: "PER_USER_LIMIT" };
  return { ok: true };
}

/**
 * Giữ một lượt: tăng `usedCount`, `userUsage/{uid}.count`, tạo `redemptions/{key}`.
 * `status = "USED"` khi đơn chốt ngay (trả tại sân, miễn phí) để không phải ghi hai lần.
 * Redemption tạo với precondition `exists = false`: gọi hai lần cho cùng `key` thì commit báo `ALREADY_EXISTS`.
 */
export function reserveWrites(
  ref: VoucherRef,
  uid: string,
  key: string,
  status: Redemption["status"] = "RESERVED",
): Write[] {
  const base = voucherPath(ref);
  return [
    incrementWrite(base, "usedCount", 1, true),
    incrementWrite(`${base}/userUsage/${uid}`, "count", 1, false, { userId: uid }),
    createWrite(
      `${base}/redemptions/${key}`,
      { userId: uid, status },
      status === "USED" ? ["reservedAt", "usedAt"] : ["reservedAt"],
    ),
  ];
}

/** Chốt lượt đang giữ (`RESERVED → USED`). Đã `USED` hoặc không có redemption thì không ghi gì. */
export function commitWrites(ref: VoucherRef, key: string, redemption: Redemption | null): Write[] {
  if (redemption?.status !== "RESERVED") return [];
  return [{
    update: { name: documentName(`${voucherPath(ref)}/redemptions/${key}`), fields: { status: { stringValue: "USED" } } },
    updateMask: { fieldPaths: ["status"] },
    updateTransforms: [{ fieldPath: "usedAt", setToServerValue: "REQUEST_TIME" }],
    currentDocument: { exists: true },
  }];
}

/** Nhả lượt đang giữ: giảm hai bộ đếm, xóa redemption. Chỉ nhả `RESERVED`; lượt `USED` giữ nguyên. */
export function releaseWrites(ref: VoucherRef, key: string, redemption: Redemption | null): Write[] {
  if (redemption?.status !== "RESERVED") return [];
  const base = voucherPath(ref);
  return [
    incrementWrite(base, "usedCount", -1, true),
    incrementWrite(`${base}/userUsage/${redemption.userId}`, "count", -1, true),
    deleteWrite(`${base}/redemptions/${key}`),
  ];
}

function incrementWrite(
  path: string,
  field: string,
  by: number,
  mustExist: boolean,
  data: Record<string, string> = {},
): Write {
  return {
    update: {
      name: documentName(path),
      fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, { stringValue: v }])),
    },
    updateMask: { fieldPaths: Object.keys(data) },
    updateTransforms: [{ fieldPath: field, increment: { integerValue: String(by) } }],
    ...(mustExist ? { currentDocument: { exists: true } } : {}),
  };
}

function toLoadedVoucher(ref: VoucherRef, doc: Record<string, unknown>): LoadedVoucher {
  const type = String(doc.type ?? "").toUpperCase() === "FIXED" ? "FIXED" : "PERCENT";
  return {
    ...ref,
    type,
    value: toInt(doc.value) ?? 0,
    maxDiscount: positiveOrNull(doc.maxDiscount),
    minOrderTotal: toInt(doc.minOrderTotal) ?? 0,
    validFromMs: toMillis(doc.validFrom),
    validToMs: toMillis(doc.validTo),
    usageLimit: positiveOrNull(doc.usageLimit),
    usedCount: toInt(doc.usedCount) ?? 0,
    perUserLimit: positiveOrNull(doc.perUserLimit),
    isActive: doc.isActive !== false,
  };
}

function toInt(value: unknown): number | null {
  const n = Number(value);
  return value != null && Number.isFinite(n) ? Math.trunc(n) : null;
}

/** Dữ liệu hiện có (111/120) ghi 0 cho "không giới hạn"/"không trần". */
function positiveOrNull(value: unknown): number | null {
  const n = toInt(value);
  return n != null && n > 0 ? n : null;
}

function toMillis(value: unknown): number | null {
  if (value instanceof Date) return value.getTime();
  if (typeof value === "string" && value) {
    const ms = Date.parse(value);
    return Number.isFinite(ms) ? ms : null;
  }
  return null;
}
