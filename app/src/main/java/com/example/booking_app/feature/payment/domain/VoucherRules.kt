package com.example.booking_app.feature.payment.domain

import com.example.booking_app.feature.payment.domain.model.AppliedVoucher
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import com.example.booking_app.feature.payment.domain.model.VoucherScope

/**
 * Luật voucher thuần (không I/O), cùng thứ tự với `checkVoucher` ở `supabase/functions/_shared/payment-voucher.ts`:
 * không có → hết hạn/tắt → chưa đủ đơn tối thiểu → hết lượt. `perUserLimit` chỉ server kiểm được
 * (app không đọc `userUsage`).
 */
object VoucherRules {

    fun normalize(code: String): String = code.trim().uppercase()

    fun check(voucher: Voucher?, subtotal: Long, nowMillis: Long): VoucherCheck {
        voucher ?: return VoucherCheck.NotFound
        if (!isLive(voucher, nowMillis)) return VoucherCheck.Expired
        if (subtotal < voucher.minOrderTotal) return VoucherCheck.MinOrderNotMet(voucher.minOrderTotal)
        val limit = voucher.usageLimit
        if (limit != null && voucher.usedCount >= limit) return VoucherCheck.OutOfUses
        val discount = PaymentCalculator.discount(subtotal, voucher.type, voucher.value, voucher.maxDiscount)
        return VoucherCheck.Valid(AppliedVoucher(voucher.code, voucher.scope, discount))
    }

    /** Đang bật và `now` nằm trong `validFrom..validTo` (tính cả hai mốc). */
    fun isLive(voucher: Voucher, nowMillis: Long): Boolean =
        voucher.isActive &&
            (voucher.validFromMillis == null || nowMillis >= voucher.validFromMillis) &&
            (voucher.validToMillis == null || nowMillis <= voucher.validToMillis)

    /** Trong các voucher cùng mã, voucher cơ sở thắng voucher hệ thống. */
    fun pickByCode(code: String, candidates: List<Voucher>): Voucher? {
        val matches = candidates.filter { it.code == code }
        return matches.firstOrNull { it.scope == VoucherScope.VENUE } ?: matches.firstOrNull()
    }

    /** Gộp hai nguồn cho danh sách chọn: voucher cơ sở đứng trước, trùng mã thì bỏ bản hệ thống. */
    fun mergeAvailable(venue: List<Voucher>, system: List<Voucher>): List<Voucher> {
        val venueCodes = venue.mapTo(HashSet()) { it.code }
        return venue + system.filter { it.code !in venueCodes }
    }
}
