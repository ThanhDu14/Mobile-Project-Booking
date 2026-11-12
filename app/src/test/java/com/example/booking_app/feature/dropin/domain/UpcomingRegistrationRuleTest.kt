package com.example.booking_app.feature.dropin.domain

import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.isUpcomingAt
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lượt nằm ở tab "Sắp tới" tới khi buổi kết thúc, kể cả lúc buổi đang diễn ra (FR-019). */
class UpcomingRegistrationRuleTest {

    private fun reg(
        startHoursFromNow: Long?,
        endHoursFromNow: Long?,
        status: RegistrationStatus = RegistrationStatus.REGISTERED,
    ) = registration(status = status).copy(
        sessionStartAt = startHoursFromNow?.let { NOW.plusSeconds(it * 3600) },
        sessionEndAt = endHoursFromNow?.let { NOW.plusSeconds(it * 3600) },
    )

    @Test
    fun `buoi chua bat dau la sap toi`() {
        assertTrue(reg(2, 4).isUpcomingAt(NOW))
    }

    @Test
    fun `buoi dang dien ra van la sap toi`() {
        assertTrue(reg(-2, 3).isUpcomingAt(NOW))
    }

    @Test
    fun `buoi da ket thuc khong con sap toi`() {
        assertFalse(reg(-3, -1).isUpcomingAt(NOW))
    }

    @Test
    fun `dung luc ket thuc van tinh la sap toi`() {
        assertTrue(reg(-2, 0).isUpcomingAt(NOW))
    }

    @Test
    fun `thieu gio ket thuc thi tinh theo gio bat dau`() {
        assertTrue(reg(1, null).isUpcomingAt(NOW))
        assertFalse(reg(-1, null).isUpcomingAt(NOW))
    }

    @Test
    fun `thieu ca gio bat dau va ket thuc khong phai sap toi`() {
        assertFalse(reg(null, null).isUpcomingAt(NOW))
    }

    @Test
    fun `cho WAITLISTED dang dien ra van la sap toi`() {
        assertTrue(reg(-1, 1, RegistrationStatus.WAITLISTED).isUpcomingAt(NOW))
    }

    @Test
    fun `da huy, da check-in khong nam o sap toi`() {
        assertFalse(reg(-1, 1, RegistrationStatus.CANCELLED).isUpcomingAt(NOW))
        assertFalse(reg(-1, 1, RegistrationStatus.CHECKED_IN).isUpcomingAt(NOW))
    }
}
