package com.example.booking_app.feature.review.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.review.data.dto.CreateReviewRequest
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
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.model.SubmittedReview
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

    override suspend fun create(
        bookingId: String,
        ratings: Ratings,
        comment: String,
        photoPaths: List<String>,
    ): SubmittedReview {
        val request = CreateReviewRequest(
            bookingId = bookingId,
            ratings = RatingsDto(ratings.court, ratings.cleanliness, ratings.service),
            comment = comment,
            photoPaths = photoPaths,
        )
        val response = try {
            api.create(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.asReviewException()
        }
        return SubmittedReview(
            id = response.review.id,
            overall = response.review.overall,
            canEditUntil = Instant.parse(response.review.canEditUntil),
        )
    }

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
    }
}
