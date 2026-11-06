package com.example.booking_app.feature.owner.ui.bookings

import com.example.booking_app.feature.owner.domain.booking.BookingCheckInInput
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInRepository
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingCheckInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: RecordingCheckInRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = RecordingCheckInRepository()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun trimsManualCodeAndPassesExplicitPaymentConfirmationToServerAdapter() = runTest {
        val viewModel = BookingCheckInViewModel(repository)
        viewModel.submit("  BK-123  ", paymentCollected = true)
        advanceUntilIdle()

        assertEquals(BookingCheckInInput("BK-123", ownerConfirmedPaymentCollected = true), repository.lastInput)
        assertEquals("booking-123", viewModel.uiState.value.result)
        assertEquals("booking-123", viewModel.uiState.value.checkInResult?.bookingId)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test fun passesPaymentCollectedFalseWhenUnchecked() = runTest {
        val viewModel = BookingCheckInViewModel(repository)
        viewModel.submit("BK-456", paymentCollected = false)
        advanceUntilIdle()

        assertEquals(BookingCheckInInput("BK-456", ownerConfirmedPaymentCollected = false), repository.lastInput)
        assertEquals("booking-123", viewModel.uiState.value.result)
    }

    @Test fun rejectsBlankCredentialWithoutCallingRepository() = runTest {
        val viewModel = BookingCheckInViewModel(repository)
        viewModel.submit("  ", paymentCollected = false)

        assertTrue(viewModel.uiState.value.error?.contains("Nhập mã") == true)
        assertEquals(null, repository.lastInput)
    }

    @Test fun exposesContractOrServerFailureAndDoesNotReportSuccess() = runTest {
        repository.failure = IllegalStateException("function unavailable")
        val viewModel = BookingCheckInViewModel(repository)
        viewModel.submit("QR-CODE", paymentCollected = false)
        advanceUntilIdle()

        assertEquals("function unavailable", viewModel.uiState.value.error)
        assertEquals(null, viewModel.uiState.value.result)
    }

    private class RecordingCheckInRepository : BookingCheckInRepository {
        var lastInput: BookingCheckInInput? = null
        var failure: Throwable? = null
        override suspend fun checkIn(input: BookingCheckInInput): Result<BookingCheckInResult> {
            lastInput = input
            failure?.let { return Result.failure(it) }
            return Result.success(BookingCheckInResult("booking-123", 1_799_000_000_000, null))
        }
    }
}
