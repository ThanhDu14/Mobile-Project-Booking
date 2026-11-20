package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.CancelledBy
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import java.time.Instant

/**
 * Suy nhãn kết quả của một lượt (research R7, FR-019), không thêm enum trạng thái mới:
 * - `CHECKED_IN` → Đã tham gia;
 * - `CANCELLED` có `waitlistEndReason` (spec 081, research R6) → đọc lý do trước: `LEFT`, `AUTO_LEFT_OVERLAP` → Đã rời
 *   hàng chờ; `CLOSED` → Không được nhận chỗ; `SESSION_CANCELLED` → Buổi bị hủy;
 * - `CANCELLED` do người chơi (`USER`) hoặc hệ thống xóa tài khoản (`SYSTEM`) → Đã hủy;
 * - `CANCELLED` do chủ sân (`OWNER`), hoặc buổi đã `CANCELLED` → Buổi bị hủy;
 * - `WAITLISTED` → Đang chờ; buổi đã bị hủy → Buổi bị hủy; buổi đã kết thúc → Không được nhận chỗ;
 * - `REGISTERED` mà buổi đã kết thúc → Không đến; chưa kết thúc → Sắp tới.
 *
 * Giờ kết thúc lấy từ [DropInSession] nếu có (giờ mới khi chủ sân đổi giờ), không thì từ bản sao trong đăng ký.
 */
object RegistrationOutcomeResolver {

    fun resolve(registration: DropInRegistration, session: DropInSession?, now: Instant): RegistrationOutcome {
        val sessionCancelled = session?.status == SessionStatus.CANCELLED
        return when (registration.status) {
            RegistrationStatus.CHECKED_IN -> RegistrationOutcome.ATTENDED
            RegistrationStatus.CANCELLED -> resolveCancelled(registration, sessionCancelled)
            RegistrationStatus.REGISTERED -> {
                val endAt = session?.endAt ?: registration.sessionEndAt
                when {
                    sessionCancelled -> RegistrationOutcome.SESSION_CANCELLED
                    endAt != null && endAt.isBefore(now) -> RegistrationOutcome.NO_SHOW
                    else -> RegistrationOutcome.UPCOMING
                }
            }
            RegistrationStatus.WAITLISTED -> {
                val endAt = session?.endAt ?: registration.sessionEndAt
                when {
                    sessionCancelled -> RegistrationOutcome.SESSION_CANCELLED
                    endAt != null && endAt.isBefore(now) -> RegistrationOutcome.NOT_ADMITTED
                    else -> RegistrationOutcome.WAITING
                }
            }
        }
    }

    private fun resolveCancelled(registration: DropInRegistration, sessionCancelled: Boolean): RegistrationOutcome =
        when (registration.waitlistEndReason) {
            WaitlistEndReason.LEFT, WaitlistEndReason.AUTO_LEFT_OVERLAP -> RegistrationOutcome.LEFT_WAITLIST
            WaitlistEndReason.CLOSED -> RegistrationOutcome.NOT_ADMITTED
            WaitlistEndReason.SESSION_CANCELLED -> RegistrationOutcome.SESSION_CANCELLED
            null -> when (registration.cancelledBy) {
                CancelledBy.USER, CancelledBy.SYSTEM -> RegistrationOutcome.CANCELLED_BY_ME
                CancelledBy.OWNER -> RegistrationOutcome.SESSION_CANCELLED
                // Dữ liệu cũ chưa có `cancelledBy`: dựa vào trạng thái buổi.
                null -> if (sessionCancelled) RegistrationOutcome.SESSION_CANCELLED else RegistrationOutcome.CANCELLED_BY_ME
            }
        }
}
