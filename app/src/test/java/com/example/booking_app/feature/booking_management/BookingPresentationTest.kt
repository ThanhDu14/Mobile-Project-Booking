package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.BookingFinance
import com.example.booking_app.feature.booking_management.domain.PaymentStatus
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import com.example.booking_app.feature.booking_management.domain.VenueSnapshot
import com.example.booking_app.feature.booking_management.ui.BookingPhase
import com.example.booking_app.feature.booking_management.ui.BookingPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingPresentationTest {
    private val hour = 3_600_000L
    private val now = 1_700_000_000_000L
    private val time: (Long) -> String = { "t${(it - now) / 60_000}" }

    private fun booking(
        status: RefundPolicy.Status,
        start: Long = now + 5 * hour,
        end: Long = start + hour,
        deadline: Long? = null,
        checkedIn: Long? = null,
        method: RefundPolicy.PaymentMethod = RefundPolicy.PaymentMethod.AT_VENUE,
        paid: Long = 0,
        reason: String? = null,
    ) = BookingDetail(
        bookingId = "b1", userId = "u1", venueId = "v1",
        venueSnapshot = VenueSnapshot("Sân A", "1 Đường A", null), items = emptyList(),
        startAtMillis = start, endAtMillis = end, status = status,
        finance = BookingFinance(60_000, 0, 60_000, if (method == RefundPolicy.PaymentMethod.DEPOSIT) paid else 0, paid,
            60_000 - paid, method, if (paid > 0) PaymentStatus.PAID else PaymentStatus.UNPAID),
        checkedInAtMillis = checkedIn, approvalDeadlineMillis = deadline, cancelReason = reason,
        createdAtMillis = now - hour, updatedAtMillis = now - hour,
    )

    @Test
    fun pendingShowsTheDeadlineAndBecomesOverdueAfterIt() {
        val waiting = booking(RefundPolicy.Status.PENDING, deadline = now + 25 * 60_000)
        val phase = BookingPresentation.phase(waiting, now)
        assertEquals(BookingPhase.PENDING, phase)
        assertEquals("Chủ sân duyệt trước t25 (còn 25 phút).", BookingPresentation.note(waiting, phase, now, time))

        // Đơn PENDING thật trên production: hạn duyệt đã qua 5 ngày nhưng job chưa chạy.
        val overdue = booking(RefundPolicy.Status.PENDING, deadline = now - 5 * 24 * hour)
        assertEquals(BookingPhase.PENDING_OVERDUE, BookingPresentation.phase(overdue, now))
        assertEquals("Quá hạn duyệt", BookingPresentation.badge(BookingPhase.PENDING_OVERDUE))
    }

    @Test
    fun confirmedAfterItsEndIsNotUpcomingAndHasNoQr() {
        val upcoming = booking(RefundPolicy.Status.CONFIRMED)
        assertTrue(BookingPresentation.showQr(BookingPresentation.phase(upcoming, now)))

        // Đơn 09/11 vẫn CONFIRMED vì complete-bookings chưa chạy.
        val ended = booking(RefundPolicy.Status.CONFIRMED, start = now - 9 * 24 * hour)
        val phase = BookingPresentation.phase(ended, now)
        assertEquals(BookingPhase.ENDED_UNSETTLED, phase)
        assertFalse(BookingPresentation.showQr(phase))
        assertEquals("Đã qua giờ chơi", BookingPresentation.badge(phase))

        val playing = booking(RefundPolicy.Status.CONFIRMED, start = now - 10 * 60_000, checkedIn = now - 20 * 60_000)
        assertEquals(BookingPhase.CHECKED_IN, BookingPresentation.phase(playing, now))
    }

    @Test
    fun paidRowIsHiddenWhenNothingWasPaid() {
        assertNull(BookingPresentation.paidLabel(booking(RefundPolicy.Status.PENDING).finance))
        assertEquals(
            "Đã đặt cọc (giả lập)",
            BookingPresentation.paidLabel(booking(RefundPolicy.Status.CONFIRMED, method = RefundPolicy.PaymentMethod.DEPOSIT, paid = 18_000).finance),
        )
        assertEquals(
            "Đã thanh toán (ví giả lập)",
            BookingPresentation.paidLabel(booking(RefundPolicy.Status.CONFIRMED, method = RefundPolicy.PaymentMethod.MOCK_WALLET, paid = 60_000).finance),
        )
    }

    @Test
    fun cancellationPolicyMatchesThePaymentMethod() {
        val atVenue = BookingPresentation.cancellationPolicy(booking(RefundPolicy.Status.CONFIRMED), time)
        assertFalse("đơn trả tại sân không có tiền cọc", atVenue.contains("cọc"))

        val deposit = BookingPresentation.cancellationPolicy(
            booking(RefundPolicy.Status.CONFIRMED, method = RefundPolicy.PaymentMethod.DEPOSIT, paid = 18_000), time,
        )
        // Mốc hủy sớm = giờ chơi (sau 5 giờ) trừ 4 giờ.
        assertTrue(deposit.contains("hủy trước t60 ") && deposit.contains("mất tiền cọc"))

        val wallet = BookingPresentation.cancellationPolicy(
            booking(RefundPolicy.Status.CONFIRMED, method = RefundPolicy.PaymentMethod.MOCK_WALLET, paid = 60_000), time,
        )
        assertTrue(wallet.contains("phạt 30%, hoàn 70%"))

        // Đơn chờ duyệt luôn được hoàn 100% (FR-017).
        assertTrue(BookingPresentation.cancellationPolicy(booking(RefundPolicy.Status.PENDING), time).contains("hoàn 100%"))
    }

    @Test
    fun rejectedAndCancelledShowTheirReason() {
        val rejected = booking(RefundPolicy.Status.REJECTED, reason = "Sân bảo trì")
        assertEquals("Sân bảo trì", BookingPresentation.note(rejected, BookingPresentation.phase(rejected, now), now, time))
        val cancelled = booking(RefundPolicy.Status.CANCELLED, reason = " ")
        assertNull(BookingPresentation.note(cancelled, BookingPresentation.phase(cancelled, now), now, time))
    }

    @Test
    fun closedBookingsDoNotAskToPayAtTheVenue() {
        // Đơn #SN-333F bị từ chối nhưng vẫn hiện "Còn trả tại sân 60.000 ₫".
        // Đơn #SN-28D2 vắng mặt từng hiện "Còn trả tại sân 225.000 ₫".
        val closed = listOf(
            RefundPolicy.Status.CANCELLED, RefundPolicy.Status.REJECTED, RefundPolicy.Status.EXPIRED, RefundPolicy.Status.NO_SHOW,
        )
        for (status in closed) {
            assertFalse(BookingPresentation.showRemaining(BookingPresentation.phase(booking(status), now)))
        }
        for (status in listOf(RefundPolicy.Status.PENDING, RefundPolicy.Status.CONFIRMED, RefundPolicy.Status.COMPLETED)) {
            assertTrue(BookingPresentation.showRemaining(BookingPresentation.phase(booking(status), now)))
        }
    }
}
