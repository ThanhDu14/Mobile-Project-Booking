package com.example.booking_app.feature.owner.domain.statistics

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class OwnerStatisticsPeriod { DAY, WEEK, CUSTOM }

data class OwnerStatisticsQuery(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val timeZoneId: String = "Asia/Ho_Chi_Minh",
)

data class OwnerRevenueBreakdown(
    val depositedVnd: Long,
    val paidVnd: Long,
    val refundedBookingCount: Int,
    val refundedVnd: Long,
)

data class OwnerPeakHour(val localHour: Int, val occupiedDurationMinutes: Int)

data class OwnerStatisticsResult(
    val revenue: OwnerRevenueBreakdown,
    val occupancyPercent: Double,
    val peakHours: List<OwnerPeakHour>,
    val computedAt: Instant,
    val isPartialPeriod: Boolean,
)

interface OwnerStatisticsRepository {
    fun observe(venueId: String, query: OwnerStatisticsQuery): Flow<OwnerStatisticsResult?>
}

class OwnerStatisticsContractUnavailableException :
    IllegalStateException("Venue-scoped live statistics projection, metric denominator and freshness contract are not available.")
