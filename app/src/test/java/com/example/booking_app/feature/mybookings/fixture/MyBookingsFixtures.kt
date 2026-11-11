package com.example.booking_app.feature.mybookings.fixture

/** Synthetic scenarios only; these types are not Firestore or payment/voucher contracts. */
object MyBookingsFixtures {
    data class Booking(
        val id: String,
        val status: String,
        val createdAt: String,
        val startAt: String,
        val updatedAt: String,
        val holdExpiresAt: String? = null,
        val groupLabel: String? = null,
        val money: Money = Money(100_000L, 0L, 0L, 100_000L)
    )

    // Amounts are synthetic VND, not a representation of persisted payment data.
    data class Money(
        val subtotal: Long,
        val discount: Long,
        val paid: Long,
        val dueAtVenue: Long
    )

    data class PaymentScenario(val method: String, val status: String, val money: Money)
    data class VoucherScenario(val label: String, val discount: Long, val sharedByGroup: Boolean)
    data class WalletScenario(val balanceBefore: Long, val paid: Long, val balanceAfter: Long)
    data class GroupScenario(val label: String, val sessions: List<Booking>, val voucher: VoucherScenario)

    // Two HOLD entries share the sorting key so tests can exercise an ID tie-breaker.
    val bookings: List<Booking> = listOf(
        booking("hold-a", "HOLD", holdExpiresAt = "2026-11-08T12:05:00Z"),
        booking("hold-b", "HOLD", holdExpiresAt = "2026-11-08T12:05:00Z"),
        booking("hold-expired", "HOLD", holdExpiresAt = "2026-11-08T11:55:00Z"),
        booking("pending", "PENDING"),
        booking("confirmed", "CONFIRMED"),
        booking("completed", "COMPLETED"),
        booking("no-show", "NO_SHOW"),
        booking("cancelled", "CANCELLED"),
        booking("rejected", "REJECTED"),
        booking("expired", "EXPIRED")
    )
    const val referenceTime = "2026-11-08T12:00:00Z"

    val payments = listOf(
        PaymentScenario("AT_VENUE", "UNPAID", Money(100_000L, 0L, 0L, 100_000L)),
        PaymentScenario("DEPOSIT", "DEPOSITED", Money(100_000L, 0L, 40_000L, 60_000L)),
        PaymentScenario("MOCK_WALLET", "PAID", Money(131_111L, 20_000L, 111_111L, 0L)),
        PaymentScenario("MOCK_WALLET", "REFUNDED", Money(40_000L, 0L, 40_000L, 0L))
    )
    val wallet = WalletScenario(balanceBefore = 200_000L, paid = 111_111L, balanceAfter = 88_889L)
    val group = GroupScenario(
        label = "test-group",
        sessions = listOf(
            booking("group-session-1", "CONFIRMED", groupLabel = "test-group", money = Money(100_000L, 10_000L, 90_000L, 0L)),
            booking("group-session-2", "CONFIRMED", groupLabel = "test-group", money = Money(200_000L, 20_000L, 180_000L, 0L))
        ),
        voucher = VoucherScenario("test-shared-voucher", discount = 30_000L, sharedByGroup = true)
    )
    val singleVoucher = VoucherScenario("test-single-voucher", discount = 20_000L, sharedByGroup = false)

    private fun booking(
        id: String,
        status: String,
        holdExpiresAt: String? = null,
        groupLabel: String? = null,
        money: Money = Money(100_000L, 0L, 0L, 100_000L)
    ) = Booking(
        id = id,
        status = status,
        createdAt = "2026-11-08T12:00:00Z",
        startAt = "2026-11-09T12:00:00Z",
        updatedAt = "2026-11-08T12:00:00Z",
        holdExpiresAt = holdExpiresAt,
        groupLabel = groupLabel,
        money = money
    )
}
