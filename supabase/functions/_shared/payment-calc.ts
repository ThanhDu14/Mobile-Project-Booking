// Công thức tiền spec 050 (research R7). Server luôn tính lại bằng file này khi chốt đơn, không tin số app gửi.
// Bản Kotlin: app/.../feature/payment/domain/PaymentCalculator.kt. Hai bản chạy chung bảng ca test
// specs/050-payment-voucher/contracts/payment-calc-cases.json; sửa bên này thì sửa cả bên kia.

export type PaymentOption = "AT_VENUE" | "TRANSFER_DEPOSIT" | "TRANSFER_FULL";
export const PAYMENT_OPTIONS: readonly PaymentOption[] = ["AT_VENUE", "TRANSFER_DEPOSIT", "TRANSFER_FULL"];

/** Phần của voucher mà công thức cần. `value` là % khi PERCENT, VND khi FIXED. */
export interface VoucherTerms {
  type: "PERCENT" | "FIXED";
  value: number;
  maxDiscount?: number | null;
}

export interface PaymentBreakdown {
  subtotal: number;
  discount: number;
  total: number;
  depositAmount: number;
  remainingAfterDeposit: number;
  isFree: boolean;
}

const DEPOSIT_PERCENT = 30;
const ROUND_TO = 1000;

/** Số tiền giảm, không vượt quá `subtotal`. PERCENT cắt phần lẻ rồi mới áp trần. */
export function computeDiscount(subtotal: number, voucher: VoucherTerms | null | undefined): number {
  if (!voucher || subtotal <= 0 || voucher.value <= 0) return 0;
  let raw: number;
  if (voucher.type === "PERCENT") {
    raw = Math.floor((subtotal * voucher.value) / 100);
    if (voucher.maxDiscount != null) raw = Math.min(raw, voucher.maxDiscount);
  } else {
    raw = voucher.value;
  }
  return Math.min(Math.max(raw, 0), subtotal);
}

/** Tiền cọc 30% làm tròn tới nghìn (nửa nghìn làm tròn lên); ra 0 thì cọc cả `total`. */
export function computeDeposit(total: number): number {
  if (total <= 0) return 0;
  const raw = Math.floor((total * DEPOSIT_PERCENT) / 100);
  const rounded = Math.floor((raw + ROUND_TO / 2) / ROUND_TO) * ROUND_TO;
  return rounded === 0 ? total : Math.min(rounded, total);
}

export function paymentBreakdown(subtotal: number, voucher: VoucherTerms | null | undefined): PaymentBreakdown {
  const discount = computeDiscount(subtotal, voucher);
  const total = Math.max(subtotal - discount, 0);
  const depositAmount = computeDeposit(total);
  return { subtotal, discount, total, depositAmount, remainingAfterDeposit: total - depositAmount, isFree: total === 0 };
}

/** Số người chơi phải chuyển: 0 khi trả tại sân, tiền cọc hoặc cả `total` khi chuyển khoản. */
export function amountDue(breakdown: PaymentBreakdown, option: PaymentOption): number {
  switch (option) {
    case "AT_VENUE":
      return 0;
    case "TRANSFER_DEPOSIT":
      return breakdown.depositAmount;
    case "TRANSFER_FULL":
      return breakdown.total;
  }
}
