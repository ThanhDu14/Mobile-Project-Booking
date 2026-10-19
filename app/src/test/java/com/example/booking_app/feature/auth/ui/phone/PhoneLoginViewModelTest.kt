package com.example.booking_app.feature.auth.ui.phone

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
import com.example.booking_app.feature.auth.domain.model.OtpSession
import com.example.booking_app.feature.auth.domain.usecase.SendOtpUseCase
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneLoginViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val activity = mockk<Activity>(relaxed = true)

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        PhoneLoginViewModel(SendOtpUseCase(repository, FakeSessionManager(), FakeClock { 5L }), handle)

    @Test
    fun `so sai dinh dang thi loi duoi o, khong gui`() = runTest {
        val vm = viewModel()
        vm.onPhoneChanged("0212 345 678")
        vm.onSubmit(activity)
        testScheduler.advanceUntilIdle()
        assertEquals(ValidationError.PHONE_FORMAT, vm.uiState.value.phoneError)
        assertEquals(0, repository.sendOtpCalls.size)

        vm.onPhoneChanged("0912 345 678")
        assertNull(vm.uiState.value.phoneError)
    }

    @Test
    fun `bam Gui ma hai lan chi gui mot lan`() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel().apply { onPhoneChanged("0912345678") }
        vm.onSubmit(activity)
        vm.onSubmit(activity)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isSending)
        repository.gate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.sendOtpCalls.size)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun `gui duoc ma thi sang man hinh OTP voi phien moi`() = runTest {
        repository.sendOtpResult = AuthResult.Success(OtpSendResult.CodeSent("v1", null))
        val vm = viewModel().apply { onPhoneChanged("0912 345 678") }
        vm.events.test {
            vm.onSubmit(activity)
            assertEquals(PhoneLoginEvent.OpenOtp(OtpSession("+84912345678", "v1", null, 5L, 0)), awaitItem())
        }
    }

    @Test
    fun `go so khong co 0 dau vi o +84 da hien san cung duoc`() = runTest {
        val vm = viewModel().apply { onPhoneChanged("901 234 567") }
        vm.onSubmit(activity)
        testScheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.phoneError)
        assertEquals("+84901234567", repository.sendOtpCalls.single().first)
    }

    @Test
    fun `so khong phai so thu nghiem thi bao loi duoi o (R8)`() = runTest {
        repository.sendOtpResult = AuthResult.Failure(AuthError.PhoneNotSupported)
        val vm = viewModel().apply { onPhoneChanged("0912345678") }
        vm.onSubmit(activity)
        testScheduler.advanceUntilIdle()
        assertEquals(AuthError.PhoneNotSupported, vm.uiState.value.sendError)
    }

    @Test
    fun `mat mang thi snackbar mang`() = runTest {
        repository.sendOtpResult = AuthResult.Failure(AuthError.Network)
        val vm = viewModel().apply { onPhoneChanged("0912345678") }
        vm.events.test {
            vm.onSubmit(activity)
            assertEquals(PhoneLoginEvent.ShowNetworkError, awaitItem())
        }
    }

    @Test
    fun `so dien thoai giu qua SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).onPhoneChanged("0912345678")
        assertEquals("0912345678", viewModel(handle).uiState.value.phone)
    }
}
