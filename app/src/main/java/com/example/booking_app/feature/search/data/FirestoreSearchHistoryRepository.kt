package com.example.booking_app.feature.search.data

import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.CourtSearchSort
import com.example.booking_app.feature.search.domain.SearchHistory
import com.example.booking_app.feature.search.domain.SearchHistoryEntry
import com.example.booking_app.feature.search.domain.SearchHistoryRepository
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * `users/{uid}/searchHistory/{id}` (spec 020 data-model §2.1, research R11). Ghi bằng transaction:
 * chưa có thì tạo đủ trường kèm `createdAt`, đã có thì cập nhật và giữ `createdAt` (Rules kiểm tra).
 */
class FirestoreSearchHistoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SearchHistoryRepository {

    override fun observe(uid: String): Flow<List<SearchHistoryEntry>> = callbackFlow {
        val registration = history(uid)
            .orderBy(FIELD_SEARCHED_AT, Query.Direction.DESCENDING)
            .limit(SearchHistory.LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error.toAppError())
                else if (snapshot != null) trySend(snapshot.documents.mapNotNull(::toEntry))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun record(uid: String, criteria: CourtSearchCriteria, prune: Boolean) {
        val ref = history(uid).document(SearchHistory.idOf(criteria))
        val fields = fieldsOf(criteria)
        firestore.runTransaction { transaction ->
            val now = FieldValue.serverTimestamp()
            if (transaction.get(ref).exists()) {
                transaction.update(ref, fields + mapOf(FIELD_SEARCHED_AT to now, FIELD_UPDATED_AT to now))
            } else {
                transaction.set(
                    ref,
                    fields + mapOf(FIELD_SEARCHED_AT to now, FIELD_CREATED_AT to now, FIELD_UPDATED_AT to now),
                )
            }
        }.await()
        if (prune) {
            // Mục thứ 11 trở đi; vài mục dư sót lại (mất mạng) sẽ được dọn ở lần ghi sau.
            val extra = history(uid).orderBy(FIELD_SEARCHED_AT, Query.Direction.DESCENDING)
                .limit((SearchHistory.LIMIT + 5).toLong()).get().await().documents.drop(SearchHistory.LIMIT)
            if (extra.isNotEmpty()) {
                firestore.batch().apply { extra.forEach { delete(it.reference) } }.commit().await()
            }
        }
    }

    override suspend fun delete(uid: String, id: String) {
        history(uid).document(id).delete().await()
    }

    override suspend fun clear(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        firestore.batch().apply { ids.forEach { delete(history(uid).document(it)) } }.commit().await()
    }

    private fun history(uid: String): CollectionReference =
        firestore.collection("users").document(uid).collection("searchHistory")

    private fun fieldsOf(criteria: CourtSearchCriteria): Map<String, Any?> {
        val window = criteria.window
        return mapOf(
            "keyword" to criteria.keyword.trim().take(100),
            "districtId" to criteria.districtId,
            "priceMin" to criteria.priceMin,
            "priceMax" to criteria.priceMax,
            "date" to window?.dateKey(),
            "startTime" to window?.start?.toHhmm(),
            "endTime" to window?.end?.toHhmm(),
            "amenityIds" to criteria.amenityIds.sorted(),
            "sort" to criteria.sort.name,
        )
    }

    private fun toEntry(document: DocumentSnapshot): SearchHistoryEntry? = runCatching {
        @Suppress("UNCHECKED_CAST")
        val amenities = (document.get("amenityIds") as? List<String>).orEmpty().toSet()
        SearchHistoryEntry(
            id = document.id,
            criteria = CourtSearchCriteria(
                keyword = document.getString("keyword").orEmpty(),
                districtId = document.getString("districtId"),
                priceMin = document.getLong("priceMin"),
                priceMax = document.getLong("priceMax"),
                amenityIds = amenities,
                window = BookingSearchWindow.parse(
                    document.getString("date"),
                    document.getString("startTime"),
                    document.getString("endTime"),
                ),
                sort = runCatching { CourtSearchSort.valueOf(document.getString("sort").orEmpty()) }
                    .getOrDefault(CourtSearchSort.RATING),
            ),
        )
    }.getOrNull()

    private companion object {
        const val FIELD_SEARCHED_AT = "searchedAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
