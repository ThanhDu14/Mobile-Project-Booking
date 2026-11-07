package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.feature.courtdetail.domain.model.PhoneDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneDisplayTest {

    @Test
    fun `so di dong co khoang trang hoac dau cham goi duoc`() {
        val expected = PhoneDisplay.Dialable("+84901234567", "0901 234 567")
        assertEquals(expected, PhoneDisplay.of("0901 234 567"))
        assertEquals(expected, PhoneDisplay.of("0901.234.567"))
        assertEquals(expected, PhoneDisplay.of("+84901234567"))
    }

    @Test
    fun `so ban 11 chu so goi duoc`() {
        assertEquals(PhoneDisplay.Dialable("+842838123456", "028 3812 3456"), PhoneDisplay.of("028 3812 3456"))
    }

    @Test
    fun `qua ngan hoac qua dai chi hien thi`() {
        assertEquals(PhoneDisplay.DisplayOnly("090123"), PhoneDisplay.of("090123"))
        assertEquals(PhoneDisplay.DisplayOnly("090123456789"), PhoneDisplay.of("090123456789"))
    }

    @Test
    fun `so seed bi che chi hien thi`() {
        assertEquals(PhoneDisplay.DisplayOnly("0944xxx486"), PhoneDisplay.of("0944xxx486"))
    }

    @Test
    fun `rong hoac null tra null`() {
        assertNull(PhoneDisplay.of(null))
        assertNull(PhoneDisplay.of(""))
        assertNull(PhoneDisplay.of("   "))
    }
}
