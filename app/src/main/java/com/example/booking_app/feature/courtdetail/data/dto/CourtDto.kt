package com.example.booking_app.feature.courtdetail.data.dto

import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.SurfaceType

/** Sân con: chỉ đọc `surfaceType`, `isActive` (data-model §1.2). Thiếu `isActive` coi như đang hoạt động. */
object CourtDto {
    fun toDomain(id: String, data: Map<String, Any?>): Court = Court(
        id = id,
        surfaceType = SurfaceType.from(data["surfaceType"] as? String),
        isActive = data["isActive"] as? Boolean ?: true,
    )
}
