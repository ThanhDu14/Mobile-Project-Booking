package com.example.booking_app.feature.payment.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class PaymentStateMapperTest {

    @Test
    fun `don le`() {
        assertEquals(PaymentStateMapper.Kind.Awaiting(1_000L), PaymentStateMapper.kind(mapOf("status" to "HOLD", "holdExpiresAt" to Date(1_000L)), false))
        assertEquals(PaymentStateMapper.Kind.Confirmed, PaymentStateMapper.kind(mapOf("status" to "PENDING"), false))
        assertEquals(PaymentStateMapper.Kind.Expired, PaymentStateMapper.kind(mapOf("status" to "EXPIRED"), false))
        assertNull(PaymentStateMapper.kind(null, false))
    }

    @Test
    fun `nhom dinh ky chi chot khi DONE`() {
        assertEquals(PaymentStateMapper.Kind.Confirmed, PaymentStateMapper.kind(mapOf("status" to "DONE"), true))
        assertNull(PaymentStateMapper.kind(mapOf("status" to "PENDING"), true))
        assertEquals(PaymentStateMapper.Kind.Expired, PaymentStateMapper.kind(mapOf("status" to "EXPIRED"), true))
    }
}
