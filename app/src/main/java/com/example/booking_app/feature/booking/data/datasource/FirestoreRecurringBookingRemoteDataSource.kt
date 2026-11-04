package com.example.booking_app.feature.booking.data.datasource

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.booking.data.model.BookingSessionDto
import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.RecurringGroupDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class FirestoreRecurringBookingRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val serverTimeOffsetProvider: ServerTimeOffsetProvider
) : RecurringBookingRemoteDataSource {

    override suspend fun isRecurringAccepted(venueId: String): Boolean {
        val snapshot = firestore.collection(COLLECTION_VENUES)
            .document(venueId)
            .get()
            .await()

        if (!snapshot.exists()) {
            return false
        }
        return snapshot.getBoolean("acceptsRecurring")
            ?: snapshot.getBoolean("isRecurringAccepted")
            ?: true
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

    override suspend fun getBusySlotsForDates(
        venueId: String,
        dates: List<String>
    ): List<SlotDto> = coroutineScope {
        if (dates.isEmpty()) return@coroutineScope emptyList()

        val uniqueDates = dates.distinct()
        val busyStatuses = listOf("BOOKED", "BLOCKED", "HELD")

        val deferredList = uniqueDates.map { date ->
            async {
                val querySnapshot = firestore.collection(COLLECTION_SLOTS)
                    .whereEqualTo("venueId", venueId)
                    .whereEqualTo("date", date)
                    .whereIn("status", busyStatuses)
                    .get()
                    .await()

                querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(SlotDto::class.java)?.copy(id = doc.id)
                }
            }
        }
        deferredList.flatMap { it.await() }
    }

    override suspend fun holdRecurringBookingTransaction(
        venueId: String,
        recurringGroup: RecurringGroupDto,
        sessions: List<BookingSessionDto>,
        slotIds: List<String>,
        userId: String
    ): RecurringHoldResult {
        return try {
            serverTimeOffsetProvider.ensureSynced()
            firestore.runTransaction { transaction ->
                val nowMillis = serverTimeOffsetProvider.now()
                val now = Timestamp(Date(nowMillis))
                val conflicts = mutableListOf<String>()

                // --- Phase 1: ALL READS ---
                // P0-3: Idempotency check trên recurringGroups
                val groupRef = firestore.collection(COLLECTION_RECURRING_GROUPS).document(recurringGroup.id)
                val existingGroupSnap = transaction.get(groupRef)
                if (existingGroupSnap.exists()) {
                    val existingUserId = existingGroupSnap.getString("userId")
                    val existingStatus = existingGroupSnap.getString("status")
                    val existingExpiresAt = existingGroupSnap.getTimestamp("holdExpiresAt")
                    if (existingUserId == userId && existingStatus == "HOLD") {
                        val expiresAtMillis = existingExpiresAt?.toDate()?.time
                            ?: recurringGroup.holdExpiresAt?.toDate()?.time
                            ?: (nowMillis + HOLD_DURATION_MILLIS)
                        return@runTransaction RecurringHoldResult.Success(
                            recurringGroupId = recurringGroup.id,
                            holdExpiresAtMillis = expiresAtMillis
                        )
                    } else {
                        throw AppError.Conflict
                    }
                }

                val venueRef = firestore.collection(COLLECTION_VENUES).document(venueId)
                val venueSnap = transaction.get(venueRef)
                val acceptsRecurring = venueSnap.getBoolean("acceptsRecurring")
                    ?: venueSnap.getBoolean("isRecurringAccepted")
                    ?: true
                if (!acceptsRecurring) {
                    throw IllegalStateException("Cơ sở hiện không chấp nhận đặt sân cố định theo tuần")
                }

                for (slotId in slotIds) {
                    val parts = slotId.split("_")
                    val slotDate = if (parts.size >= 2) parts[1] else ""
                    val slotStartTime = if (parts.size >= 3) parts[2].padStart(4, '0') else "0000"
                    val slotStartMillis = calculateSlotStartMillis(slotDate, slotStartTime)

                    if (slotStartMillis in 1..nowMillis) {
                        conflicts.add(slotDate)
                        continue
                    }

                    val docRef = firestore.collection(COLLECTION_SLOTS).document(slotId)
                    val snapshot = transaction.get(docRef)

                    if (snapshot.exists()) {
                        val status = snapshot.getString("status")
                        val holdExpiresAt = snapshot.getTimestamp("holdExpiresAt")
                        val holdBy = snapshot.getString("holdBy")

                        when (status?.uppercase()) {
                            "BOOKED", "BLOCKED" -> {
                                conflicts.add(slotDate)
                            }
                            "HELD" -> {
                                val expiresAt = holdExpiresAt?.toDate()?.time ?: 0L
                                if (expiresAt > nowMillis && holdBy != userId) {
                                    conflicts.add(slotDate)
                                }
                            }
                        }
                    }
                }

                if (conflicts.isNotEmpty()) {
                    throw RecurringSlotConflictException(conflicts.distinct())
                }

                // --- Phase 2: ALL WRITES ---
                // 1. Write recurringGroups/{recurringGroupId}
                transaction.set(groupRef, recurringGroup)

                // 2. Write bookings/{bookingId} for each session
                for (session in sessions) {
                    val bookingRef = firestore.collection(COLLECTION_BOOKINGS).document(session.id)
                    transaction.set(bookingRef, session)
                }

                // 3. Write slots for all sessions
                val holdExpiresAtTimestamp = recurringGroup.holdExpiresAt ?: Timestamp(Date(nowMillis + HOLD_DURATION_MILLIS))
                for (session in sessions) {
                    for (slotId in session.slotIds) {
                        val slotRef = firestore.collection(COLLECTION_SLOTS).document(slotId)
                        val parts = slotId.split("_")
                        val slotCourtId = if (parts.isNotEmpty()) parts[0] else session.courtId
                        val slotDate = if (parts.size >= 2) parts[1] else session.date
                        val slotStartTime = if (parts.size >= 3) parts[2].padStart(4, '0') else session.startTime
                        val slotEndTime = calculateSlotEndTime(slotStartTime)

                        val slotData = hashMapOf<String, Any?>(
                            "venueId" to venueId,
                            "courtId" to slotCourtId,
                            "date" to slotDate,
                            "startTime" to slotStartTime,
                            "endTime" to slotEndTime,
                            "status" to "HELD",
                            "holdExpiresAt" to holdExpiresAtTimestamp,
                            "holdBy" to userId,
                            "bookingId" to recurringGroup.id
                        )
                        transaction.set(slotRef, slotData)
                    }
                }

                RecurringHoldResult.Success(
                    recurringGroupId = recurringGroup.id,
                    holdExpiresAtMillis = holdExpiresAtTimestamp.toDate().time
                )
            }.await()
        } catch (e: RecurringSlotConflictException) {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            val dates = e.conflictingDates.mapNotNull { dateStr ->
                runCatching { java.time.LocalDate.parse(dateStr, formatter) }.getOrNull()
            }
            RecurringHoldResult.Conflict(conflictedSessionDates = dates)
        }
    }

    override suspend fun releaseRecurringHold(
        recurringGroupId: String,
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

            // Update recurringGroup status to CANCELLED if exists
            val groupRef = firestore.collection(COLLECTION_RECURRING_GROUPS).document(recurringGroupId)
            val groupSnap = transaction.get(groupRef)
            if (groupSnap.exists()) {
                transaction.update(groupRef, "status", "CANCELLED")
            }
        }.await()
    }

    private fun calculateSlotStartMillis(date: String, startTime: String): Long {
        return try {
            val year = date.substring(0, 4).toInt()
            val month = date.substring(4, 6).toInt()
            val day = date.substring(6, 8).toInt()
            val hour = startTime.substring(0, 2).toInt()
            val minute = startTime.substring(2, 4).toInt()
            ZonedDateTime.of(
                year, month, day, hour, minute, 0, 0,
                ZONE_VIETNAM
            ).toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private fun calculateSlotEndTime(startTime: String): String {
        return try {
            val hour = startTime.substring(0, 2).toInt()
            val minute = startTime.substring(2, 4).toInt()
            val totalMinutes = hour * 60 + minute + 30
            val endHour = (totalMinutes / 60) % 24
            val endMinute = totalMinutes % 60
            String.format(Locale.US, "%02d%02d", endHour, endMinute)
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val COLLECTION_VENUES = "venues"
        const val SUBCOLLECTION_COURTS = "courts"
        const val SUBCOLLECTION_PRICE_RULES = "priceRules"
        const val COLLECTION_SLOTS = "slots"
        const val COLLECTION_RECURRING_GROUPS = "recurringGroups"
        const val COLLECTION_BOOKINGS = "bookings"
        const val HOLD_DURATION_MILLIS = 5 * 60 * 1000L

        val ZONE_VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}

class RecurringSlotConflictException(
    val conflictingDates: List<String>
) : RuntimeException("Một số slot trong chu kỳ đã bị trùng lịch")
