package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.calendar.OwnerCalendarRepository
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Streams only the public availability projection from `slots`; it never reads booking documents or QR fields.
 * Firestore Rules currently allow slot reads publicly, so this class does not weaken security rules.
 */
class FirestoreOwnerCalendarRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : OwnerCalendarRepository {
    override fun observeSlots(venueId: String, dates: List<String>): Flow<Map<String, List<OwnerSlot>>> {
        if (dates.isEmpty()) return flowOf(emptyMap())
        val dateFlows = dates.distinct().map { date ->
            observeDate(venueId, date).map { date to it }
        }
        return combine(dateFlows) { entries -> entries.associate { it.first to it.second } }
    }

    private fun observeDate(venueId: String, date: String): Flow<List<OwnerSlot>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection(SLOTS)
            .whereEqualTo("venueId", venueId)
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.map { doc ->
                        OwnerSlot(
                            id = doc.id,
                            venueId = doc.getString("venueId").orEmpty(),
                            courtId = doc.getString("courtId").orEmpty(),
                            date = doc.getString("date").orEmpty(),
                            startAt = doc.getTimestamp("startAt"),
                            endAt = doc.getTimestamp("endAt"),
                            status = doc.getString("status").orEmpty(),
                            blockReason = doc.getString("blockReason"),
                            bookingId = null,
                            holdBy = null,
                        )
                    })
                }
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val SLOTS = "slots"
    }
}
