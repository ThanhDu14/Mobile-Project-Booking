package com.example.booking_app.feature.booking_management.data

import com.example.booking_app.core.session.SessionManager
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
    private val session: SessionManager
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
        checkHoldSupported(tab)
        val uid = requireNotNull(session.currentUid()) { "Sign in required" }
        var q = query(uid, tab)
        if (cursor != null) {
            val decoded = BookingPageCursor.decode(cursor, tab, uid)
            q = q.startAfter(decoded.timestamp, decoded.id)
        }
        val result = q.limit((pageSize + 1).toLong()).get(Source.SERVER).await()
        check(isAuthoritativeSnapshot(result.metadata.isFromCache, result.metadata.hasPendingWrites())) {
            "Booking data is not confirmed by the server"
        }
        val docs = result.documents
        check(session.currentUid() == uid) { "Session changed" }
        val page = docs.take(pageSize)
        val bookings = page.mapNotNull { doc ->
            try {
                BookingManagementMapper.map(doc.id, requireNotNull(doc.data), uid)
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

    private fun checkHoldSupported(tab: BookingTab) {
        if (tab == BookingTab.HOLD) throw UnsupportedOperationException("HOLD requires trusted time to exclude expired bookings")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTabInvalidations(tab: BookingTab): Flow<Unit> {
        checkHoldSupported(tab)
        return session.sessionState.map { session.currentUid() }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) flowOf(Unit) else callbackFlow {
                val registration = query(uid, tab).addSnapshotListener { _, error ->
                    if (session.currentUid() == uid) {
                        if (error != null) close(error) else trySend(Unit)
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
                            if (detail.status == RefundPolicy.Status.HOLD) checkHoldSupported(BookingTab.HOLD)
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
