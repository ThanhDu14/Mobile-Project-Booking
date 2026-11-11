package com.example.booking_app.feature.booking_management.domain

import kotlinx.coroutines.flow.Flow

interface BookingManagementRepository {
    suspend fun getPage(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage
    /** Emits when a tab's membership or ordering may have changed. Discard loaded pages and
     * cursors and reload from the first page; this is not a stream of individual page updates.
     * Changes that move a booking between tabs must invalidate both affected tabs.
     */
    fun observeTabInvalidations(tab: BookingTab): Flow<Unit>
    fun observeBooking(bookingId: String): Flow<BookingDetail?>
    fun observeGroup(recurringGroupId: String): Flow<RecurringBookingGroup?>
    /** A non-authoritative estimate, never an input to cancellation. */
    suspend fun estimateQuote(bookingId: String): RefundPolicyQuote
    suspend fun cancelBooking(request: CancelBookingRequest): CancellationResult
    suspend fun cancelRemainingGroup(request: CancelGroupRequest): CancellationResult
    fun observeTabCounts(): Flow<Map<BookingTab, Int>> = kotlinx.coroutines.flow.flowOf(emptyMap())
}
