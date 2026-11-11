package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import com.example.booking_app.feature.booking_management.domain.RefundPolicy.PaymentMethod
import com.example.booking_app.feature.booking_management.domain.RefundPolicy.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefundPolicyTest {
    private fun calculate(
        status: Status = Status.CONFIRMED,
        method: PaymentMethod = PaymentMethod.MOCK_WALLET,
        paid: Long = 111_111L,
        now: Long = 0L,
        start: Long = 1L,
        checkedIn: Boolean = false
    ) = RefundPolicy.calculate(status, method, paid, now, start, checkedIn)

    @Test fun pendingAlwaysRefundsPaidBeforeStart() {
        for (method in listOf(PaymentMethod.DEPOSIT, PaymentMethod.MOCK_WALLET)) {
            assertEquals(RefundPolicy.Result(111_111L, 0L), calculate(Status.PENDING, method))
        }
        assertEquals(RefundPolicy.Result(0, 0), calculate(Status.PENDING, PaymentMethod.AT_VENUE, 0))
    }

    @Test fun confirmedBoundaryIncludesExactly240Minutes() {
        val boundary = 240L * 60 * 1000
        assertEquals(RefundPolicy.Result(111_111, 0), calculate(start = boundary))
        assertEquals(RefundPolicy.Result(78_111, 33_000), calculate(start = boundary - 1))
        assertEquals(RefundPolicy.Result(111_111, 0), calculate(start = boundary + 1))
    }

    @Test fun latePaymentMethods() {
        assertEquals(RefundPolicy.Result(0, 111_111), calculate(method = PaymentMethod.DEPOSIT))
        assertEquals(RefundPolicy.Result(0, 0), calculate(method = PaymentMethod.AT_VENUE, paid = 0))
        assertEquals(RefundPolicy.Result(140_000, 60_000), calculate(paid = 200_000))
        assertEquals(RefundPolicy.Result(999, 0), calculate(paid = 999))
    }

    @Test fun cancellationRequiresEligibleStatusBeforeStartAndNoCheckIn() {
        for (status in Status.values().filter { it != Status.PENDING && it != Status.CONFIRMED }) {
            assertNull(calculate(status = status))
        }
        for (status in listOf(Status.PENDING, Status.CONFIRMED)) {
            assertNull(calculate(status = status, now = 1, start = 1))
            assertNull(calculate(status = status, now = 2, start = 1))
            assertNull(calculate(status = status, checkedIn = true))
        }
    }

    @Test fun largeAmountsAndTimesDoNotOverflow() {
        val result = calculate(paid = Long.MAX_VALUE)!!
        assertEquals(2_767_011_611_056_432_000L, result.penaltyAmount)
        assertEquals(Long.MAX_VALUE - result.penaltyAmount, result.refundAmount)
        assertEquals(RefundPolicy.Result(111_111, 0), calculate(now = Long.MIN_VALUE, start = Long.MAX_VALUE))
        assertEquals(RefundPolicy.Result(78_111, 33_000), calculate(now = Long.MAX_VALUE - 1, start = Long.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePaidAmountIsRejected() { calculate(paid = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun atVenueCannotHavePrepayment() { calculate(method = PaymentMethod.AT_VENUE, paid = 1) }
}
