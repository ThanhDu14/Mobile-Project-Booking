package com.example.booking_app.feature.booking.domain

import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import com.example.booking_app.feature.booking.domain.usecase.ReleaseHoldUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReleaseHoldUseCaseTest {

    private val repository: BookingRepository = mockk()
    private lateinit var useCase: ReleaseHoldUseCase

    private val venueId = "demo-venue-01"
    private val slotIds = listOf("court-01_20261027_0600", "court-01_20261027_0630")
    private val userId = "user-123"

    @Before
    fun setUp() {
        useCase = ReleaseHoldUseCase(repository)
    }

    @Test
    fun releaseHold_successful_callsRepositoryAndReturnsSuccess() = runTest {
        coEvery {
            repository.releaseHoldSlots(venueId, slotIds, userId)
        } returns Result.success(Unit)

        val result = useCase(venueId, slotIds, userId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            repository.releaseHoldSlots(venueId, slotIds, userId)
        }
    }

    @Test
    fun releaseHold_repositoryFails_returnsFailure() = runTest {
        val error = RuntimeException("Network error")
        coEvery {
            repository.releaseHoldSlots(venueId, slotIds, userId)
        } returns Result.failure(error)

        val result = useCase(venueId, slotIds, userId)

        assertTrue(result.isFailure)
    }
}
