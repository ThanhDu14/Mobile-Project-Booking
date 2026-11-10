package com.example.booking_app.feature.booking_management.domain

/** A local estimate; the server makes the final cancellation decision. Times are epoch milliseconds. */
object RefundPolicy {
    enum class Status { PENDING, CONFIRMED, HOLD, COMPLETED, CANCELLED, REJECTED, EXPIRED, NO_SHOW }
    enum class PaymentMethod { DEPOSIT, MOCK_WALLET, AT_VENUE }

    data class Result(val refundAmount: Long, val penaltyAmount: Long)

    /** Returns null when cancellation is not allowed. paidAmount is the amount after voucher. */
    fun calculate(
        status: Status,
        paymentMethod: PaymentMethod,
        paidAmount: Long,
        nowMillis: Long,
        startMillis: Long,
        checkedIn: Boolean
    ): Result? {
        require(paidAmount >= 0)
        require(paymentMethod != PaymentMethod.AT_VENUE || paidAmount == 0L)
        if (checkedIn || nowMillis >= startMillis ||
            (status != Status.PENDING && status != Status.CONFIRMED)
        ) return null

        val early = nowMillis <= Long.MAX_VALUE - EARLY_MILLIS &&
            startMillis >= nowMillis + EARLY_MILLIS
        val penalty = if (status == Status.PENDING || early) {
            0L
        } else when (paymentMethod) {
            PaymentMethod.DEPOSIT -> paidAmount
            PaymentMethod.MOCK_WALLET -> {
                // floor(30% / 1000) * 1000, without multiplying paidAmount by 30.
                (paidAmount / 10_000L * 3L + paidAmount % 10_000L * 3L / 10_000L) * 1_000L
            }
            PaymentMethod.AT_VENUE -> 0L
        }
        return Result(refundAmount = paidAmount - penalty, penaltyAmount = penalty)
    }

    private const val EARLY_MILLIS = 240L * 60L * 1_000L
}
