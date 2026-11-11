package com.example.booking_app.feature.dropin.domain.model

import java.time.Instant

/** Buổi vãng lai (data-model mục 1 và 6). Tiền là VND kiểu `Long`. */
data class DropInSession(
    val id: String,
    val venueId: String,
    val ownerId: String,
    val districtId: String,
    val venueName: String,
    val venueAddress: String,
    val districtName: String,
    val courtIds: List<String>,
    /** Tên sân con, repository đọc từ `venues/{venueId}/courts`; rỗng nếu chưa đọc. */
    val courtNames: List<String> = emptyList(),
    val startAt: Instant,
    val endAt: Instant,
    val timeSlot: TimeSlot?,
    val level: Level?,
    val capacity: Int,
    val registeredCount: Int,
    val pricePerPerson: Long,
    val status: SessionStatus,
    val note: String?,
    /** `false` khi `venues/{venueId}.status != ACTIVE` (AC 1.5). */
    val venueActive: Boolean = true,
) {
    val remaining: Int get() = (capacity - registeredCount).coerceAtLeast(0)

    fun isOwnedBy(uid: String?): Boolean = uid != null && uid == ownerId
}
