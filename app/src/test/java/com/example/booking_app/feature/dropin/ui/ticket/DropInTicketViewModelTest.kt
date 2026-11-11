package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T040: vé vãng lai (AC 2.3, 2.4, 3.5). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInTicketViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mineFlow = MutableStateFlow<DropInRegistration?>(registration())
    private val sessionFlow = MutableStateFlow<DropInSession?>(session())

    private fun TestScope.viewModel(
        observeSession: ObserveDropInSession = ObserveDropInSession { sessionFlow },
        justRegistered: Boolean? = null,
    ): DropInTicketViewModel {
        val args = buildMap<String, Any?> {
            put(DropInTicketViewModel.ARG_SESSION_ID, "s1")
            if (justRegistered != null) put(DropInTicketViewModel.ARG_JUST_REGISTERED, justRegistered)
        }
        val vm = DropInTicketViewModel(
            SavedStateHandle(args),
            ObserveMyDropInRegistration { mineFlow },
            observeSession,
            FakeClock { NOW.toEpochMilli() },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun DropInTicketViewModel.content() = uiState.value as DropInTicketUiState.Content

    @Test
    fun `dang ky con hieu luc thi co QR va huy duoc`() = runTest {
        val vm = viewModel(justRegistered = true)
        advanceUntilIdle()

        assertEquals("qr-token-123", vm.content().qrToken)
        assertTrue(vm.content().labels.isEmpty())
        assertTrue(vm.content().canCancel)
        assertTrue(vm.justRegistered)
    }

    @Test
    fun `buoi bi huy thi khong co QR va co nhan`() = runTest {
        sessionFlow.value = session(status = SessionStatus.CANCELLED)
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().qrToken)
        assertEquals(NoQrReason.SESSION_CANCELLED, vm.content().noQrReason)
        assertEquals(listOf(TicketLabel.SESSION_CANCELLED), vm.content().labels)
        assertFalse(vm.content().canCancel)
        assertFalse(vm.justRegistered)
    }

    @Test
    fun `buoi doi gio thi co nhan Buoi da thay doi`() = runTest {
        mineFlow.value = registration(sessionChanged = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(TicketLabel.SESSION_CHANGED), vm.content().labels)
    }

    @Test
    fun `da check-in thi van co QR nhung khong huy duoc`() = runTest {
        mineFlow.value = registration(status = RegistrationStatus.CHECKED_IN)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("qr-token-123", vm.content().qrToken)
        assertFalse(vm.content().canCancel)
    }

    @Test
    fun `chua co dang ky thi NotFound`() = runTest {
        mineFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DropInTicketUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `doc buoi loi van hien ve`() = runTest {
        val vm = viewModel(observeSession = ObserveDropInSession { flow<DropInSession?> { error("offline") } })
        advanceUntilIdle()

        assertNull(vm.content().session)
        assertEquals("qr-token-123", vm.content().qrToken)
    }
}
