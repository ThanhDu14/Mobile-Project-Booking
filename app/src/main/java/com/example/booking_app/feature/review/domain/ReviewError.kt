package com.example.booking_app.feature.review.domain

/**
 * Lỗi khi ghi đánh giá, theo bảng mã lỗi của contracts/edge-function-review.md. Tên trùng `error.code`
 * server trả về; riêng [NETWORK] do app tự đặt khi mất mạng hoặc quá thời gian.
 */
enum class ReviewError {
    INVALID_ARGUMENT,
    UNAUTHENTICATED,
    NOT_OWNER,
    OWN_VENUE,
    ACCOUNT_LOCKED,
    NOT_FOUND,
    BOOKING_NOT_COMPLETED,
    REVIEW_WINDOW_CLOSED,
    ALREADY_REVIEWED,
    PHOTO_MISSING,
    INTERNAL,

    /** Mất mạng: giữ nguyên bản nháp và hiện "Thử lại". */
    NETWORK,
    ;

    companion object {
        /** Mã lạ (server mới thêm mà app chưa biết) coi như [INTERNAL]. */
        fun fromCode(code: String?): ReviewError =
            entries.firstOrNull { it != NETWORK && it.name == code } ?: INTERNAL
    }
}

/** Ném từ `ReviewRepository` để ViewModel lấy [error] mà không cần biết lỗi gốc. */
class ReviewException(val error: ReviewError, cause: Throwable? = null) : Exception(error.name, cause)
