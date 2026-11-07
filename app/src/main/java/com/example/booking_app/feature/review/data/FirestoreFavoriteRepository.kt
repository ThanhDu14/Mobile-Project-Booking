package com.example.booking_app.feature.review.data

import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.review.data.dto.FavoriteDto
import com.example.booking_app.feature.review.data.dto.FavoriteVenueStateDto
import com.example.booking_app.feature.review.data.dto.toDomain
import com.example.booking_app.feature.review.domain.FavoriteRepository
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * `users/{uid}/favorites/{venueId}`, client ghi trực tiếp (research R7). Firestore giữ thay đổi
 * offline trong cache và gửi khi có mạng; listener nhận thay đổi cục bộ ngay nên trái tim đổi tức thì.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreFavoriteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
) : FavoriteRepository {

    override fun observeIsFavorite(venueId: String): Flow<Boolean> {
        val uid = sessionManager.currentUid() ?: return flowOf(false)
        return callbackFlow {
            val registration = favorites(uid).document(venueId).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toAppError())
                } else {
                    trySend(snapshot?.exists() == true)
                }
            }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    override fun observeFavorites(limit: Int): Flow<List<FavoriteVenue>> {
        val uid = sessionManager.currentUid()
            ?: return flow { throw AppError.NotSignedIn }
        return callbackFlow {
            val registration = favorites(uid)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error.toAppError())
                    } else if (snapshot != null) {
                        trySend(snapshot.documents.map { it.toFavoriteDto() })
                    }
                }
            awaitClose { registration.remove() }
        }.mapLatest { dtos -> withVenueStates(dtos) }
    }

    override suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit> {
        val uid = sessionManager.currentUid() ?: return Result.failure(AppError.NotSignedIn)
        val ref = favorites(uid).document(venue.venueId)
        return try {
            // Không đặt timeout: offline thì Task chỉ xong khi có mạng, còn UI đã đổi từ trước.
            if (favorite) {
                ref.set(
                    mapOf(
                        FIELD_VENUE_ID to venue.venueId,
                        FIELD_SNAPSHOT to mapOf(
                            "name" to venue.name,
                            "photoPath" to venue.photoPath,
                            "address" to venue.address,
                        ),
                        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                ref.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toAppError())
        }
    }

    private fun favorites(uid: String): CollectionReference =
        firestore.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_FAVORITES)

    /** Đọc `venues/{id}` của từng sân trong trang (tối đa 20 lượt đọc) để biết sân còn hoạt động không. */
    private suspend fun withVenueStates(dtos: List<FavoriteDto>): List<FavoriteVenue> = coroutineScope {
        dtos.map { dto -> async { dto.toDomain(readVenueState(dto.venueId)) } }.awaitAll()
    }

    private suspend fun readVenueState(venueId: String): FavoriteVenueStateDto? = try {
        val doc = firestore.collection(COLLECTION_VENUES).document(venueId).get().await()
        if (!doc.exists()) {
            FavoriteVenueStateDto(exists = false)
        } else {
            FavoriteVenueStateDto(
                exists = true,
                status = doc.getString("status"),
                name = doc.getString("name"),
                photoPath = (doc.get("photoPaths") as? List<*>)?.firstOrNull()?.toString(),
                address = doc.getString("address"),
                ratingAvg = doc.getDouble("ratingAvg")?.takeIf { (doc.getLong("ratingCount") ?: 0L) > 0L },
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Offline và chưa có cache của cơ sở: hiển thị theo venueSnapshot.
        null
    }

    private fun DocumentSnapshot.toFavoriteDto(): FavoriteDto {
        val snapshot = get(FIELD_SNAPSHOT) as? Map<*, *>
        return FavoriteDto(
            venueId = getString(FIELD_VENUE_ID) ?: id,
            name = snapshot?.get("name") as? String ?: "",
            photoPath = snapshot?.get("photoPath") as? String,
            address = snapshot?.get("address") as? String ?: "",
            createdAt = getTimestamp(FIELD_CREATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toInstant(),
        )
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_FAVORITES = "favorites"
        const val COLLECTION_VENUES = "venues"
        const val FIELD_VENUE_ID = "venueId"
        const val FIELD_SNAPSHOT = "venueSnapshot"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
