package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.booking.BookingCheckInInput
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInRepository
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInResult
import com.example.booking_app.feature.owner.domain.booking.CheckInContractUnavailableException
import javax.inject.Inject

/** No guessed payload or client-side check-in write while Feature 060 confirms the named server function. */
class ContractPendingBookingCheckInRepository @Inject constructor() : BookingCheckInRepository {
    override suspend fun checkIn(input: BookingCheckInInput): Result<BookingCheckInResult> =
        Result.failure(CheckInContractUnavailableException())
}
