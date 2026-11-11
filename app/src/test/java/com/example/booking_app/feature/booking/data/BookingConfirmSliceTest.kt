package com.example.booking_app.feature.booking.data

import com.example.booking_app.feature.booking.data.mapper.BookingDocumentBuilder
import com.example.booking_app.feature.booking.domain.model.ConfirmBookingResult
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.example.booking_app.feature.booking.domain.usecase.ConfirmHoldToBookingUseCase
import com.example.booking_app.feature.booking.ui.HoldToPaymentArgs
import com.example.booking_app.feature.booking_management.data.BookingManagementMapper
import com.example.booking_app.feature.booking_management.domain.PaymentStatus
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BookingConfirmSliceTest {

    private val zoneVietnam = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun sampleGiaHanArgs(): HoldToPaymentArgs {
        return HoldToPaymentArgs(
            bookingId = "booking-gia-han-req-123",
            venueId = "venue-gia-han-01",
            venueName = "CLB Cầu Lông Gia Hân",
            venueAddress = "123 Đào Duy Anh, Phường 9",
            districtName = "Phú Nhuận",
            photoUrl = "https://example.com/photo.jpg",
            isRecurring = false,
            totalSessions = 1,
            subtotalAmount = 180_000L,
            slotIds = arrayOf(
                "court4_20261109_2000",
                "court4_20261109_2030",
                "court4_20261109_2100",
                "court4_20261109_2130",
                "court4_20261109_2200",
                "court4_20261109_2230"
            ),
            holdExpiresAtMillis = ZonedDateTime.of(2026, 11, 9, 16, 50, 0, 0, zoneVietnam).toInstant().toEpochMilli(),
            courtDetails = arrayOf(
                "S1|Sân 4|PU|T2, 09/11|20:00 – 23:00|180000"
            ),
            depositAmount = 54_000L
        )
    }

    @Test
    fun `build doc from Gia Han args passes BookingManagementMapper with PENDING status and correct finance`() {
        val args = sampleGiaHanArgs()
        val userId = "player-du-01"
        // 16:45 on 2026-11-09
        val nowMillis = ZonedDateTime.of(2026, 11, 9, 16, 45, 0, 0, zoneVietnam).toInstant().toEpochMilli()

        val docData = BookingDocumentBuilder.build(
            args = args,
            paymentMethod = "AT_VENUE",
            userId = userId,
            nowMillis = nowMillis,
            venuePhone = "0901234567",
            useServerTimestamp = false
        )

        // Strict validation: doc must pass 060 mapper without error
        val booking = BookingManagementMapper.map(args.bookingId, docData, userId)

        assertEquals(args.bookingId, booking.bookingId)
        assertEquals(userId, booking.userId)
        assertEquals(args.venueId, booking.venueId)
        assertEquals(RefundPolicy.Status.PENDING, booking.status)

        // Venue snapshot verification
        assertEquals("CLB Cầu Lông Gia Hân", booking.venueSnapshot.name)
        assertEquals("123 Đào Duy Anh, Phường 9", booking.venueSnapshot.address)
        assertEquals("0901234567", booking.venueSnapshot.phone)

        // Items verification: 6 slots of 30 minutes, 30,000 VND each, total 180,000 VND
        assertEquals(6, booking.items.size)
        assertEquals(180_000L, booking.items.sumOf { it.price })
        booking.items.forEach { item ->
            assertEquals("Sân 4 · PU", item.courtName)
            assertEquals("court4", item.courtId)
            assertEquals(30_000L, item.price)
        }

        // Timing verification: startAt == 20:00, endAt == 23:00
        val expectedStartMillis = ZonedDateTime.of(2026, 11, 9, 20, 0, 0, 0, zoneVietnam).toInstant().toEpochMilli()
        val expectedEndMillis = ZonedDateTime.of(2026, 11, 9, 23, 0, 0, 0, zoneVietnam).toInstant().toEpochMilli()
        assertEquals(expectedStartMillis, booking.startAtMillis)
        assertEquals(expectedEndMillis, booking.endAtMillis)

        // Approval deadline: min(now + 60m, startAt)
        // now is 16:45, now + 60m is 17:45 < startAt 20:00 -> deadline is 17:45
        val expectedDeadlineMillis = nowMillis + 60 * 60 * 1000L
        assertEquals(expectedDeadlineMillis, booking.approvalDeadlineMillis)

        // Finance verification for AT_VENUE
        assertEquals(180_000L, booking.finance.subtotal)
        assertEquals(0L, booking.finance.discount)
        assertEquals(180_000L, booking.finance.total)
        assertEquals(0L, booking.finance.depositAmount)
        assertEquals(0L, booking.finance.paidAmount)
        assertEquals(180_000L, booking.finance.remainingAmount)
        assertEquals(RefundPolicy.PaymentMethod.AT_VENUE, booking.finance.paymentMethod)
        assertEquals(PaymentStatus.UNPAID, booking.finance.paymentStatus)
    }

    @Test
    fun `build doc for DEPOSIT payment calculates 30 percent deposit and DEPOSITED status`() {
        val args = sampleGiaHanArgs()
        val userId = "player-du-01"
        val nowMillis = ZonedDateTime.of(2026, 11, 9, 16, 45, 0, 0, zoneVietnam).toInstant().toEpochMilli()

        val docData = BookingDocumentBuilder.build(
            args = args,
            paymentMethod = "DEPOSIT",
            userId = userId,
            nowMillis = nowMillis,
            useServerTimestamp = false
        )

        val booking = BookingManagementMapper.map(args.bookingId, docData, userId)
        assertEquals(54_000L, booking.finance.depositAmount)
        assertEquals(54_000L, booking.finance.paidAmount)
        assertEquals(126_000L, booking.finance.remainingAmount)
        assertEquals(RefundPolicy.PaymentMethod.DEPOSIT, booking.finance.paymentMethod)
        assertEquals(PaymentStatus.DEPOSITED, booking.finance.paymentStatus)
    }

    @Test
    fun `build doc for MOCK_WALLET payment sets 100 percent paid and PAID status`() {
        val args = sampleGiaHanArgs()
        val userId = "player-du-01"
        val nowMillis = ZonedDateTime.of(2026, 11, 9, 16, 45, 0, 0, zoneVietnam).toInstant().toEpochMilli()

        val docData = BookingDocumentBuilder.build(
            args = args,
            paymentMethod = "WALLET",
            userId = userId,
            nowMillis = nowMillis,
            useServerTimestamp = false
        )

        val booking = BookingManagementMapper.map(args.bookingId, docData, userId)
        assertEquals(0L, booking.finance.depositAmount)
        assertEquals(180_000L, booking.finance.paidAmount)
        assertEquals(0L, booking.finance.remainingAmount)
        assertEquals(RefundPolicy.PaymentMethod.MOCK_WALLET, booking.finance.paymentMethod)
        assertEquals(PaymentStatus.PAID, booking.finance.paymentStatus)
    }

    @Test
    fun `confirmHoldToBookingUseCase delegates to repository and handles expired hold`() = runTest {
        val repository: BookingRepository = mockk()
        val useCase = ConfirmHoldToBookingUseCase(repository)
        val args = sampleGiaHanArgs()

        // 1. Expired hold returns ConfirmBookingResult.Expired
        coEvery { repository.confirmHoldToBooking(args, "AT_VENUE", "player-du-01") } returns ConfirmBookingResult.Expired

        val expiredResult = useCase(args, "AT_VENUE", "player-du-01")
        assertTrue(expiredResult is ConfirmBookingResult.Expired)

        // 2. Idempotent confirm: second call succeeds with same bookingId
        coEvery { repository.confirmHoldToBooking(args, "AT_VENUE", "player-du-01") } returns ConfirmBookingResult.Success(args.bookingId)

        val successResult1 = useCase(args, "AT_VENUE", "player-du-01")
        val successResult2 = useCase(args, "AT_VENUE", "player-du-01")
        assertTrue(successResult1 is ConfirmBookingResult.Success)
        assertTrue(successResult2 is ConfirmBookingResult.Success)
        assertEquals(args.bookingId, (successResult1 as ConfirmBookingResult.Success).bookingId)
        assertEquals(args.bookingId, (successResult2 as ConfirmBookingResult.Success).bookingId)

        coVerify(exactly = 3) { repository.confirmHoldToBooking(args, "AT_VENUE", "player-du-01") }
    }
}
