package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.data.dto.SessionDto
import com.example.booking_app.feature.dropin.data.dto.toDateKey
import com.example.booking_app.feature.dropin.data.dto.toDomain
import com.example.booking_app.feature.dropin.data.dto.toDropInError
import com.example.booking_app.feature.dropin.domain.DropInSessionRepository
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `dropInSessions` (T021, research R6). Lỗi trả `Result.failure(DropInError)`: mất mạng là
 * [DropInError.Network], bộ lọc sai là [DropInError.InvalidArgument].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreDropInSessionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val venueStatusCache: VenueStatusCache,
    private val clock: AppClock,
) : DropInSessionRepository {

    /** Tên sân con theo cơ sở; ít đổi nên giữ suốt phiên chạy app. */
    private val courtNamesMutex = Mutex()
    private val courtNamesCache = mutableMapOf<String, Map<String, String>>()

    override suspend fun page(filter: DropInFilter, cursor: DropInCursor?): Result<Page<DropInSession>> {
        if (filter.validate() != null) return Result.failure(DropInError.InvalidArgument())
        return try {
            val now = Instant.ofEpochMilli(clock.now())
            val snapshot = buildQuery(filter, cursor, now).get().awaitWithTimeout()
            val docs = snapshot.documents
            val dtos = docs.map { it.toSessionDto() }
            val states = venueStatusCache.activeStates(dtos.mapNotNull { it.venueId })
            val items = dtos
                .mapNotNull { it.toDomain(venueActive = states[it.venueId] ?: true) }
                .filter { it.venueActive }
            // Con trỏ theo document thô cuối trang, để buổi bị lọc (cơ sở ẩn) không làm sót trang sau.
            val next = docs.lastOrNull()
                ?.takeIf { docs.size >= Page.PAGE_SIZE }
                ?.let { last -> last.getTimestamp(FIELD_START_AT)?.toInstant()?.let { DropInCursor(it, last.id) } }
            Result.success(Page(items, next))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toDropInError())
        }
    }

    private fun buildQuery(filter: DropInFilter, cursor: DropInCursor?, now: Instant): Query {
        var query: Query = firestore.collection(COLLECTION_SESSIONS)
            .whereIn(FIELD_STATUS, listOf(SessionStatus.OPEN.name, SessionStatus.FULL.name))
            .whereGreaterThanOrEqualTo(FIELD_START_AT, now.toTimestamp())
            .whereLessThan(FIELD_START_AT, now.plus(WINDOW).toTimestamp())
        if (filter.districtIds.isNotEmpty()) query = query.whereIn(FIELD_DISTRICT_ID, filter.districtIds.toList())
        filter.date?.let { query = query.whereEqualTo(FIELD_DATE, it.toDateKey()) }
        filter.timeSlot?.let { query = query.whereEqualTo(FIELD_TIME_SLOT, it.name) }
        filter.level?.let { query = query.whereEqualTo(FIELD_LEVEL, it.name) }
        filter.maxPrice?.let { query = query.whereLessThanOrEqualTo(FIELD_PRICE, it) }
        query = query
            .orderBy(FIELD_START_AT, Query.Direction.ASCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
        cursor?.let { query = query.startAfter(it.startAt.toTimestamp(), it.id) }
        return query.limit(Page.PAGE_SIZE.toLong())
    }

    override fun observe(sessionId: String): Flow<DropInSession?> = callbackFlow {
        val registration = firestore.collection(COLLECTION_SESSIONS).document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toAppError().toDropInError())
                } else {
                    trySend(snapshot?.takeIf { it.exists() }?.toSessionDto())
                }
            }
        awaitClose { registration.remove() }
    }
        .distinctUntilChanged()
        .mapLatest { dto ->
            if (dto == null) return@mapLatest null
            val venueId = dto.venueId ?: return@mapLatest null
            val active = venueStatusCache.isActive(venueId)
            val names = courtNames(venueId, dto.courtIds)
            dto.toDomain(venueActive = active, courtNames = names)
        }

    /** Tên theo thứ tự [courtIds]; sân không đọc được thì bỏ qua (Rules chặn khi cơ sở ngừng hoạt động). */
    private suspend fun courtNames(venueId: String, courtIds: List<String>): List<String> {
        if (courtIds.isEmpty()) return emptyList()
        val known = courtNamesMutex.withLock { courtNamesCache[venueId].orEmpty() }
        val missing = courtIds.filter { it !in known }
        val fetched = if (missing.isEmpty()) emptyMap() else coroutineScope {
            missing.map { id -> async { id to readCourtName(venueId, id) } }.awaitAll()
                .mapNotNull { (id, name) -> name?.let { id to it } }
                .toMap()
        }
        if (fetched.isNotEmpty()) {
            courtNamesMutex.withLock { courtNamesCache[venueId] = courtNamesCache[venueId].orEmpty() + fetched }
        }
        val all = known + fetched
        return courtIds.mapNotNull { all[it] }
    }

    private suspend fun readCourtName(venueId: String, courtId: String): String? = try {
        firestore.collection(COLLECTION_VENUES).document(venueId)
            .collection(COLLECTION_COURTS).document(courtId)
            .get().await()
            .getString(FIELD_NAME)?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun DocumentSnapshot.toSessionDto(): SessionDto = SessionDto.fromMap(id, data.orEmpty())

    private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))

    private companion object {
        val WINDOW: Duration = Duration.ofDays(30)
        const val COLLECTION_SESSIONS = "dropInSessions"
        const val COLLECTION_VENUES = "venues"
        const val COLLECTION_COURTS = "courts"
        const val FIELD_STATUS = "status"
        const val FIELD_START_AT = "startAt"
        const val FIELD_DISTRICT_ID = "districtId"
        const val FIELD_DATE = "date"
        const val FIELD_TIME_SLOT = "timeSlot"
        const val FIELD_LEVEL = "level"
        const val FIELD_PRICE = "pricePerPerson"
        const val FIELD_NAME = "name"
    }
}
