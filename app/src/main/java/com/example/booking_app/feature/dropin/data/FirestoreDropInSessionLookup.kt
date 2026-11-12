package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.dropin.data.dto.SessionDto
import com.example.booking_app.feature.dropin.data.dto.toDomain
import com.example.booking_app.feature.dropin.domain.DropInSessionLookup
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đọc buổi theo lô `whereIn(documentId, ≤ 20 ID)` cho "Lượt vãng lai của tôi" (T045, research R7).
 * `get()` mặc định tự dùng cache offline khi mất mạng. Lô nào lỗi thì bỏ qua lô đó, màn hình vẫn hiện lượt bằng
 * giờ sao trong đăng ký.
 */
@Singleton
class FirestoreDropInSessionLookup @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val venueStatusCache: VenueStatusCache,
) : DropInSessionLookup {

    override suspend fun getByIds(ids: Collection<String>): Map<String, DropInSession> {
        val unique = ids.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return emptyMap()
        val dtos = coroutineScope {
            unique.chunked(BATCH_SIZE).map { chunk -> async { readChunk(chunk) } }.awaitAll().flatten()
        }
        val states = try {
            venueStatusCache.activeStates(dtos.mapNotNull { it.venueId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        return dtos
            .mapNotNull { it.toDomain(venueActive = states[it.venueId] ?: true) }
            .associateBy { it.id }
    }

    private suspend fun readChunk(ids: List<String>): List<SessionDto> = try {
        firestore.collection(COLLECTION_SESSIONS)
            .whereIn(FieldPath.documentId(), ids)
            .get()
            .awaitWithTimeout()
            .documents
            .map { SessionDto.fromMap(it.id, it.data.orEmpty()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val COLLECTION_SESSIONS = "dropInSessions"

        /** Firestore cho tối đa 30 giá trị trong `whereIn`; giữ 20 theo trang của research R7. */
        const val BATCH_SIZE = 20
    }
}
