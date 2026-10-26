package com.example.booking_app.feature.auth.ui.forgot

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.usecase.SendPasswordResetUseCase
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ForgotPasswordViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val prefs = FakeSessionPrefs()

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): ForgotPasswordViewModel {
        val clock = FakeClock { testScheduler.currentTime }
        return ForgotPasswordViewModel(SendPasswordResetUseCase(repository, prefs, clock), prefs, clock, handle)
    }

    @Test
    fun `arg email dien san`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf("email" to "nam@email.com")))
        assertEquals("nam@email.com", vm.uiState.value.email)
    }

    @Test
    fun `email sai dinh dang thi loi duoi o, khong gui`() = runTest {
        val vm = viewModel()
        vm.onEmailChanged("nam@")
        vm.onSubmit()
        testScheduler.runCurrent()
        assertEquals(ValidationError.EMAIL_FORMAT, vm.uiState.value.emailError)
        assertEquals(0, repository.resetCalls.size)

        vm.onEmailChanged("nam@email.com")
        assertNull(vm.uiState.value.emailError)
    }

    @Test
    fun `gui xong thi sent, dem nguoc 60 giay roi moi gui lai duoc (US3-3)`() = runTest {
        val vm = viewModel()
        vm.onEmailChanged("nam@email.com")
        vm.onSubmit()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.sent)
        assertEquals(60, vm.uiState.value.resendInSeconds)

        // Còn đếm thì bấm "Gửi lại" không gửi.
        vm.onSubmit()
        testScheduler.runCurrent()
        assertEquals(1, repository.resetCalls.size)

        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertEquals(0, vm.uiState.value.resendInSeconds)
        vm.onSubmit()
        testScheduler.runCurrent()
        assertEquals(2, repository.resetCalls.size)
    }

    @Test
    fun `email co hay khong co tai khoan deu cung thong bao sent (FR-018)`() = runTest {
        repository.resetResult = AuthResult.Failure(AuthError.InvalidCredentials)
        val vm = viewModel()
        vm.onEmailChanged("khongco@email.com")
        vm.onSubmit()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.sent)
    }

    @Test
    fun `moc lay tu DataStore nen tao lai ViewModel van con dem`() = runTest {
        testScheduler.advanceTimeBy(100_000)
        prefs.lastPasswordResetAt = testScheduler.currentTime - 15_000
        val vm = viewModel()
        testScheduler.runCurrent()
        assertEquals(45, vm.uiState.value.resendInSeconds)
    }

    @Test
    fun `moc qua 60 giay thi xoa khoi DataStore`() = runTest {
        testScheduler.advanceTimeBy(100_000)
        prefs.lastPasswordResetAt = testScheduler.currentTime - 61_000
        val vm = viewModel()
        testScheduler.runCurrent()
        assertEquals(0, vm.uiState.value.resendInSeconds)
        assertNull(prefs.lastPasswordResetAt)
    }

    @Test
    fun `bam Gui hai lan chi goi mot lan`() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onEmailChanged("nam@email.com")
        vm.onSubmit()
        vm.onSubmit()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isSubmitting)
        repository.gate!!.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(1, repository.resetCalls.size)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `qua nhieu lan thu hoac mat mang thi bao loi, chua sent`() = runTest {
        val vm = viewModel()
        vm.onEmailChanged("nam@email.com")
        repository.resetResult = AuthResult.Failure(AuthError.TooManyAttempts)
        vm.events.test {
            vm.onSubmit()
            assertEquals(ForgotPasswordEvent.ShowError(AuthError.TooManyAttempts), awaitItem())
            repository.resetResult = AuthResult.Failure(AuthError.Network)
            vm.onSubmit()
            assertEquals(ForgotPasswordEvent.ShowNetworkError, awaitItem())
        }
        assertFalse(vm.uiState.value.sent)
    }
}
