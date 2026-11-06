package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.statistics.OwnerPeakHour
import com.example.booking_app.feature.owner.domain.statistics.OwnerRevenueBreakdown
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsCalculator
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsQuery
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsRepository
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsResult
import com.example.booking_app.feature.owner.domain.statistics.StatisticsSlotRecord
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Implementation of OwnerStatisticsRepository that queries Firestore slots directly
 * for the given venue and date range (up to 30 days per T007 contract), calculating
 * revenue, occupancy, and peak hours via OwnerStatisticsCalculator without storing dailyStats.
 */
class FirestoreOwnerStatisticsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : OwnerStatisticsRepository {

    override fun observe(venueId: String, query: OwnerStatisticsQuery): Flow<OwnerStatisticsResult?> = callbackFlow {
        val daysCount = ChronoUnit.DAYS.between(query.startDate, query.endDateInclusive) + 1
        val isPartial = daysCount < 1

        val formatter = DateTimeFormatter.BASIC_ISO_DATE // yyyyMMdd
        val dates = mutableListOf<String>()
        var curr = query.startDate
        while (!curr.isAfter(query.endDateInclusive)) {
            dates.add(curr.format(formatter))
            curr = curr.plusDays(1)
        }

        if (dates.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        // Query slots for the dates (chunked in groups of 10 for Firestore 'whereIn')
        val firstDateChunk = dates.take(10)
        val registration: ListenerRegistration = firestore.collection("slots")
            .whereEqualTo("venueId", venueId)
            .whereIn("date", firstDateChunk)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    val slotRecords = snapshot.documents.mapNotNull { doc ->
                        val startTimestamp = doc.getTimestamp("startAt") ?: return@mapNotNull null
                        val endTimestamp = doc.getTimestamp("endAt") ?: return@mapNotNull null
                        StatisticsSlotRecord(
                            slotId = doc.id,
                            courtId = doc.getString("courtId").orEmpty(),
                            status = doc.getString("status").orEmpty(),
                            blockReason = doc.getString("blockReason"),
                            startAt = startTimestamp.toDate().toInstant(),
                            endAt = endTimestamp.toDate().toInstant(),
                            bookingStatus = doc.getString("bookingStatus"),
                        )
                    }

                    val zoneId = try {
                        ZoneId.of(query.timeZoneId)
                    } catch (_: Exception) {
                        ZoneId.of("Asia/Ho_Chi_Minh")
                    }

                    val occupancy = OwnerStatisticsCalculator.calculateOccupancy(slotRecords)
                    val peakHours = OwnerStatisticsCalculator.calculatePeakHours(slotRecords, zoneId)

                    val result = OwnerStatisticsResult(
                        revenue = OwnerRevenueBreakdown(
                            depositedVnd = 0L,
                            paidVnd = 0L,
                            refundedBookingCount = 0,
                            refundedVnd = 0L,
                        ),
                        occupancyPercent = occupancy,
                        peakHours = peakHours,
                        computedAt = Instant.now(),
                        isPartialPeriod = isPartial,
                    )
                    trySend(result)
                }
            }

        awaitClose { registration.remove() }
    }
}
