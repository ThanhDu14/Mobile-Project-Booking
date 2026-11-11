package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.domain.CalculateRefundQuoteUseCase
import com.example.booking_app.feature.booking_management.domain.GenerateQrCodeUseCase
import com.example.booking_app.feature.booking_management.domain.RefundPolicy.PaymentMethod
import com.example.booking_app.feature.booking_management.domain.RefundPolicy.Status
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test

class DomainUseCasesTest {
    private val quote = CalculateRefundQuoteUseCase()

    @Test fun quoteUsesSuppliedTimeAndPolicy() {
        val boundary = 240L * 60 * 1000
        fun check(status: Status, method: PaymentMethod, paid: Long, now: Long, start: Long, refund: Long, penalty: Long) {
            val result = quote(status, method, paid, now, start, false)
            assertTrue(result.isEligibleForCancel)
            assertEquals(paid, result.paidAmount)
            assertEquals(refund, result.refundAmount)
            assertEquals(penalty, result.penaltyAmount)
            assertTrue(result.policyExplanation.contains("Máy chủ"))
        }
        check(Status.PENDING, PaymentMethod.MOCK_WALLET, 111_111, 0, 1, 111_111, 0)
        check(Status.CONFIRMED, PaymentMethod.DEPOSIT, 111_111, 0, boundary, 111_111, 0)
        check(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 111_111, 0, boundary + 1, 111_111, 0)
        check(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 111_111, 1, boundary, 78_111, 33_000)
        check(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 40_000, 0, 1, 28_000, 12_000)
        check(Status.CONFIRMED, PaymentMethod.DEPOSIT, 40_000, 0, 1, 0, 40_000)
        check(Status.CONFIRMED, PaymentMethod.AT_VENUE, 0, 0, 1, 0, 0)
        val large = quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, Long.MAX_VALUE, 0, 1, false)
        assertEquals(Long.MAX_VALUE - large.penaltyAmount, large.refundAmount)
    }

    @Test fun quoteBoundaryIneligibleAndExtremeTimes() {
        val boundary = 240L * 60_000
        val exact = quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 40_000, 0, boundary, false)
        assertTrue(exact.isEarlyCancel)
        assertEquals(240, exact.minutesUntilStart)
        val late = quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 40_000, 1, boundary, false)
        assertFalse(late.isEarlyCancel)
        assertEquals(239, late.minutesUntilStart)
        val ineligible = quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 40_000, 60_001, 0, false)
        assertFalse(ineligible.isEligibleForCancel)
        assertEquals(-1, ineligible.minutesUntilStart)
        assertEquals(40_000, ineligible.paidAmount)
        assertEquals(0, ineligible.refundAmount)
        assertEquals(0, ineligible.penaltyAmount)
        assertTrue(ineligible.policyExplanation.contains("Không đủ điều kiện"))
        assertFalse(quote(Status.PENDING, PaymentMethod.DEPOSIT, 1, 0, 1, true).isEligibleForCancel)
        val extremes = quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 1, Long.MIN_VALUE, Long.MAX_VALUE, false)
        assertEquals(307_445_734_561_825L, extremes.minutesUntilStart)
        assertTrue(extremes.isEarlyCancel)
        assertEquals(-307_445_734_561_825L, quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 1, Long.MAX_VALUE, Long.MIN_VALUE, false).minutesUntilStart)
        assertEquals(1, quote(Status.CONFIRMED, PaymentMethod.MOCK_WALLET, 1, -59_999, 59_999, false).minutesUntilStart)
    }

    @Test fun qrEncodesTokenAs512PixelMatrix() {
        val token = "booking-secret-đ-123"
        val matrix = GenerateQrCodeUseCase()(token)
        assertEquals(512, matrix.width)
        assertEquals(512, matrix.height)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.isSet(i % matrix.width, i / matrix.width)) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        assertEquals(token, QRCodeReader().decode(bitmap).text)
    }
}
