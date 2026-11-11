package com.example.booking_app.feature.courtdetail.domain.model

import java.time.Instant

/**
 * Một trong 3 đánh giá mới nhất ở chi tiết sân (FR-014). [authorName] `null` → "Người dùng đã xóa".
 * [comment] giữ nguyên, giao diện tự rút gọn 3 dòng.
 */
data class ReviewPreview(
    val id: String,
    val authorName: String?,
    val authorAvatarUrl: String?,
    val overall: Int,
    val comment: String?,
    val photoUrls: List<String>,
    val reply: String?,
    val createdAt: Instant?,
)
