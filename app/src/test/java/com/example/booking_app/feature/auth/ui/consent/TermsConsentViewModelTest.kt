package com.example.booking_app.feature.auth.ui.consent

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.ConsentPrefill
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.auth.FakeOnSignupApi
import com.example.booking_app.feature.auth.data.OnSignupRequest
import com.example.booking_app.feature.auth.domain.usecase.CompleteSignupUseCase
import com.example.booking_app.feature.auth.domain.validation.ValidationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TermsConsentViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val prefill = ConsentPrefill(ConsentPrefill.Provider.GOOGLE, "nam.le@gmail.com", null, "Lê Hoàng Nam")
    private val sessionManager = FakeSessionManager(SessionState.NeedsConsent(prefill))
    private val api = FakeOnSignupApi()
    private val handle = SavedStateHandle()

    private fun viewModel() =
        TermsConsentViewModel(sessionManager, CompleteSignupUseCase(api, sessionManager, FakeSessionPrefs()), handle)

    @Test
    fun `prefill lay tu NeedsConsent, ho ten dien san va sua duoc`() = runTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(prefill, vm.uiState.value.prefill)
        assertEquals("Lê Hoàng Nam", vm.uiState.value.name)
        vm.onNameChanged("Nam Lê")
        assertEquals("Nam Lê", vm.uiState.value.name)
        // Tạo lại (xoay màn hình) thì giữ tên đã sửa, không điền lại tên từ Google.
        val again = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals("Nam Lê", again.uiState.value.name)
    }

    @Test
    fun `chua danh dau dong y thi nut tat va khong gui`() = runTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.canSubmit)
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<OnSignupRequest>(), api.requests)
        vm.onTermsChecked(true)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `ho ten sai thi bao loi duoi o, khong gui`() = runTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.onTermsChecked(true)
        vm.onNameChanged(" A ")
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(ValidationError.NAME_LENGTH, vm.uiState.value.nameError)
        assertEquals(emptyList<OnSignupRequest>(), api.requests)
    }

    @Test
    fun `bam hai lan chi goi on-signup mot lan voi termsAccepted true`() = runTest {
        api.gate = CompletableDeferred()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.onTermsChecked(true)
        vm.onSubmit()
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isSubmitting)
        api.gate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(OnSignupRequest("Lê Hoàng Nam", true)), api.requests)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `server bao ten sai thi loi duoi o ho ten`() = runTest {
        api.result = {
            throw AppError.Unknown(
                com.example.booking_app.core.network.FunctionHttpException(
                    400,
                    """{ "error": { "code": "INVALID_DISPLAY_NAME", "message": "x" } }""",
                ),
            )
        }
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.onTermsChecked(true)
        vm.onSubmit()
        testScheduler.advanceUntilIdle()
        assertEquals(ValidationError.NAME_LENGTH, vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `dung tai khoan khac thi dang xuat`() = runTest {
        val vm = viewModel()
        vm.onUseOtherAccount()
        testScheduler.advanceUntilIdle()
        assertEquals(1, sessionManager.signOutCalls)
    }

    @Test
    fun `het 15 giay thi snackbar mang, giu ho ten`() = runTest {
        api.result = { throw AppError.Timeout }
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.onTermsChecked(true)
        vm.events.test {
            vm.onSubmit()
            assertEquals(TermsConsentEvent.ShowNetworkError, awaitItem())
        }
        assertEquals("Lê Hoàng Nam", vm.uiState.value.name)
        assertNull(vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.isSubmitting)
    }
}
