package com.example.booking_app.feature.auth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpSessionTest {

    private val session = OtpSession(
        phoneE164 = "+84912345678",
        verificationId = "v1",
        resendToken = null,
        sentAtElapsed = 1_000L,
        wrongCount = 0,
    )

    @Test
    fun `dung duoc trong 5 phut ke tu luc gui (FR-016)`() {
        assertTrue(session.isUsable(nowElapsed = 1_000L))
        assertTrue(session.isUsable(nowElapsed = 1_000L + 299_999))
        assertFalse(session.isUsable(nowElapsed = 1_000L + 300_000))
    }

    @Test
    fun `sai du 5 lan thi khong dung duoc nua`() {
        assertTrue(session.copy(wrongCount = 4).isUsable(1_000L))
        assertFalse(session.copy(wrongCount = 5).isUsable(1_000L))
    }

    @Test
    fun `so lan con lai bang 5 tru so lan sai`() {
        assertEquals(5, session.remainingAttempts)
        assertEquals(2, session.copy(wrongCount = 3).remainingAttempts)
        assertEquals(0, session.copy(wrongCount = 5).remainingAttempts)
    }

    @Test
    fun `so dien thoai hien dang che bot`() {
        assertEquals("+84 9•• ••• 678", PhoneNumber("+84912345678").masked())
        assertEquals("+84 3•• ••• 999", PhoneNumber("+84301234999").masked())
    }
}
