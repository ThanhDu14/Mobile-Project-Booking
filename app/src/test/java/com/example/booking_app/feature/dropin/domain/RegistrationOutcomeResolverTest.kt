package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.CancelledBy
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** T042: nhãn kết quả của lượt vãng lai theo bảng research R7. */
class RegistrationOutcomeResolverTest {

    /** Buổi đã kết thúc 1 giờ trước (bắt đầu 3 giờ trước, dài 2 giờ). */
    private val endedSession = session(startAt = NOW.minusSeconds(3 * 3600L))

    /** Buổi bắt đầu sau 3 giờ. */
    private val futureSession = session(startAt = NOW.plusSeconds(3 * 3600L))

    private fun resolve(
        status: RegistrationStatus,
        cancelledBy: CancelledBy? = null,
        sessionStatus: SessionStatus = SessionStatus.OPEN,
        ended: Boolean = true,
        now: Instant = NOW,
    ): RegistrationOutcome {
        val base = if (ended) endedSession else futureSession
        val reg = registration(status = status).copy(
            cancelledBy = cancelledBy,
            sessionStartAt = base.startAt,
            sessionEndAt = base.endAt,
        )
        return RegistrationOutcomeResolver.resolve(reg, base.copy(status = sessionStatus), now)
    }

    @Test
    fun `CHECKED_IN la Da tham gia`() {
        assertEquals(RegistrationOutcome.ATTENDED, resolve(RegistrationStatus.CHECKED_IN))
    }

    @Test
    fun `CANCELLED do nguoi choi huy la Da huy`() {
        assertEquals(
            RegistrationOutcome.CANCELLED_BY_ME,
            resolve(RegistrationStatus.CANCELLED, cancelledBy = CancelledBy.USER),
        )
    }

    @Test
    fun `CANCELLED do he thong huy (xoa tai khoan) cung la Da huy`() {
        assertEquals(
            RegistrationOutcome.CANCELLED_BY_ME,
            resolve(RegistrationStatus.CANCELLED, cancelledBy = CancelledBy.SYSTEM),
        )
    }

    @Test
    fun `CANCELLED do chu san huy la Buoi bi huy`() {
        assertEquals(
            RegistrationOutcome.SESSION_CANCELLED,
            resolve(RegistrationStatus.CANCELLED, cancelledBy = CancelledBy.OWNER),
        )
    }

    @Test
    fun `CANCELLED khong ro ai huy nhung buoi CANCELLED la Buoi bi huy`() {
        assertEquals(
            RegistrationOutcome.SESSION_CANCELLED,
            resolve(RegistrationStatus.CANCELLED, cancelledBy = null, sessionStatus = SessionStatus.CANCELLED),
        )
    }

    @Test
    fun `REGISTERED ma buoi da ket thuc la Khong den`() {
        assertEquals(RegistrationOutcome.NO_SHOW, resolve(RegistrationStatus.REGISTERED, ended = true))
    }

    @Test
    fun `REGISTERED chua bat dau la Sap toi`() {
        assertEquals(RegistrationOutcome.UPCOMING, resolve(RegistrationStatus.REGISTERED, ended = false))
    }

    @Test
    fun `REGISTERED dang dien ra (chua ket thuc) chua tinh la Khong den`() {
        // Buổi bắt đầu 1 giờ trước, còn 1 giờ nữa mới kết thúc.
        val running = session(startAt = NOW.minusSeconds(3600L))
        val reg = registration(status = RegistrationStatus.REGISTERED)
            .copy(sessionStartAt = running.startAt, sessionEndAt = running.endAt)
        assertEquals(RegistrationOutcome.UPCOMING, RegistrationOutcomeResolver.resolve(reg, running, NOW))
    }

    @Test
    fun `REGISTERED nhung buoi da bi chu san huy la Buoi bi huy`() {
        assertEquals(
            RegistrationOutcome.SESSION_CANCELLED,
            resolve(RegistrationStatus.REGISTERED, sessionStatus = SessionStatus.CANCELLED, ended = false),
        )
    }

    @Test
    fun `khong doc duoc buoi thi dung gio sao trong dang ky`() {
        val reg = registration(status = RegistrationStatus.REGISTERED).copy(
            sessionStartAt = NOW.minusSeconds(3 * 3600L),
            sessionEndAt = NOW.minusSeconds(3600L),
        )
        assertEquals(RegistrationOutcome.NO_SHOW, RegistrationOutcomeResolver.resolve(reg, null, NOW))
    }

    @Test
    fun `gio moi cua buoi (chu san doi gio) uu tien hon gio sao trong dang ky`() {
        // Đăng ký còn giờ cũ (đã qua), nhưng buổi đã dời sang ngày mai.
        val moved = session(startAt = NOW.plusSeconds(24 * 3600L))
        val reg = registration(status = RegistrationStatus.REGISTERED, sessionChanged = true).copy(
            sessionStartAt = NOW.minusSeconds(3 * 3600L),
            sessionEndAt = NOW.minusSeconds(3600L),
        )
        assertEquals(RegistrationOutcome.UPCOMING, RegistrationOutcomeResolver.resolve(reg, moved, NOW))
    }
}
