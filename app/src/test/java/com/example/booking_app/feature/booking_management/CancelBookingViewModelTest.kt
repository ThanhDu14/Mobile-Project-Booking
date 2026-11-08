package com.example.booking_app.feature.booking_management

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking_management.domain.*
import com.example.booking_app.feature.booking_management.ui.CancelBookingViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancelBookingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val timeProvider: ServerTimeOffsetProvider = mockk {
        every { now() } returns 1_000_000L
    }

    private class FakeRepo(
        private val bookingFlow: Flow<BookingDetail?>,
        var cancelHandler: (CancelBookingRequest) -> CancellationResult = { CancellationResult(100_000L, 0L) }
    ) : BookingManagementRepository {
        val cancellationRequests = mutableListOf<CancelBookingRequest>()

        override fun observeBooking(bookingId: String): Flow<BookingDetail?> = bookingFlow
        override fun observeGroup(recurringGroupId: String): Flow<RecurringBookingGroup?> = error("Not used")
        override fun observeTabInvalidations(tab: BookingTab): Flow<Unit> = error("Not used")
        override suspend fun getPage(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage = error("Not used")
        override suspend fun estimateQuote(bookingId: String): RefundPolicyQuote = error("Not used")
        override suspend fun cancelBooking(request: CancelBookingRequest): CancellationResult {
            cancellationRequests.add(request)
            return cancelHandler(request)
        }
        override suspend fun cancelRemainingGroup(request: CancelGroupRequest): CancellationResult = error("Not used")
    }

    private fun sampleBooking(
        id: String = "b1",
        status: RefundPolicy.Status = RefundPolicy.Status.CONFIRMED,
        startAtMillis: Long = 1_000_000L + 5 * 3600_000L, // 5 hours later
        paidAmount: Long = 100_000L
    ) = BookingDetail(
        bookingId = id,
        userId = "u1",
        venueId = "v1",
        venueSnapshot = VenueSnapshot("Sân Test", "Địa chỉ test", "0901234567"),
        items = listOf(
            BookingSlotItem("s1", "c1", "Sân 1", startAtMillis, startAtMillis + 3600_000L, 100_000L)
        ),
        startAtMillis = startAtMillis,
        endAtMillis = startAtMillis + 3600_000L,
        status = status,
        finance = BookingFinance(
            subtotal = 100_000L,
            discount = 0L,
            total = 100_000L,
            depositAmount = 0L,
            paidAmount = paidAmount,
            remainingAmount = 0L,
            paymentMethod = RefundPolicy.PaymentMethod.MOCK_WALLET,
            paymentStatus = PaymentStatus.PAID
        ),
        createdAtMillis = 500L,
        updatedAtMillis = 500L
    )

    @Test
    fun `initial state has confirm disabled and calculates quote`() = runTest {
        val booking = sampleBooking()
        val repo = FakeRepo(flowOf(booking))
        val handle = SavedStateHandle(mapOf("bookingId" to "b1"))
        val calculateQuote = CalculateRefundQuoteUseCase()
        val cancelUseCase = CancelBookingUseCase(repo)

        val vm = CancelBookingViewModel(handle, repo, calculateQuote, cancelUseCase, timeProvider)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isConfirmEnabled)
        assertNotNull(state.booking)
        assertNotNull(state.quote)
        assertTrue(state.quote!!.isEarlyCancel)
        assertEquals(100_000L, state.quote!!.refundAmount)
    }

    @Test
    fun `standard reason enables confirm`() = runTest {
        val repo = FakeRepo(flowOf(sampleBooking()))
        val vm = CancelBookingViewModel(
            SavedStateHandle(mapOf("bookingId" to "b1")),
            repo,
            CalculateRefundQuoteUseCase(),
            CancelBookingUseCase(repo),
            timeProvider
        )
        advanceUntilIdle()

        vm.onReasonSelected("Bận việc đột xuất")
        assertTrue(vm.uiState.value.isConfirmEnabled)
    }

    @Test
    fun `OTHER reason requires non-blank other text to enable confirm`() = runTest {
        val repo = FakeRepo(flowOf(sampleBooking()))
        val vm = CancelBookingViewModel(
            SavedStateHandle(mapOf("bookingId" to "b1")),
            repo,
            CalculateRefundQuoteUseCase(),
            CancelBookingUseCase(repo),
            timeProvider
        )
        advanceUntilIdle()

        vm.onReasonSelected("OTHER")
        assertFalse(vm.uiState.value.isConfirmEnabled)

        vm.onOtherReasonTextChanged("   ")
        assertFalse(vm.uiState.value.isConfirmEnabled)

        vm.onOtherReasonTextChanged("Chấn thương chân khi tập")
        assertTrue(vm.uiState.value.isConfirmEnabled)
    }

    @Test
    fun `confirmCancel sends request with reason and idempotency requestId`() = runTest {
        val repo = FakeRepo(flowOf(sampleBooking()))
        repo.cancelHandler = { CancellationResult(refundAmount = 100_000L, penaltyAmount = 0L) }
        val vm = CancelBookingViewModel(
            SavedStateHandle(mapOf("bookingId" to "b1")),
            repo,
            CalculateRefundQuoteUseCase(),
            CancelBookingUseCase(repo),
            timeProvider
        )
        advanceUntilIdle()

        vm.onReasonSelected("Bận việc đột xuất")
        vm.confirmCancel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isSuccess)
        assertNotNull(state.resultMessage)
        assertEquals(1, repo.cancellationRequests.size)
        val sent = repo.cancellationRequests.first()
        assertEquals("b1", sent.bookingId)
        assertEquals("Bận việc đột xuất", sent.reason)
        assertTrue(sent.requestId.isNotBlank())
    }

    @Test
    fun `confirmCancel with UnsupportedOperationException updates error state`() = runTest {
        val repo = FakeRepo(flowOf(sampleBooking()))
        repo.cancelHandler = { throw UnsupportedOperationException("Server transaction not verified") }
        val vm = CancelBookingViewModel(
            SavedStateHandle(mapOf("bookingId" to "b1")),
            repo,
            CalculateRefundQuoteUseCase(),
            CancelBookingUseCase(repo),
            timeProvider
        )
        advanceUntilIdle()

        vm.onReasonSelected("Thời tiết xấu")
        vm.confirmCancel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSuccess)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("bảo trì"))
    }
}
