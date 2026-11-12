package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.util.AppClock
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Đọc trạng thái hoạt động của một cơ sở. `null` = không biết (offline, lỗi khác): không cache. */
fun interface VenueStatusSource {
    suspend fun isActive(venueId: String): Boolean?
}

/** `venues/{id}.status == ACTIVE`. Rules chặn đọc cơ sở không ACTIVE nên `PERMISSION_DENIED` nghĩa là ngừng hoạt động. */
class FirestoreVenueStatusSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : VenueStatusSource {
    override suspend fun isActive(venueId: String): Boolean? = try {
        val doc = firestore.collection(COLLECTION_VENUES).document(venueId).get().await()
        doc.exists() && doc.getString(FIELD_STATUS) == STATUS_ACTIVE
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) false else null
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val COLLECTION_VENUES = "venues"
        const val FIELD_STATUS = "status"
        const val STATUS_ACTIVE = "ACTIVE"
    }
}

/**
 * Cache trạng thái cơ sở trong bộ nhớ [TTL_MS] (T022, research R6): mỗi trang đọc tối đa 20 `get`,
 * các trang sau dùng lại kết quả. Không biết trạng thái (offline) thì coi là hoạt động để không ẩn
 * nhầm buổi; server vẫn chặn đăng ký bằng `VENUE_INACTIVE`.
 */
@Singleton
class VenueStatusCache @Inject constructor(
    private val source: VenueStatusSource,
    private val clock: AppClock,
) {
    private data class Entry(val active: Boolean, val readAtMs: Long)

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    /** Trạng thái của từng cơ sở trong [venueIds]; đọc song song những ID chưa có hoặc đã hết hạn. */
    suspend fun activeStates(venueIds: Collection<String>): Map<String, Boolean> {
        val ids = venueIds.toSet()
        if (ids.isEmpty()) return emptyMap()
        val now = clock.now()
        val cached = mutex.withLock {
            ids.mapNotNull { id -> entries[id]?.takeIf { now - it.readAtMs < TTL_MS }?.let { id to it.active } }.toMap()
        }
        val missing = ids - cached.keys
        if (missing.isEmpty()) return cached
        val fetched = coroutineScope {
            missing.map { id -> async { id to source.isActive(id) } }.awaitAll()
        }
        mutex.withLock {
            fetched.forEach { (id, active) -> if (active != null) entries[id] = Entry(active, now) }
        }
        return cached + fetched.associate { (id, active) -> id to (active ?: true) }
    }

    suspend fun isActive(venueId: String): Boolean = activeStates(listOf(venueId))[venueId] ?: true

    suspend fun clear() = mutex.withLock { entries.clear() }

    companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}
