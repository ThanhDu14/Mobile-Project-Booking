package com.example.booking_app.feature.payment.domain.model

/** Bảng tiền của một đơn (research R7); tiền là VND nguyên. Tạo bằng `PaymentCalculator.breakdown`. */
data class PaymentBreakdown(
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    /** 30% của `total` làm tròn tới nghìn; bằng `total` khi làm tròn ra 0. */
    val depositAmount: Long,
    /** `total == 0`: không chọn cách trả, server ghi `paymentStatus = PAID`. */
    val isFree: Boolean,
) {
    val remainingAfterDeposit: Long get() = total - depositAmount

    fun amountDue(option: PaymentOption): Long = when (option) {
        PaymentOption.AT_VENUE -> 0L
        PaymentOption.TRANSFER_DEPOSIT -> depositAmount
        PaymentOption.TRANSFER_FULL -> total
    }
}
