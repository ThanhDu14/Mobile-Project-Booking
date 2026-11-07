package com.example.booking_app.feature.auth.domain.usecase

import android.app.Activity
import android.os.Parcelable
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import com.example.booking_app.feature.auth.domain.model.OtpSendResult
import com.example.booking_app.feature.auth.domain.model.OtpSession
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SendOtpUseCaseTest {

    private val repository = FakeAuthRepository()
    private val sessionManager = FakeSessionManager()
    private val activity = mockk<Activity>(relaxed = true)
    private val useCase = SendOtpUseCase(repository, sessionManager, FakeClock { 7_000L })

    @Test
    fun `so duoc chuan hoa E164 truoc khi gui (FR-017)`() = runTest {
        useCase(activity, "0912 345 678")
        assertEquals("+84912345678", repository.sendOtpCalls.single().first)
    }

    @Test
    fun `gui duoc ma thi tao phien OTP moi, wrongCount 0`() = runTest {
        repository.sendOtpResult = AuthResult.Success(OtpSendResult.CodeSent("v1", null))
        val result = useCase(activity, "+84912345678")
        assertEquals(
            AuthResult.Success(SendOtpResult.CodeSent(OtpSession("+84912345678", "v1", null, 7_000L, 0))),
            result,
        )
    }

    @Test
    fun `gui lai thi dung resendToken cua phien cu`() = runTest {
        val token = mockk<Parcelable>()
        useCase(activity, "+84912345678", resendToken = token)
        assertEquals(token, repository.sendOtpCalls.single().second)
    }

    @Test
    fun `may tu xac minh duoc thi dang nhap luon va lam moi phien`() = runTest {
        repository.sendOtpResult = AuthResult.Success(OtpSendResult.AutoVerified)
        assertEquals(AuthResult.Success(SendOtpResult.SignedIn), useCase(activity, "+84912345678"))
        assertEquals(listOf(true), sessionManager.refreshCalls)
    }

    @Test
    fun `PhoneNotSupported va TooManyAttempts giu nguyen`() = runTest {
        for (error in listOf(AuthError.PhoneNotSupported, AuthError.TooManyAttempts)) {
            repository.sendOtpResult = AuthResult.Failure(error)
            assertEquals(AuthResult.Failure(error), useCase(activity, "+84912345678"))
        }
    }
}
