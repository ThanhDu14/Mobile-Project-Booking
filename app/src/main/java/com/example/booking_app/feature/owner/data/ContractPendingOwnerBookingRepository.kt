package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.booking.OwnerBooking
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingCommand
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingContractUnavailableException
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingDetail
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Fails closed until the owner-safe projection and command contract are approved and deployed. */
class ContractPendingOwnerBookingRepository @Inject constructor() : OwnerBookingRepository {
    override fun observeBookings(venueId: String): Flow<List<OwnerBooking>> = flow {
        throw OwnerBookingContractUnavailableException()
    }

    override suspend fun getBooking(venueId: String, bookingId: String): Result<OwnerBookingDetail> =
        Result.failure(OwnerBookingContractUnavailableException())

    override suspend fun submit(command: OwnerBookingCommand): Result<Unit> =
        Result.failure(OwnerBookingContractUnavailableException())
}
