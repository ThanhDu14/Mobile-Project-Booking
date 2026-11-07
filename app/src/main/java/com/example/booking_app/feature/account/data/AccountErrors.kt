package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.account.domain.model.AccountError
import kotlinx.coroutines.CancellationException

/**
 * Chạy [block] và đổi lỗi mạng/hết giờ thành `AccountError.Network`. `AppError` khác (khóa, chưa đăng nhập, phiên bị
 * thu hồi) giữ nguyên để `SessionManager` xử lý; lỗi lạ thành `AccountError.Unknown`.
 */
internal suspend fun <T> accountCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: AccountError) {
    throw e
} catch (e: AppError) {
    throw when (e) {
        AppError.Network, AppError.Timeout -> AccountError.Network
        is AppError.Unknown -> AccountError.Unknown(e.cause)
        else -> e
    }
} catch (e: Exception) {
    throw AccountError.Unknown(e)
}
