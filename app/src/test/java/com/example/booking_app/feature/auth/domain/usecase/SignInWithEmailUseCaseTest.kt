package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignInWithEmailUseCaseTest {

    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()
    private val useCase = SignInWithEmailUseCase(repository, sessionManager)

    @Test
    fun `email duoc trim va viet thuong truoc khi gui`() = runTest {
        useCase("  Nam.Le@Example.COM ", "abc12345")
        assertEquals(listOf("nam.le@example.com" to "abc12345"), repository.signInCalls)
    }

    @Test
    fun `thanh cong thi lam moi token`() = runTest {
        assertEquals(AuthResult.Success(Unit), useCase("nam@example.com", "abc12345"))
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `sai email va sai mat khau cung mot loi InvalidCredentials`() = runTest {
        repository.signInResult = AuthResult.Failure(AuthError.InvalidCredentials)
        assertEquals(AuthResult.Failure(AuthError.InvalidCredentials), useCase("khong.co@example.com", "abc12345"))
        assertEquals(AuthResult.Failure(AuthError.InvalidCredentials), useCase("nam@example.com", "sai12345"))
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
    }

    @Test
    fun `tai khoan bi khoa thi AccountLocked`() = runTest {
        repository.signInResult = AuthResult.Failure(AuthError.AccountLocked)
        assertEquals(AuthResult.Failure(AuthError.AccountLocked), useCase("nam@example.com", "abc12345"))
    }
}
