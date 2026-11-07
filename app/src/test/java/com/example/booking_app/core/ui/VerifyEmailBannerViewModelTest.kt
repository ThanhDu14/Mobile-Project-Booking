package com.example.booking_app.core.ui

import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.AuthUser
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.VerificationEmailSender
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VerifyEmailBannerViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val passwordUser = AuthUser(
        uid = "u1",
        email = "nam@email.com",
        phone = null,
        displayName = "Lê Hoàng Nam",
        providers = listOf("password"),
        isEmailVerified = false,
    )

    private val sessionManager = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = false))
    private val authSource = FakeSessionAuthSource().apply { authState.value = passwordUser }
    private val prefs = FakeSessionPrefs()

    private class FakeSender(private val prefs: FakeSessionPrefs, private val now: () -> Long) : VerificationEmailSender {
        var result = true
        var calls = 0
        override suspend fun send(): Boolean {
            calls++
            if (result) prefs.lastVerificationEmailAt = now()
            return result
        }
    }

    private fun TestScope.sender() = FakeSender(prefs) { testScheduler.currentTime }

    private fun TestScope.viewModel(sender: FakeSender = sender()) = VerifyEmailBannerViewModel(
        sessionManager = sessionManager,
        authSource = authSource,
        prefs = prefs,
        sender = sender,
        clock = FakeClock { testScheduler.currentTime },
    )

    @Test
    fun `hien voi tai khoan mat khau chua xac minh`() = runTest {
        val vm = viewModel()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.visible)
    }

    @Test
    fun `an voi tai khoan Google hoac chi co so dien thoai (FR-009)`() = runTest {
        authSource.authState.value = passwordUser.copy(providers = listOf("google.com"))
        val vm = viewModel()
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.visible)

        authSource.authState.value = passwordUser.copy(email = null, phone = "+84901234567", providers = listOf("phone"))
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.visible)
    }

    @Test
    fun `an khi da xac minh hoac khong dang nhap`() = runTest {
        val vm = viewModel()
        sessionManager.sessionState.value = SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.visible)

        sessionManager.sessionState.value = SessionState.SignedOut(null)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.visible)
    }

    @Test
    fun `onResume goi reload, da xac minh thi lam moi token roi an`() = runTest {
        val vm = viewModel()
        testScheduler.runCurrent()

        vm.onResume()
        testScheduler.runCurrent()
        assertEquals(1, authSource.reloadCalls)
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
        assertTrue(vm.uiState.value.visible)

        authSource.reloadedUser = passwordUser.copy(isEmailVerified = true)
        vm.onResume()
        testScheduler.runCurrent()
        assertEquals(listOf(true), sessionManager.refreshCalls)
        assertFalse(vm.uiState.value.visible)
    }

    @Test
    fun `Gui lai email thi dem nguoc tu 60 giay`() = runTest {
        val sender = sender()
        val vm = viewModel(sender)
        testScheduler.runCurrent()
        assertEquals(0, vm.uiState.value.resendInSeconds)

        vm.events.test {
            vm.onResend()
            testScheduler.runCurrent()
            assertEquals(VerifyEmailBannerEvent.Sent, awaitItem())
        }
        assertEquals(1, sender.calls)
        assertEquals(60, vm.uiState.value.resendInSeconds)

        // Đang đếm thì bấm không gửi.
        vm.onResend()
        testScheduler.runCurrent()
        assertEquals(1, sender.calls)

        testScheduler.advanceTimeBy(15_000)
        testScheduler.runCurrent()
        assertEquals(45, vm.uiState.value.resendInSeconds)
        testScheduler.advanceTimeBy(45_000)
        testScheduler.runCurrent()
        assertEquals(0, vm.uiState.value.resendInSeconds)
    }

    @Test
    fun `tao lai ViewModel van con dem vi moc lay tu DataStore`() = runTest {
        testScheduler.advanceTimeBy(100_000)
        prefs.lastVerificationEmailAt = testScheduler.currentTime - 20_000
        val vm = viewModel()
        testScheduler.runCurrent()
        assertEquals(40, vm.uiState.value.resendInSeconds)
    }

    @Test
    fun `gui loi thi bao loi, khong dem nguoc`() = runTest {
        val sender = sender().apply { result = false }
        val vm = viewModel(sender)
        vm.events.test {
            vm.onResend()
            testScheduler.runCurrent()
            assertEquals(VerifyEmailBannerEvent.SendFailed, awaitItem())
        }
        assertEquals(0, vm.uiState.value.resendInSeconds)
        assertFalse(vm.uiState.value.isSending)
    }
}
