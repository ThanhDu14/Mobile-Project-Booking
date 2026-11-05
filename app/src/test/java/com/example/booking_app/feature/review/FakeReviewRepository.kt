package com.example.booking_app.feature.review

import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewAuthor
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.model.SubmittedReview
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant

val COMPLETED_AT: Instant = Instant.parse("2026-10-04T09:00:00Z")

/**
 * Repository giả. [createError] làm `create` ném lỗi; [createGate] giữ `create` treo cho tới khi
 * được complete (kiểm tra chặn gửi trùng).
 */
class FakeReviewRepository : ReviewRepository {
    var target: ReviewTarget = reviewTarget("b1")
    var targetError: Throwable? = null
    val myReview = MutableStateFlow<Review?>(null)
    var createError: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    val createCalls = mutableListOf<CreateCall>()

    data class CreateCall(val bookingId: String, val ratings: Ratings, val comment: String, val photoPaths: List<String>)

    override fun observeSummary(venueId: String): Flow<VenueRatingSummary> =
        MutableStateFlow(VenueRatingSummary(0.0, 0, 0.0, 0.0, 0.0))

    override fun observeMyReview(bookingId: String): Flow<Review?> = myReview

    override suspend fun getReviewTarget(bookingId: String): ReviewTarget {
        targetError?.let { throw it }
        return target
    }

    override suspend fun create(
        bookingId: String,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String>,
    ): SubmittedReview {
        createCalls += CreateCall(bookingId, ratings, comment, photoPaths)
        createGate?.await()
        createError?.let { throw it }
        val overall = Math.round((ratings.court + ratings.cleanliness + ratings.service) * 10.0 / 3) / 10.0
        return SubmittedReview(bookingId, overall, COMPLETED_AT.plus(Duration.ofDays(7)))
    }
}

fun reviewTarget(bookingId: String, status: String = "COMPLETED", completedAt: Instant? = COMPLETED_AT) = ReviewTarget(
    bookingId = bookingId,
    venueId = "venueA",
    venueName = "Sân Phú Thọ",
    venuePhotoUrl = null,
    startAt = Instant.parse("2026-10-04T00:00:00Z"),
    endAt = Instant.parse("2026-10-04T02:00:00Z"),
    courtNames = listOf("Sân 2"),
    status = status,
    completedAt = completedAt,
)

fun review(id: String, ratings: Ratings = Ratings(5, 4, 3), comment: String = "Sân sạch") = Review(
    id = id,
    venueId = "venueA",
    author = ReviewAuthor("Người chơi", null, isDeleted = false),
    ratings = ratings,
    overall = 4.0,
    comment = comment,
    photoUrls = emptyList(),
    ownerReply = null,
    isHidden = false,
    isEdited = false,
    createdAt = COMPLETED_AT.plus(Duration.ofHours(1)),
    canEditUntil = COMPLETED_AT.plus(Duration.ofDays(7)),
)
