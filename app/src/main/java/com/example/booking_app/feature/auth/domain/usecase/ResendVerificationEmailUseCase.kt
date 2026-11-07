package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.session.VerificationEmailSender
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import javax.inject.Inject

/**
 * Gửi email xác minh (FR-009, research R9). Gửi được thì ghi mốc `last_verification_email_at` để banner
 * đếm 60 giây trước khi cho gửi lại, kể cả khi xoay màn hình hay mở lại app.
 */
class ResendVerificationEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val prefs: SessionPrefs,
    private val clock: AppClock,
) : VerificationEmailSender {

    suspend operator fun invoke(): AuthResult<Unit> {
        val result = repository.sendEmailVerification()
        if (result is AuthResult.Success) prefs.setLastVerificationEmailAt(clock.now())
        return result
    }

    override suspend fun send(): Boolean = invoke() is AuthResult.Success
}
