package com.example.booking_app.feature.payment.domain.repository

import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt

/**
 * Chốt đơn và đọc hóa đơn (contracts/ui-contracts.md §2). Lỗi là `PaymentError`.
 * `startTransfer` và `observe` thêm ở US4.
 */
interface PaymentRepository {
    /** `booking-payment-confirm`: trả tại sân hoặc miễn phí. */
    suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt>

    /** Đọc lại đơn (`bookings/{id}`) hoặc nhóm định kỳ (`recurringGroups/{id}` + các buổi) để hiện hóa đơn. */
    suspend fun receipt(bookingId: String, isRecurring: Boolean): Result<PaymentReceipt>
}
