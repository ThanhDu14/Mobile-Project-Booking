package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.feature.account.data.AccountErrorMapper
import com.example.booking_app.feature.account.data.ReauthDataSource
import com.example.booking_app.feature.account.domain.validation.DifferentPasswordRule
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Đổi mật khẩu (US5, research R9): xác nhận lại bằng mật khẩu hiện tại rồi `updatePassword`. Firebase thu hồi
 * refresh token của mọi phiên, máy này nhận token mới nên vẫn đăng nhập, các máy khác bị đăng xuất.
 */
class ChangePasswordUseCase @Inject constructor(
    private val reauthenticate: ReauthenticateUseCase,
    private val dataSource: ReauthDataSource,
) {
    suspend operator fun invoke(current: String, new: String) {
        DifferentPasswordRule.validate(current, new)?.let { throw it }
        reauthenticate.withPassword(current)
        try {
            dataSource.updatePassword(new)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountErrorMapper.map(e)
        }
    }
}
