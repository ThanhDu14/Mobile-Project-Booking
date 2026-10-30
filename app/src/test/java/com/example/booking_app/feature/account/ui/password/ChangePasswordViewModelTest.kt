package com.example.booking_app.feature.account.ui.password

import app.cash.turbine.test
import com.example.booking_app.R
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeIdTokenProvider
import com.example.booking_app.feature.account.FakeReauthDataSource
import com.example.booking_app.feature.account.domain.usecase.ChangePasswordUseCase
import com.example.booking_app.feature.account.domain.usecase.ReauthenticateUseCase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dataSource = FakeReauthDataSource()
    private val viewModel = ChangePasswordViewModel(
        ChangePasswordUseCase(ReauthenticateUseCase(dataSource, FakeIdTokenProvider()), dataSource),
    )

    private fun fill(current: String = CURRENT, new: String = NEW, confirm: String = new) {
        viewModel.onCurrentChanged(current)
        viewModel.onNewChanged(new)
        viewModel.onConfirmChanged(confirm)
    }

    @Test
    fun `dau tich theo PasswordRule cua 010`() {
        viewModel.onNewChanged("abc")
        assertFalse(viewModel.uiState.value.checks.len8)
        assertFalse(viewModel.uiState.value.checks.letterDigit)
        viewModel.onNewChanged("abcdefgh")
        assertTrue(viewModel.uiState.value.checks.len8)
        assertFalse(viewModel.uiState.value.checks.letterDigit)
        viewModel.onNewChanged("abcdefg1")
        assertTrue(viewModel.uiState.value.checks.len8)
        assertTrue(viewModel.uiState.value.checks.letterDigit)
    }

    @Test
    fun `mat khau moi khong dat quy tac thi loi va khong goi Firebase`() = runTest {
        fill(new = "ngan1")
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_weak_password, viewModel.uiState.value.newError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `nhap lai khong khop thi loi ConfirmPasswordRule`() = runTest {
        fill(confirm = "Khac12345")
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_password_mismatch, viewModel.uiState.value.confirmError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `trung mat khau cu thi loi va khong goi use case`() = runTest {
        fill(new = CURRENT)
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_same_password, viewModel.uiState.value.newError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `chua nhap mat khau hien tai thi loi`() = runTest {
        fill(current = "")
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_current_password_required, viewModel.uiState.value.currentError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `sai mat khau hien tai thi loi duoi o mat khau hien tai`() = runTest {
        dataSource.failure = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "x")
        fill()
        viewModel.onSubmit()
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(R.string.account_error_wrong_password, state.currentError)
        assertFalse(state.isSubmitting)
        assertEquals(listOf("password:$CURRENT"), dataSource.calls)
    }

    @Test
    fun `server tu choi mat khau moi thi loi duoi o mat khau moi`() = runTest {
        dataSource.updateFailure = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "policy")
        fill()
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_weak_password, viewModel.uiState.value.newError)
        assertNull(viewModel.uiState.value.currentError)
    }

    @Test
    fun `thanh cong thi xac nhan lai roi doi mat khau va phat su kien quay lai`() = runTest {
        viewModel.events.test {
            fill()
            viewModel.onSubmit()
            assertEquals(ChangePasswordEvent.Changed, awaitItem())
        }
        assertEquals(listOf("password:$CURRENT", "update:$NEW"), dataSource.calls)
    }

    @Test
    fun `dang gui thi khoa nut, bam lap khong goi them`() = runTest {
        val gate = CompletableDeferred<Unit>()
        dataSource.updateGate = gate
        fill()
        viewModel.onSubmit()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onSubmit()
        runCurrent()
        assertEquals(listOf("password:$CURRENT", "update:$NEW"), dataSource.calls)
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `mat mang thi bao loi co Thu lai va xoa cac o mat khau`() = runTest {
        dataSource.failure = FirebaseNetworkException("offline")
        viewModel.events.test {
            fill()
            viewModel.onSubmit()
            assertEquals(ChangePasswordEvent.ShowError(R.string.account_error_network, retryable = true), awaitItem())
        }
        val state = viewModel.uiState.value
        assertEquals("", state.current)
        assertEquals("", state.new)
        assertEquals("", state.confirm)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `qua nhieu lan thu thi bao thong bao chung`() = runTest {
        dataSource.failure = AppError.RateLimited
        viewModel.events.test {
            fill()
            viewModel.onSubmit()
            assertEquals(ChangePasswordEvent.ShowError(R.string.account_error_too_many_attempts, retryable = false), awaitItem())
        }
    }

    @Test
    fun `sua o thi xoa loi cua o do`() = runTest {
        fill(new = CURRENT)
        viewModel.onSubmit()
        runCurrent()
        viewModel.onNewChanged(NEW)
        assertNull(viewModel.uiState.value.newError)
    }

    private companion object {
        const val CURRENT = "Matkhau123"
        const val NEW = "Matkhau456"
    }
}
