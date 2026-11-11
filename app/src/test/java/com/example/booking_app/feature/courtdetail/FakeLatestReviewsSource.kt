package com.example.booking_app.feature.courtdetail

import com.example.booking_app.feature.courtdetail.domain.LatestReviewsSource
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import java.time.Instant

/** Nguồn đánh giá giả: đếm số lần gọi để kiểm tra thời điểm tải (FR-024) và trường hợp không gọi. */
class FakeLatestReviewsSource(
    var reviews: List<ReviewPreview> = emptyList(),
    var error: Throwable? = null,
) : LatestReviewsSource {
    var calls = 0
    var lastLimit: Int? = null

    override suspend fun latest(venueId: String, limit: Int): List<ReviewPreview> {
        calls++
        lastLimit = limit
        error?.let { throw it }
        return reviews
    }
}

fun testReview(
    id: String,
    createdAt: Instant? = Instant.parse("2026-10-30T03:00:00Z"),
    authorName: String? = "Minh Anh",
    overall: Int = 5,
    comment: String? = "Sân đẹp",
    photoUrls: List<String> = emptyList(),
    reply: String? = null,
) = ReviewPreview(
    id = id,
    authorName = authorName,
    authorAvatarUrl = null,
    overall = overall,
    comment = comment,
    photoUrls = photoUrls,
    reply = reply,
    createdAt = createdAt,
)
