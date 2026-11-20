package com.example.booking_app.feature.dropin.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.confirm.ConfirmMode
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** T016: chi tiết buổi với hàng chờ (spec 081 US1, AC 1.1, 1.4, 1.5, 4.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInDetailViewModelWaitlistTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun fullSession(
        tickets: List<Int> = emptyList(),
        startInSeconds: Long = 3 * 3600L,
        status: SessionStatus = SessionStatus.FULL,
    ) = session(
        startAt = NOW.plusSeconds(startInSeconds),
        capacity = 12,
        registeredCount = 12,
        status = status,
        waitlistTickets = tickets,
    )

    private val sessionFlow = MutableStateFlow<DropInSession?>(fullSession())
    private val mineFlow = MutableStateFlow<DropInRegistration?>(null)
    private val sessionManager = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))

    private fun TestScope.viewModel(): DropInDetailViewModel {
        val vm = DropInDetailViewModel(
            SavedStateHandle(mapOf(DropInDetailViewModel.ARG_SESSION_ID to "s1")),
            ObserveDropInSession { sessionFlow },
            ObserveMyDropInRegistration { mineFlow },
            sessionManager,
            FakeClock { NOW.toEpochMilli() },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun DropInDetailViewModel.content() = uiState.value as DropInDetailUiState.Content

    private fun waiting(ticket: Int): DropInRegistration =
        registration(status = RegistrationStatus.WAITLISTED, qrToken = null, waitlistTicket = ticket)

    @Test
    fun `buoi FULL hang cho mo chua co luot thi JOIN_WAITLIST kem so nguoi dang cho`() = runTest {
        sessionFlow.value = fullSession(tickets = listOf(1, 2, 4))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.JOIN_WAITLIST, vm.content().action)
        assertEquals(3, vm.content().session.waitlistCount)
        assertNull(vm.content().waitlist)
    }

    @Test
    fun `dang WAITLISTED thi VIEW_WAITLIST kem vi tri va vi tri doi theo mang ve`() = runTest {
        sessionFlow.value = fullSession(tickets = listOf(1, 2, 3))
        mineFlow.value = waiting(2)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.VIEW_WAITLIST, vm.content().action)
        assertEquals(WaitlistPosition(2, 3), vm.content().waitlist)

        sessionFlow.value = fullSession(tickets = listOf(2, 3))
        advanceUntilIdle()
        assertEquals(WaitlistPosition(1, 2), vm.content().waitlist)
    }

    @Test
    fun `buoi OPEN chi co REGISTER, khong co JOIN_WAITLIST`() = runTest {
        sessionFlow.value = session(status = SessionStatus.OPEN)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.REGISTER, vm.content().action)
    }

    @Test
    fun `con 30 phut tro xuong thi khong con JOIN_WAITLIST`() = runTest {
        sessionFlow.value = fullSession(startInSeconds = 30 * 60L)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DetailAction.FULL, vm.content().action)

        sessionFlow.value = fullSession(startInSeconds = 10 * 60L)
        advanceUntilIdle()
        assertEquals(DetailAction.FULL, vm.content().action)
    }

    @Test
    fun `buoi bi huy thi khong co JOIN_WAITLIST`() = runTest {
        sessionFlow.value = fullSession(status = SessionStatus.CANCELLED)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.CLOSED, vm.content().action)
    }

    @Test
    fun `luot cho cua buoi bi huy thi CLOSED, khong VIEW_WAITLIST`() = runTest {
        sessionFlow.value = fullSession(status = SessionStatus.CANCELLED)
        mineFlow.value = waiting(1)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.CLOSED, vm.content().action)
    }

    @Test
    fun `chu co so xem buoi cua minh thi khong co JOIN_WAITLIST`() = runTest {
        sessionManager.sessionState.value = SessionState.SignedIn("owner1", AppRole.OWNER, emailVerified = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.OWN_SESSION, vm.content().action)
    }

    @Test
    fun `luot cho da bi huy thi lai duoc JOIN_WAITLIST`() = runTest {
        mineFlow.value = registration(status = RegistrationStatus.CANCELLED, qrToken = null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.JOIN_WAITLIST, vm.content().action)
    }

    @Test
    fun `da duoc nhan vao buoi thi VIEW_TICKET`() = runTest {
        mineFlow.value = registration(promotedAt = NOW)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.VIEW_TICKET, vm.content().action)
    }

    @Test
    fun `khach bam Vao hang cho thi yeu cau dang nhap voi che do WAITLIST`() = runTest {
        sessionManager.sessionState.value = SessionState.SignedOut(null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinWaitlistClicked()
            assertEquals(DropInDetailEvent.RequireLogin("s1", ConfirmMode.WAITLIST), awaitItem())
        }
    }

    @Test
    fun `da dang nhap bam Vao hang cho thi mo xac nhan che do WAITLIST`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinWaitlistClicked()
            assertEquals(DropInDetailEvent.OpenConfirm("s1", ConfirmMode.WAITLIST), awaitItem())
        }
    }

    @Test
    fun `bam Xem luot cho thi mo man hinh hang cho`() = runTest {
        sessionFlow.value = fullSession(tickets = listOf(1))
        mineFlow.value = waiting(1)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onViewWaitlistClicked()
            assertEquals(DropInDetailEvent.OpenWaitlist("s1"), awaitItem())
        }
    }

    @Test
    fun `nut Vao hang cho bi bo qua khi khong o trang thai JOIN_WAITLIST`() = runTest {
        sessionFlow.value = session(status = SessionStatus.OPEN)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinWaitlistClicked()
            expectNoEvents()
        }
    }
}
