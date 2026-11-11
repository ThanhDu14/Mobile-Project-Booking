package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.CourtSummary

/** Gộp sân con theo loại mặt sân (FR-006): chỉ sân đang hoạt động, loại nhiều sân đứng trước. */
object CourtSummaries {
    fun from(courts: List<Court>): CourtSummary {
        val active = courts.filter { it.isActive }
        val bySurface = active.groupingBy { it.surfaceType }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<*, Int>> { it.second }.thenBy { (it.first as Enum<*>).ordinal })
        return CourtSummary(active.size, bySurface)
    }
}
