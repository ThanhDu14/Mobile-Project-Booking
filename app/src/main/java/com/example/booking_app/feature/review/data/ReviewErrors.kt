package com.example.booking_app.feature.review.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.example.booking_app.feature.review.domain.ReviewError

/**
 * Đổi lỗi sau [com.example.booking_app.core.network.FunctionErrors] thành [ReviewError]. `FunctionErrors`
 * đã xử lý `401` (làm mới token) và `ACCOUNT_LOCKED` (đăng xuất); các mã còn lại nằm trong body của
 * [FunctionHttpException] bọc trong [AppError.Unknown].
 */
fun Throwable.toReviewError(): ReviewError = when (this) {
    AppError.Network, AppError.Timeout -> ReviewError.NETWORK
    AppError.Locked -> ReviewError.ACCOUNT_LOCKED
    AppError.NotSignedIn -> ReviewError.UNAUTHENTICATED
    is AppError.Unknown -> (cause as? FunctionHttpException)
        ?.let { ReviewError.fromCode(parseFunctionErrorCode(it.body)) }
        ?: ReviewError.INTERNAL
    else -> ReviewError.INTERNAL
}
