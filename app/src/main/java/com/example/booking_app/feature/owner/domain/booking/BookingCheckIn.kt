package com.example.booking_app.feature.owner.domain.booking

/** UI input only. It is deliberately not a wire DTO: Database README names the function but not its payload. */
data class BookingCheckInInput(
    val credential: String,
    val ownerConfirmedPaymentCollected: Boolean,
) {
    fun normalized(): BookingCheckInInput = copy(credential = credential.trim())
}

data class BookingCheckInResult(
    val bookingId: String,
    val checkedInAtEpochMillis: Long?,
    val paymentStatus: String?,
    val remainingAmountVnd: Long? = null,
    val paymentConfirmationRequired: Boolean = false,
    val courtNames: List<String> = emptyList(),
    val startAt: java.time.Instant? = null,
    val endAt: java.time.Instant? = null,
)

interface BookingCheckInRepository {
    suspend fun checkIn(input: BookingCheckInInput): Result<BookingCheckInResult>
}

class CheckInContractUnavailableException :
    IllegalStateException("Hợp đồng checkin-booking chưa xác nhận request/response và chưa có function trong project.")
