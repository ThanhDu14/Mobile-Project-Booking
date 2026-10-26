package com.example.booking_app.feature.booking.data.datasource

import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class FirestoreBookingRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : BookingRemoteDataSource {

    override suspend fun getVenueOperatingHours(venueId: String): Pair<String, String> {
        val snapshot = firestore.collection(COLLECTION_VENUES)
            .document(venueId)
            .get()
            .await()

        val status = snapshot.getString("status") ?: "ACTIVE"
        if (status != "ACTIVE") {
            throw IllegalStateException("Cơ sở hiện không hoạt động (status: $status)")
        }

        val openTime = snapshot.getString("openTime")?.padStart(4, '0') ?: "0600"
        val closeTime = snapshot.getString("closeTime")?.padStart(4, '0') ?: "2200"

        return Pair(openTime, closeTime)
    }

    override suspend fun getActiveCourts(venueId: String): List<CourtDto> {
        val snapshot = firestore.collection(COLLECTION_VENUES)
            .document(venueId)
            .collection(SUBCOLLECTION_COURTS)
            .whereEqualTo("isActive", true)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(CourtDto::class.java)?.copy(id = doc.id)
        }.sortedBy { it.sortOrder }
    }

    override suspend fun getPriceRules(venueId: String): List<PriceRuleDto> {
        val snapshot = firestore.collection(COLLECTION_VENUES)
            .document(venueId)
            .collection(SUBCOLLECTION_PRICE_RULES)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(PriceRuleDto::class.java)?.copy(id = doc.id)
        }
    }

    override fun getBusySlotsRealtime(venueId: String, date: String): Flow<List<SlotDto>> = callbackFlow {
        val query = firestore.collection(COLLECTION_SLOTS)
            .whereEqualTo("venueId", venueId)
            .whereEqualTo("date", date)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val slots = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SlotDto::class.java)?.copy(id = doc.id)
                }
                trySend(slots)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    override suspend fun holdSlotsAtomic(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        userId: String,
        requestId: String
    ): HoldResult {
        return firestore.runTransaction { transaction ->
            // --- Phase 1: ALL READS ---
            val now = Timestamp.now()
            val snapshots = mutableMapOf<String, DocumentSnapshot>()
            val conflicts = mutableListOf<String>()

            for (slotId in slotIds) {
                val docRef = firestore.collection(COLLECTION_SLOTS).document(slotId)
                val snapshot = transaction.get(docRef)
                snapshots[slotId] = snapshot

                if (snapshot.exists()) {
                    val status = snapshot.getString("status")
                    val holdExpiresAt = snapshot.getTimestamp("holdExpiresAt")
                    val holdBy = snapshot.getString("holdBy")
                    val bookingId = snapshot.getString("bookingId")

                    when (status) {
                        "BOOKED", "BLOCKED" -> {
                            conflicts.add(slotId)
                        }
                        "HELD" -> {
                            val isStillValidHold = holdExpiresAt != null && holdExpiresAt > now
                            val isSameUserAndRequest = holdBy == userId && bookingId == requestId
                            if (isStillValidHold && !isSameUserAndRequest) {
                                conflicts.add(slotId)
                            }
                        }
                    }
                }
            }

            if (conflicts.isNotEmpty()) {
                return@runTransaction HoldResult.Conflict(conflictedSlotIds = conflicts)
            }

            // --- Phase 2: ALL WRITES ---
            val holdExpiresAtMillis = System.currentTimeMillis() + HOLD_DURATION_MILLIS
            val holdExpiresAtTimestamp = Timestamp(Date(holdExpiresAtMillis))

            for (slotId in slotIds) {
                val docRef = firestore.collection(COLLECTION_SLOTS).document(slotId)
                val parts = slotId.split("_")
                val startTime = if (parts.size >= 3) parts[2].padStart(4, '0') else "0000"
                val startMinutes = (startTime.substring(0, 2).toIntOrNull() ?: 0) * 60 +
                        (startTime.substring(2, 4).toIntOrNull() ?: 0)
                val endMinutes = startMinutes + 30
                val endTime = String.format("%02d%02d", endMinutes / 60, endMinutes % 60)

                val data = hashMapOf<String, Any?>(
                    "venueId" to venueId,
                    "courtId" to courtId,
                    "date" to date,
                    "startTime" to startTime,
                    "endTime" to endTime,
                    "status" to "HELD",
                    "holdExpiresAt" to holdExpiresAtTimestamp,
                    "holdBy" to userId,
                    "bookingId" to requestId
                )
                transaction.set(docRef, data)
            }

            HoldResult.Success(
                bookingId = requestId,
                holdExpiresAtMillis = holdExpiresAtMillis
            )
        }.await()
    }

    override suspend fun releaseHoldSlots(
        venueId: String,
        slotIds: List<String>,
        userId: String
    ) {
        firestore.runTransaction { transaction ->
            // Phase 1: ALL READS
            val validSlotRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
            for (slotId in slotIds) {
                val docRef = firestore.collection(COLLECTION_SLOTS).document(slotId)
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val holdBy = snapshot.getString("holdBy")
                    if (holdBy == userId) {
                        validSlotRefs.add(docRef)
                    }
                }
            }

            // Phase 2: ALL WRITES
            for (ref in validSlotRefs) {
                val updateData = hashMapOf<String, Any?>(
                    "status" to "AVAILABLE",
                    "holdExpiresAt" to null,
                    "holdBy" to null,
                    "bookingId" to null
                )
                transaction.update(ref, updateData)
            }
        }.await()
    }

    companion object {
        const val COLLECTION_VENUES = "venues"
        const val SUBCOLLECTION_COURTS = "courts"
        const val SUBCOLLECTION_PRICE_RULES = "priceRules"
        const val COLLECTION_SLOTS = "slots"
        const val HOLD_DURATION_MILLIS = 5 * 60 * 1000L // 5 minutes
    }
}
