package com.example.booking_app.feature.auth.ui.setup

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.session.PendingSignup
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.auth.FakeOnSignupApi
import com.example.booking_app.feature.auth.data.OnSignupRequest
import com.example.booking_app.feature.auth.data.OnSignupResponse
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountSetupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val api = FakeOnSignupApi()
    private val prefs = FakeSessionPrefs().apply { pending = PendingSignup("u1", "Lê Hoàng Nam", 1L) }
    private val sessionManager = FakeSessionManager(SessionState.Initializing)

    private fun viewModel() = AccountSetupViewModel(prefs, CompleteSignupUseCase(api, sessionManager, prefs), sessionManager)

    @Test
    fun `co PendingSignup thi tu goi on-signup, dang chay la Loading`() = runTest {
        api.gate = CompletableDeferred()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(AccountSetupUiState.Loading, vm.uiState.value)
        assertEquals(listOf(OnSignupRequest("Lê Hoàng Nam", true)), api.requests)

        api.gate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        // Xong thì SessionManager chuyển SignedIn, MainActivity điều hướng; màn hình vẫn Loading.
        assertEquals(AccountSetupUiState.Loading, vm.uiState.value)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `loi mang thi Failed, Thu lai goi lai`() = runTest {
        api.result = { throw AppError.Network }
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(AccountSetupUiState.Failed(AppError.Network), vm.uiState.value)

        api.result = { OnSignupResponse("CREATED", "PLAYER") }
        vm.onRetry()
        testScheduler.advanceUntilIdle()
        assertEquals(AccountSetupUiState.Loading, vm.uiState.value)
        assertEquals(2, api.requests.size)
    }

    @Test
    fun `khong co PendingSignup (InitFailed khi mo app) thi lam moi phien`() = runTest {
        prefs.pending = null
        sessionManager.sessionState.value = SessionState.InitFailed(AppError.Network)
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, api.requests.size)
        assertEquals(listOf(true), sessionManager.refreshCalls)
        // Làm mới mà vẫn lỗi (fake không đổi trạng thái) thì hiện lỗi đó.
        assertEquals(AccountSetupUiState.Failed(AppError.Network), vm.uiState.value)
    }

    @Test
    fun `Dang xuat thi goi signOut`() = runTest {
        api.result = { throw AppError.Network }
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.onSignOut()
        testScheduler.advanceUntilIdle()
        assertEquals(1, sessionManager.signOutCalls)
    }
}
