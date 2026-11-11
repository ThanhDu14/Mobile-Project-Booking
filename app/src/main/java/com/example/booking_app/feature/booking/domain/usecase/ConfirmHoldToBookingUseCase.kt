package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.ConfirmBookingResult
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.example.booking_app.feature.booking.ui.HoldToPaymentArgs
import javax.inject.Inject

class ConfirmHoldToBookingUseCase @Inject constructor(
    private val repository: BookingRepository
) {
    suspend operator fun invoke(
        args: HoldToPaymentArgs,
        paymentMethod: String,
        userId: String
    ): ConfirmBookingResult {
        if (userId.isBlank()) {
            return ConfirmBookingResult.Error("Người dùng chưa đăng nhập")
        }
        if (args.slotIds.isEmpty()) {
            return ConfirmBookingResult.Error("Danh sách khung giờ rỗng")
        }
        return repository.confirmHoldToBooking(args, paymentMethod, userId)
    }
}
