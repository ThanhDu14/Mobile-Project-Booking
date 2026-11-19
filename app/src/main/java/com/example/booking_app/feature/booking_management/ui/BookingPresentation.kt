package com.example.booking_app.feature.booking_management.ui

import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.BookingFinance
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
    /** Mốc hủy sớm 4 giờ (240 phút) trước giờ chơi, cùng giá trị với RefundPolicy. */
    private const val EARLY_CANCEL_MILLIS = 240L * 60_000L

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

    /** Đơn đã đóng (hủy, bị từ chối, hết hạn giữ chỗ) không còn khoản nào phải trả tại sân. */
    fun showRemaining(phase: BookingPhase): Boolean =
        phase != BookingPhase.CANCELLED && phase != BookingPhase.REJECTED && phase != BookingPhase.EXPIRED

    /** Nhãn dòng "đã trả"; null khi chưa trả đồng nào (ví dụ đơn trả tại sân). */
    fun paidLabel(finance: BookingFinance): String? = when {
        finance.paidAmount <= 0 -> null
        finance.paymentMethod == RefundPolicy.PaymentMethod.DEPOSIT -> "Đã đặt cọc (giả lập)"
        else -> "Đã thanh toán (ví giả lập)"
    }

    /** Chính sách hủy theo đúng phương thức thanh toán của đơn (FR-017 đến FR-020). */
    fun cancellationPolicy(booking: BookingDetail, formatTime: (Long) -> String): String {
        if (booking.status == RefundPolicy.Status.PENDING) {
            return "Chính sách hủy: đơn chưa được duyệt, hủy trước giờ chơi được hoàn 100% số tiền đã trả."
        }
        val deadline = formatTime(booking.startAtMillis - EARLY_CANCEL_MILLIS)
        return when (booking.finance.paymentMethod) {
            RefundPolicy.PaymentMethod.AT_VENUE ->
                "Chính sách hủy: đơn trả tại sân hủy trước giờ chơi không mất phí. Đơn đã check-in không hủy được."
            RefundPolicy.PaymentMethod.DEPOSIT ->
                "Chính sách hủy: hủy trước $deadline hôm chơi (từ 4 giờ trở lên) được hoàn 100% tiền cọc. " +
                    "Hủy sau mốc đó mất tiền cọc. Đơn đã check-in không hủy được."
            RefundPolicy.PaymentMethod.MOCK_WALLET ->
                "Chính sách hủy: hủy trước $deadline hôm chơi (từ 4 giờ trở lên) được hoàn 100% vào ví. " +
                    "Hủy sau mốc đó phạt 30%, hoàn 70%. Đơn đã check-in không hủy được."
        }
    }
}
