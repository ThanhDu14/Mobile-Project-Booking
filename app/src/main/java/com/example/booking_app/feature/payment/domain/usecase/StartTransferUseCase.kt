package com.example.booking_app.feature.payment.domain.usecase

import com.example.booking_app.feature.payment.domain.model.ConfirmRequest
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.TransferInstruction
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import javax.inject.Inject

/** Bắt đầu chuyển khoản VietQR (`booking-payment-transfer`): mã thanh toán, số tiền, hạn 15 phút. */
class StartTransferUseCase @Inject constructor(private val repository: PaymentRepository) {
    suspend operator fun invoke(req: ConfirmRequest, option: PaymentOption): Result<TransferInstruction> {
        require(option != PaymentOption.AT_VENUE) { "Trả tại sân dùng ConfirmAtVenueUseCase" }
        return repository.startTransfer(req, option)
    }
}
