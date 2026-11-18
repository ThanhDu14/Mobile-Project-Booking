package com.example.booking_app.feature.booking_management.ui

import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.RefundPolicy

/**
 * Cách hiển thị một đơn theo trạng thái và thời điểm hiện tại (rà soát 060 ngày 2026-11-18).
 *
 * Trạng thái lưu trên server có thể trễ so với thực tế khi job phía server chưa chạy: đơn PENDING đã quá
 * hạn duyệt, đơn CONFIRMED đã qua giờ chơi. App không được coi các đơn đó là "đang chờ" hay "sắp tới".
 */
enum class BookingPhase {
    HOLD, PENDING, PENDING_OVERDUE, UPCOMING, ENDED_UNSETTLED, CHECKED_IN,
    COMPLETED, NO_SHOW, CANCELLED, REJECTED, EXPIRED
}

object BookingPresentation {

    fun phase(booking: BookingDetail, nowMillis: Long): BookingPhase = when (booking.status) {
        RefundPolicy.Status.HOLD -> BookingPhase.HOLD
        RefundPolicy.Status.PENDING -> {
            val deadline = booking.approvalDeadlineMillis
            if (deadline != null && deadline <= nowMillis) BookingPhase.PENDING_OVERDUE else BookingPhase.PENDING
        }
        RefundPolicy.Status.CONFIRMED -> when {
            booking.checkedInAtMillis != null && booking.endAtMillis > nowMillis -> BookingPhase.CHECKED_IN
            booking.endAtMillis <= nowMillis -> BookingPhase.ENDED_UNSETTLED
            else -> BookingPhase.UPCOMING
        }
        RefundPolicy.Status.COMPLETED -> BookingPhase.COMPLETED
        RefundPolicy.Status.NO_SHOW -> BookingPhase.NO_SHOW
        RefundPolicy.Status.CANCELLED -> BookingPhase.CANCELLED
        RefundPolicy.Status.REJECTED -> BookingPhase.REJECTED
        RefundPolicy.Status.EXPIRED -> BookingPhase.EXPIRED
    }

    fun badge(phase: BookingPhase): String = when (phase) {
        BookingPhase.HOLD -> "Chờ thanh toán"
        BookingPhase.PENDING -> "Chờ duyệt"
        BookingPhase.PENDING_OVERDUE -> "Quá hạn duyệt"
        BookingPhase.UPCOMING -> "Đã xác nhận"
        BookingPhase.ENDED_UNSETTLED -> "Đã qua giờ chơi"
        BookingPhase.CHECKED_IN -> "Đã check-in"
        BookingPhase.COMPLETED -> "Hoàn thành"
        BookingPhase.NO_SHOW -> "Vắng mặt / Chưa check-in"
        BookingPhase.CANCELLED -> "Đã hủy"
        BookingPhase.REJECTED -> "Đã từ chối"
        BookingPhase.EXPIRED -> "Hết hạn giữ chỗ"
    }

    /** Dòng giải thích thêm dưới trạng thái; null thì không hiện. [formatTime] đổi millis sang "HH:mm". */
    fun note(booking: BookingDetail, phase: BookingPhase, nowMillis: Long, formatTime: (Long) -> String): String? =
        when (phase) {
            BookingPhase.PENDING -> booking.approvalDeadlineMillis?.let { deadline ->
                val minutes = ((deadline - nowMillis + 59_999) / 60_000).coerceAtLeast(1)
                "Chủ sân duyệt trước ${formatTime(deadline)} (còn $minutes phút)."
            }
            BookingPhase.PENDING_OVERDUE ->
                "Chủ sân chưa duyệt kịp. Hệ thống sẽ tự từ chối và hoàn tiền nếu bạn đã trả."
            BookingPhase.ENDED_UNSETTLED -> "Buổi chơi đã kết thúc. Hệ thống đang cập nhật kết quả đơn."
            BookingPhase.REJECTED, BookingPhase.CANCELLED -> booking.cancelReason?.takeIf { it.isNotBlank() }
            else -> null
        }

    /** QR chỉ có ý nghĩa với đơn đã xác nhận, chưa check-in và chưa hết giờ chơi. */
    fun showQr(phase: BookingPhase): Boolean = phase == BookingPhase.UPCOMING
}
