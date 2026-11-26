// Khoản cần hoàn tiền thủ công (spec 050 data-model §1.5, research R9). Dùng chung cho sepay-webhook
// (tiền về muộn, sai số tiền), 060 (người chơi hủy) và 112 (chủ sân từ chối/hủy, quá hạn duyệt).
// Không còn cộng ví: refund nằm ở `PENDING` tới khi chủ sân bấm "Đã hoàn tiền" (owner-mark-refunded).
import { createWrite, type Write } from "./firestore.ts";

export const REFUND_REASONS = ["REJECTED", "APPROVAL_TIMEOUT", "CANCELLED", "LATE_TRANSFER", "AMOUNT_MISMATCH"] as const;
export type RefundReason = typeof REFUND_REASONS[number];

export interface RefundInput {
  id: string;
  bookingId?: string | null;
  recurringGroupId?: string | null;
  venueId: string;
  userId: string;
  amount: number;
  penaltyAmount: number;
  reason: RefundReason;
  sepayTransactionId?: number | null;
}

/**
 * `refund_sepay_{id}` khi khoản hoàn sinh từ một giao dịch SePay (một đơn có thể có nhiều khoản như vậy),
 * không thì `refund_{bookingId}`.
 */
export function refundIdFor(src: { bookingId?: string | null; sepayTransactionId?: number | null }): string {
  if (src.sepayTransactionId != null) return `refund_sepay_${src.sepayTransactionId}`;
  if (src.bookingId) return `refund_${src.bookingId}`;
  throw new Error("refundIdFor: cần bookingId hoặc sepayTransactionId");
}

/** Tạo `refunds/{id}` trạng thái `PENDING`; đã có thì commit báo `ALREADY_EXISTS` (gọi lại an toàn). */
export function refundCreateWrite(input: RefundInput): Write {
  if (!REFUND_REASONS.includes(input.reason)) throw new Error(`refund: lý do không hợp lệ ${input.reason}`);
  if (!isMoney(input.amount) || !isMoney(input.penaltyAmount)) throw new Error("refund: số tiền phải là số nguyên ≥ 0");
  if (!input.id || !input.venueId || !input.userId) throw new Error("refund: thiếu id, venueId hoặc userId");
  if (!input.bookingId && !input.recurringGroupId) throw new Error("refund: cần bookingId hoặc recurringGroupId");
  return createWrite(
    `refunds/${input.id}`,
    {
      bookingId: input.bookingId ?? null,
      recurringGroupId: input.recurringGroupId ?? null,
      venueId: input.venueId,
      userId: input.userId,
      amount: input.amount,
      penaltyAmount: input.penaltyAmount,
      reason: input.reason,
      status: "PENDING",
      sepayTransactionId: input.sepayTransactionId ?? null,
      doneAt: null,
      remindedAt: null,
      reportedAt: null,
    },
    ["createdAt"],
  );
}

function isMoney(n: number): boolean {
  return Number.isSafeInteger(n) && n >= 0;
}
