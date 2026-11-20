package com.example.booking_app.feature.dropin.domain.model

import java.time.Duration
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
    /** Số vé của các lượt đang chờ, tăng dần; chỉ có số, không có danh tính (spec 081 FR-006). */
    val waitlistTickets: List<Int> = emptyList(),
    val waitlistCount: Int = waitlistTickets.size,
) {
    val remaining: Int get() = (capacity - registeredCount).coerceAtLeast(0)

    fun isOwnedBy(uid: String?): Boolean = uid != null && uid == ownerId

    /** Hàng chờ mở khi buổi `FULL` và còn trước `startAt − 30 phút` (spec 081 FR-001, FR-014). */
    fun isWaitlistOpen(now: Instant): Boolean =
        status == SessionStatus.FULL && now.isBefore(startAt.minus(WAITLIST_CLOSE_BEFORE_START))

    companion object {
        val WAITLIST_CLOSE_BEFORE_START: Duration = Duration.ofMinutes(30)
    }
}
