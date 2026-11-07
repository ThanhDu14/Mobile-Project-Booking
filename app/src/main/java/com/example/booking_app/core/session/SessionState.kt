package com.example.booking_app.core.session

import com.example.booking_app.core.network.AppError

/** Trạng thái phiên đăng nhập (data-model §4). `SignedOut` đồng thời là chế độ khách (research R15). */
sealed interface SessionState {
    data object Loading : SessionState

    data class SignedOut(val reason: SignOutReason?) : SessionState

    /** Đã xác thực bằng Google/OTP nhưng chưa đồng ý điều khoản. */
    data class NeedsConsent(val prefill: ConsentPrefill) : SessionState

    /** Đang chờ `on-signup` gán claim `appRole`. */
    data object Initializing : SessionState

    data class InitFailed(val error: AppError) : SessionState

    data class SignedIn(val uid: String, val appRole: AppRole, val emailVerified: Boolean) : SessionState
}

enum class SignOutReason { LOCKED }
