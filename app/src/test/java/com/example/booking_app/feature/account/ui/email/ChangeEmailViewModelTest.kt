package com.example.booking_app.feature.account.ui.email

import com.example.booking_app.R
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeIdTokenProvider
import com.example.booking_app.feature.account.FakeReauthDataSource
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.usecase.ChangeEmailUseCase
import com.example.booking_app.feature.account.domain.usecase.ReauthenticateUseCase
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

/** T093: đổi email đăng nhập (spec 011 US7, FR-016, research R10). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeEmailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dataSource = FakeReauthDataSource() // email hiện tại "a@b.vn"
    private val viewModel = ChangeEmailViewModel(
        ChangeEmailUseCase(ReauthenticateUseCase(dataSource, FakeIdTokenProvider()), dataSource),
    )

    private fun fill(email: String = "moi@example.com", password: String = "abc12345") {
        viewModel.onEmailChanged(email)
        viewModel.onPasswordChanged(password)
    }

    @Test
    fun `email sai dinh dang thi emailError va khong goi Firebase`() = runTest {
        fill(email = "khong-phai-email")
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.auth_error_email_format, viewModel.uiState.value.emailError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `email moi trung email hien tai sau trim lowercase thi loi same_email, khong goi use case`() = runTest {
        fill(email = "  A@B.VN ")
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_same_email, viewModel.uiState.value.emailError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `thieu mat khau thi passwordError`() = runTest {
        fill(password = "")
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_current_password_required, viewModel.uiState.value.passwordError)
        assertEquals(emptyList<String>(), dataSource.calls)
    }

    @Test
    fun `sai mat khau hien tai thi passwordError, khong gui thu`() = runTest {
        dataSource.failure = AccountError.WrongCurrentPassword
        fill()
        viewModel.onSubmit()
        runCurrent()
        assertEquals(R.string.account_error_wrong_password, viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.sent)
        assertEquals(listOf("password:abc12345"), dataSource.calls)
    }

    @Test
    fun `thanh cong thi xac nhan lai roi gui link toi email da chuan hoa, sent = true`() = runTest {
        fill(email = " Moi@Example.com ")
        viewModel.onSubmit()
        runCurrent()
        assertTrue(viewModel.uiState.value.sent)
        assertNull(viewModel.uiState.value.emailError)
        assertEquals(listOf("password:abc12345", "verifyEmail:moi@example.com"), dataSource.calls)
    }

    @Test
    fun `email da thuoc tai khoan khac cung cho sent = true va cung thong bao (US7-2)`() = runTest {
        // FirebaseReauthDataSource nuốt lỗi email đã dùng; với use case thì không có gì khác thành công.
        fill(email = "da-dung@example.com")
        viewModel.onSubmit()
        runCurrent()
        assertTrue(viewModel.uiState.value.sent)
    }

    @Test
    fun `dang gui thi khoa nut, bam lap khong gui them`() = runTest {
        val gate = CompletableDeferred<Unit>()
        dataSource.verifyEmailGate = gate
        fill()
        viewModel.onSubmit()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onSubmit()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, dataSource.calls.count { it.startsWith("verifyEmail:") })
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
