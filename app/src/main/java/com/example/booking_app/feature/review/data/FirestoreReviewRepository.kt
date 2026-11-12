package com.example.booking_app.feature.review.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.review.data.dto.CreateReviewRequest
import com.example.booking_app.feature.review.data.dto.DeleteReviewRequest
import com.example.booking_app.feature.review.data.dto.DiscardPhotosRequest
import com.example.booking_app.feature.review.data.dto.ReplyReviewRequest
import com.example.booking_app.feature.review.data.dto.UpdateReviewRequest
import com.example.booking_app.feature.review.data.dto.ReviewWriteResponse
import com.example.booking_app.feature.review.data.dto.RatingsDto
import com.example.booking_app.feature.review.data.dto.ReviewBookingDto
import com.example.booking_app.feature.review.data.dto.ReviewDto
import com.example.booking_app.feature.review.data.dto.VenueRatingFieldsDto
import com.example.booking_app.feature.review.data.dto.toDomain
import com.example.booking_app.feature.review.data.dto.toSummary
import com.example.booking_app.feature.review.data.dto.toTarget
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.OwnerReply
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.model.SubmittedReview
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject

/**
 * Đọc `reviews`, `venues`, `bookings` bằng listener/get của Firestore; ghi qua Edge Function `review`
 * (client không được ghi `reviews`, contracts/security-rules.md).
 */
class FirestoreReviewRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
    private val api: ReviewApi,
) : ReviewRepository {

    override fun observeSummary(venueId: String): Flow<VenueRatingSummary> = callbackFlow {
        val registration = firestore.collection(COLLECTION_VENUES).document(venueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.asReviewException())
                } else if (snapshot != null) {
                    trySend(snapshot.toVenueRating().toSummary())
                }
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override fun observeMyReview(bookingId: String): Flow<Review?> {
        val uid = sessionManager.currentUid()
            ?: return flow { throw ReviewException(ReviewError.UNAUTHENTICATED) }
        return callbackFlow {
            val registration = firestore.collection(COLLECTION_REVIEWS).document(bookingId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error.asReviewException())
                    } else {
                        val review = snapshot
                            ?.takeIf { it.exists() && it.getString("userId") == uid }
                            ?.toReviewDto()
                            ?.toDomain()
                        trySend(review)
                    }
                }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    override fun observeVenueReviews(
        venueId: String,
        stars: Int?,
        withPhotos: Boolean,
        limit: Int,
    ): Flow<List<Review>> {
        var query: Query = firestore.collection(COLLECTION_REVIEWS)
            .whereEqualTo(FIELD_VENUE_ID, venueId)
            .whereEqualTo(FIELD_STATUS, STATUS_VISIBLE)
        if (stars != null) query = query.whereEqualTo(FIELD_STAR_BUCKET, stars)
        if (withPhotos) query = query.whereEqualTo(FIELD_HAS_PHOTOS, true)
        return observeList(query, limit)
    }

    override fun observeOwnerReviews(venueIds: List<String>, unrepliedOnly: Boolean, limit: Int): Flow<List<Review>> {
        if (venueIds.isEmpty()) return flow { emit(emptyList()) }
        var query: Query = firestore.collection(COLLECTION_REVIEWS)
            .whereIn(FIELD_VENUE_ID, venueIds.distinct().take(ReviewRepository.MAX_OWNER_VENUES))
            .whereEqualTo(FIELD_STATUS, STATUS_VISIBLE)
        if (unrepliedOnly) query = query.whereEqualTo(FIELD_HAS_OWNER_REPLY, false)
        return observeList(query, limit)
    }

    /**
     * Listener cho một trang (mới nhất trước, [limit] tăng dần khi tải thêm). Listener được gỡ khi
     * không còn ai thu thập (rời màn hình), tránh tốn lượt đọc của gói miễn phí.
     */
    private fun observeList(query: Query, limit: Int): Flow<List<Review>> = callbackFlow {
        val registration = query
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.asReviewException())
                } else if (snapshot != null) {
                    trySend(snapshot.documents.map { it.toReviewDto().toDomain() })
                }
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override suspend fun getReviewTarget(bookingId: String): ReviewTarget {
        val doc = try {
            firestore.collection(COLLECTION_BOOKINGS).document(bookingId).get().awaitWithTimeout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.asReviewException()
        }
        if (!doc.exists()) throw ReviewException(ReviewError.NOT_FOUND)
        return doc.toBookingDto().toTarget { ReviewPhotoUrls.venuePhoto(it) }
    }

    override suspend fun getDropInReviewTarget(sessionId: String): ReviewTarget {
        val uid = sessionManager.currentUid() ?: throw ReviewException(ReviewError.UNAUTHENTICATED)
        val sessionRef = firestore.collection(COLLECTION_DROP_IN_SESSIONS).document(sessionId)
        try {
            val session = sessionRef.get().awaitWithTimeout()
            if (!session.exists()) throw ReviewException(ReviewError.NOT_FOUND)
            val registration = sessionRef.collection(COLLECTION_REGISTRATIONS).document(uid).get().awaitWithTimeout()
            val venueId = session.getString("venueId").orEmpty()
            val venue = session.get("venueSnapshot") as? Map<*, *>
            val courtIds = (session.get("courtIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val endAt = session.instantOf("endAt")
            return ReviewTarget(
                bookingId = null,
                venueId = venueId,
                venueName = venue?.get("name") as? String ?: "",
                venuePhotoUrl = ReviewPhotoUrls.venuePhoto(venue?.get("photoUrl") as? String),
                startAt = session.instantOf("startAt"),
                endAt = endAt,
                courtNames = courtNames(venueId, courtIds),
                status = registration.takeIf { it.exists() }?.getString("status"),
                completedAt = endAt,
                sessionId = sessionId,
                reviewId = dropInReviewId(sessionId),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.asReviewException()
        }
    }

    /** Tên sân con theo thứ tự [courtIds]; sân không đọc được thì bỏ qua (chỉ để hiển thị). */
    private suspend fun courtNames(venueId: String, courtIds: List<String>): List<String> {
        if (venueId.isBlank()) return emptyList()
        return courtIds.mapNotNull { courtId ->
            try {
                firestore.collection(COLLECTION_VENUES).document(venueId).collection(COLLECTION_COURTS).document(courtId)
                    .get().awaitWithTimeout().getString("name")?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun dropInReviewId(sessionId: String): String {
        val uid = sessionManager.currentUid() ?: throw ReviewException(ReviewError.UNAUTHENTICATED)
        return "${sessionId}_$uid"
    }

    override suspend fun create(
        bookingId: String?,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String>,
        sessionId: String?,
    ): SubmittedReview {
        val request = CreateReviewRequest(
            bookingId = bookingId,
            sessionId = sessionId,
            ratings = RatingsDto(ratings.court, ratings.cleanliness, ratings.service),
            comment = comment,
            photoPaths = photoPaths,
        )
        return call { api.create(request) }.toSubmitted()
    }

    override suspend fun update(
        reviewId: String,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String>,
    ): SubmittedReview {
        val request = UpdateReviewRequest(
            reviewId = reviewId,
            ratings = RatingsDto(ratings.court, ratings.cleanliness, ratings.service),
            comment = comment,
            photoPaths = photoPaths,
        )
        return call { api.update(request) }.toSubmitted()
    }

    override suspend fun delete(reviewId: String) {
        call { api.delete(DeleteReviewRequest(reviewId)) }
    }

    override suspend fun reply(reviewId: String, text: String): OwnerReply {
        val reply = call { api.reply(ReplyReviewRequest(reviewId, text)) }.ownerReply
        return OwnerReply(reply.text, Instant.parse(reply.repliedAt), isEdited = reply.editedAt != null)
    }

    override suspend fun discardPhotos(bookingId: String?, photoPaths: List<String>, sessionId: String?): Int {
        if (photoPaths.isEmpty()) return 0
        return call { api.discardPhotos(DiscardPhotosRequest(photoPaths, bookingId, sessionId)) }.deleted
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.asReviewException()
    }

    private fun ReviewWriteResponse.toSubmitted() = SubmittedReview(
        id = review.id,
        overall = review.overall,
        canEditUntil = Instant.parse(review.canEditUntil),
    )

    private fun Throwable.asReviewException(): ReviewException =
        this as? ReviewException ?: ReviewException(toAppError().toReviewError(), this)

    private fun DocumentSnapshot.toReviewDto(): ReviewDto {
        val snapshot = get("userSnapshot") as? Map<*, *>
        val ratings = get("ratings") as? Map<*, *>
        val reply = get("ownerReply") as? Map<*, *>
        return ReviewDto(
            id = id,
            venueId = getString("venueId").orEmpty(),
            userId = getString("userId"),
            displayName = snapshot?.get("displayName") as? String,
            avatarPath = snapshot?.get("avatarPath") as? String,
            authorDeleted = getBoolean("authorDeleted") == true,
            court = ratings.intOf("court"),
            cleanliness = ratings.intOf("cleanliness"),
            service = ratings.intOf("service"),
            overall = getDouble("overall") ?: 0.0,
            comment = getString("comment").orEmpty(),
            photoPaths = (get("photoPaths") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            ownerReplyText = reply?.get("text") as? String,
            ownerRepliedAt = (reply?.get("repliedAt") as? com.google.firebase.Timestamp)?.toInstant(),
            ownerReplyEditedAt = (reply?.get("editedAt") as? com.google.firebase.Timestamp)?.toInstant(),
            status = getString("status"),
            completedAt = instantOf("completedAt"),
            editedAt = instantOf("editedAt"),
            targetType = getString("targetType"),
            createdAt = instantOf("createdAt"),
        )
    }

    private fun DocumentSnapshot.toVenueRating(): VenueRatingFieldsDto = VenueRatingFieldsDto(
        ratingAvg = getDouble("ratingAvg"),
        ratingCount = getLong("ratingCount"),
        ratingSums = (get("ratingSums") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toLong() } } }
            ?.toMap(),
    )

    private fun DocumentSnapshot.toBookingDto(): ReviewBookingDto {
        val venue = get("venueSnapshot") as? Map<*, *>
        val items = (get("items") as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        return ReviewBookingDto(
            id = id,
            venueId = getString("venueId").orEmpty(),
            venueName = venue?.get("name") as? String,
            venuePhotoUrl = venue?.get("photoUrl") as? String,
            courtNames = items.mapNotNull { it["courtName"] as? String },
            startAt = instantOf("startAt"),
            endAt = instantOf("endAt"),
            status = getString("status"),
            completedAt = instantOf("completedAt"),
        )
    }

    private fun DocumentSnapshot.instantOf(field: String): Instant? =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant()

    private fun Map<*, *>?.intOf(key: String): Int = (this?.get(key) as? Number)?.toInt() ?: 0

    private companion object {
        const val COLLECTION_REVIEWS = "reviews"
        const val COLLECTION_VENUES = "venues"
        const val COLLECTION_BOOKINGS = "bookings"
        const val COLLECTION_DROP_IN_SESSIONS = "dropInSessions"
        const val COLLECTION_REGISTRATIONS = "registrations"
        const val COLLECTION_COURTS = "courts"
        const val FIELD_VENUE_ID = "venueId"
        const val FIELD_STATUS = "status"
        const val FIELD_STAR_BUCKET = "starBucket"
        const val FIELD_HAS_PHOTOS = "hasPhotos"
        const val FIELD_HAS_OWNER_REPLY = "hasOwnerReply"
        const val FIELD_CREATED_AT = "createdAt"
        const val STATUS_VISIBLE = "VISIBLE"
    }
}
