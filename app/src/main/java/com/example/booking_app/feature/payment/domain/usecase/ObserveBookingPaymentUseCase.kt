package com.example.booking_app.feature.payment.domain.usecase

import com.example.booking_app.feature.payment.domain.model.BookingPaymentState
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Theo dõi đơn/nhóm đang chờ chuyển khoản để màn QR tự sang hóa đơn khi webhook chốt (research R8). */
class ObserveBookingPaymentUseCase @Inject constructor(private val repository: PaymentRepository) {
    operator fun invoke(bookingId: String, isRecurring: Boolean): Flow<BookingPaymentState> =
        repository.observe(bookingId, isRecurring)
}
