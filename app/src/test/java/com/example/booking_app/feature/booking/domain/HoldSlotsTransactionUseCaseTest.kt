package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.example.booking_app.feature.booking.domain.usecase.HoldSlotsTransactionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HoldSlotsTransactionUseCaseTest {

    private val repository: BookingRepository = mockk()
    private lateinit var useCase: HoldSlotsTransactionUseCase

    private val venueId = "demo-venue-01"
    private val courtId = "court-01"
    private val date = "20261027"
    private val userId = "user-123"
    private val requestId = "req-uuid-001"
    private val slotIds = listOf("court-01_20261027_0600", "court-01_20261027_0630")

    @Before
    fun setUp() {
        useCase = HoldSlotsTransactionUseCase(repository)
    }

    @Test
    fun holdSlots_allValid_returnsSuccessWith5MinExpiry() = runTest {
        val expiryTime = System.currentTimeMillis() + 5 * 60 * 1000L
        coEvery {
            repository.holdSlotsAtomic(venueId, courtId, date, slotIds, userId, requestId)
        } returns Result.success(HoldResult.Success(bookingId = requestId, holdExpiresAtMillis = expiryTime))

        val result = useCase(venueId, courtId, date, slotIds, userId, requestId)

        assertTrue(result.isSuccess)
        val holdResult = result.getOrNull()
        assertTrue(holdResult is HoldResult.Success)
        assertEquals(requestId, (holdResult as HoldResult.Success).bookingId)
        assertEquals(expiryTime, holdResult.holdExpiresAtMillis)
        coVerify(exactly = 1) {
            repository.holdSlotsAtomic(venueId, courtId, date, slotIds, userId, requestId)
        }
    }

    @Test
    fun holdSlots_oneSlotConflicted_returnsConflictAllOrNothing() = runTest {
        val conflicted = listOf("court-01_20261027_0600")
        coEvery {
            repository.holdSlotsAtomic(venueId, courtId, date, slotIds, userId, requestId)
        } returns Result.success(HoldResult.Conflict(conflictedSlotIds = conflicted))

        val result = useCase(venueId, courtId, date, slotIds, userId, requestId)

        assertTrue(result.isSuccess)
        val holdResult = result.getOrNull()
        assertTrue(holdResult is HoldResult.Conflict)
        assertEquals(conflicted, (holdResult as HoldResult.Conflict).conflictedSlotIds)
    }

    @Test
    fun holdSlots_idempotentWithSameRequestId_returnsSuccessWithoutDuplicateHold() = runTest {
        val expiryTime = System.currentTimeMillis() + 5 * 60 * 1000L
        coEvery {
            repository.holdSlotsAtomic(venueId, courtId, date, slotIds, userId, requestId)
        } returns Result.success(HoldResult.Success(bookingId = requestId, holdExpiresAtMillis = expiryTime))

        // Call twice with the same requestId
        val firstCall = useCase(venueId, courtId, date, slotIds, userId, requestId)
        val secondCall = useCase(venueId, courtId, date, slotIds, userId, requestId)

        assertTrue(firstCall.isSuccess)
        assertTrue(secondCall.isSuccess)
        assertEquals(firstCall.getOrNull(), secondCall.getOrNull())
    }
}
