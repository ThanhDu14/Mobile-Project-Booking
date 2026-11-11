package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.CancelledBy
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import java.time.Instant

/**
 * Suy nhãn kết quả của một lượt (research R7, FR-019), không thêm enum trạng thái mới:
 * - `CHECKED_IN` → Đã tham gia;
 * - `CANCELLED` do người chơi (`USER`) hoặc hệ thống xóa tài khoản (`SYSTEM`) → Đã hủy;
 * - `CANCELLED` do chủ sân (`OWNER`), hoặc buổi đã `CANCELLED` → Buổi bị hủy;
 * - `REGISTERED` mà buổi đã kết thúc → Không đến; chưa kết thúc → Sắp tới.
 *
 * Giờ kết thúc lấy từ [DropInSession] nếu có (giờ mới khi chủ sân đổi giờ), không thì từ bản sao trong đăng ký.
 */
object RegistrationOutcomeResolver {

    fun resolve(registration: DropInRegistration, session: DropInSession?, now: Instant): RegistrationOutcome {
        val sessionCancelled = session?.status == SessionStatus.CANCELLED
        return when (registration.status) {
            RegistrationStatus.CHECKED_IN -> RegistrationOutcome.ATTENDED
            RegistrationStatus.CANCELLED -> when (registration.cancelledBy) {
                CancelledBy.USER, CancelledBy.SYSTEM -> RegistrationOutcome.CANCELLED_BY_ME
                CancelledBy.OWNER -> RegistrationOutcome.SESSION_CANCELLED
                // Dữ liệu cũ chưa có `cancelledBy`: dựa vào trạng thái buổi.
                null -> if (sessionCancelled) RegistrationOutcome.SESSION_CANCELLED else RegistrationOutcome.CANCELLED_BY_ME
            }
            RegistrationStatus.REGISTERED -> {
                val endAt = session?.endAt ?: registration.sessionEndAt
                when {
                    sessionCancelled -> RegistrationOutcome.SESSION_CANCELLED
                    endAt != null && endAt.isBefore(now) -> RegistrationOutcome.NO_SHOW
                    else -> RegistrationOutcome.UPCOMING
                }
            }
            // Hàng chờ (spec 081) chưa có nhãn riêng; buổi bị hủy thì báo như lượt thường.
            RegistrationStatus.WAITLISTED ->
                if (sessionCancelled) RegistrationOutcome.SESSION_CANCELLED else RegistrationOutcome.UPCOMING
        }
    }
}
