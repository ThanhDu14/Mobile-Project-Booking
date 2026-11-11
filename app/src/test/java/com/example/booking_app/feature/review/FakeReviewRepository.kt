package com.example.booking_app.feature.review

import com.example.booking_app.feature.review.domain.ReviewPhotoTooLargeException
import com.example.booking_app.feature.review.domain.ReviewPhotoUploader
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.OwnerReply
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewAuthor
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.model.ReviewTargetType
import com.example.booking_app.feature.review.domain.model.SubmittedReview
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

val COMPLETED_AT: Instant = Instant.parse("2026-10-04T09:00:00Z")

const val FAKE_UID = "u1"

/**
 * Repository giả. [createError] làm `create` ném lỗi; [createGate] giữ `create` treo cho tới khi
 * được complete (kiểm tra chặn gửi trùng).
 */
class FakeReviewRepository : ReviewRepository {
    var target: ReviewTarget = reviewTarget("b1")
    var targetError: Throwable? = null

    /** Thẻ đầu màn hình đánh giá buổi vãng lai; `uid` của người dùng giả là [FAKE_UID]. */
    var dropInTarget: ReviewTarget = dropInReviewTarget("s1")
    val dropInTargetCalls = mutableListOf<String>()

    /** Đánh giá theo id (`{sessionId}_{uid}`); id không có trong map thì dùng [myReview]. */
    val myReviewsById = mutableMapOf<String, Review?>()
    val observedMyReviewIds = mutableListOf<String>()
    var observeMyReviewError: Throwable? = null
    val myReview = MutableStateFlow<Review?>(null)
    var createError: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    val createCalls = mutableListOf<CreateCall>()
    var updateError: Throwable? = null
    val updateCalls = mutableListOf<CreateCall>()
    var deleteError: Throwable? = null
    val deleteCalls = mutableListOf<String>()
    var replyError: Throwable? = null
    val replyCalls = mutableListOf<Pair<String, String>>()
    val discardCalls = mutableListOf<Pair<String?, List<String>>>()
    val discardSessionIds = mutableListOf<String?>()

    /** Toàn bộ đánh giá "trên server"; danh sách lọc theo đúng điều kiện truy vấn của repository thật. */
    val reviews = MutableStateFlow<List<Review>>(emptyList())

    /** Đánh giá có `starBucket` theo id (mặc định `round(overall)`). */
    val starBuckets = mutableMapOf<String, Int>()
    var listError: Throwable? = null
    val venueQueries = mutableListOf<VenueQuery>()
    val ownerQueries = mutableListOf<OwnerQuery>()

    data class CreateCall(
        val bookingId: String?,
        val ratings: Ratings,
        val comment: String,
        val photoPaths: List<String>,
        val sessionId: String? = null,
    )
    data class VenueQuery(val venueId: String, val stars: Int?, val withPhotos: Boolean, val limit: Int)
    data class OwnerQuery(val venueIds: List<String>, val unrepliedOnly: Boolean, val limit: Int)

    override fun observeSummary(venueId: String): Flow<VenueRatingSummary> =
        MutableStateFlow(VenueRatingSummary(4.0, 25, 4.2, 3.9, 4.1))

    override fun observeMyReview(bookingId: String): Flow<Review?> {
        observedMyReviewIds += bookingId
        observeMyReviewError?.let { error -> return flow { throw error } }
        return if (bookingId in myReviewsById) MutableStateFlow(myReviewsById[bookingId]) else myReview
    }

    override fun observeVenueReviews(venueId: String, stars: Int?, withPhotos: Boolean, limit: Int): Flow<List<Review>> {
        venueQueries += VenueQuery(venueId, stars, withPhotos, limit)
        listError?.let { error -> return flow { throw error } }
        return reviews.map { all ->
            all.filter { it.venueId == venueId && !it.isHidden }
                .filter { stars == null || (starBuckets[it.id] ?: Math.round(it.overall).toInt()) == stars }
                .filter { !withPhotos || it.photoUrls.isNotEmpty() }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }
    }

    override fun observeOwnerReviews(venueIds: List<String>, unrepliedOnly: Boolean, limit: Int): Flow<List<Review>> {
        ownerQueries += OwnerQuery(venueIds, unrepliedOnly, limit)
        listError?.let { error -> return flow { throw error } }
        return reviews.map { all ->
            all.filter { it.venueId in venueIds && !it.isHidden }
                .filter { !unrepliedOnly || it.ownerReply == null }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }
    }

    override suspend fun getReviewTarget(bookingId: String): ReviewTarget {
        targetError?.let { throw it }
        return target
    }

    override suspend fun getDropInReviewTarget(sessionId: String): ReviewTarget {
        dropInTargetCalls += sessionId
        targetError?.let { throw it }
        return dropInTarget
    }

    override fun dropInReviewId(sessionId: String): String = "${sessionId}_$FAKE_UID"

    override suspend fun create(
        bookingId: String?,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String>,
        sessionId: String?,
    ): SubmittedReview {
        createCalls += CreateCall(bookingId, ratings, comment, photoPaths, sessionId)
        createGate?.await()
        createError?.let { throw it }
        return SubmittedReview(bookingId ?: dropInReviewId(sessionId.orEmpty()), overallOf(ratings), COMPLETED_AT.plus(Duration.ofDays(7)))
    }

    override suspend fun update(
        reviewId: String,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String>,
    ): SubmittedReview {
        updateCalls += CreateCall(reviewId, ratings, comment, photoPaths)
        updateError?.let { throw it }
        return SubmittedReview(reviewId, overallOf(ratings), COMPLETED_AT.plus(Duration.ofDays(7)))
    }

    override suspend fun delete(reviewId: String) {
        deleteCalls += reviewId
        deleteError?.let { throw it }
        myReview.value = null
    }

    override suspend fun reply(reviewId: String, text: String): OwnerReply {
        replyCalls += reviewId to text
        replyError?.let { throw it }
        val reply = OwnerReply(text, COMPLETED_AT.plus(Duration.ofDays(1)), isEdited = false)
        reviews.value = reviews.value.map { if (it.id == reviewId) it.copy(ownerReply = reply) else it }
        return reply
    }

    override suspend fun discardPhotos(bookingId: String?, photoPaths: List<String>, sessionId: String?): Int {
        discardCalls += bookingId to photoPaths
        discardSessionIds += sessionId
        return photoPaths.size
    }

    private fun overallOf(ratings: Ratings) =
        Math.round((ratings.court + ratings.cleanliness + ratings.service) * 10.0 / 3) / 10.0
}

/**
 * Uploader giả: ảnh có uri trong [failing] thì lỗi, trong [tooLarge] thì quá 2 MB; [gate] giữ việc tải
 * treo cho tới khi được complete. Path trả về là `player1/{bookingId}/{tên file của uri}`.
 */
class FakePhotoUploader : ReviewPhotoUploader {
    val failing = mutableSetOf<String>()
    val tooLarge = mutableSetOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()

    override suspend fun upload(bookingId: String, localUri: String): String {
        calls += localUri
        gate?.await()
        if (localUri in tooLarge) throw ReviewPhotoTooLargeException()
        if (localUri in failing) throw com.example.booking_app.feature.review.domain.ReviewException(
            com.example.booking_app.feature.review.domain.ReviewError.NETWORK,
        )
        return "player1/$bookingId/${localUri.substringAfterLast('/')}.jpg"
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

/** Buổi vãng lai đã kết thúc lúc [COMPLETED_AT] và lượt đăng ký đã check-in. */
fun dropInReviewTarget(
    sessionId: String,
    status: String? = "CHECKED_IN",
    endAt: Instant? = COMPLETED_AT,
) = ReviewTarget(
    bookingId = null,
    venueId = "venueA",
    venueName = "Sân Hòa Bình",
    venuePhotoUrl = null,
    startAt = endAt?.minus(Duration.ofHours(2)),
    endAt = endAt,
    courtNames = listOf("Sân 1", "Sân 2"),
    status = status,
    completedAt = endAt,
    sessionId = sessionId,
    reviewId = "${sessionId}_$FAKE_UID",
)

fun review(
    id: String,
    ratings: Ratings = Ratings(5, 4, 3),
    comment: String = "Sân sạch",
    venueId: String = "venueA",
    overall: Double = 4.0,
    createdAt: Instant = COMPLETED_AT.plus(Duration.ofHours(1)),
    photoPaths: List<String> = emptyList(),
    ownerReply: OwnerReply? = null,
    targetType: ReviewTargetType = ReviewTargetType.BOOKING,
) = Review(
    id = id,
    venueId = venueId,
    author = ReviewAuthor("Người chơi", null, isDeleted = false),
    ratings = ratings,
    overall = overall,
    comment = comment,
    photoUrls = photoPaths.map { "https://cdn.test/$it" },
    ownerReply = ownerReply,
    isHidden = false,
    isEdited = false,
    createdAt = createdAt,
    canEditUntil = COMPLETED_AT.plus(Duration.ofDays(7)),
    photoPaths = photoPaths,
    targetType = targetType,
)
