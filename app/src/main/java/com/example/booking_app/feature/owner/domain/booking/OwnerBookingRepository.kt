package com.example.booking_app.feature.owner.domain.booking

import kotlinx.coroutines.flow.Flow

/** Implement against an approved owner-safe server projection; raw bookings contain qrToken. */
interface OwnerBookingRepository {
    fun observeBookings(venueId: String): Flow<List<OwnerBooking>>
    suspend fun getBooking(venueId: String, bookingId: String): Result<OwnerBookingDetail>
    suspend fun submit(command: OwnerBookingCommand): Result<Unit>
}

class OwnerBookingContractUnavailableException :
    IllegalStateException("Hợp đồng đọc và xử lý booking phía máy chủ chưa được xác nhận.")
