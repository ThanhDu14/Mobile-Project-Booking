package com.example.booking_app.core.model

/**
 * Thông tin rút gọn của một cơ sở, dùng chung giữa các nhóm (spec 070 research R12):
 * lưu yêu thích, hiển thị thẻ sân. [photoPath] là path hoặc URL ảnh đầu tiên, có thể không có.
 */
data class VenueSummary(
    val venueId: String,
    val name: String,
    val photoPath: String?,
    val address: String,
)
