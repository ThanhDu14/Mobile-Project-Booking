package com.example.booking_app.feature.auth.ui.otp

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSendResult
import com.example.booking_app.feature.auth.domain.usecase.SendOtpUseCase
import com.example.booking_app.feature.auth.domain.usecase.VerifyOtpUseCase
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OtpViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()
    private val activity = mockk<Activity>(relaxed = true)

    private fun args(sentAt: Long = 0L) = SavedStateHandle(
        mapOf("phoneE164" to "+84912345678", "verificationId" to "v1", "sentAtElapsed" to sentAt),
    )

    private fun TestScope.viewModel(handle: SavedStateHandle = args()): OtpViewModel {
        val clock = FakeClock { testScheduler.currentTime }
        return OtpViewModel(
            verifyOtp = VerifyOtpUseCase(repository, sessionManager, clock),
            sendOtp = SendOtpUseCase(repository, sessionManager, clock),
            clock = clock,
            savedStateHandle = handle,
        )
    }

    private fun OtpViewModel.enter(code: String = "123456") {
        onCodeChanged(code)
        onSubmit()
    }

    @Test
    fun `so hien dang che bot`() = runTest {
        assertEquals("+84 9•• ••• 678", viewModel().uiState.value.maskedPhone)
    }

    @Test
    fun `Editing roi Verifying, dung ma thi dang nhap`() = runTest {
        val vm = viewModel()
        testScheduler.runCurrent()
        assertEquals(OtpState.Editing, vm.uiState.value.state)
        vm.enter()
        assertEquals(OtpState.Verifying, vm.uiState.value.state)
        testScheduler.runCurrent()
        assertEquals(listOf("v1" to "123456"), repository.verifyOtpCalls)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `sai ma thi WrongCode kem so lan con lai, sai 5 lan thi Locked5`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.InvalidCode)
        val vm = viewModel()
        repeat(4) { i ->
            vm.enter("00000$i")
            testScheduler.runCurrent()
            assertEquals(OtpState.WrongCode, vm.uiState.value.state)
            assertEquals(4 - i, vm.uiState.value.remainingAttempts)
        }
        vm.enter("000009")
        testScheduler.runCurrent()
        assertEquals(OtpState.Locked5, vm.uiState.value.state)
        assertEquals(0, vm.uiState.value.remainingAttempts)

        // Đã khóa thì nhập nữa cũng không gửi.
        vm.enter("123456")
        testScheduler.runCurrent()
        assertEquals(5, repository.verifyOtpCalls.size)
    }

    @Test
    fun `qua 5 phut thi Expired`() = runTest {
        val vm = viewModel()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(300_000)
        testScheduler.runCurrent()
        assertEquals(OtpState.Expired, vm.uiState.value.state)
        vm.enter()
        testScheduler.runCurrent()
        assertEquals(0, repository.verifyOtpCalls.size)
    }

    @Test
    fun `Gui lai ma chi bat sau 60 giay, gui lai thi wrongCount ve 0`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.InvalidCode)
        val vm = viewModel()
        testScheduler.runCurrent()
        assertEquals(60, vm.uiState.value.resendInSeconds)
        vm.enter("000000")
        testScheduler.runCurrent()
        assertEquals(4, vm.uiState.value.remainingAttempts)

        vm.onResend(activity)
        testScheduler.runCurrent()
        assertEquals(0, repository.sendOtpCalls.size)

        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertEquals(0, vm.uiState.value.resendInSeconds)
        repository.sendOtpResult = AuthResult.Success(OtpSendResult.CodeSent("v2", null))
        vm.onResend(activity)
        testScheduler.runCurrent()
        assertEquals(listOf<Pair<String, Any?>>("+84912345678" to null), repository.sendOtpCalls)
        with(vm.uiState.value) {
            assertEquals(OtpState.Editing, state)
            assertEquals(5, remainingAttempts)
            assertEquals(60, resendInSeconds)
            assertEquals("", code)
        }

        // Mã mới dùng verificationId mới.
        repository.verifyOtpResult = AuthResult.Success(Unit)
        vm.enter("654321")
        testScheduler.runCurrent()
        assertEquals("v2" to "654321", repository.verifyOtpCalls.last())
    }

    @Test
    fun `phien OTP giu qua SavedStateHandle, ma da nhap thi khong`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.InvalidCode)
        val handle = args()
        val vm = viewModel(handle)
        vm.enter("000000")
        testScheduler.runCurrent()
        vm.onCodeChanged("12")

        val restored = viewModel(handle)
        testScheduler.runCurrent()
        assertEquals(4, restored.uiState.value.remainingAttempts)
        assertEquals("", restored.uiState.value.code)
        assertFalse(handle.keys().any { it.contains("code", ignoreCase = true) })
    }

    @Test
    fun `ma chi nhan chu so, toi da 6 ky tu`() = runTest {
        val vm = viewModel()
        vm.onCodeChanged("12a3456789")
        assertEquals("123456", vm.uiState.value.code)
        assertTrue(vm.uiState.value.canSubmit)
        vm.onCodeChanged("123")
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `mat mang thi snackbar, khong tinh la sai`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.Network)
        val vm = viewModel()
        vm.events.test {
            vm.enter()
            assertEquals(OtpEvent.ShowNetworkError, awaitItem())
        }
        assertEquals(OtpState.Editing, vm.uiState.value.state)
        assertEquals(5, vm.uiState.value.remainingAttempts)
    }
}
