package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.session.PendingSignup
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.model.AuthResult
import javax.inject.Inject

/**
 * Đăng ký email/mật khẩu (US1, FR-004): tạo tài khoản → lưu `PendingSignup` → gửi email xác minh →
 * gọi `on-signup`. `PendingSignup` lưu trước khi khởi tạo để đóng app giữa chừng thì lần mở sau
 * (`AccountSetupFragment`) gọi tiếp được (research R4). Bấm "Thử lại" sau khi hết giờ không tạo lại
 * tài khoản đã tạo xong (R13, xử lý trong `AuthRepository.signUpWithEmail`).
 *
 * [name] đã qua `NameRule`. Xong thì `SessionManager` chuyển `SignedIn`, `MainActivity` điều hướng.
 */
class RegisterWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val prefs: SessionPrefs,
    private val resendVerification: ResendVerificationEmailUseCase,
    private val completeSignup: CompleteSignupUseCase,
    private val clock: AppClock,
) {
    suspend operator fun invoke(name: String, email: String, password: String): CompleteSignupResult {
        val displayName = name.trim()
        val uid = when (val created = repository.signUpWithEmail(email.trim().lowercase(), password)) {
            is AuthResult.Failure -> return CompleteSignupResult.Failure(created.error)
            is AuthResult.Success -> created.value
        }
        prefs.savePendingSignup(PendingSignup(uid, displayName, clock.now()))
        // Lỗi gửi email thì vẫn cho dùng app, banner có nút gửi lại. Thử lại thì không gửi lần nữa.
        if (prefs.lastVerificationEmailAt() == null) resendVerification()
        return completeSignup(displayName, termsAccepted = true)
    }
}
