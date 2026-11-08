package com.example.booking_app.feature.booking_management.data

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking_management.domain.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreBookingManagementRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: SessionManager,
    private val serverTimeOffsetProvider: ServerTimeOffsetProvider
) : BookingManagementRepository {
    private fun statuses(tab: BookingTab) = when (tab) {
        BookingTab.HOLD -> listOf("HOLD")
        BookingTab.PENDING -> listOf("PENDING")
        BookingTab.UPCOMING -> listOf("CONFIRMED")
        BookingTab.COMPLETED -> listOf("COMPLETED", "NO_SHOW")
        BookingTab.CANCELLED -> listOf("CANCELLED", "REJECTED")
    }
    private fun query(uid: String, tab: BookingTab): Query {
        val sort = bookingSort(tab)
        return firestore.collection("bookings")
            .whereEqualTo("userId", uid).whereIn("status", statuses(tab))
            .orderBy(sort.field, sort.direction)
            .orderBy(FieldPath.documentId(), sort.direction)
    }

    override suspend fun getPage(tab: BookingTab, cursor: String?, pageSize: Int): BookingPage {
        require(pageSize in 1..100)
        val uid = requireNotNull(session.currentUid()) { "Sign in required" }
        var q = query(uid, tab)
        if (cursor != null) {
            val decoded = BookingPageCursor.decode(cursor, tab, uid)
            q = q.startAfter(decoded.timestamp, decoded.id)
        }
        val (docs, isAuthoritative) = try {
            val res = q.limit((pageSize + 1).toLong()).get(Source.SERVER).await()
            Pair(res.documents, isAuthoritativeSnapshot(res.metadata.isFromCache, res.metadata.hasPendingWrites()))
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                val fallbackRes = firestore.collection("bookings")
                    .whereEqualTo("userId", uid)
                    .get(Source.SERVER)
                    .await()
                val targetStatuses = statuses(tab)
                val sort = bookingSort(tab)
                var filtered = fallbackRes.documents.filter { doc ->
                    doc.getString("status") in targetStatuses
                }.sortedWith(Comparator { d1, d2 ->
                    val t1 = d1.getTimestamp(sort.field)?.seconds ?: 0L
                    val t2 = d2.getTimestamp(sort.field)?.seconds ?: 0L
                    val cmp = if (sort.direction == Query.Direction.DESCENDING) t2.compareTo(t1) else t1.compareTo(t2)
                    if (cmp != 0) cmp else {
                        if (sort.direction == Query.Direction.DESCENDING) d2.id.compareTo(d1.id) else d1.id.compareTo(d2.id)
                    }
                })
                if (cursor != null) {
                    val decoded = BookingPageCursor.decode(cursor, tab, uid)
                    val idx = filtered.indexOfFirst { it.id == decoded.id }
                    if (idx >= 0 && idx + 1 < filtered.size) {
                        filtered = filtered.subList(idx + 1, filtered.size)
                    } else {
                        filtered = emptyList()
                    }
                }
                Pair(filtered, isAuthoritativeSnapshot(fallbackRes.metadata.isFromCache, fallbackRes.metadata.hasPendingWrites()))
            } else {
                throw e
            }
        }
        check(isAuthoritative) {
            "Booking data is not confirmed by the server"
        }
        check(session.currentUid() == uid) { "Session changed" }
        val page = docs.take(pageSize)
        if (tab == BookingTab.HOLD) {
            serverTimeOffsetProvider.ensureSynced()
        }
        val now = serverTimeOffsetProvider.now()
        val bookings = page.mapNotNull { doc ->
            try {
                val detail = BookingManagementMapper.map(doc.id, requireNotNull(doc.data), uid)
                if (tab == BookingTab.HOLD && detail.status == RefundPolicy.Status.HOLD) {
                    val expires = detail.holdExpiresAtMillis
                    if (expires != null && expires <= now) null else detail
                } else detail
            } catch (_: Exception) {
                null
            }
        }
        val nextCursor = if (docs.size > pageSize) {
            val last = page.last()
            val timestamp = last.getTimestamp(bookingSort(tab).field)
            if (timestamp != null) {
                BookingPageCursor(timestamp, last.id).encode(tab, uid)
            } else null
        } else null
        return BookingPage(bookings, nextCursor)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTabInvalidations(tab: BookingTab): Flow<Unit> {
        return session.sessionState.map { session.currentUid() }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) flowOf(Unit) else callbackFlow {
                val registration = firestore.collection("bookings")
                    .whereEqualTo("userId", uid)
                    .addSnapshotListener { _, error ->
                        if (session.currentUid() == uid) {
                            if (error != null) {
                                // Do not crash flow on snapshot error
                            } else {
                                trySend(Unit)
                            }
                        }
                    }
                awaitClose { registration.remove() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTabCounts(): Flow<Map<BookingTab, Int>> {
        return session.sessionState.map { session.currentUid() }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyMap()) else callbackFlow {
                val registration = firestore.collection("bookings")
                    .whereEqualTo("userId", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (session.currentUid() == uid) {
                            if (error != null) {
                                // Ignore error
                            } else if (snapshot != null) {
                                val now = serverTimeOffsetProvider.now()
                                var holdCount = 0
                                var pendingCount = 0
                                var upcomingCount = 0
                                var completedCount = 0
                                var cancelledCount = 0

                                for (doc in snapshot.documents) {
                                    val status = doc.getString("status") ?: continue
                                    when (status) {
                                        "HOLD" -> {
                                            val expiresAt = doc.getTimestamp("holdExpiresAt")?.toDate()?.time
                                            if (expiresAt == null || expiresAt > now) {
                                                holdCount++
                                            }
                                        }
                                        "PENDING" -> pendingCount++
                                        "CONFIRMED" -> upcomingCount++
                                        "COMPLETED", "NO_SHOW" -> completedCount++
                                        "CANCELLED", "REJECTED" -> cancelledCount++
                                    }
                                }
                                trySend(
                                    mapOf(
                                        BookingTab.HOLD to holdCount,
                                        BookingTab.PENDING to pendingCount,
                                        BookingTab.UPCOMING to upcomingCount,
                                        BookingTab.COMPLETED to completedCount,
                                        BookingTab.CANCELLED to cancelledCount
                                    )
                                )
                            }
                        }
                    }
                awaitClose { registration.remove() }
            }
        }
    }

    override fun observeBooking(bookingId: String): Flow<BookingDetail?> {
        require(bookingId.isNotBlank() && '/' !in bookingId)
        return ownerScopedDetailFlow(session.sessionState.map { session.currentUid() }, session::currentUid) { uid ->
            callbackFlow {
                val registration = firestore.collection("bookings").document(bookingId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (session.currentUid() == uid) {
                        if (error != null) close(error)
                        // Clear previously displayed data immediately while offline or local writes are pending.
                        else if (snapshot == null || !isAuthoritativeSnapshot(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())) trySend(null)
                        else if (!snapshot.exists()) trySend(null)
                        else try {
                            val detail = BookingManagementMapper.map(snapshot.id, requireNotNull(snapshot.data), uid)
                            trySend(detail)
                        } catch (_: Exception) { trySend(null) }
                    }
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override fun observeGroup(recurringGroupId: String): Flow<RecurringBookingGroup?> =
        throw UnsupportedOperationException("Recurring group reads require a complete, owner-verified group contract")

    override suspend fun estimateQuote(bookingId: String): RefundPolicyQuote =
        throw UnsupportedOperationException("A trustworthy server-time estimate is not available")

    override suspend fun cancelBooking(request: CancelBookingRequest): CancellationResult =
        throw UnsupportedOperationException("Cancellation requires a trusted server endpoint")

    override suspend fun cancelRemainingGroup(request: CancelGroupRequest): CancellationResult =
        throw UnsupportedOperationException("Cancellation requires a trusted server endpoint")
}
