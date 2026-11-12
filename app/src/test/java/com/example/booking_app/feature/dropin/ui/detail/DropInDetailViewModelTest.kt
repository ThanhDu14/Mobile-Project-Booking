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
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T019: chi tiết buổi (US1, AC 1.4, 1.7, 2.7). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionFlow = MutableStateFlow<DropInSession?>(session())
    private val mineFlow = MutableStateFlow<DropInRegistration?>(null)
    private val mineRequests = mutableListOf<String>()
    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))

    private fun TestScope.viewModel(
        observeSession: ObserveDropInSession = ObserveDropInSession { sessionFlow },
    ): DropInDetailViewModel {
        val vm = DropInDetailViewModel(
            SavedStateHandle(mapOf(DropInDetailViewModel.ARG_SESSION_ID to "s1")),
            observeSession,
            ObserveMyDropInRegistration { id ->
                mineRequests += id
                mineFlow
            },
            session,
            FakeClock { NOW.toEpochMilli() },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun DropInDetailViewModel.content() = uiState.value as DropInDetailUiState.Content

    @Test
    fun `con cho va chua dang ky thi REGISTER`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.REGISTER, vm.content().action)
        assertEquals(5, vm.content().session.remaining)
    }

    @Test
    fun `du nguoi thi FULL`() = runTest {
        sessionFlow.value = session(capacity = 12, registeredCount = 12, status = SessionStatus.FULL)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.FULL, vm.content().action)
    }

    @Test
    fun `so cho cap nhat realtime`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        sessionFlow.value = session(registeredCount = 11)
        advanceUntilIdle()
        assertEquals(1, vm.content().session.remaining)

        sessionFlow.value = session(registeredCount = 12)
        advanceUntilIdle()
        assertEquals(DetailAction.FULL, vm.content().action)
    }

    @Test
    fun `da bat dau thi CLOSED`() = runTest {
        sessionFlow.value = session(startAt = NOW.minusSeconds(60))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.CLOSED, vm.content().action)
    }

    @Test
    fun `bi huy thi CLOSED`() = runTest {
        sessionFlow.value = session(status = SessionStatus.CANCELLED)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.CLOSED, vm.content().action)
    }

    @Test
    fun `co so ngung hoat dong thi CLOSED`() = runTest {
        sessionFlow.value = session(venueActive = false)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.CLOSED, vm.content().action)
    }

    @Test
    fun `da co dang ky con hieu luc thi VIEW_TICKET`() = runTest {
        mineFlow.value = registration()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.VIEW_TICKET, vm.content().action)
        assertEquals(registration(), vm.content().myRegistration)
    }

    @Test
    fun `dang ky da huy thi lai duoc REGISTER`() = runTest {
        mineFlow.value = registration(status = RegistrationStatus.CANCELLED)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.REGISTER, vm.content().action)
    }

    @Test
    fun `chu co so xem buoi cua minh thi khong co REGISTER`() = runTest {
        session.sessionState.value = SessionState.SignedIn("owner1", AppRole.OWNER, emailVerified = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.OWN_SESSION, vm.content().action)
    }

    @Test
    fun `khach khong doc dang ky cua minh`() = runTest {
        session.sessionState.value = SessionState.SignedOut(null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DetailAction.REGISTER, vm.content().action)
        assertTrue(mineRequests.isEmpty())
    }

    @Test
    fun `khach bam dang ky thi yeu cau dang nhap`() = runTest {
        session.sessionState.value = SessionState.SignedOut(null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRegisterClicked()
            assertEquals(DropInDetailEvent.RequireLogin("s1"), awaitItem())
        }
    }

    @Test
    fun `da dang nhap bam dang ky thi mo xac nhan`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRegisterClicked()
            assertEquals(DropInDetailEvent.OpenConfirm("s1"), awaitItem())
        }
    }

    @Test
    fun `bam xem ve thi mo ve`() = runTest {
        mineFlow.value = registration()
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onViewTicketClicked()
            assertEquals(DropInDetailEvent.OpenTicket("s1"), awaitItem())
        }
    }

    @Test
    fun `buoi khong ton tai thi NotFound`() = runTest {
        sessionFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DropInDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `loi doc buoi thi Error, thu lai thi Content`() = runTest {
        var fail = true
        val vm = viewModel(
            observeSession = ObserveDropInSession {
                flow {
                    if (fail) error("boom")
                    emit(session())
                }
            },
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DropInDetailUiState.Error)

        fail = false
        vm.retry()
        advanceUntilIdle()

        assertEquals(DetailAction.REGISTER, vm.content().action)
    }
}
