package com.example.booking_app.feature.account.ui.delete

import app.cash.turbine.test
import com.example.booking_app.R
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.SignOutReason
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeAccountFunctionsApi
import com.example.booking_app.feature.account.FakeIdTokenProvider
import com.example.booking_app.feature.account.FakeReauthDataSource
import com.example.booking_app.feature.account.data.ReauthAccount
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.DeletionImpact
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import com.example.booking_app.feature.account.domain.usecase.DeleteAccountUseCase
import com.example.booking_app.feature.account.domain.usecase.LoadDeletionImpactUseCase
import com.example.booking_app.feature.account.domain.usecase.ReauthenticateUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T086: màn hình Xóa tài khoản (US6, FR-018–FR-022, FR-021). */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val reauthSource = FakeReauthDataSource()
    private val api = FakeAccountFunctionsApi()
    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))

    private fun viewModel(): DeleteAccountViewModel {
        val reauth = ReauthenticateUseCase(reauthSource, FakeIdTokenProvider())
        return DeleteAccountViewModel(
            LoadDeletionImpactUseCase(api),
            DeleteAccountUseCase(reauth, api, session),
            reauth,
        )
    }

    private fun DeleteAccountViewModel.fillValid(password: String = "abc12345") {
        onPasswordChanged(password)
        onConfirmTextChanged("XÓA")
        onAcknowledgedChanged(true)
    }

    @Test
    fun `tai ban xem truoc khi mo man hinh, loi thi Error va thu lai duoc`() = runTest {
        api.previewResult = { throw AppError.Network }
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.uiState.value.impact is ImpactState.Error)
        api.previewResult = { FakeAccountFunctionsApi.IMPACT }
        vm.onRetryLoad()
        runCurrent()
        assertEquals(FakeAccountFunctionsApi.IMPACT, (vm.uiState.value.impact as ImpactState.Content).impact)
    }

    @Test
    fun `nut xoa chi bat khi du 3 dieu kien (US6-2)`() = runTest {
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.uiState.value.canDelete)
        vm.onPasswordChanged("abc12345")
        vm.onConfirmTextChanged("XÓA")
        assertFalse("chưa đánh dấu", vm.uiState.value.canDelete)
        vm.onAcknowledgedChanged(true)
        assertTrue(vm.uiState.value.canDelete)
        vm.onConfirmTextChanged("XOA")
        assertFalse("sai chữ XÓA", vm.uiState.value.canDelete)
        vm.onConfirmTextChanged(" xóa ")
        vm.onPasswordChanged("")
        assertFalse("chưa nhập mật khẩu", vm.uiState.value.canDelete)
    }

    @Test
    fun `tai khoan Google can xac nhan qua hop thoai truoc khi bat nut`() = runTest {
        reauthSource.account = ReauthAccount(setOf("google.com"), "a@b.vn", null)
        val vm = viewModel()
        runCurrent()
        assertEquals(ReauthState.Required(ReauthMethod.Google), vm.uiState.value.reauth)
        vm.onConfirmTextChanged("XÓA")
        vm.onAcknowledgedChanged(true)
        assertFalse(vm.uiState.value.canDelete)
        vm.onReauthDone()
        assertTrue(vm.uiState.value.canDelete)
        vm.onDelete()
        runCurrent()
        assertEquals(listOf("confirm:sha256:v1:XÓA"), api.calls.filter { it.startsWith("confirm") })
        assertEquals(emptyList<String>(), reauthSource.calls)
    }

    @Test
    fun `thanh cong thi xac nhan mat khau, confirm roi signOut ACCOUNT_DELETED`() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.onDelete()
        runCurrent()
        assertEquals(listOf("password:abc12345"), reauthSource.calls)
        assertEquals(listOf("preview", "confirm:sha256:v1:XÓA"), api.calls)
        assertEquals(listOf(SignOutReason.ACCOUNT_DELETED), session.signOutReasons)
    }

    @Test
    fun `ImpactChanged thi cap nhat impact va bo danh dau`() = runTest {
        val newer = FakeAccountFunctionsApi.IMPACT.copy(impactVersion = "sha256:v2")
        api.confirmResults += { throw AccountError.ImpactChanged(newer) }
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.onDelete()
        runCurrent()
        val state = vm.uiState.value
        assertEquals(newer, (state.impact as ImpactState.Content).impact)
        assertFalse(state.acknowledged)
        assertFalse(state.isDeleting)
        assertEquals("XÓA", state.confirmText)
    }

    @Test
    fun `ReauthRequired thi phat su kien mo lai xac nhan danh tinh`() = runTest {
        reauthSource.account = ReauthAccount(setOf("phone"), null, "+84912345678")
        api.confirmResults += { throw AccountError.ReauthRequired }
        val vm = viewModel()
        runCurrent()
        vm.onReauthDone()
        vm.onConfirmTextChanged("XÓA")
        vm.onAcknowledgedChanged(true)
        vm.events.test {
            vm.onDelete()
            runCurrent()
            assertEquals(DeleteAccountEvent.OpenReauth, awaitItem())
        }
        assertEquals(ReauthState.Required(ReauthMethod.Phone("+84912345678")), vm.uiState.value.reauth)
    }

    @Test
    fun `loi 500 giu nguyen man hinh, bao tai khoan van dung binh thuong (FR-021)`() = runTest {
        api.confirmResults += { throw AccountError.Unknown(null) }
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.events.test {
            vm.onDelete()
            runCurrent()
            assertEquals(DeleteAccountEvent.ShowError(R.string.account_error_delete_failed), awaitItem())
        }
        val state = vm.uiState.value
        assertTrue(state.acknowledged)
        assertEquals("XÓA", state.confirmText)
        assertFalse(state.isDeleting)
        assertEquals(emptyList<SignOutReason?>(), session.signOutReasons)
    }

    @Test
    fun `het gio thi tu goi lai confirm mot lan voi cung impactVersion, thanh cong thi dang xuat`() = runTest {
        api.confirmResults += { throw AccountError.Network }
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.onDelete()
        runCurrent()
        assertEquals(2, api.calls.count { it == "confirm:sha256:v1:XÓA" })
        assertEquals(listOf(SignOutReason.ACCOUNT_DELETED), session.signOutReasons)
    }

    @Test
    fun `het gio ca 2 lan thi bao chua ro ket qua, khong khang dinh tai khoan con dung`() = runTest {
        api.confirmResults += { throw AccountError.Network }
        api.confirmResults += { throw AccountError.Network }
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.events.test {
            vm.onDelete()
            runCurrent()
            assertEquals(DeleteAccountEvent.ShowError(R.string.account_error_delete_unknown_result), awaitItem())
        }
        assertEquals(2, api.calls.count { it.startsWith("confirm") })
    }

    @Test
    fun `sai mat khau thi loi o mat khau, khong goi confirm`() = runTest {
        reauthSource.failure = AccountError.WrongCurrentPassword
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.onDelete()
        runCurrent()
        assertEquals(R.string.account_error_wrong_password, vm.uiState.value.passwordError)
        assertEquals(0, api.calls.count { it.startsWith("confirm") })
    }

    @Test
    fun `admin va qua nhieu don thi bao loi rieng`() = runTest {
        api.confirmResults += { throw AccountError.DeleteTooLarge }
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.events.test {
            vm.onDelete()
            runCurrent()
            assertEquals(DeleteAccountEvent.ShowError(R.string.account_error_delete_too_large), awaitItem())
        }
    }

    @Test
    fun `bam lap khi dang xoa khong goi them`() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.confirmResults += { gate.await() }
        val vm = viewModel()
        runCurrent()
        vm.fillValid()
        vm.onDelete()
        runCurrent()
        assertTrue(vm.uiState.value.isDeleting)
        assertFalse(vm.uiState.value.canDelete)
        vm.onDelete()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, api.calls.count { it.startsWith("confirm") })
    }
}
