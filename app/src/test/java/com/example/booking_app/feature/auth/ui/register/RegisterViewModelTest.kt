package com.example.booking_app.feature.auth.ui.register

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.FakeGoogleCredentialDataSource
import com.example.booking_app.feature.auth.FakeOnSignupApi
import com.example.booking_app.feature.auth.data.OnSignupResponse
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupUseCase
import com.example.booking_app.feature.auth.domain.usecase.RegisterWithEmailUseCase
import com.example.booking_app.feature.auth.domain.usecase.ResendVerificationEmailUseCase
import com.example.booking_app.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.example.booking_app.feature.auth.domain.validation.PasswordChecks
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

class RegisterViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val prefs = FakeSessionPrefs()
    private val sessionManager = FakeSessionManager()
    private val api = FakeOnSignupApi()
    private val google = FakeGoogleCredentialDataSource()
    private val clock = FakeClock { 0L }
    private val activity = mockk<Activity>(relaxed = true)

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = RegisterViewModel(
        registerWithEmail = RegisterWithEmailUseCase(
            repository = repository,
            prefs = prefs,
            resendVerification = ResendVerificationEmailUseCase(repository, prefs, clock),
            completeSignup = CompleteSignupUseCase(api, sessionManager, prefs),
            clock = clock,
        ),
        signInWithGoogle = SignInWithGoogleUseCase(google, repository, sessionManager),
        savedStateHandle = handle,
    )

    private fun RegisterViewModel.fill(
        name: String = "Lê Hoàng Nam",
        email: String = "nam@email.com",
        password: String = "abc12345",
        confirm: String = password,
        terms: Boolean = true,
    ) {
        onNameChanged(name)
        onEmailChanged(email)
        onPasswordChanged(password)
        onConfirmPasswordChanged(confirm)
        onTermsChecked(terms)
    }

    @Test
    fun `chua danh dau dieu khoan thi khong dang ky duoc (US1-2)`() = runTest {
        val vm = viewModel().apply { fill(terms = false) }
        assertFalse(vm.uiState.value.canSubmit)
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.signUpCalls.size)

        vm.onTermsChecked(true)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `roi o thi hien loi cua o do, khong gui yeu cau (US1-6)`() = runTest {
        val vm = viewModel()
        vm.onNameChanged("N")
        vm.onFieldFocusLost(RegisterField.NAME)
        assertEquals(ValidationError.NAME_LENGTH, vm.uiState.value.nameError)
        // Ô chưa rời thì chưa báo lỗi.
        assertNull(vm.uiState.value.emailError)

        vm.onEmailChanged("nam@")
        vm.onFieldFocusLost(RegisterField.EMAIL)
        assertEquals(ValidationError.EMAIL_FORMAT, vm.uiState.value.emailError)

        // Gõ lại thì ẩn lỗi của ô đó.
        vm.onNameChanged("Nam")
        assertNull(vm.uiState.value.nameError)
        assertEquals(0, repository.signUpCalls.size)
    }

    @Test
    fun `bam Dang ky voi o sai thi hien loi tung o va khong gui (FR-020)`() = runTest {
        val vm = viewModel().apply { fill(name = " ", email = "nam@", password = "abcdefgh", confirm = "khac") }
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        with(vm.uiState.value) {
            assertEquals(ValidationError.NAME_LENGTH, nameError)
            assertEquals(ValidationError.EMAIL_FORMAT, emailError)
            assertEquals(ValidationError.PASSWORD_RULE, passwordError)
            assertEquals(ValidationError.PASSWORD_MISMATCH, confirmError)
            assertFalse(isSubmitting)
        }
        assertEquals(0, repository.signUpCalls.size)
    }

    @Test
    fun `hai dau tich mat khau cap nhat khi go`() = runTest {
        val vm = viewModel()
        assertEquals(PasswordChecks(len8 = false, letterDigit = false), vm.uiState.value.passwordChecks)
        vm.onPasswordChanged("abcdefgh")
        assertEquals(PasswordChecks(len8 = true, letterDigit = false), vm.uiState.value.passwordChecks)
        vm.onPasswordChanged("abc1")
        assertEquals(PasswordChecks(len8 = false, letterDigit = true), vm.uiState.value.passwordChecks)
        vm.onPasswordChanged("abc12345")
        assertEquals(PasswordChecks(len8 = true, letterDigit = true), vm.uiState.value.passwordChecks)
    }

    @Test
    fun `bam Dang ky hai lan chi goi mot lan (FR-022)`() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel().apply { fill() }
        vm.onSubmit()
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isSubmitting)
        assertFalse(vm.uiState.value.canSubmit)
        repository.gate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.signUpCalls.size)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `mat mang hoac het 15 giay thi snackbar mang, giu ho ten va email`() = runTest {
        api.result = { throw AppError.Timeout }
        val vm = viewModel().apply { fill() }
        vm.events.test {
            vm.onSubmit()
            assertEquals(RegisterEvent.ShowNetworkError, awaitItem())
        }
        with(vm.uiState.value) {
            assertEquals("Lê Hoàng Nam", name)
            assertEquals("nam@email.com", email)
            assertFalse(isSubmitting)
        }

        // "Thử lại" dùng lại mật khẩu đang nhập, không bắt nhập lại.
        api.result = { OnSignupResponse("CREATED", "PLAYER") }
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("nam@email.com" to "abc12345", "nam@email.com" to "abc12345"), repository.signUpCalls)
    }

    @Test
    fun `ho ten, email giu qua SavedStateHandle, mat khau thi khong (FR-021)`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).fill()
        val restored = viewModel(handle)
        assertEquals("Lê Hoàng Nam", restored.uiState.value.name)
        assertEquals("nam@email.com", restored.uiState.value.email)
        assertTrue(restored.uiState.value.termsChecked)
        assertFalse(handle.keys().any { it.contains("password", ignoreCase = true) })
    }

    @Test
    fun `email da dung thi bao loi duoi o email kem hai link (US1-3)`() = runTest {
        repository.signUpResult = AuthResult.Failure(AuthError.EmailInUse)
        val vm = viewModel().apply { fill() }
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.emailInUse)
        assertEquals(0, api.requests.size)

        // Sửa email thì ẩn lỗi.
        vm.onEmailChanged("nam2@email.com")
        assertFalse(vm.uiState.value.emailInUse)
    }

    @Test
    fun `thanh cong thi khong tu dieu huong, cho SessionState doi`() = runTest {
        val vm = viewModel().apply { fill() }
        vm.events.test {
            vm.onSubmit()
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(1, api.requests.size)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `ho ten bi server tu choi thi bao loi duoi o ho ten`() = runTest {
        api.result = {
            throw AppError.Unknown(
                FunctionHttpException(
                    400,
                    """{ "error": { "code": "INVALID_DISPLAY_NAME", "message": "x" } }""",
                ),
            )
        }
        val vm = viewModel().apply { fill() }
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(ValidationError.NAME_LENGTH, vm.uiState.value.nameError)
    }

    @Test
    fun `huy Google thi khong bao loi, trung email thi mo man hinh dang nhap`() = runTest {
        google.result = { throw GetCredentialCancellationException() }
        val vm = viewModel()
        vm.events.test {
            vm.onGoogle(activity)
            testScheduler.advanceUntilIdle()
            expectNoEvents()

            repository.googleResult = AuthResult.Failure(AuthError.NeedsLinking("nam@congty.vn", null))
            google.result = { "token" }
            vm.onGoogle(activity)
            assertEquals(RegisterEvent.OpenLogin, awaitItem())
        }
    }
}
