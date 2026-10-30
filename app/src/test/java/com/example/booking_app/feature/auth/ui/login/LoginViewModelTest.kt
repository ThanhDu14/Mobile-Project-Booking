package com.example.booking_app.feature.auth.ui.login

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.FakeGoogleCredentialDataSource
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import com.example.booking_app.feature.auth.domain.usecase.LinkGoogleToPasswordUseCase
import com.example.booking_app.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.example.booking_app.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()

    private val google = FakeGoogleCredentialDataSource()
    private val activity = mockk<Activity>(relaxed = true)

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = LoginViewModel(
        signInWithEmail = SignInWithEmailUseCase(repository, sessionManager),
        signInWithGoogle = SignInWithGoogleUseCase(google, repository, sessionManager),
        linkGoogleToPassword = LinkGoogleToPasswordUseCase(repository, sessionManager, CoroutineScope(mainRule.dispatcher)),
        savedStateHandle = handle,
    )

    private fun LoginViewModel.fill(email: String = "nam@example.com", password: String = "abc12345") {
        onEmailChanged(email)
        onPasswordChanged(password)
    }

    @Test
    fun `bam dang nhap hai lan chi goi mot lan`() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel().apply { fill() }
        vm.onSubmit()
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isSubmitting)
        repository.gate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.signInCalls.size)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `email sai dinh dang hoac thieu mat khau thi bao loi duoi o va khong gui`() = runTest {
        val vm = viewModel().apply { fill(email = "nam@", password = "") }
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(ValidationError.EMAIL_FORMAT, vm.uiState.value.emailError)
        assertEquals(ValidationError.PASSWORD_REQUIRED, vm.uiState.value.passwordError)
        assertEquals(0, repository.signInCalls.size)
    }

    @Test
    fun `sai thong tin thi formError chung, khong chi ra o nao`() = runTest {
        repository.signInResult = AuthResult.Failure(AuthError.InvalidCredentials)
        val vm = viewModel().apply { fill() }
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        with(vm.uiState.value) {
            assertEquals(AuthError.InvalidCredentials, formError)
            assertNull(emailError)
            assertNull(passwordError)
        }
        // Sửa ô nhập thì ẩn lỗi chung.
        vm.onPasswordChanged("abc123456")
        assertNull(vm.uiState.value.formError)
    }

    @Test
    fun `tai khoan bi khoa thi hien hop thoai khoa`() = runTest {
        repository.signInResult = AuthResult.Failure(AuthError.AccountLocked)
        val vm = viewModel().apply { fill() }
        vm.events.test {
            vm.onSubmit()
            assertEquals(LoginEvent.ShowLocked, awaitItem())
        }
        assertNull(vm.uiState.value.formError)
    }

    @Test
    fun `lockedNotice true thi hien hop thoai khoa ngay khi mo, mot lan`() = runTest {
        val handle = SavedStateHandle(mapOf("lockedNotice" to true))
        val vm = viewModel(handle)
        vm.events.test { assertEquals(LoginEvent.ShowLocked, awaitItem()) }
        // Tạo lại sau xoay màn hình: không hiện lại.
        val again = viewModel(handle)
        again.events.test {
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `sessionRevokedNotice true thi bao phien het han mot lan (011 T018)`() = runTest {
        val handle = SavedStateHandle(mapOf("sessionRevokedNotice" to true))
        val vm = viewModel(handle)
        vm.events.test { assertEquals(LoginEvent.ShowSessionRevoked, awaitItem()) }
        val again = viewModel(handle)
        again.events.test {
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `mat mang hoac het 15 giay thi snackbar mang, giu email, xoa mat khau`() = runTest {
        repository.signInResult = AuthResult.Failure(AuthError.Network)
        val vm = viewModel().apply { fill() }
        vm.events.test {
            vm.onSubmit()
            assertEquals(LoginEvent.ClearPassword, awaitItem())
            assertEquals(LoginEvent.ShowNetworkError, awaitItem())
        }
        assertEquals("nam@example.com", vm.uiState.value.email)
        assertNull(vm.uiState.value.formError)
        // Mật khẩu đã xóa: bấm lại thì báo thiếu mật khẩu, không gửi lại mật khẩu cũ.
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(ValidationError.PASSWORD_REQUIRED, vm.uiState.value.passwordError)
        assertEquals(1, repository.signInCalls.size)
    }

    @Test
    fun `email giu qua SavedStateHandle, mat khau thi khong`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).fill()
        assertEquals("nam@example.com", handle.get<String>("email"))
        assertFalse(handle.keys().any { it.contains("password", ignoreCase = true) })
    }

    // ---------- Google (US4, T061) ----------

    @Test
    fun `huy bottom sheet Google thi khong co loi nao`() = runTest {
        google.result = { throw GetCredentialCancellationException() }
        val vm = viewModel()
        vm.events.test {
            vm.onGoogle(activity)
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertNull(vm.uiState.value.formError)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `Google trung email tai khoan mat khau thi mo hop thoai lien ket`() = runTest {
        val pending = object : PendingCredential {}
        repository.googleResult = AuthResult.Failure(AuthError.NeedsLinking("nam@congty.vn", pending))
        val vm = viewModel()
        vm.events.test {
            vm.onGoogle(activity)
            assertEquals(LoginEvent.OpenLinkDialog("nam@congty.vn"), awaitItem())
        }

        // Nhập sai mật khẩu trong hộp thoại: báo lỗi trong hộp thoại, chưa liên kết.
        repository.signInResult = AuthResult.Failure(AuthError.InvalidCredentials)
        vm.onLinkSubmit("sai")
        testScheduler.advanceUntilIdle()
        assertEquals(AuthError.InvalidCredentials, vm.uiState.value.linkError)
        assertEquals(emptyList<PendingCredential>(), repository.linkCalls)

        repository.signInResult = AuthResult.Success(Unit)
        vm.events.test {
            vm.onLinkSubmit("abc12345")
            assertEquals(LoginEvent.LinkDone, awaitItem())
        }
        assertEquals(listOf(pending), repository.linkCalls)
    }

    @Test
    fun `khong co tai khoan Google thi bao loi bang snackbar`() = runTest {
        google.result = { throw NoCredentialException() }
        val vm = viewModel()
        vm.events.test {
            vm.onGoogle(activity)
            assertEquals(LoginEvent.ShowError(AuthError.NoGoogleAccount), awaitItem())
        }
    }

    @Test
    fun `bam Google hai lan chi mo mot lan`() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onGoogle(activity)
        vm.onGoogle(activity)
        testScheduler.advanceUntilIdle()
        repository.gate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(1, google.calls)
    }
}
