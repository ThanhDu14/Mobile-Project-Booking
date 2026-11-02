package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import com.example.booking_app.feature.booking.domain.usecase.CreateRecurringHoldTransactionUseCase
import com.example.booking_app.feature.booking.fixture.RecurringBookingFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CreateRecurringHoldTransactionUseCaseTest {

    private val repository = mockk<RecurringBookingRepository>()
    private lateinit var useCase: CreateRecurringHoldTransactionUseCase

    private val config = RecurringBookingFixtures.sampleConfig()
    private val venueId = "venue-01"
    private val userId = "user-01"
    private val requestId = "req-uuid-1234"

    @Before
    fun setUp() {
        useCase = CreateRecurringHoldTransactionUseCase(repository)
    }

    private fun sampleValidSessions(): List<RecurringSession> = (1..4).map { i ->
        RecurringSession(
            date = LocalDate.of(2026, 11, i),
            startTime = "1800",
            endTime = "2000",
            courtId = "court-01",
            courtName = "Sân 1",
            slotIds = listOf("c1_${i}_1800", "c1_${i}_1830"),
            price = 200_000L,
            status = RecurringSessionStatus.AVAILABLE
        )
    }

    @Test
    fun allSlotsAvailable_holdTransactionSucceedsWithGroupId() = runTest {
        val sessions = sampleValidSessions()
        val expectedExpiresAt = System.currentTimeMillis() + 300_000L
        coEvery {
            repository.holdRecurringBookingTransaction(
                venueId = venueId,
                config = config,
                sessions = sessions,
                userId = userId,
                requestId = requestId
            )
        } returns Result.success(RecurringHoldResult.Success("rec-group-123", expectedExpiresAt))

        val result = useCase(
            venueId = venueId,
            config = config,
            sessions = sessions,
            userId = userId,
            requestId = requestId
        )

        assertTrue(result.isSuccess)
        val holdResult = result.getOrThrow()
        assertTrue(holdResult is RecurringHoldResult.Success)
        val success = holdResult as RecurringHoldResult.Success
        assertEquals("rec-group-123", success.recurringGroupId)
        assertEquals(expectedExpiresAt, success.holdExpiresAtMillis)

        coVerify(exactly = 1) {
            repository.holdRecurringBookingTransaction(venueId, config, sessions, userId, requestId)
        }
    }

    @Test
    fun oneSlotOccupied_allOrNothingRollback_returnsConflictedDates() = runTest {
        val sessions = sampleValidSessions()
        val conflictedDate = LocalDate.of(2026, 11, 2)
        coEvery {
            repository.holdRecurringBookingTransaction(
                venueId = venueId,
                config = config,
                sessions = sessions,
                userId = userId,
                requestId = requestId
            )
        } returns Result.success(RecurringHoldResult.Conflict(listOf(conflictedDate)))

        val result = useCase(
            venueId = venueId,
            config = config,
            sessions = sessions,
            userId = userId,
            requestId = requestId
        )

        assertTrue(result.isSuccess)
        val holdResult = result.getOrThrow()
        assertTrue(holdResult is RecurringHoldResult.Conflict)
        val conflict = holdResult as RecurringHoldResult.Conflict
        assertEquals(listOf(conflictedDate), conflict.conflictedSessionDates)
    }
}
