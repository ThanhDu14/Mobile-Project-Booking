package com.example.booking_app.feature.courtdetail.domain.model

enum class SurfaceType {
    WOOD,
    PU,
    MAT,
    OTHER,
    ;

    companion object {
        fun from(raw: String?): SurfaceType = entries.firstOrNull { it.name == raw && it != OTHER } ?: OTHER
    }
}

/** Sân con `venues/{id}/courts/{courtId}`: chỉ các trường 030 dùng. */
data class Court(val id: String, val surfaceType: SurfaceType, val isActive: Boolean)

/** "4 sân · 3 Thảm, 1 Gỗ" (FR-006): chỉ đếm sân đang hoạt động, loại nhiều sân đứng trước. */
data class CourtSummary(val activeCount: Int, val bySurface: List<Pair<SurfaceType, Int>>)
