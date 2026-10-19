package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.session.PendingSignup
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.auth.data.OnSignupApi
import com.example.booking_app.feature.auth.data.OnSignupRequest
import com.example.booking_app.feature.auth.data.OnSignupResponse
import com.example.booking_app.feature.auth.domain.model.AuthError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompleteSignupUseCaseTest {

    private class FakeOnSignupApi : OnSignupApi {
        var result: () -> OnSignupResponse = { OnSignupResponse("CREATED", "PLAYER") }
        val requests = mutableListOf<OnSignupRequest>()
        override suspend fun onSignup(request: OnSignupRequest): OnSignupResponse {
            requests += request
            return result()
        }
    }

    private class FakeSessionManager : SessionManager {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.Initializing)
        override val isGuest = MutableStateFlow(false)
        val refreshCalls = mutableListOf<Boolean>()
        var signOutCalls = 0
        override suspend fun refresh(forceTokenRefresh: Boolean) { refreshCalls += forceTokenRefresh }
        override suspend fun signOut() { signOutCalls++ }
        override fun currentUid(): String? = null
        override fun currentAppRole(): AppRole? = null
    }

    private val api = FakeOnSignupApi()
    private val sessionManager = FakeSessionManager()
    private val prefs = FakeSessionPrefs().apply { pending = PendingSignup("u1", "Lê Hoàng Nam", 1L) }
    private val useCase = CompleteSignupUseCase(api, sessionManager, prefs)

    private fun functionError(status: Int, code: String) =
        AppError.Unknown(FunctionHttpException(status, """{ "error": { "code": "$code", "message": "x" } }"""))

    @Test
    fun `200 thi lam moi token roi xoa PendingSignup`() = runTest {
        val result = useCase(displayName = "Lê Hoàng Nam", termsAccepted = true)
        assertEquals(CompleteSignupResult.Success(AppRole.PLAYER), result)
        assertEquals(listOf(OnSignupRequest("Lê Hoàng Nam", true)), api.requests)
        assertEquals(listOf(true), sessionManager.refreshCalls)
        assertNull(prefs.pending)
    }

    @Test
    fun `400 TERMS_REQUIRED va INVALID_DISPLAY_NAME giu PendingSignup`() = runTest {
        api.result = { throw functionError(400, "TERMS_REQUIRED") }
        assertEquals(CompleteSignupResult.TermsRequired, useCase("Nam", termsAccepted = false))
        api.result = { throw functionError(400, "INVALID_DISPLAY_NAME") }
        assertEquals(CompleteSignupResult.InvalidDisplayName, useCase("N", termsAccepted = true))
        assertEquals("u1", prefs.pending?.uid)
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
    }

    @Test
    fun `403 ACCOUNT_LOCKED thi AccountLocked, SessionManager tu dang xuat qua AccountLockedBus`() = runTest {
        api.result = { throw AppError.Locked }
        assertEquals(CompleteSignupResult.Failure(AuthError.AccountLocked), useCase("Nam", true))
        // Không gọi signOut() để giữ lý do LOCKED cho thông báo khóa (FunctionErrors đã báo AccountLockedBus).
        assertEquals(0, sessionManager.signOutCalls)
    }

    @Test
    fun `401 sau khi da thu lai thi dang xuat`() = runTest {
        api.result = { throw AppError.NotSignedIn }
        val result = useCase("Nam", true)
        assertEquals(1, sessionManager.signOutCalls)
        assertEquals(CompleteSignupResult.Failure(AuthError.Unknown(AppError.NotSignedIn)), result)
    }

    @Test
    fun `mat mang hoac het 15 giay thi Network va giu PendingSignup`() = runTest {
        for (error in listOf(AppError.Network, AppError.Timeout)) {
            api.result = { throw error }
            assertEquals(CompleteSignupResult.Failure(AuthError.Network), useCase("Nam", true))
        }
        assertEquals("u1", prefs.pending?.uid)
    }

    @Test
    fun `500 INTERNAL thi Unknown`() = runTest {
        val error = functionError(500, "INTERNAL")
        api.result = { throw error }
        assertEquals(CompleteSignupResult.Failure(AuthError.Unknown(error)), useCase("Nam", true))
    }
}
