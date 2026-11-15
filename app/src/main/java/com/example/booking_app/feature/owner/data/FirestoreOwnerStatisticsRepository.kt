package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.statistics.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.booking_app.core.network.FunctionErrors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@Serializable
private data class StatisticsRequest(val venueId: String, val startDate: String, val endDate: String)
@Serializable
private data class StatisticsBookingWire(
    val bookingId: String, val status: String, val paymentStatus: String? = null,
    val paymentMethod: String? = null, val total: Long = 0, val depositAmount: Long = 0,
    val refundAmount: Long = 0, val startAt: String, val endAt: String,
    val date: String? = null, val startTime: String? = null, val endTime: String? = null,
)
@Serializable
private data class StatisticsSlotWire(
    val slotId: String, val courtId: String, val status: String, val blockReason: String? = null,
    val bookingId: String? = null, val startAt: String, val endAt: String,
    val date: String? = null, val startTime: String? = null, val endTime: String? = null,
)
@Serializable
private data class StatisticsSnapshot(
    val bookings: List<StatisticsBookingWire>, val slots: List<StatisticsSlotWire>, val computedAt: String,
)

/** Venue-scoped source; revenue comes from bookings, availability from courts and price rules. */
class FirestoreOwnerStatisticsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerStatisticsRepository {
    override fun observe(venueId: String, query: OwnerStatisticsQuery): Flow<OwnerStatisticsResult?> = flow {
        val days = ChronoUnit.DAYS.between(query.startDate, query.endDateInclusive) + 1
        require(days in 1..30) { "Chọn khoảng báo cáo từ 1 đến 30 ngày" }
        val zone = ZoneId.of(query.timeZoneId)
        val start = query.startDate.atStartOfDay(zone).toInstant()
        val end = query.endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant()
        val venueRef = firestore.collection("venues").document(venueId)
        val venue = venueRef.get(Source.SERVER).await()
        val courts = venueRef.collection("courts").get(Source.SERVER).await().documents.filter { it.getBoolean("isActive") != false }
        val rules = venueRef.collection("priceRules").get(Source.SERVER).await().documents
        val open = LocalTime.parse(venue.getString("openTime"), TIME)
        val close = LocalTime.parse(venue.getString("closeTime"), TIME)
        val dates = (0 until days).map { query.startDate.plusDays(it) }
        val available = buildList {
            dates.forEach { date -> courts.forEach { court ->
                var time = open
                while (time < close && time.plusMinutes(30) <= close) {
                    val next = time.plusMinutes(30)
                    val priced = rules.any { rule ->
                        val weekdays = (rule.get("daysOfWeek") as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() }.orEmpty()
                        val from = rule.getString("startTime").orEmpty()
                        val to = rule.getString("endTime").orEmpty()
                        date.dayOfWeek.value in weekdays && time.format(TIME) >= from && next.format(TIME) <= to && (rule.getLong("pricePerHour") ?: 0) > 0
                    }
                    if (priced) add(StatisticsSlotRecord("${court.id}_${date.format(DATE)}_${time.format(TIME)}", court.id, "AVAILABLE",
                        startAt = date.atTime(time).atZone(zone).toInstant(), endAt = date.atTime(next).atZone(zone).toInstant()))
                    time = next
                }
            } }
        }
        while (currentCoroutineContext().isActive) {
            val snapshot = functionErrors.invokeWithAuthRetry {
                supabase.functions.invoke("owner-statistics-source", body = StatisticsRequest(
                    venueId, query.startDate.toString(), query.endDateInclusive.toString()))
            }.body<StatisticsSnapshot>()
            val bookings = snapshot.bookings
            val statuses = bookings.associate { it.bookingId to it.status }
            val actualSlots = snapshot.slots.map { slot ->
                StatisticsSlotRecord(slot.slotId, slot.courtId, slot.status, slot.blockReason,
                    Instant.parse(slot.startAt), Instant.parse(slot.endAt),
                    statuses[slot.bookingId] ?: if (slot.bookingId != null) "UNKNOWN" else null)
            }.associateBy { it.slotId }
            // Existing bookings remain historical evidence after a court is disabled or prices change.
            val records = (available.map { actualSlots[it.slotId] ?: it } + actualSlots.values.filter {
                it.bookingStatus in setOf("CONFIRMED", "COMPLETED", "NO_SHOW")
            }).distinctBy { it.slotId }
            val revenue = OwnerStatisticsCalculator.calculateRevenue(bookings.map { booking ->
                StatisticsBookingRecord(booking.bookingId, booking.status, booking.paymentStatus,
                    booking.paymentMethod, booking.total, booking.depositAmount, booking.refundAmount)
            })
            emit(if (bookings.isEmpty() && snapshot.slots.isEmpty()) null else OwnerStatisticsResult(
                revenue, OwnerStatisticsCalculator.calculateOccupancy(records),
                OwnerStatisticsCalculator.calculatePeakHours(records, zone), Instant.parse(snapshot.computedAt), end > Instant.now()))
            delay(30_000)
        }
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")
    }
}
