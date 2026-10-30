package com.example.booking_app.core.ui

/**
 * Bốn trạng thái của một màn hình danh sách (constitution nguyên tắc V): đang tải, có dữ liệu,
 * rỗng, lỗi kèm "Thử lại". Dùng chung với [bindStateViews].
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    data object Empty : UiState<Nothing>

    /** [retry] do Fragment gắn vào nút "Thử lại"; `null` thì ẩn nút. */
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UiState<Nothing>
}
