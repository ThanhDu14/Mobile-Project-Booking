package com.example.booking_app.core.session

import android.net.Uri

/** Quyết định điều hướng theo phiên. `MainActivity` chỉ thực hiện, không tự rẽ nhánh (research R15). */
sealed interface RouteDecision {
    data object Welcome : RouteDecision

    /** Trang chủ người chơi ở chế độ khách; bị khóa thì mở thêm màn hình đăng nhập chồng lên. */
    data class PlayerGuest(val showLockedLogin: Boolean) : RouteDecision

    data class RoleHome(val appRole: AppRole) : RouteDecision

    data object AccountSetup : RouteDecision

    data object Consent : RouteDecision

    /** Đóng `nav_auth` để quay lại đúng màn hình bên dưới, rồi mở [returnTo] nếu có (FR-026). */
    data class PopAuth(val returnTo: Uri?) : RouteDecision

    /** Xóa back stack và vào graph của vai trò. */
    data class ResetTo(val appRole: AppRole) : RouteDecision
}

object SessionRouter {

    /** Đích khi mở app. `null` khi còn đang tải phiên. */
    fun startDestination(state: SessionState, welcomeSeen: Boolean): RouteDecision? = when (state) {
        SessionState.Loading -> null
        is SessionState.SignedOut -> when {
            state.reason == SignOutReason.LOCKED -> RouteDecision.PlayerGuest(showLockedLogin = true)
            welcomeSeen -> RouteDecision.PlayerGuest(showLockedLogin = false)
            else -> RouteDecision.Welcome
        }
        is SessionState.SignedIn -> RouteDecision.RoleHome(state.appRole)
        is SessionState.NeedsConsent -> RouteDecision.Consent
        SessionState.Initializing, is SessionState.InitFailed -> RouteDecision.AccountSetup
    }

    /** Đích sau khi đăng nhập xong (`SignedOut` → `SignedIn`). */
    fun afterSignIn(appRole: AppRole, hasPlayerGraphBelowAuth: Boolean, returnTo: Uri?): RouteDecision =
        if (appRole == AppRole.PLAYER && hasPlayerGraphBelowAuth) {
            RouteDecision.PopAuth(returnTo)
        } else {
            RouteDecision.ResetTo(appRole)
        }
}
