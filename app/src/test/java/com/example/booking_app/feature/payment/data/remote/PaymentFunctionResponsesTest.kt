package com.example.booking_app.feature.payment.data.remote

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.payment.domain.model.PaymentError
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc phản hồi `booking-payment-confirm` và ánh xạ mã lỗi (contracts/edge-functions.md). */
class PaymentFunctionResponsesTest {

    private fun http(status: Int, body: String) = AppError.Unknown(FunctionHttpException(status, body))

    @Test
    fun `doc hoa don tu body 200`() {
        val receipt = PaymentFunctionResponses.parseConfirm(
            """{"bookingId":"b1","status":"PENDING","paymentStatus":"UNPAID","receipt":{"bookingId":"b1",
            "recurringGroupId":null,"venueName":"Sân A","items":["Sân 1 · 20/11/2026 18:00–19:00"],"option":"AT_VENUE",
            "status":"UNPAID","subtotal":120000,"discount":24000,"total":96000,"paidAmount":0,"remainingAmount":96000,
            "voucherCode":"SALE20"}}""",
        )
        assertEquals("b1", receipt.bookingId)
        assertEquals(PaymentOption.AT_VENUE, receipt.option)
        assertEquals(PaymentStatus.UNPAID, receipt.status)
        assertEquals(96_000L, receipt.total)
        assertEquals("SALE20", receipt.voucherCode)
    }

    @Test
    fun `anh xa ma loi`() {
        assertTrue(PaymentFunctionResponses.mapFailure(http(409, """{"error":{"code":"HOLD_EXPIRED","message":"x"}}""")) is PaymentError.HoldExpired)
        val price = PaymentFunctionResponses.mapFailure(
            http(409, """{"error":{"code":"PRICE_CHANGED","message":"x","breakdown":{"subtotal":240000,"discount":0,"total":240000,"depositAmount":72000,"remainingAfterDeposit":168000,"isFree":false}}}"""),
        ) as PaymentError.PriceChanged
        assertEquals(240_000L, price.breakdown.total)
        assertEquals(72_000L, price.breakdown.depositAmount)
        val voucher = PaymentFunctionResponses.mapFailure(
            http(422, """{"error":{"code":"VOUCHER_INVALID","message":"x","reason":"MIN_ORDER","minOrderTotal":200000}}"""),
        ) as PaymentError.VoucherInvalid
        assertEquals("MIN_ORDER", voucher.reason)
        assertEquals(200_000L, voucher.minOrderTotal)
        assertTrue(PaymentFunctionResponses.mapFailure(http(409, """{"error":{"code":"NO_PRICE","message":"x"}}""")) is PaymentError.NoPrice)
        assertTrue(PaymentFunctionResponses.mapFailure(AppError.Network) is PaymentError.Network)
        assertTrue(PaymentFunctionResponses.mapFailure(AppError.Timeout) is PaymentError.Network)
        assertEquals(AppError.Locked, PaymentFunctionResponses.mapFailure(AppError.Locked))
        assertTrue(PaymentFunctionResponses.mapFailure(http(500, "oops")) is PaymentError.Unknown)
    }

    @Test
    fun `doc phan hoi booking-payment-transfer`() {
        val i = PaymentFunctionResponses.parseTransfer(
            """{"bookingId":"b1","recurringGroupId":null,"paymentCode":"SNPAAAA2222","amountDue":36000,
            "holdExpiresAt":"2026-11-20T03:15:00.000Z","qrUrl":"https://qr.sepay.vn/img?acc=1","sandbox":true,
            "payoutAccount":{"bankShortName":"MBBank","accountNumber":"0123456789","accountHolderName":"NGUYEN VAN A"}}""",
        )
        assertEquals("SNPAAAA2222", i.paymentCode)
        assertEquals(36_000L, i.amountDue)
        assertEquals(1_795_144_500_000L, i.holdExpiresAtMillis)
        assertEquals("MBBank", i.payoutAccount.bankShortName)
        assertEquals(null, i.recurringGroupId)
        assertTrue(
            PaymentFunctionResponses.mapFailure(http(422, """{"error":{"code":"TRANSFER_NOT_SUPPORTED","message":"x"}}"""))
                is PaymentError.TransferNotSupported,
        )
    }
}
