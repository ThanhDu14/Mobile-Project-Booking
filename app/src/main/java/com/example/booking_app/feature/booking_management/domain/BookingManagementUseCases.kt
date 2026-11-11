package com.example.booking_app.feature.booking_management.domain

/** The server determines eligibility, allocation and the final result; reuse requestId on retry. */
class CancelBookingUseCase(private val repository: BookingManagementRepository) {
    suspend operator fun invoke(request: CancelBookingRequest): CancellationResult =
        repository.cancelBooking(request)
}

/** Group allocation and voucher handling remain the server's responsibility. */
class CancelRemainingGroupUseCase(private val repository: BookingManagementRepository) {
    suspend operator fun invoke(request: CancelGroupRequest): CancellationResult =
        repository.cancelRemainingGroup(request)
}

class GetBookingPageUseCase(private val repository: BookingManagementRepository) {
    suspend operator fun invoke(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage =
        repository.getPage(tab, cursor, pageSize)
}

class ObserveBookingTabInvalidationsUseCase(private val repository: BookingManagementRepository) {
    operator fun invoke(tab: BookingTab) = repository.observeTabInvalidations(tab)
}

class ObserveBookingUseCase(private val repository: BookingManagementRepository) {
    operator fun invoke(bookingId: String) = repository.observeBooking(bookingId)
}

class ObserveBookingGroupUseCase(private val repository: BookingManagementRepository) {
    operator fun invoke(recurringGroupId: String) = repository.observeGroup(recurringGroupId)
}
