package com.example.booking_app.feature.payment.domain.model

/** Body của `booking-payment-confirm` (contracts/edge-functions.md §1). */
data class ConfirmRequest(
    /** = bookingId; với đơn định kỳ = recurringGroupId. Giữ nguyên khi bấm lại để server xử lý idempotent. */
    val requestId: String,
    val recurringGroupId: String?,
    val venueId: String,
    val slotIds: List<String>,
    val voucherCode: String?,
    /** Tổng app đang hiển thị; server tính khác thì trả `PRICE_CHANGED`. */
    val expectedTotal: Long,
)
