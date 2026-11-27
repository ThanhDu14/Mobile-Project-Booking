package com.example.booking_app.feature.payment.domain.model

/** Voucher nằm ở `venues/{venueId}/vouchers` (VENUE) hay `systemVouchers` (SYSTEM). */
enum class VoucherScope { VENUE, SYSTEM }

enum class VoucherType { PERCENT, FIXED }

/** Voucher đọc từ Firestore (data-model §1.6). `value` là % khi PERCENT, VND khi FIXED. */
data class Voucher(
    val code: String,
    val scope: VoucherScope,
    val type: VoucherType,
    val value: Long,
    val maxDiscount: Long? = null,
    val minOrderTotal: Long = 0L,
    val validFromMillis: Long? = null,
    val validToMillis: Long? = null,
    val usageLimit: Long? = null,
    val usedCount: Long = 0L,
    val perUserLimit: Long? = null,
    val isActive: Boolean = true,
)

/** Voucher đã áp vào đơn cùng số tiền giảm. */
data class AppliedVoucher(val code: String, val scope: VoucherScope, val discount: Long)

/** Kết quả kiểm voucher ở app (server kiểm lại khi chốt). */
sealed interface VoucherCheck {
    data class Valid(val voucher: AppliedVoucher) : VoucherCheck
    data object NotFound : VoucherCheck
    data object Expired : VoucherCheck
    data class MinOrderNotMet(val minOrderTotal: Long) : VoucherCheck
    data object OutOfUses : VoucherCheck
    data object PerUserLimitReached : VoucherCheck
}
