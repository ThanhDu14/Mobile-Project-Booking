package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SignOutReason
import com.example.booking_app.feature.account.data.AccountFunctionsApi
import com.example.booking_app.feature.account.domain.model.AccountError
import javax.inject.Inject

/**
 * Xóa tài khoản (US6, research R11): xác nhận lại bằng mật khẩu nếu màn hình có ô mật khẩu, gọi `confirm` với
 * `impactVersion` đã xem, rồi đăng xuất với lý do `ACCOUNT_DELETED` (`MainActivity` hiện "Tài khoản đã được xóa").
 * Hết giờ hoặc mất mạng: server có thể đã chốt, nên gọi lại đúng một lần (idempotent) rồi mới báo
 * [AccountError.DeleteResultUnknown].
 */
class DeleteAccountUseCase @Inject constructor(
    private val reauthenticate: ReauthenticateUseCase,
    private val api: AccountFunctionsApi,
    private val sessionManager: SessionManager,
) {
    /** [password] khác `null` khi tài khoản xác nhận bằng mật khẩu ngay trên màn hình. */
    suspend operator fun invoke(impactVersion: String, confirmText: String, password: String?) {
        password?.let { reauthenticate.withPassword(it) }
        try {
            api.confirmDeletion(impactVersion, confirmText)
        } catch (_: AccountError.Network) {
            try {
                api.confirmDeletion(impactVersion, confirmText)
            } catch (_: AccountError.Network) {
                throw AccountError.DeleteResultUnknown
            }
        }
        sessionManager.signOut(SignOutReason.ACCOUNT_DELETED)
    }
}
