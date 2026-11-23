package com.example.booking_app.feature.payment.domain.repository

import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import kotlinx.coroutines.flow.Flow

/**
 * Chốt đơn, chuyển khoản và đọc hóa đơn (contracts/ui-contracts.md §2). Lỗi là `PaymentError`.
 */
interface PaymentRepository {
    /** `booking-payment-confirm`: trả tại sân hoặc miễn phí. */
    suspend fun confirmAtVenue(req: ConfirmRequest): Result<PaymentReceipt>

    /** Đọc lại đơn (`bookings/{id}`) hoặc nhóm định kỳ (`recurringGroups/{id}` + các buổi) để hiện hóa đơn. */
    suspend fun receipt(bookingId: String, isRecurring: Boolean): Result<PaymentReceipt>

    /** `booking-payment-transfer`: cọc 30% hoặc trả đủ; gọi lại cùng `requestId` nhận lại cùng mã. */
    suspend fun startTransfer(req: ConfirmRequest, option: PaymentOption): Result<TransferInstruction>

    /** Listener realtime trên `bookings/{id}` hoặc `recurringGroups/{id}`; ngừng thu thì gỡ listener. */
    fun observe(bookingId: String, isRecurring: Boolean): Flow<BookingPaymentState>
}
