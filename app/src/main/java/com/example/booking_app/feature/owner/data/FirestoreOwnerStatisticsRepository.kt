package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.statistics.*
import com.example.booking_app.feature.owner.domain.OwnerOperatingSlots
import kotlinx.coroutines.flow.*
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
    val availability: StatisticsAvailabilityWire,
)
@Serializable
private data class StatisticsPriceWire(val daysOfWeek: List<Int>, val startTime: String, val endTime: String, val pricePerHour: Long)
@Serializable
private data class StatisticsAvailabilityWire(val openTime: String, val closeTime: String, val courtIds: List<String>, val priceRules: List<StatisticsPriceWire>)

/** Venue-scoped source; revenue comes from bookings, availability from courts and price rules. */
class FirestoreOwnerStatisticsRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : OwnerStatisticsRepository {
    override fun observe(venueId: String, query: OwnerStatisticsQuery): Flow<OwnerStatisticsResult?> = flow {
        val days = ChronoUnit.DAYS.between(query.startDate, query.endDateInclusive) + 1
        require(days in 1..30) { "Chọn khoảng báo cáo từ 1 đến 30 ngày" }
        val zone = ZoneId.of(query.timeZoneId)
        val end = query.endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant()
        val dates = (0 until days).map { query.startDate.plusDays(it) }
        while (currentCoroutineContext().isActive) {
            val snapshot = functionErrors.invokeWithAuthRetry {
                supabase.functions.invoke("owner-statistics-source", body = StatisticsRequest(
                    venueId, query.startDate.toString(), query.endDateInclusive.toString()))
            }.body<StatisticsSnapshot>()
            val times = OwnerOperatingSlots.startMinutes(snapshot.availability.openTime, snapshot.availability.closeTime)
            val courts = snapshot.availability.courtIds
            val rules = snapshot.availability.priceRules
            val available = buildList {
                dates.forEach { date -> courts.forEach { court ->
                    times.forEach { minute ->
                        val time = LocalTime.of(minute / 60, minute % 60)
                        val next = time.plusMinutes(30)
                        val priced = rules.any { rule ->
                            date.dayOfWeek.value in rule.daysOfWeek && time.format(TIME) >= rule.startTime && next.format(TIME) <= rule.endTime && rule.pricePerHour > 0
                        }
                        if (priced) add(StatisticsSlotRecord("${court}_${date.format(DATE)}_${time.format(TIME)}", court, "AVAILABLE",
                            startAt = date.atTime(time).atZone(zone).toInstant(), endAt = date.atTime(next).atZone(zone).toInstant()))
                    }
                } }
            }
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
