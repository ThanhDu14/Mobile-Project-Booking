package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T011: vị trí trong hàng chờ và mốc đóng hàng chờ (spec 081 data-model mục 5, FR-001, FR-005). */
class WaitlistPositionTest {

    private fun waiting(ticket: Int?) =
        registration(status = RegistrationStatus.WAITLISTED, qrToken = null, waitlistTicket = ticket)

    @Test
    fun `vi tri bang chi so cua ve trong mang cong 1, tong bang kich thuoc mang`() {
        val s = session(waitlistTickets = listOf(3, 5, 9))

        assertEquals(WaitlistPosition(position = 1, total = 3), WaitlistPosition.of(s, waiting(3)))
        assertEquals(WaitlistPosition(position = 2, total = 3), WaitlistPosition.of(s, waiting(5)))
        assertEquals(WaitlistPosition(position = 3, total = 3), WaitlistPosition.of(s, waiting(9)))
    }

    @Test
    fun `ve khong con trong mang thi null`() {
        val s = session(waitlistTickets = listOf(3, 9))

        assertNull(WaitlistPosition.of(s, waiting(5)))
    }

    @Test
    fun `mang rong thi null`() {
        assertNull(WaitlistPosition.of(session(waitlistTickets = emptyList()), waiting(1)))
    }

    @Test
    fun `luot khong co so ve thi null`() {
        assertNull(WaitlistPosition.of(session(waitlistTickets = listOf(1, 2)), waiting(null)))
    }

    @Test
    fun `nguoi phia truoc roi hang cho thi vi tri tien len`() {
        val mine = waiting(5)
        assertEquals(2, WaitlistPosition.of(session(waitlistTickets = listOf(3, 5, 9)), mine)?.position)
        assertEquals(1, WaitlistPosition.of(session(waitlistTickets = listOf(5, 9)), mine)?.position)
    }

    @Test
    fun `hang cho mo khi FULL va con hon 30 phut, dong dung moc 30 phut`() {
        fun full(startInSeconds: Long) =
            session(startAt = NOW.plusSeconds(startInSeconds), capacity = 12, registeredCount = 12, status = SessionStatus.FULL)

        assertTrue(full(31 * 60L).isWaitlistOpen(NOW))
        assertTrue(full(30 * 60L + 1).isWaitlistOpen(NOW))
        assertFalse(full(30 * 60L).isWaitlistOpen(NOW))
        assertFalse(full(29 * 60L).isWaitlistOpen(NOW))
        assertFalse(full(-60L).isWaitlistOpen(NOW))
    }

    @Test
    fun `hang cho khong mo khi buoi khong FULL`() {
        listOf(SessionStatus.OPEN, SessionStatus.CANCELLED, SessionStatus.COMPLETED).forEach { status ->
            assertFalse(status.name, session(status = status).isWaitlistOpen(NOW))
        }
    }
}
