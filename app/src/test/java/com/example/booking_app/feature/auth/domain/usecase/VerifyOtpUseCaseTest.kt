package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VerifyOtpUseCaseTest {

    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()
    private var now = 10_000L
    private val useCase = VerifyOtpUseCase(repository, sessionManager, FakeClock { now })

    private val session = OtpSession("+84912345678", "v1", null, sentAtElapsed = 10_000L, wrongCount = 0)

    @Test
    fun `dung ma thi dang nhap va lam moi phien`() = runTest {
        assertEquals(VerifyOtpResult.Success, useCase(session, "123456"))
        assertEquals(listOf("v1" to "123456"), repository.verifyOtpCalls)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `sai ma thi WrongCode va wrongCount tang 1`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.InvalidCode)
        assertEquals(VerifyOtpResult.WrongCode(session.copy(wrongCount = 1)), useCase(session, "000000"))
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
    }

    @Test
    fun `qua 5 phut thi CodeExpired, khong goi Firebase`() = runTest {
        now = 10_000L + 300_000
        assertEquals(VerifyOtpResult.Failure(AuthError.CodeExpired), useCase(session, "123456"))
        assertEquals(emptyList<Pair<String, String>>(), repository.verifyOtpCalls)
    }

    @Test
    fun `da sai 5 lan thi CodeExpired, khong goi Firebase`() = runTest {
        assertEquals(VerifyOtpResult.Failure(AuthError.CodeExpired), useCase(session.copy(wrongCount = 5), "123456"))
        assertEquals(emptyList<Pair<String, String>>(), repository.verifyOtpCalls)
    }

    @Test
    fun `Firebase bao ERROR_SESSION_EXPIRED thi CodeExpired`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.CodeExpired)
        assertEquals(VerifyOtpResult.Failure(AuthError.CodeExpired), useCase(session, "123456"))
    }

    @Test
    fun `mat mang thi Network, khong tinh la sai`() = runTest {
        repository.verifyOtpResult = AuthResult.Failure(AuthError.Network)
        assertEquals(VerifyOtpResult.Failure(AuthError.Network), useCase(session, "123456"))
    }
}
