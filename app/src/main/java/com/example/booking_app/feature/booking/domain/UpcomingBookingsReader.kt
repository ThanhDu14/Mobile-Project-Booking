package com.example.booking_app.feature.booking.domain

import java.time.Instant
import javax.inject.Inject

/*
 * INTERFACE TẠM do spec 080 (C) tạo theo contracts/ui-screens.md mục 3, chờ B (spec 040/060) cài thật.
 * B: giữ nguyên chữ ký, cài bằng truy vấn `bookings` (index `userId, status, startAt`), bind trong
 * module của mình rồi xóa `UpcomingBookingsStubModule` ở `feature/dropin/data/di/`.
 */

/** Đơn đặt sân còn hiệu lực của người chơi, dùng cho cảnh báo trùng giờ ở spec 080 (FR-012, research R11). */
interface UpcomingBookingsReader {
    /** Đơn còn hiệu lực (chưa hủy, chưa hoàn thành) của [uid] có khoảng giờ giao với `[from, to)`. */
    suspend fun upcoming(uid: String, from: Instant, to: Instant): List<UpcomingBooking>
}

data class UpcomingBooking(
    val bookingId: String,
    val venueId: String?,
    val startAt: Instant,
    val endAt: Instant,
)

/** Fake trả rỗng, dùng tới khi B cài [UpcomingBookingsReader] thật. */
class EmptyUpcomingBookingsReader @Inject constructor() : UpcomingBookingsReader {
    override suspend fun upcoming(uid: String, from: Instant, to: Instant): List<UpcomingBooking> = emptyList()
}
