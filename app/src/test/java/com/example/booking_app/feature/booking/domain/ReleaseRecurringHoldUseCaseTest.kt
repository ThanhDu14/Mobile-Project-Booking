package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import com.example.booking_app.feature.booking.domain.usecase.ReleaseRecurringHoldUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReleaseRecurringHoldUseCaseTest {

    private val repository = mockk<RecurringBookingRepository>()
    private lateinit var useCase: ReleaseRecurringHoldUseCase

    private val recurringGroupId = "rec-group-123"
    private val slotIds = listOf("c1_20261110_1800", "c1_20261110_1830")
    private val userId = "user-01"

    @Before
    fun setUp() {
        useCase = ReleaseRecurringHoldUseCase(repository)
    }

    @Test
    fun releaseHold_invokesRepositoryWithCorrectIds() = runTest {
        coEvery {
            repository.releaseRecurringHold(recurringGroupId, slotIds, userId)
        } returns Result.success(Unit)

        val result = useCase(recurringGroupId, slotIds, userId)
        assertTrue(result.isSuccess)

        coVerify(exactly = 1) {
            repository.releaseRecurringHold(recurringGroupId, slotIds, userId)
        }
    }

    @Test
    fun releaseHold_whenRepositoryFails_returnsFailure() = runTest {
        coEvery {
            repository.releaseRecurringHold(recurringGroupId, slotIds, userId)
        } returns Result.failure(RuntimeException("Network error"))

        val result = useCase(recurringGroupId, slotIds, userId)
        assertTrue(result.isFailure)
    }
}
