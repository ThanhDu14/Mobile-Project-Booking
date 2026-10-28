package com.example.booking_app.feature.booking.data.datasource

import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.example.booking_app.feature.booking.domain.model.VenueInfo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class FirestoreBookingRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : BookingRemoteDataSource {

    override suspend fun getVenueOperatingHours(venueId: String): Pair<String, String> {
        val snapshot = firestore.collection(COLLECTION_VENUES)
            .document(venueId)
            .get()
            .await()

        if (!snapshot.exists()) {
            throw NoSuchElementException("Không tìm thấy thông tin cơ sở: $venueId")
        }

        val openTime = snapshot.getString("openTime")?.padStart(4, '0')
            ?: throw IllegalStateException("Cơ sở thiếu cấu hình openTime")
        val closeTime = snapshot.getString("closeTime")?.padStart(4, '0')
            ?: throw IllegalStateException("Cơ sở thiếu cấu hình closeTime")
        return Pair(openTime, closeTime)
    }

    override suspend fun getVenueInfo(venueId: String): VenueInfo {
        val snapshot = firestore.collection(COLLECTION_VENUES)
            .document(venueId)
            .get()
            .await()

        if (!snapshot.exists()) {
            throw NoSuchElementException("Không tìm thấy thông tin cơ sở: $venueId")
        }

        val name = snapshot.getString("name").orEmpty()
        val address = snapshot.getString("address").orEmpty()
        val districtId = snapshot.getString("districtId").orEmpty()
        val openTime = snapshot.getString("openTime")?.padStart(4, '0') ?: "0500"
        val closeTime = snapshot.getString("closeTime")?.padStart(4, '0') ?: "2400"
        val status = snapshot.getString("status")
        val photoPaths = snapshot.get("photoPaths") as? List<*>
        val photoUrl = photoPaths?.firstOrNull()?.toString()?.takeIf { it.startsWith("http") }

        val districtName = DISTRICT_NAMES[districtId] ?: parseDistrictFromAddress(address) ?: districtId

        return VenueInfo(
            id = venueId,
            name = name,
            address = address,
            districtName = districtName,
            openTime = openTime,
            closeTime = closeTime,
            photoUrl = photoUrl,
            status = status
        )
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
            val nowMillis = now.toDate().time
            val snapshots = mutableMapOf<String, DocumentSnapshot>()
            val conflicts = mutableListOf<String>()

            for (slotId in slotIds) {
                // Kiểm tra slot quá khứ so với giờ máy chủ (Q11)
                val parts = slotId.split("_")
                val slotDate = if (parts.size >= 2) parts[1] else date
                val slotStartTime = if (parts.size >= 3) parts[2].padStart(4, '0') else "0000"
                val slotStartMillis = calculateSlotStartMillis(slotDate, slotStartTime)
                if (slotStartMillis > 0L && slotStartMillis <= nowMillis) {
                    conflicts.add(slotId)
                    continue
                }

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
                return@runTransaction HoldResult.Conflict(conflictedSlotIds = conflicts.distinct())
            }

            // --- Phase 2: ALL WRITES ---
            val holdExpiresAtMillis = System.currentTimeMillis() + HOLD_DURATION_MILLIS
            val holdExpiresAtTimestamp = Timestamp(Date(holdExpiresAtMillis))

            for (slotId in slotIds) {
                val docRef = firestore.collection(COLLECTION_SLOTS).document(slotId)
                val parts = slotId.split("_")
                val itemCourtId = if (parts.isNotEmpty() && parts[0].isNotEmpty()) parts[0] else courtId
                val itemDate = if (parts.size >= 2) parts[1] else date
                val startTime = if (parts.size >= 3) parts[2].padStart(4, '0') else "0000"
                val startMinutes = (startTime.substring(0, 2).toIntOrNull() ?: 0) * 60 +
                        (startTime.substring(2, 4).toIntOrNull() ?: 0)
                val endMinutes = startMinutes + 30
                val endTime = String.format(Locale.US, "%02d%02d", endMinutes / 60, endMinutes % 60)

                val data = hashMapOf<String, Any?>(
                    "venueId" to venueId,
                    "courtId" to itemCourtId,
                    "date" to itemDate,
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

    private fun parseDistrictFromAddress(address: String): String? {
        val parts = address.split(",")
        for (part in parts.reversed()) {
            val trimmed = part.trim()
            if (trimmed.startsWith("Quận") || trimmed.startsWith("Huyện") || trimmed.startsWith("TP.")) {
                return trimmed
            }
        }
        return null
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

    companion object {
        const val COLLECTION_VENUES = "venues"
        const val SUBCOLLECTION_COURTS = "courts"
        const val SUBCOLLECTION_PRICE_RULES = "priceRules"
        const val COLLECTION_SLOTS = "slots"
        const val HOLD_DURATION_MILLIS = 5 * 60 * 1000L // 5 minutes

        val ZONE_VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

        val DISTRICT_NAMES = mapOf(
            "q1" to "Quận 1",
            "q3" to "Quận 3",
            "q4" to "Quận 4",
            "q5" to "Quận 5",
            "q6" to "Quận 6",
            "q7" to "Quận 7",
            "q8" to "Quận 8",
            "q10" to "Quận 10",
            "q11" to "Quận 11",
            "q12" to "Quận 12",
            "binh-tan" to "Bình Tân",
            "binh-thanh" to "Bình Thạnh",
            "go-vap" to "Gò Vấp",
            "phu-nhuan" to "Phú Nhuận",
            "tan-binh" to "Tân Bình",
            "tan-phu" to "Tân Phú",
            "thu-duc" to "TP. Thủ Đức",
            "binh-chanh" to "Bình Chánh",
            "can-gio" to "Cần Giờ",
            "cu-chi" to "Củ Chi",
            "hoc-mon" to "Hóc Môn",
            "nha-be" to "Nhà Bè"
        )
    }
}
