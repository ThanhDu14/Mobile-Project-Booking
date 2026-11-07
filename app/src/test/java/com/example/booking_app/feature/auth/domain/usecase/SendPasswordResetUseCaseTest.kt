package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.FakeSessionPrefs
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.auth.FakeAuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthError
import com.example.booking_app.feature.auth.domain.model.AuthResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendPasswordResetUseCaseTest {

    private val repository = FakeAuthRepository()
    private val prefs = FakeSessionPrefs()
    private val useCase = SendPasswordResetUseCase(repository, prefs, FakeClock { 5_000L })

    @Test
    fun `email duoc trim va viet thuong, gui xong ghi moc last_password_reset_at`() = runTest {
        assertEquals(AuthResult.Success(Unit), useCase("  Nam.Le@Email.COM "))
        assertEquals(listOf("nam.le@email.com"), repository.resetCalls)
        assertEquals(5_000L, prefs.lastPasswordResetAt)
    }

    @Test
    fun `email chua dang ky van thanh cong voi cung thong bao (FR-018)`() = runTest {
        // Không bật Email enumeration protection thì Firebase trả ERROR_USER_NOT_FOUND.
        repository.resetResult = AuthResult.Failure(AuthError.InvalidCredentials)
        assertEquals(AuthResult.Success(Unit), useCase("khongco@email.com"))
        assertEquals(5_000L, prefs.lastPasswordResetAt)
    }

    @Test
    fun `qua nhieu lan thu thi TooManyAttempts, khong ghi moc`() = runTest {
        repository.resetResult = AuthResult.Failure(AuthError.TooManyAttempts)
        assertEquals(AuthResult.Failure(AuthError.TooManyAttempts), useCase("nam@email.com"))
        assertNull(prefs.lastPasswordResetAt)
    }

    @Test
    fun `mat mang thi Network`() = runTest {
        repository.resetResult = AuthResult.Failure(AuthError.Network)
        assertEquals(AuthResult.Failure(AuthError.Network), useCase("nam@email.com"))
        assertNull(prefs.lastPasswordResetAt)
    }
}
