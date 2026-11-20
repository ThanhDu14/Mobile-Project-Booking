package com.example.booking_app.feature.dropin.domain.model

import java.time.Instant

/**
 * Nhãn kết quả ở tab "Đã qua" (research R7). Spec 081 thêm [WAITING] (đang chờ, nằm ở "Sắp tới"), [LEFT_WAITLIST]
 * ("Đã rời hàng chờ", cả khi tự rời do trùng giờ) và [NOT_ADMITTED] ("Không được nhận chỗ"), research R6.
 */
enum class RegistrationOutcome {
    UPCOMING, ATTENDED, CANCELLED_BY_ME, SESSION_CANCELLED, NO_SHOW,
    WAITING, LEFT_WAITLIST, NOT_ADMITTED,
}

data class RegisterRequest(
    val sessionId: String,
    val partySize: Int,
    val paymentMethod: PaymentMethod,
    /** UUID, giữ nguyên khi thử lại (idempotent, AC 2.8). */
    val requestId: String,
    /** `true` khi vào hàng chờ của buổi đã đủ người (spec 081 FR-001); mặc định đăng ký thường. */
    val joinWaitlist: Boolean = false,
)

data class CancelQuote(
    val refundAmount: Long,
    val reason: CancelReason,
) {
    val isFree: Boolean get() = reason == CancelReason.AT_VENUE || reason == CancelReason.WAITLIST_LEAVE
}

/** [PROMOTED_GRACE]: vừa được nhận từ hàng chờ sát giờ, hủy trong 30 phút hoàn 100%. [WAITLIST_LEAVE]: rời hàng chờ, luôn 0 đ. */
enum class CancelReason { EARLY, LATE, SESSION_CHANGED, AT_VENUE, PROMOTED_GRACE, WAITLIST_LEAVE }

/** Cảnh báo ở màn hình xác nhận, chỉ nhắc, không chặn (FR-012). */
enum class DropInWarning { OVERLAP_DROP_IN, OVERLAP_BOOKING, LEVEL_MISMATCH }

/** Con trỏ phân trang theo `orderBy startAt` (kèm ID để không trùng). */
data class DropInCursor(val startAt: Instant, val id: String)

data class Page<T>(val items: List<T>, val next: DropInCursor?) {
    val canLoadMore: Boolean get() = next != null

    companion object {
        const val PAGE_SIZE = 20
    }
}

/** Lỗi nghiệp vụ map từ `error.code` của Edge Function (contract mục 4). Dùng trong `Result.failure`. */
sealed class DropInError(code: String) : Exception(code) {
    /** [waitlistOpen]: hàng chờ còn mở, app hiện lựa chọn "Vào hàng chờ" (spec 081 contract mục 1). */
    data class SessionFull(val remaining: Int, val waitlistOpen: Boolean = false) : DropInError("SESSION_FULL")

    /** Vào hàng chờ khi buổi còn chỗ: chuyển sang đăng ký thường (spec 081 AC 1.5). */
    data class SessionHasSpots(val remaining: Int) : DropInError("SESSION_HAS_SPOTS")

    /** Còn ≤ 30 phút trước giờ bắt đầu hoặc buổi đã hủy (spec 081 AC 4.3). */
    class WaitlistClosed : DropInError("WAITLIST_CLOSED")

    /** Rời hàng chờ khi đã được đẩy lên: không hủy đăng ký, mở vé (spec 081 FR-013). */
    class AlreadyPromoted : DropInError("ALREADY_PROMOTED")
    class SessionStarted : DropInError("SESSION_STARTED")
    class SessionCancelled : DropInError("SESSION_CANCELLED")
    class VenueInactive : DropInError("VENUE_INACTIVE")
    class OwnSession : DropInError("OWN_SESSION")
    class AlreadyRegistered : DropInError("ALREADY_REGISTERED")
    class AlreadyWaitlisted : DropInError("ALREADY_WAITLISTED")
    class NotCancellable : DropInError("NOT_CANCELLABLE")

    /** Thêm người vượt tối đa 4 người/lượt (FR-008a); [maxAdditional] là số người còn thêm được. */
    data class PartyLimit(val maxAdditional: Int) : DropInError("PARTY_LIMIT")

    /** Thêm người khi không còn lượt `REGISTERED` ở buổi đó (đã hủy, đã check-in, chưa đăng ký). */
    class NotRegistered : DropInError("NOT_REGISTERED")
    class NotFound : DropInError("NOT_FOUND")
    class AccountLocked : DropInError("ACCOUNT_LOCKED")
    class InvalidArgument : DropInError("INVALID_ARGUMENT")
    class Unauthenticated : DropInError("UNAUTHENTICATED")
    class Busy : DropInError("BUSY")
    class Network : DropInError("NETWORK")
    data class Unknown(val code: String?) : DropInError(code ?: "UNKNOWN")
}
