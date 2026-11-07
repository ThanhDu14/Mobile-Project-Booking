package com.example.booking_app.feature.review.ui.common

import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.feature.review.domain.ReviewError

/** Thông báo snackbar cho từng [ReviewError]; `when` không có `else` nên thêm mã mới là phải thêm chuỗi. */
@StringRes
fun ReviewError.messageRes(): Int = when (this) {
    ReviewError.INVALID_ARGUMENT -> R.string.review_error_invalid_argument
    ReviewError.UNAUTHENTICATED -> R.string.review_error_unauthenticated
    ReviewError.NOT_OWNER -> R.string.review_error_not_owner
    ReviewError.OWN_VENUE -> R.string.review_error_own_venue
    ReviewError.ACCOUNT_LOCKED -> R.string.review_error_account_locked
    ReviewError.NOT_FOUND -> R.string.review_error_not_found
    ReviewError.BOOKING_NOT_COMPLETED -> R.string.review_error_booking_not_completed
    ReviewError.REVIEW_WINDOW_CLOSED -> R.string.review_error_window_closed
    ReviewError.ALREADY_REVIEWED -> R.string.review_error_already_reviewed
    ReviewError.PHOTO_MISSING -> R.string.review_error_photo_missing
    ReviewError.INTERNAL -> R.string.review_error_internal
    ReviewError.NETWORK -> R.string.review_error_network
}
