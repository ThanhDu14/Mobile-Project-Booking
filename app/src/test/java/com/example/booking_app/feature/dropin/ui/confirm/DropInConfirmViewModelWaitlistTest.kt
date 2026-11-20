package com.example.booking_app.feature.dropin.ui.confirm

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistJoinResult
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.example.booking_app.feature.dropin.domain.usecase.CheckDropInWarnings
import com.example.booking_app.feature.dropin.domain.usecase.JoinWaitlist
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.RegisterDropIn
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T017: xác nhận vào hàng chờ (spec 081 US1, AC 1.3, 1.5, 1.6, FR-002, FR-016). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInConfirmViewModelWaitlistTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionFlow = MutableStateFlow<DropInSession?>(
        session(capacity = 12, registeredCount = 12, status = SessionStatus.FULL),
    )
    private val joinRequests = mutableListOf<RegisterRequest>()
    private val joinResults = ArrayDeque<Result<WaitlistJoinResult>>()
    private val join = JoinWaitlist { req ->
        joinRequests += req
        joinResults.removeFirstOrNull() ?: Result.success(joined(req.partySize))
    }
    private var registerCalls = 0
    private val register = RegisterDropIn {
        registerCalls++
        Result.success(registration())
    }

    private fun joined(partySize: Int): WaitlistJoinResult = WaitlistJoinResult(
        registration = registration(
            partySize = partySize,
            status = RegistrationStatus.WAITLISTED,
            qrToken = null,
            waitlistTicket = 3,
        ),
        position = WaitlistPosition(position = 3, total = 3),
    )

    private fun handle(mode: ConfirmMode = ConfirmMode.WAITLIST) = SavedStateHandle(
        mapOf(DropInConfirmViewModel.ARG_SESSION_ID to "s1", DropInConfirmViewModel.ARG_MODE to mode),
    )

    private fun viewModel(handle: SavedStateHandle = handle()) = DropInConfirmViewModel(
        handle,
        ObserveDropInSession { sessionFlow },
        register,
        CheckDropInWarnings { emptyList() },
        join,
    )

    private fun DropInConfirmViewModel.form(): ConfirmForm = when (val s = uiState.value) {
        is DropInConfirmUiState.Editing -> s.form
        is DropInConfirmUiState.Submitting -> s.form
        is DropInConfirmUiState.Failed -> s.form
        else -> throw AssertionError("Không có form: $s")
    }

    @Test
    fun `che do WAITLIST thi toi da 4 nguoi du buoi khong con cho nao`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ConfirmMode.WAITLIST, vm.form().mode)
        assertEquals(4, vm.form().maxParty)
        assertTrue(vm.form().canSubmit)
        vm.setPartySize(4)
        assertEquals(4, vm.form().partySize)
        vm.increment()
        assertEquals(4, vm.form().partySize)
    }

    @Test
    fun `so tien se ghi nhan bang gia nhan so nguoi`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setPartySize(3)

        assertEquals(180_000L, vm.form().total)
    }

    @Test
    fun `mac dinh la che do REGISTER khi khong co argument mode`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf(DropInConfirmViewModel.ARG_SESSION_ID to "s1")))
        advanceUntilIdle()

        assertEquals(ConfirmMode.REGISTER, vm.form().mode)
        assertEquals(0, vm.form().maxParty)
    }

    @Test
    fun `gui yeu cau voi joinWaitlist true va khong goi dang ky thuong`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setPartySize(2)
        vm.setPaymentMethod(PaymentMethod.AT_VENUE)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToWaitlist("s1"), awaitItem())
        }
        assertEquals(
            RegisterRequest("s1", 2, PaymentMethod.AT_VENUE, vm.requestId, joinWaitlist = true),
            joinRequests.single(),
        )
        assertEquals(0, registerCalls)
    }

    @Test
    fun `requestId giu nguyen qua SavedStateHandle khi thu lai`() = runTest {
        val handle = handle()
        val first = viewModel(handle)
        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(first.requestId, second.requestId)
    }

    @Test
    fun `mat mang giu lua chon va thu lai cung requestId`() = runTest {
        joinResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()
        vm.setPartySize(3)
        vm.setPaymentMethod(PaymentMethod.AT_VENUE)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NetworkError, awaitItem())

            val editing = vm.uiState.value as DropInConfirmUiState.Editing
            assertEquals(3, editing.form.partySize)
            assertEquals(PaymentMethod.AT_VENUE, editing.form.paymentMethod)

            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToWaitlist("s1"), awaitItem())
        }
        assertEquals(2, joinRequests.size)
        assertEquals(joinRequests[0], joinRequests[1])
    }

    @Test
    fun `buoi con cho thi chuyen sang che do REGISTER`() = runTest {
        joinResults += Result.failure(DropInError.SessionHasSpots(remaining = 2))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.SwitchMode("s1", ConfirmMode.REGISTER), awaitItem())
        }
    }

    @Test
    fun `hang cho da dong thi Failed`() = runTest {
        joinResults += Result.failure(DropInError.WaitlistClosed())
        val vm = viewModel()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        val failed = vm.uiState.value as DropInConfirmUiState.Failed
        assertTrue(failed.reason is DropInError.WaitlistClosed)
        assertEquals(1, failed.form.partySize)
    }

    @Test
    fun `da co luot cho thi mo man hinh hang cho`() = runTest {
        joinResults += Result.failure(DropInError.AlreadyWaitlisted())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToWaitlist("s1"), awaitItem())
        }
    }

    @Test
    fun `da dang ky roi thi mo ve`() = runTest {
        joinResults += Result.failure(DropInError.AlreadyRegistered())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToTicket("s1"), awaitItem())
        }
    }

    @Test
    fun `dang ky thuong gap SESSION_FULL con mo hang cho thi cho chuyen sang che do WAITLIST`() = runTest {
        val registerFull = RegisterDropIn { Result.failure(DropInError.SessionFull(remaining = 0, waitlistOpen = true)) }
        sessionFlow.value = session(capacity = 12, registeredCount = 11)
        val vm = DropInConfirmViewModel(
            handle(ConfirmMode.REGISTER),
            ObserveDropInSession { sessionFlow },
            registerFull,
            CheckDropInWarnings { emptyList() },
            join,
        )
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()
        val failed = vm.uiState.value as DropInConfirmUiState.Failed
        assertTrue((failed.reason as DropInError.SessionFull).waitlistOpen)

        vm.events.test {
            vm.onJoinWaitlistInstead()
            assertEquals(DropInConfirmEvent.SwitchMode("s1", ConfirmMode.WAITLIST), awaitItem())
        }
    }
}
