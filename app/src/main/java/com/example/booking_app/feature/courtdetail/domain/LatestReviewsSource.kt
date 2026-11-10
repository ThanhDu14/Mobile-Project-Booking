package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview

/**
 * Các đánh giá `VISIBLE` mới nhất của một cơ sở (FR-014, research R3). Bản cài đặt gọi `ReviewRepository.latestVisible`
 * của 070 khi đã có; chưa có thì truy vấn tạm (`FirestoreLatestReviewsSource`). Ném
 * [com.example.booking_app.core.network.AppError].
 */
interface LatestReviewsSource {
    suspend fun latest(venueId: String, limit: Int = DEFAULT_LIMIT): List<ReviewPreview>

    companion object {
        const val DEFAULT_LIMIT = 3
    }
}
