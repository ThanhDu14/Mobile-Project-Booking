package com.example.booking_app.feature.payment.domain.usecase

import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import javax.inject.Inject

/** Chốt đơn trả tại sân / miễn phí. `requestId` cố định theo phiên thanh toán (do `PaymentViewModel` giữ). */
class ConfirmAtVenueUseCase @Inject constructor(
    private val repository: PaymentRepository,
) {
    suspend operator fun invoke(req: ConfirmRequest): Result<PaymentReceipt> = repository.confirmAtVenue(req)
}
