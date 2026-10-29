package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.core.network.IdTokenProvider
import com.example.booking_app.feature.account.data.AccountErrorMapper
import com.example.booking_app.feature.account.data.ReauthDataSource
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Xác nhận lại danh tính trước thao tác nhạy cảm (research R8, FR-017). Thành công thì ép làm mới ID token để claim
 * `auth_time` mới, server `delete-account` dựa vào đó. Lỗi ném ra đã qua `AccountErrorMapper`.
 */
class ReauthenticateUseCase @Inject constructor(
    private val dataSource: ReauthDataSource,
    private val idTokenProvider: IdTokenProvider,
) {
    /** Ưu tiên `password` → Google → số điện thoại. `null` khi chưa đăng nhập hoặc không có phương thức nào dùng được. */
    fun method(): ReauthMethod? {
        val account = dataSource.currentAccount() ?: return null
        return when {
            PASSWORD in account.providers -> ReauthMethod.Password
            GOOGLE in account.providers -> ReauthMethod.Google
            PHONE in account.providers && account.phoneE164 != null -> ReauthMethod.Phone(account.phoneE164)
            else -> null
        }
    }

    suspend fun withPassword(password: String) = reauth { dataSource.reauthWithPassword(password) }

    suspend fun withGoogle(idToken: String) = reauth { dataSource.reauthWithGoogle(idToken) }

    suspend fun withPhoneCode(verificationId: String, code: String) =
        reauth { dataSource.reauthWithPhone(verificationId, code) }

    /** Máy tự xác minh OTP: `SendOtpUseCase` của 010 đã đăng nhập lại bằng số hiện tại, chỉ còn làm mới token. */
    suspend fun afterPhoneAutoVerified() = reauth {}

    private suspend fun reauth(block: suspend () -> Unit) {
        try {
            block()
            idTokenProvider.getToken(forceRefresh = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountErrorMapper.map(e)
        }
    }

    private companion object {
        const val PASSWORD = "password"
        const val GOOGLE = "google.com"
        const val PHONE = "phone"
    }
}
