package com.example.booking_app.feature.auth.domain.usecase

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.feature.auth.data.OnSignupApi
import com.example.booking_app.feature.auth.data.OnSignupRequest
import com.example.booking_app.feature.auth.domain.model.AuthError
import javax.inject.Inject

sealed interface CompleteSignupResult {
    data class Success(val appRole: AppRole) : CompleteSignupResult

    /** Quay lại bước đồng ý điều khoản. */
    data object TermsRequired : CompleteSignupResult

    /** Lỗi dưới ô họ tên. */
    data object InvalidDisplayName : CompleteSignupResult

    data class Failure(val error: AuthError) : CompleteSignupResult
}

/**
 * Gọi `on-signup` để khởi tạo tài khoản (FR-006, research R2, R4). Xong thì làm mới token để có
 * claim `appRole`, rồi xóa `PendingSignup`. Lỗi mạng giữ `PendingSignup` để lần mở app sau gọi lại.
 */
class CompleteSignupUseCase @Inject constructor(
    private val api: OnSignupApi,
    private val sessionManager: SessionManager,
    private val prefs: SessionPrefs,
) {
    suspend operator fun invoke(displayName: String?, termsAccepted: Boolean): CompleteSignupResult {
        val response = try {
            api.onSignup(OnSignupRequest(displayName, termsAccepted))
        } catch (e: AppError) {
            return e.toResult()
        }
        sessionManager.refresh(forceTokenRefresh = true)
        prefs.clearPendingSignup()
        return CompleteSignupResult.Success(AppRole.fromClaim(response.appRole) ?: AppRole.PLAYER)
    }

    private suspend fun AppError.toResult(): CompleteSignupResult = when (this) {
        AppError.Network, AppError.Timeout -> CompleteSignupResult.Failure(AuthError.Network)
        AppError.RateLimited -> CompleteSignupResult.Failure(AuthError.TooManyAttempts)
        AppError.Conflict -> CompleteSignupResult.Failure(AuthError.Unknown(this))
        // FunctionErrors đã báo AccountLockedBus; SessionManager đăng xuất và giữ lý do LOCKED.
        AppError.Locked -> CompleteSignupResult.Failure(AuthError.AccountLocked)
        // [011] SessionManager tự đăng xuất với lý do ACCOUNT_DELETED / SESSION_REVOKED.
        AppError.AccountDeleted, AppError.SessionRevoked -> CompleteSignupResult.Failure(AuthError.Unknown(this))
        // Đã làm mới token và thử lại một lần mà vẫn 401.
        AppError.NotSignedIn -> {
            sessionManager.signOut()
            CompleteSignupResult.Failure(AuthError.Unknown(this))
        }
        is AppError.Unknown -> when (parseFunctionErrorCode((cause as? FunctionHttpException)?.body)) {
            "TERMS_REQUIRED" -> CompleteSignupResult.TermsRequired
            "INVALID_DISPLAY_NAME" -> CompleteSignupResult.InvalidDisplayName
            else -> CompleteSignupResult.Failure(AuthError.Unknown(this))
        }
    }
}
