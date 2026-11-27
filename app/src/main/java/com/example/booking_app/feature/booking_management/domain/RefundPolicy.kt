package com.example.booking_app.feature.booking_management.domain

/** A local estimate; the server makes the final cancellation decision. Times are epoch milliseconds. */
object RefundPolicy {
    enum class Status { PENDING, CONFIRMED, HOLD, COMPLETED, CANCELLED, REJECTED, EXPIRED, NO_SHOW }
    /** Cách trả của spec 050. `DEPOSIT`/`MOCK_WALLET` là tên cũ trước khi bỏ ví, đọc qua [fromStored]. */
    enum class PaymentMethod {
        AT_VENUE, TRANSFER_DEPOSIT, TRANSFER_FULL;

        companion object {
            fun fromStored(raw: String?): PaymentMethod = when (raw) {
                "TRANSFER_DEPOSIT", "DEPOSIT" -> TRANSFER_DEPOSIT
                "TRANSFER_FULL", "MOCK_WALLET" -> TRANSFER_FULL
                "AT_VENUE" -> AT_VENUE
                else -> throw IllegalArgumentException("paymentMethod không hợp lệ: $raw")
            }
        }
    }

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
            PaymentMethod.TRANSFER_DEPOSIT -> paidAmount
            PaymentMethod.TRANSFER_FULL -> {
                // floor(30% / 1000) * 1000, without multiplying paidAmount by 30.
                (paidAmount / 10_000L * 3L + paidAmount % 10_000L * 3L / 10_000L) * 1_000L
            }
            PaymentMethod.AT_VENUE -> 0L
        }
        return Result(refundAmount = paidAmount - penalty, penaltyAmount = penalty)
    }

    private const val EARLY_MILLIS = 240L * 60L * 1_000L
}
