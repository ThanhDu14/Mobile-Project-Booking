package com.example.booking_app.feature.courtdetail.domain.model

import com.example.booking_app.core.schedule.TimeOfDay

/** Trạng thái mở cửa theo giờ Việt Nam (research R6). */
sealed interface OpeningStatus {
    data object AllDay : OpeningStatus
    data class Open(val closesAt: TimeOfDay) : OpeningStatus
    data class Closed(val reopensAt: TimeOfDay) : OpeningStatus
}
