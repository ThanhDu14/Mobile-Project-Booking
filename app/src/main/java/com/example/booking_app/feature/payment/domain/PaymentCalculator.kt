package com.example.booking_app.feature.payment.domain

import com.example.booking_app.feature.payment.domain.model.PaymentBreakdown
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherType

/**
 * Công thức tiền spec 050 (research R7), dùng để xem trước ở app. Server tính lại bằng
 * `supabase/functions/_shared/payment-calc.ts`; hai bản chạy chung bảng ca test
 * `specs/050-payment-voucher/contracts/payment-calc-cases.json`, sửa bên này thì sửa cả bên kia.
 */
object PaymentCalculator {

    private const val DEPOSIT_PERCENT = 30L
    private const val ROUND_TO = 1_000L

    /** Số tiền giảm, không vượt quá `subtotal`. PERCENT cắt phần lẻ rồi mới áp trần. */
    fun discount(subtotal: Long, type: VoucherType, value: Long, maxDiscount: Long?): Long {
        if (subtotal <= 0L || value <= 0L) return 0L
        val raw = when (type) {
            VoucherType.PERCENT -> (subtotal * value / 100).let { if (maxDiscount != null) minOf(it, maxDiscount) else it }
            VoucherType.FIXED -> value
        }
        return raw.coerceIn(0L, subtotal)
    }

    /** Tiền cọc 30% làm tròn tới nghìn (nửa nghìn làm tròn lên); ra 0 thì cọc cả `total`. */
    fun deposit(total: Long): Long {
        if (total <= 0L) return 0L
        val raw = total * DEPOSIT_PERCENT / 100
        val rounded = (raw + ROUND_TO / 2) / ROUND_TO * ROUND_TO
        return if (rounded == 0L) total else minOf(rounded, total)
    }

    fun breakdown(subtotal: Long, voucher: Voucher?): PaymentBreakdown =
        breakdown(subtotal, voucher?.let { discount(subtotal, it.type, it.value, it.maxDiscount) } ?: 0L)

    /** Bảng tiền khi đã có số tiền giảm (`AppliedVoucher.discount`, tính bằng [discount]). */
    fun breakdown(subtotal: Long, discount: Long): PaymentBreakdown {
        val total = (subtotal - discount).coerceAtLeast(0L)
        return PaymentBreakdown(
            subtotal = subtotal,
            discount = discount,
            total = total,
            depositAmount = deposit(total),
            isFree = total == 0L,
        )
    }
}
