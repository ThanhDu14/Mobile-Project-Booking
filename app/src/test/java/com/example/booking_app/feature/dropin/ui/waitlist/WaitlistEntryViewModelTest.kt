package com.example.booking_app.feature.dropin.ui.waitlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import com.example.booking_app.feature.dropin.domain.usecase.LeaveWaitlist
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Màn hình lượt chờ (spec 081): T018 hiển thị, T029 được đẩy lên, T037 rời hàng chờ, T048 kết quả cuối.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WaitlistEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionFlow = MutableStateFlow<DropInSession?>(
        session(capacity = 12, registeredCount = 12, status = SessionStatus.FULL, waitlistTickets = listOf(3, 5, 9)),
    )
    private val mineFlow = MutableStateFlow<DropInRegistration?>(waiting(ticket = 5))

    private val leaveCalls = mutableListOf<Pair<String, String>>()
    private val leaveResults = ArrayDeque<Result<DropInRegistration>>()
    private var leaveGate: CompletableDeferred<Unit>? = null
    private val leave = LeaveWaitlist { sessionId, requestId ->
        leaveCalls += sessionId to requestId
        leaveGate?.await()
        leaveResults.removeFirstOrNull() ?: Result.success(
            registration(status = RegistrationStatus.CANCELLED, qrToken = null, waitlistEndReason = WaitlistEndReason.LEFT),
        )
    }

    private fun waiting(ticket: Int = 5, partySize: Int = 2, method: PaymentMethod = PaymentMethod.AT_VENUE) =
        registration(
            partySize = partySize,
            status = RegistrationStatus.WAITLISTED,
            paymentMethod = method,
            qrToken = null,
            waitlistTicket = ticket,
        )

    private fun handle() = SavedStateHandle(mapOf(WaitlistEntryViewModel.ARG_SESSION_ID to "s1"))

    private fun viewModel(handle: SavedStateHandle = handle()) = WaitlistEntryViewModel(
        handle,
        ObserveDropInSession { sessionFlow },
        ObserveMyDropInRegistration { mineFlow },
        leave,
    )

    private fun WaitlistEntryViewModel.content() = uiState.value as WaitlistEntryUiState.Content

    // ---------- T018: hiển thị ----------

    @Test
    fun `Loading roi Content voi vi tri, tong, so nguoi, so tien, cach thanh toan`() = runTest {
        val vm = viewModel()
        assertEquals(WaitlistEntryUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val content = vm.content()
        assertEquals(2, content.position)
        assertEquals(3, content.total)
        assertEquals(2, content.partySize)
        assertEquals(120_000L, content.amount)
        assertEquals(PaymentMethod.AT_VENUE, content.paymentMethod)
    }

    @Test
    fun `vi tri cap nhat khi mang waitlistTickets doi`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, vm.content().position)

        sessionFlow.value = session(status = SessionStatus.FULL, waitlistTickets = listOf(5, 9))
        advanceUntilIdle()

        assertEquals(1, vm.content().position)
        assertEquals(2, vm.content().total)
    }

    @Test
    fun `khong co luot cho thi Empty`() = runTest {
        mineFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `loi doc du lieu thi Error va thu lai duoc`() = runTest {
        var attempts = 0
        val vm = WaitlistEntryViewModel(
            handle(),
            ObserveDropInSession { sessionFlow },
            ObserveMyDropInRegistration {
                flow {
                    if (attempts++ == 0) throw DropInError.Network()
                    emit(waiting())
                }
            },
            leave,
        )
        advanceUntilIdle()
        assertTrue((vm.uiState.value as WaitlistEntryUiState.Error).cause is DropInError.Network)

        vm.retry()
        advanceUntilIdle()

        assertEquals(2, vm.content().position)
    }

    // ---------- T048: kết quả cuối ----------

    @Test
    fun `luot bi dong thi hien ket qua cuoi theo ly do`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        mineFlow.value = registration(
            status = RegistrationStatus.CANCELLED,
            qrToken = null,
            waitlistTicket = 5,
            waitlistEndReason = WaitlistEndReason.CLOSED,
        )
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Ended(WaitlistEndReason.CLOSED), vm.uiState.value)
    }

    @Test
    fun `buoi bi huy thi ket qua cuoi la SESSION_CANCELLED du thieu ly do`() = runTest {
        sessionFlow.value = session(status = SessionStatus.CANCELLED, waitlistTickets = emptyList())
        mineFlow.value = registration(status = RegistrationStatus.CANCELLED, qrToken = null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Ended(WaitlistEndReason.SESSION_CANCELLED), vm.uiState.value)
    }

    @Test
    fun `ma tu roi do trung gio duoc giu lai de hien thi`() = runTest {
        mineFlow.value = registration(
            status = RegistrationStatus.CANCELLED,
            qrToken = null,
            waitlistEndReason = WaitlistEndReason.AUTO_LEFT_OVERLAP,
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Ended(WaitlistEndReason.AUTO_LEFT_OVERLAP), vm.uiState.value)
    }

    // ---------- T029: được đẩy lên ----------

    @Test
    fun `luot chuyen REGISTERED thi phat su kien Promoted mo ve`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            mineFlow.value = registration(promotedAt = NOW)
            advanceUntilIdle()
            assertEquals(WaitlistEntryEvent.Promoted("s1"), awaitItem())
            expectNoEvents()
        }
    }

    // ---------- T037: rời hàng chờ ----------

    @Test
    fun `xac nhan roi thi Leaving roi Left va gui requestId da luu`() = runTest {
        leaveGate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmLeave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is WaitlistEntryUiState.Leaving)

        leaveGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Left, vm.uiState.value)
        assertEquals(listOf("s1" to vm.requestId), leaveCalls)
    }

    @Test
    fun `sau khi roi thi du lieu realtime khong ghi de trang thai Left`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmLeave()
        advanceUntilIdle()
        mineFlow.value = registration(
            status = RegistrationStatus.CANCELLED,
            qrToken = null,
            waitlistEndReason = WaitlistEndReason.LEFT,
        )
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Left, vm.uiState.value)
    }

    @Test
    fun `da duoc nhan thi bao ALREADY_PROMOTED roi mo ve, khong Left`() = runTest {
        leaveResults += Result.failure(DropInError.AlreadyPromoted())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.confirmLeave()
            advanceUntilIdle()
            assertEquals(WaitlistEntryEvent.AlreadyPromoted("s1"), awaitItem())
        }
        assertTrue(vm.uiState.value !is WaitlistEntryUiState.Left)
    }

    @Test
    fun `mat mang giu hop thoai va thu lai cung requestId`() = runTest {
        leaveResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmLeave()
        advanceUntilIdle()

        val failed = vm.content()
        assertTrue(failed.leaveError is DropInError.Network)
        assertEquals(2, failed.position)

        vm.confirmLeave()
        advanceUntilIdle()

        assertEquals(WaitlistEntryUiState.Left, vm.uiState.value)
        assertEquals(2, leaveCalls.size)
        assertEquals(leaveCalls[0], leaveCalls[1])
    }

    @Test
    fun `bam xac nhan nhieu lan khi dang roi chi gui mot lan`() = runTest {
        leaveGate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmLeave()
        vm.confirmLeave()
        vm.confirmLeave()
        advanceUntilIdle()
        leaveGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, leaveCalls.size)
    }

    @Test
    fun `chua co noi dung thi bam roi bi bo qua`() = runTest {
        mineFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmLeave()
        advanceUntilIdle()

        assertTrue(leaveCalls.isEmpty())
        assertNull((vm.uiState.value as? WaitlistEntryUiState.Content)?.leaveError)
    }

    @Test
    fun `requestId giu nguyen qua SavedStateHandle`() = runTest {
        val handle = handle()
        val first = viewModel(handle)
        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(first.requestId, second.requestId)
    }
}
