package com.example.booking_app.feature.auth.domain.usecase

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.FakeGoogleCredentialDataSource
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.PendingCredential
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignInWithGoogleUseCaseTest {

    private val google = FakeGoogleCredentialDataSource()
    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()
    private val useCase = SignInWithGoogleUseCase(google, repository, sessionManager)
    private val activity = mockk<Activity>(relaxed = true)

    @Test
    fun `thanh cong thi dang nhap Firebase bang idToken roi lam moi token`() = runTest {
        assertEquals(AuthResult.Success(Unit), useCase(activity))
        assertEquals(listOf("google-id-token"), repository.googleCalls)
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `huy bottom sheet thi GoogleCancelled, khong goi Firebase`() = runTest {
        google.result = { throw GetCredentialCancellationException() }
        assertEquals(AuthResult.Failure(AuthError.GoogleCancelled), useCase(activity))
        assertEquals(emptyList<String>(), repository.googleCalls)
    }

    @Test
    fun `khong co tai khoan Google tren may thi NoGoogleAccount`() = runTest {
        google.result = { throw NoCredentialException() }
        assertEquals(AuthResult.Failure(AuthError.NoGoogleAccount), useCase(activity))
    }

    @Test
    fun `email trung tai khoan mat khau thi NeedsLinking kem pendingCredential`() = runTest {
        val pending = object : PendingCredential {}
        repository.googleResult = AuthResult.Failure(AuthError.NeedsLinking("nam@congty.vn", pending))
        assertEquals(AuthResult.Failure(AuthError.NeedsLinking("nam@congty.vn", pending)), useCase(activity))
        assertEquals(emptyList<Boolean>(), sessionManager.refreshCalls)
    }
}
