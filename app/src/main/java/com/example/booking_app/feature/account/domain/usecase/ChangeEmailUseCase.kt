package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.feature.account.data.AccountErrorMapper
import com.example.booking_app.feature.account.data.ReauthDataSource
import com.example.booking_app.feature.account.domain.model.AccountError
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Đổi email đăng nhập (US7, FR-016, research R10): xác nhận lại bằng mật khẩu hiện tại rồi gửi link xác minh tới
 * email mới. `users.email` do server đồng bộ sau khi bấm link (`sync-account-email`).
 */
class ChangeEmailUseCase @Inject constructor(
    private val reauthenticate: ReauthenticateUseCase,
    private val dataSource: ReauthDataSource,
) {
    /** Email đăng nhập hiện tại, đã viết thường. */
    fun currentEmail(): String? = dataSource.currentAccount()?.email?.trim()?.lowercase()

    /** [newEmail] đã chuẩn hóa bằng `EmailRule`. Trùng email hiện tại thì ném [AccountError.SameEmail]. */
    suspend operator fun invoke(newEmail: String, currentPassword: String) {
        if (newEmail == currentEmail()) throw AccountError.SameEmail
        reauthenticate.withPassword(currentPassword)
        try {
            dataSource.verifyBeforeUpdateEmail(newEmail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountErrorMapper.map(e)
        }
    }
}
