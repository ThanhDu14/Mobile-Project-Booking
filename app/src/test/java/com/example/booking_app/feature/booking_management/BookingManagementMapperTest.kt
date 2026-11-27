package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.data.BookingManagementMapper
import com.example.booking_app.feature.booking_management.domain.PaymentStatus
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class BookingManagementMapperTest {
    private val start = Timestamp(Date(1000000))
    private val end = Timestamp(Date(2800000))
    private fun booking() = mutableMapOf<String, Any?>(
        "userId" to "user", "venueId" to "venue", "venueSnapshot" to mapOf("name" to "Name", "address" to "Address"),
        "items" to listOf(mapOf("slotId" to "slot", "courtId" to "court", "courtName" to "Court", "startAt" to start, "endAt" to end, "price" to 90L)),
        "startAt" to start, "endAt" to end, "status" to "CONFIRMED", "subtotal" to 90L, "discount" to 10L,
        "total" to 80L, "depositAmount" to 20L, "paidAmount" to 20L, "remainingAmount" to 60L,
        "paymentMethod" to "DEPOSIT", "paymentStatus" to "DEPOSITED", "createdAt" to start, "updatedAt" to start
    )
    @Test fun mapsPersistedSlotPriceAndFinance() {
        val result = BookingManagementMapper.map("id", booking(), "user")
        assertEquals(90L, result.items.single().price)
        assertEquals(20L, result.finance.paidAmount)
        assertEquals(1000000L, result.startAtMillis)
    }
    @Test fun rejectsWrongOwner() {
        assertThrows(IllegalArgumentException::class.java) { BookingManagementMapper.map("id", booking(), "other") }
    }
    @Test fun rejectsMissingMoneyAndTimes() {
        val data = booking()
        data.remove("paidAmount")
        assertThrows(IllegalStateException::class.java) { BookingManagementMapper.map("id", data, "user") }
        data["paidAmount"] = 20L
        data.remove("startAt")
        assertThrows(IllegalStateException::class.java) { BookingManagementMapper.map("id", data, "user") }
    }
    // The 041 writer persists BookingSessionDto directly, not the complete 060 schema.
    private fun recurringSession() = mutableMapOf<String, Any?>(
        "recurringGroupId" to "group", "userId" to "user", "venueId" to "venue",
        "courtId" to "court", "courtName" to "Court", "date" to "20261108",
        "startTime" to "0900", "endTime" to "1000", "slotIds" to listOf("slot1", "slot2"),
        "status" to "HOLD", "price" to 180L, "depositAmount" to 54L
    )

    @Test fun rejectsIncomplete041SessionInsteadOfInventingSnapshotTimeOrSlotPrices() {
        assertThrows(IllegalStateException::class.java) {
            BookingManagementMapper.map("session", recurringSession(), "user")
        }
    }

    @Test fun rejects041SessionForAnotherOwnerBeforeReadingItsFields() {
        assertThrows(IllegalArgumentException::class.java) {
            BookingManagementMapper.map("session", recurringSession(), "other")
        }
    }

    @Test fun rejectsUnknownStatus() {
        val data = booking().apply { put("status", "UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { BookingManagementMapper.map("id", data, "user") }
    }

    // Spec 050 (T058): cách trả mới và trạng thái thanh toán mới; đơn cũ vẫn đọc được.
    @Test fun mapsTransferMethodsAndNewPaymentStatuses() {
        val full = BookingManagementMapper.map("id", booking().apply {
            put("paymentMethod", "TRANSFER_FULL"); put("paymentStatus", "REFUND_PENDING")
        }, "user")
        assertEquals(RefundPolicy.PaymentMethod.TRANSFER_FULL, full.finance.paymentMethod)
        assertEquals(PaymentStatus.REFUND_PENDING, full.finance.paymentStatus)
        val awaiting = BookingManagementMapper.map("id", booking().apply {
            put("paymentMethod", "TRANSFER_DEPOSIT"); put("paymentStatus", "AWAITING_TRANSFER")
        }, "user")
        assertEquals(RefundPolicy.PaymentMethod.TRANSFER_DEPOSIT, awaiting.finance.paymentMethod)
        assertEquals(PaymentStatus.AWAITING_TRANSFER, awaiting.finance.paymentStatus)
    }

    @Test fun mapsLegacyWalletAndDepositMethods() {
        assertEquals(RefundPolicy.PaymentMethod.TRANSFER_DEPOSIT, BookingManagementMapper.map("id", booking(), "user").finance.paymentMethod)
        val wallet = BookingManagementMapper.map("id", booking().apply { put("paymentMethod", "MOCK_WALLET") }, "user")
        assertEquals(RefundPolicy.PaymentMethod.TRANSFER_FULL, wallet.finance.paymentMethod)
    }
}
