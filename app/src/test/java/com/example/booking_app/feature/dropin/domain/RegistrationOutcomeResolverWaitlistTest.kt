package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.CancelledBy
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import org.junit.Assert.assertEquals
import org.junit.Test

/** T044: nhãn của lượt chờ theo research R6; `waitlistEndReason` được ưu tiên đọc trước `cancelledBy`. */
class RegistrationOutcomeResolverWaitlistTest {

    private val futureSession = session(startAt = NOW.plusSeconds(3 * 3600L))
    private val endedSession = session(startAt = NOW.minusSeconds(3 * 3600L))

    private fun resolve(
        status: RegistrationStatus,
        reason: WaitlistEndReason? = null,
        cancelledBy: CancelledBy? = null,
        sessionStatus: SessionStatus = SessionStatus.OPEN,
        ended: Boolean = false,
    ): RegistrationOutcome {
        val base = if (ended) endedSession else futureSession
        val reg = registration(
            status = status,
            qrToken = null,
            waitlistTicket = 3,
            waitlistEndReason = reason,
            cancelledBy = cancelledBy,
        ).copy(sessionStartAt = base.startAt, sessionEndAt = base.endAt)
        return RegistrationOutcomeResolver.resolve(reg, base.copy(status = sessionStatus), NOW)
    }

    @Test
    fun `dang cho la WAITING`() {
        assertEquals(RegistrationOutcome.WAITING, resolve(RegistrationStatus.WAITLISTED))
    }

    @Test
    fun `dang cho ma buoi bi huy la Buoi bi huy`() {
        assertEquals(
            RegistrationOutcome.SESSION_CANCELLED,
            resolve(RegistrationStatus.WAITLISTED, sessionStatus = SessionStatus.CANCELLED),
        )
    }

    @Test
    fun `dang cho ma buoi da ket thuc thi khong duoc nhan cho`() {
        assertEquals(RegistrationOutcome.NOT_ADMITTED, resolve(RegistrationStatus.WAITLISTED, ended = true))
    }

    @Test
    fun `LEFT la Da roi hang cho du ai huy`() {
        assertEquals(
            RegistrationOutcome.LEFT_WAITLIST,
            resolve(RegistrationStatus.CANCELLED, WaitlistEndReason.LEFT, CancelledBy.USER),
        )
        assertEquals(
            RegistrationOutcome.LEFT_WAITLIST,
            resolve(RegistrationStatus.CANCELLED, WaitlistEndReason.LEFT, cancelledBy = null),
        )
    }

    @Test
    fun `AUTO_LEFT_OVERLAP do he thong van la Da roi hang cho, khong phai Da huy`() {
        assertEquals(
            RegistrationOutcome.LEFT_WAITLIST,
            resolve(RegistrationStatus.CANCELLED, WaitlistEndReason.AUTO_LEFT_OVERLAP, CancelledBy.SYSTEM),
        )
    }

    @Test
    fun `CLOSED la Khong duoc nhan cho`() {
        assertEquals(
            RegistrationOutcome.NOT_ADMITTED,
            resolve(RegistrationStatus.CANCELLED, WaitlistEndReason.CLOSED, CancelledBy.SYSTEM, ended = true),
        )
    }

    @Test
    fun `SESSION_CANCELLED la Buoi bi huy`() {
        assertEquals(
            RegistrationOutcome.SESSION_CANCELLED,
            resolve(RegistrationStatus.CANCELLED, WaitlistEndReason.SESSION_CANCELLED, CancelledBy.OWNER),
        )
    }

    @Test
    fun `luot cho da duoc nhan van theo quy tac cua luot thuong`() {
        val reg = registration(promotedAt = NOW).copy(
            sessionStartAt = futureSession.startAt,
            sessionEndAt = futureSession.endAt,
        )
        assertEquals(RegistrationOutcome.UPCOMING, RegistrationOutcomeResolver.resolve(reg, futureSession, NOW))

        val ended = reg.copy(sessionStartAt = endedSession.startAt, sessionEndAt = endedSession.endAt)
        assertEquals(RegistrationOutcome.NO_SHOW, RegistrationOutcomeResolver.resolve(ended, endedSession, NOW))
    }
}
