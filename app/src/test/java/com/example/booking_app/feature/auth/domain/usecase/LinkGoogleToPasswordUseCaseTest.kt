package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkGoogleToPasswordUseCaseTest {

    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()
    private val pending = object : PendingCredential {}

    @Test
    fun `dang nhap mat khau roi lien ket Google, xong thi lam moi token`() = runTest {
        val useCase = LinkGoogleToPasswordUseCase(repository, sessionManager, backgroundScope)
        assertEquals(AuthResult.Success(Unit), useCase(" Nam@CongTy.vn", "abc12345", pending))
        assertEquals(listOf("nam@congty.vn" to "abc12345"), repository.signInCalls)
        assertEquals(listOf(pending), repository.linkCalls)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `sai mat khau thi InvalidCredentials, khong lien ket`() = runTest {
        repository.signInResult = AuthResult.Failure(AuthError.InvalidCredentials)
        val useCase = LinkGoogleToPasswordUseCase(repository, sessionManager, backgroundScope)
        assertEquals(AuthResult.Failure(AuthError.InvalidCredentials), useCase("nam@congty.vn", "sai", pending))
        assertEquals(emptyList<PendingCredential>(), repository.linkCalls)
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
    }

    @Test
    fun `lien ket loi thi tra loi, khong lam moi token`() = runTest {
        repository.linkResult = AuthResult.Failure(AuthError.Network)
        val useCase = LinkGoogleToPasswordUseCase(repository, sessionManager, backgroundScope)
        assertEquals(AuthResult.Failure(AuthError.Network), useCase("nam@congty.vn", "abc12345", pending))
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
    }
}
