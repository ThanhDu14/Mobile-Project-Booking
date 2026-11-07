package com.example.booking_app.feature.account.ui

import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.account.domain.model.AccountError

/** Chuỗi hiển thị cho lỗi của `feature/account` (FR-023). Lỗi riêng của US6, US7 thêm ở story tương ứng. */
@StringRes
fun accountErrorMessage(error: Throwable): Int = when (error) {
    AccountError.Network, AppError.Network, AppError.Timeout -> R.string.account_error_network
    AccountError.ImageTooLarge -> R.string.account_error_image_size
    AccountError.UnsupportedImage -> R.string.account_error_image_type
    AccountError.WrongCurrentPassword -> R.string.account_error_wrong_password
    AccountError.SamePassword -> R.string.account_error_same_password
    AccountError.WeakPassword -> R.string.account_error_weak_password
    AccountError.ReauthWrongAccount -> R.string.account_reauth_wrong_account
    AccountError.WrongOtpCode -> R.string.account_error_wrong_otp
    AccountError.OtpExpired -> R.string.account_error_otp_expired
    AppError.RateLimited -> R.string.account_error_too_many_attempts
    AppError.SessionRevoked -> R.string.account_session_revoked
    else -> R.string.account_error_generic
}

/** Lỗi mạng thì Snackbar có nút "Thử lại" (FR-023). */
fun isRetryable(error: Throwable): Boolean =
    error == AccountError.Network || error == AppError.Network || error == AppError.Timeout

/** Khóa Fragment Result API giữa các màn hình của `nav_account`. */
object AccountResults {
    const val DISTRICTS = "account_districts_result"
    const val DISTRICT_IDS = "district_ids"
    const val AVATAR_SOURCE = "account_avatar_source_result"
    const val SOURCE = "source"
    const val SOURCE_CAMERA = "camera"
    const val SOURCE_GALLERY = "gallery"
    const val AVATAR_CROP = "account_avatar_crop_result"
    const val CROPPED_URI = "cropped_uri"
    const val REAUTH = "account_reauth_result"
    const val REAUTH_DONE = "done"
}
