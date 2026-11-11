package com.example.booking_app.feature.booking.data

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking.domain.UpcomingBooking
import com.example.booking_app.feature.booking.domain.UpcomingBookingsReader
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Read the current user's bookings from the server, including legacy recurring sessions. */
class FirestoreUpcomingBookingsReader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: SessionManager,
    private val serverTime: ServerTimeOffsetProvider,
) : UpcomingBookingsReader {
    override suspend fun upcoming(uid: String, from: Instant, to: Instant): List<UpcomingBooking> {
        require(uid.isNotBlank() && session.currentUid() == uid) { "Session does not match uid" }
        require(from < to) { "Invalid interval" }
        // Query by owner rather than startAt: legacy 041 sessions do not have startAt.
        val snapshot = firestore.collection("bookings").whereEqualTo("userId", uid)
            .get(Source.SERVER).await()
        check(!snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) { "Booking data is not authoritative" }
        check(session.currentUid() == uid) { "Session changed" }
        val now = Instant.ofEpochMilli(serverTime.now())
        val result = mutableListOf<UpcomingBooking>()
        for (doc in snapshot.documents) {
            val data = requireNotNull(doc.data)
            val status = data["status"] as? String ?: error("Missing booking status")
            if (status !in setOf("HOLD", "PENDING", "CONFIRMED")) continue
            if (status == "HOLD") {
                val groupId = (data["recurringGroupId"] as? String)?.takeIf { it.isNotBlank() }
                val expiry = if (groupId != null) {
                    val group = firestore.collection("recurringGroups").document(groupId).get(Source.SERVER).await()
                    check(group.exists() && group.getString("userId") == uid) { "Invalid hold group" }
                    if (group.getString("status") != "HOLD") continue
                    group.getTimestamp("holdExpiresAt")
                } else data["holdExpiresAt"] as? Timestamp
                checkNotNull(expiry) { "Missing hold expiry" }
                if (!expiry.toDate().toInstant().isAfter(now)) continue
            }
            val intervals = bookingIntervals(data)
            for ((start, end) in intervals) {
                if (start < to && end > from) {
                    result.add(UpcomingBooking(doc.id, data["venueId"] as? String, start, end))
                }
            }
        }
        check(session.currentUid() == uid) { "Session changed" }
        return result.sortedBy { it.startAt }
    }
}

internal fun bookingIntervals(data: Map<String, Any?>): List<Pair<Instant, Instant>> {
    fun interval(start: Any?, end: Any?): Pair<Instant, Instant> {
        val a = (start as? Timestamp)?.toDate()?.toInstant() ?: error("Missing booking start")
        val b = (end as? Timestamp)?.toDate()?.toInstant() ?: error("Missing booking end")
        require(a < b) { "Invalid booking interval" }
        return a to b
    }
    val items = data["items"] as? List<*>
    if (!items.isNullOrEmpty()) return items.map { item ->
        val fields = item as? Map<*, *> ?: error("Invalid booking item")
        interval(fields["startAt"], fields["endAt"])
    }
    if (data["startAt"] != null || data["endAt"] != null) return listOf(interval(data["startAt"], data["endAt"]))
    val date = LocalDate.parse(data["date"] as? String ?: error("Missing session date"), DateTimeFormatter.BASIC_ISO_DATE)
    fun time(value: Any?): LocalTime {
        val text = value as? String ?: error("Missing session time")
        return if (text.contains(':')) LocalTime.parse(text) else LocalTime.parse(text, DateTimeFormatter.ofPattern("HHmm"))
    }
    val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    val start = date.atTime(time(data["startTime"])).atZone(zone).toInstant()
    val end = date.atTime(time(data["endTime"])).atZone(zone).toInstant()
    require(start < end) { "Invalid session interval" }
    return listOf(start to end)
}
