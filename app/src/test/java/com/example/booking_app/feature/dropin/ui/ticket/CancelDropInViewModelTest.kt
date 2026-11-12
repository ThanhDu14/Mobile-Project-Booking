package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.CancelReason
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.usecase.CancelDropIn
import com.example.booking_app.feature.dropin.domain.usecase.QuoteCancel
import com.example.booking_app.feature.dropin.ui.registration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T052 (spec 080 US4, AC 4.1–4.4, FR-017, FR-018): bottom sheet hủy đăng ký. */
@OptIn(ExperimentalCoroutinesApi::class)
class CancelDropInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val quoteCalls = mutableListOf<Pair<String, String>>()
    private val quoteResults = ArrayDeque<Result<CancelQuote>>()
    private val cancelCalls = mutableListOf<Pair<String, String>>()
    private val cancelResults = ArrayDeque<Result<DropInRegistration>>()

    /** Đặt để giữ lời gọi hủy ở trạng thái đang chạy. */
    private var cancelGate: CompletableDeferred<Unit>? = null

    private val quoteCancel = QuoteCancel { sessionId, requestId ->
        quoteCalls += sessionId to requestId
        quoteResults.removeFirstOrNull() ?: Result.success(CancelQuote(120_000, CancelReason.EARLY))
    }
    private val cancelDropIn = CancelDropIn { sessionId, requestId ->
        cancelCalls += sessionId to requestId
        cancelGate?.await()
        cancelResults.removeFirstOrNull() ?: Result.success(cancelled(refund = 120_000))
    }

    private val events = mutableListOf<CancelDropInEvent>()

    private fun cancelled(refund: Long) = registration(partySize = 2).copy(
        status = RegistrationStatus.CANCELLED,
        paymentStatus = if (refund > 0) PaymentStatus.REFUNDED else PaymentStatus.PAID,
        refundAmount = refund,
        qrToken = null,
        ticketCode = null,
    )

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to "s1"))): CancelDropInViewModel {
        val vm = CancelDropInViewModel(handle, quoteCancel, cancelDropIn)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return vm
    }

    @Test
    fun `Quoting roi Quote voi tung reason, dung dryRun`() = runTest {
        val cases = listOf(
            CancelQuote(120_000, CancelReason.EARLY),
            CancelQuote(0, CancelReason.LATE),
            CancelQuote(180_000, CancelReason.SESSION_CHANGED),
            CancelQuote(0, CancelReason.AT_VENUE),
        )
        cases.forEach { expected ->
            quoteResults += Result.success(expected)
            val vm = viewModel()
            advanceUntilIdle()
            val state = vm.uiState.value as CancelDropInUiState.Quote
            assertEquals(expected, state.quote)
        }
        assertEquals(4, quoteCalls.size)
        assertTrue(cancelCalls.isEmpty())
    }

    @Test
    fun `chuoi hien thi theo reason`() {
        assertEquals(CancelQuoteText.REFUND_FULL, CancelQuoteText.of(CancelQuote(120_000, CancelReason.EARLY)))
        assertEquals(CancelQuoteText.REFUND_FULL, CancelQuoteText.of(CancelQuote(60_000, CancelReason.SESSION_CHANGED)))
        assertEquals(CancelQuoteText.NO_REFUND, CancelQuoteText.of(CancelQuote(0, CancelReason.LATE)))
        assertEquals(CancelQuoteText.FREE, CancelQuoteText.of(CancelQuote(0, CancelReason.AT_VENUE)))
    }

    @Test
    fun `dang tinh quote thi trang thai Quoting`() = runTest {
        val vm = CancelDropInViewModel(SavedStateHandle(mapOf("sessionId" to "s1")), { _, _ ->
            CompletableDeferred<Result<CancelQuote>>().await()
        }, cancelDropIn)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(CancelDropInUiState.Quoting, vm.uiState.value)
    }

    @Test
    fun `xac nhan - Cancelling roi Done, cung requestId voi quote`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        cancelGate = CompletableDeferred()
        vm.confirm()
        assertTrue(vm.uiState.value is CancelDropInUiState.Cancelling)
        vm.confirm() // bấm lại khi đang hủy: bỏ qua
        cancelGate!!.complete(Unit)
        advanceUntilIdle()
        val done = vm.uiState.value as CancelDropInUiState.Done
        assertEquals(120_000L, done.refundAmount)
        assertEquals(1, cancelCalls.size)
        assertEquals("s1", cancelCalls.single().first)
        assertEquals(quoteCalls.single().second, cancelCalls.single().second)
    }

    @Test
    fun `mat mang - giu sheet, thu lai cung requestId`() = runTest {
        cancelResults += Result.failure(DropInError.Network())
        cancelResults += Result.failure(DropInError.Busy())
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()
        val failed = vm.uiState.value as CancelDropInUiState.Quote
        assertTrue(failed.error is DropInError.Network)
        assertTrue(failed.canRetry)
        assertTrue(events.isEmpty())

        vm.confirm()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as CancelDropInUiState.Quote).error is DropInError.Busy)

        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CancelDropInUiState.Done)
        assertEquals(3, cancelCalls.size)
        assertEquals(1, cancelCalls.map { it.second }.distinct().size)
    }

    @Test
    fun `requestId giu qua SavedStateHandle khi tao lai ViewModel`() = runTest {
        val handle = SavedStateHandle(mapOf("sessionId" to "s1"))
        cancelResults += Result.failure(DropInError.Network())
        val first = viewModel(handle)
        advanceUntilIdle()
        first.confirm()
        advanceUntilIdle()

        val second = viewModel(handle)
        advanceUntilIdle()
        second.confirm()
        advanceUntilIdle()
        assertEquals(2, cancelCalls.size)
        assertEquals(cancelCalls[0].second, cancelCalls[1].second)
    }

    @Test
    fun `loi tinh quote do mang - QuoteFailed, thu lai`() = runTest {
        quoteResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as CancelDropInUiState.QuoteFailed).error is DropInError.Network)
        vm.retryQuote()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CancelDropInUiState.Quote)
    }

    @Test
    fun `khong con huy duoc - dong sheet kem loi`() = runTest {
        cancelResults += Result.failure(DropInError.SessionStarted())
        val vm = viewModel()
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        val close = events.single() as CancelDropInEvent.Close
        assertTrue(close.error is DropInError.SessionStarted)

        quoteResults += Result.failure(DropInError.NotCancellable())
        events.clear()
        viewModel()
        advanceUntilIdle()
        assertTrue((events.single() as CancelDropInEvent.Close).error is DropInError.NotCancellable)
    }

    @Test
    fun `chua co quote thi confirm khong lam gi`() = runTest {
        quoteResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(cancelCalls.isEmpty())
        assertFalse(vm.uiState.value is CancelDropInUiState.Cancelling)
    }
}
