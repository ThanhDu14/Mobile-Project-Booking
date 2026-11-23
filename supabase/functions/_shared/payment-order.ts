// Phần dùng chung của các Edge Function chốt đơn spec 050 (`booking-payment-confirm`, `booking-payment-transfer`):
// đọc slot đang giữ + bảng giá + cơ sở, kiểm voucher, tính lại tiền, dựng hóa đơn. Mọi hàm chạy trong transaction
// của function gọi, không tự commit.
import { HttpError } from "./auth.ts";
import { createWrite, type FirestoreTransaction, updateWrite, type Write } from "./firestore.ts";
import { type PaymentBreakdown, paymentBreakdown } from "./payment-calc.ts";
import {
  checkVoucher,
  commitWrites,
  type LoadedVoucher,
  loadRedemption,
  loadUserUsageCount,
  loadVoucher,
  releaseWrites,
  reserveWrites,
  type VoucherRef,
  type VoucherScope,
} from "./payment-voucher.ts";
import { type ParsedSlot, parseSlotId, type PriceRule, slotPrice, toPriceRule } from "./slot-pricing.ts";

export type Doc = Record<string, unknown>;

export const holdExpired = () => new HttpError(409, "HOLD_EXPIRED", "Đã hết thời gian giữ chỗ");
export const forbidden = () => new HttpError(403, "FORBIDDEN", "Đơn này không thuộc về bạn");

/** Hóa đơn trả cho app (data-model §2 `PaymentReceipt`). */
export interface Receipt {
  bookingId: string;
  recurringGroupId: string | null;
  venueName: string;
  items: string[];
  option: string;
  status: string;
  subtotal: number;
  discount: number;
  total: number;
  paidAmount: number;
  remainingAmount: number;
  voucherCode: string | null;
}

// ---------- Đọc chung ----------

export const asDate = (
  v: unknown,
): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
export const asNumber = (
  v: unknown,
): number => (typeof v === "number" && Number.isFinite(v) ? v : 0);
export const asString = (
  v: unknown,
): string | null => (typeof v === "string" && v ? v : null);

export interface PricedSlot extends ParsedSlot {
  courtName: string;
  price: number;
}

export interface OrderContext {
  venue: Doc;
  ownerId: string;
  venueSnapshot: Doc;
  slots: PricedSlot[];
}

/**
 * Cơ sở còn hoạt động, mọi slot đang `HELD` cho đúng người và đúng đơn (`holderKey`), chưa hết hạn; kèm giá
 * tính lại từ bảng giá và tên sân.
 */
export async function loadOrder(
  tx: FirestoreTransaction,
  uid: string,
  venueId: string,
  holderKey: string,
  slotIds: string[],
  now: Date,
): Promise<OrderContext> {
  const venue = await tx.get(`venues/${venueId}`);
  if (!venue) {
    throw new HttpError(404, "VENUE_NOT_FOUND", "Không tìm thấy cơ sở");
  }
  if (venue.status != null && venue.status !== "ACTIVE") {
    throw new HttpError(409, "VENUE_INACTIVE", "Cơ sở tạm ngừng hoạt động");
  }
  const parsed = slotIds.map((id) => {
    const p = parseSlotId(id);
    if (!p) throw holdExpired();
    return p;
  });
  const slotDocs = await Promise.all(
    parsed.map((p) => tx.get(`slots/${p.slotId}`)),
  );
  slotDocs.forEach((s) => {
    const expiresAt = asDate(s?.holdExpiresAt);
    const bookingId = asString(s?.bookingId);
    const ok = s != null && s.status === "HELD" && s.holdBy === uid &&
      (s.venueId == null || s.venueId === venueId) &&
      (bookingId == null || bookingId === holderKey) && expiresAt != null &&
      expiresAt.getTime() > now.getTime();
    if (!ok) throw holdExpired();
  });

  const rules = (await tx.query("priceRules", [], { parent: `venues/${venueId}` }))
    .map((d) => toPriceRule(d.data))
    .filter((r): r is PriceRule => r != null);
  const courtIds = [...new Set(parsed.map((p) => p.courtId))];
  const courts = await Promise.all(
    courtIds.map((c) => tx.get(`venues/${venueId}/courts/${c}`)),
  );
  const courtNames = new Map(courtIds.map((c, i) => {
    const doc = courts[i];
    const name = asString(doc?.name) ?? `Sân ${c}`;
    const surface = asString(doc?.surfaceType);
    return [c, surface ? `${name} · ${surface}` : name];
  }));

  const slots = parsed
    .map((p) => {
      const price = slotPrice(rules, p);
      if (price == null) {
        throw new HttpError(
          409,
          "NO_PRICE",
          "Khung giờ không có trong bảng giá của cơ sở",
        );
      }
      return { ...p, courtName: courtNames.get(p.courtId)!, price };
    })
    .sort((a, b) =>
      a.startAt.getTime() - b.startAt.getTime() ||
      a.courtId.localeCompare(b.courtId)
    );

  return {
    venue,
    ownerId: asString(venue.ownerId) ?? "",
    venueSnapshot: {
      name: asString(venue.name) ?? `Cơ sở ${venueId}`,
      address: asString(venue.address) ?? "",
      phone: asString(venue.phone),
      photoUrl: asString(venue.photoUrl) ??
        asString((venue.photoUrls as unknown[] | undefined)?.[0]),
    },
    slots,
  };
}

export interface VoucherOutcome {
  voucher: LoadedVoucher | null;
  writes: Write[];
}

/**
 * Kiểm voucher cho đơn có khóa `key`. Lượt đang giữ tạm cho đơn này (`RESERVED`, khi đổi từ chuyển khoản) được
 * chốt thành `USED` mà không đếm lại (khi `status = USED`); mã khác mã đang giữ thì nhả lượt cũ. Mã mới thì kiểm
 * đủ điều kiện và giữ ở `status`: `USED` khi đơn chốt ngay, `RESERVED` khi chờ chuyển khoản.
 */
export async function evaluateVoucher(
  tx: FirestoreTransaction,
  uid: string,
  venueId: string,
  key: string,
  code: string | null,
  held: VoucherRef | null,
  subtotal: number,
  now: Date,
  status: "USED" | "RESERVED" = "USED",
): Promise<VoucherOutcome> {
  const writes: Write[] = [];
  if (held && held.code !== code) {
    writes.push(
      ...releaseWrites(held, key, await loadRedemption(tx, held, key)),
    );
  }
  if (!code) return { voucher: null, writes };

  const voucher = await loadVoucher(tx, venueId, code);
  const redemption = voucher ? await loadRedemption(tx, voucher, key) : null;
  if (voucher && redemption?.userId === uid) {
    // Lượt đã giữ cho đơn này: chốt ngay (`USED`) hoặc giữ nguyên khi vẫn đang chờ chuyển khoản.
    if (status === "USED") writes.push(...commitWrites(voucher, key, redemption));
    return { voucher, writes };
  }
  const usage = voucher ? await loadUserUsageCount(tx, voucher, uid) : 0;
  const check = checkVoucher(voucher, usage, subtotal, now);
  if (!check.ok) {
    const extra: Doc = { reason: check.reason };
    if (check.reason === "MIN_ORDER") extra.minOrderTotal = check.minOrderTotal;
    throw new HttpError(
      422,
      "VOUCHER_INVALID",
      "Mã khuyến mãi không dùng được",
      extra,
    );
  }
  writes.push(...reserveWrites(voucher!, uid, key, status));
  return { voucher, writes };
}

export function priceBreakdown(
  subtotal: number,
  voucher: LoadedVoucher | null,
  expectedTotal: number,
): PaymentBreakdown {
  const breakdown = paymentBreakdown(subtotal, voucher);
  if (breakdown.total !== expectedTotal) {
    throw new HttpError(409, "PRICE_CHANGED", "Giá đơn đã thay đổi", {
      breakdown,
    });
  }
  return breakdown;
}

export function heldVoucher(doc: Doc | null, venueId: string): VoucherRef | null {
  const code = asString(doc?.voucherCode);
  const scope = doc?.voucherScope === "VENUE" || doc?.voucherScope === "SYSTEM"
    ? doc.voucherScope as VoucherScope
    : null;
  return code && scope ? { scope, venueId, code } : null;
}

export function itemsOf(slots: PricedSlot[]): Doc[] {
  return slots.map((s) => ({
    slotId: s.slotId,
    courtId: s.courtId,
    courtName: s.courtName,
    startAt: s.startAt,
    endAt: s.endAt,
    price: s.price,
  }));
}

export function paymentFields(
  subtotal: number,
  discount: number,
  total: number,
  voucher: LoadedVoucher | null,
): Doc {
  return {
    subtotal,
    discount,
    total,
    voucherCode: voucher?.code ?? null,
    voucherScope: voucher?.scope ?? null,
    paymentMethod: "AT_VENUE",
    paymentStatus: total === 0 ? "PAID" : "UNPAID",
    amountDue: 0,
    depositAmount: 0,
    paidAmount: 0,
    remainingAmount: total,
  };
}

export function approvalDeadline(now: Date, startAt: Date): Date {
  return new Date(
    Math.min(now.getTime() + APPROVAL_WINDOW_MS, startAt.getTime()),
  );
}

export function notificationWrite(
  ownerId: string,
  key: string,
  venueName: string,
  recurring: boolean,
  now: Date,
): Write[] {
  if (!ownerId) return [];
  return [createWrite(`users/${ownerId}/notifications/booking_new_${key}`, {
    type: "BOOKING_NEW",
    title: "Có đơn đặt sân mới",
    body: recurring
      ? `Đơn đặt cố định mới tại ${venueName}, cần duyệt trong 60 phút`
      : `Đơn đặt sân mới tại ${venueName}, cần duyệt trong 60 phút`,
    data: recurring ? { recurringGroupId: key } : { bookingId: key },
    isRead: false,
    createdAt: now,
  })];
}

export function deactivateCode(doc: Doc | null): Write[] {
  const code = asString(doc?.paymentCode);
  return code ? [updateWrite(`paymentCodes/${code}`, { active: false })] : [];
}

// ---------- Hóa đơn ----------

const APPROVAL_WINDOW_MS = 60 * 60_000;
const VN_OFFSET_MS = 7 * 60 * 60_000;
const pad = (n: number) => String(n).padStart(2, "0");

function vnParts(d: Date) {
  const t = new Date(d.getTime() + VN_OFFSET_MS);
  return {
    date: `${pad(t.getUTCDate())}/${pad(t.getUTCMonth() + 1)}/${t.getUTCFullYear()}`,
    time: `${pad(t.getUTCHours())}:${pad(t.getUTCMinutes())}`,
  };
}

/** Gộp các slot liền nhau cùng sân thành một dòng: "Sân 1 · Thảm PVC · 20/11/2026 18:00–19:00". */
export function receiptLines(items: Doc[]): string[] {
  const sorted = items
    .map((i) => ({
      name: String(i.courtName ?? "Sân"),
      start: asDate(i.startAt),
      end: asDate(i.endAt),
    }))
    .filter((i): i is { name: string; start: Date; end: Date } => i.start != null && i.end != null)
    .sort((a, b) => a.name.localeCompare(b.name) || a.start.getTime() - b.start.getTime());
  const groups: { name: string; start: Date; end: Date }[] = [];
  for (const i of sorted) {
    const last = groups[groups.length - 1];
    if (
      last && last.name === i.name && last.end.getTime() === i.start.getTime()
    ) last.end = i.end;
    else groups.push({ ...i });
  }
  return groups
    .sort((a, b) => a.start.getTime() - b.start.getTime())
    .map((g) => `${g.name} · ${vnParts(g.start).date} ${vnParts(g.start).time}–${vnParts(g.end).time}`);
}

export function receiptOf(
  id: string,
  groupId: string | null,
  payment: Doc,
  venueName: string,
  items: Doc[],
): Receipt {
  return {
    bookingId: id,
    recurringGroupId: groupId,
    venueName,
    items: receiptLines(items),
    option: String(payment.paymentMethod ?? "AT_VENUE"),
    status: String(payment.paymentStatus ?? "UNPAID"),
    subtotal: asNumber(payment.subtotal),
    discount: asNumber(payment.discount),
    total: asNumber(payment.total),
    paidAmount: asNumber(payment.paidAmount),
    remainingAmount: asNumber(payment.remainingAmount),
    voucherCode: asString(payment.voucherCode),
  };
}
