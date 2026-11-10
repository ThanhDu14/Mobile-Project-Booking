package com.example.booking_app.core.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceRuleMapperTest {

    @Test
    fun `doc du truong, giu endTime 2400`() {
        val rule = PriceRuleMapper.fromFirestore(
            "rule-01",
            mapOf(
                "daysOfWeek" to listOf(6L, 7L),
                "startTime" to "0500",
                "endTime" to "2400",
                "pricePerHour" to 100000L,
                "label" to "WEEKEND",
            ),
        )
        assertEquals(PriceRule("rule-01", listOf(6, 7), "0500", "2400", 100000L, "WEEKEND"), rule)
        assertEquals(50000L, rule!!.pricePerSlot)
    }

    @Test
    fun `thieu truong bat buoc thi bo qua`() {
        assertNull(PriceRuleMapper.fromFirestore("r", mapOf("startTime" to "0500", "endTime" to "2400")))
        assertNull(
            PriceRuleMapper.fromFirestore(
                "r",
                mapOf("daysOfWeek" to listOf(1L), "startTime" to "0500", "endTime" to "2400"),
            ),
        )
    }

    @Test
    fun `thieu label thi la NORMAL`() {
        val rule = PriceRuleMapper.fromFirestore(
            "r",
            mapOf("daysOfWeek" to listOf(1L), "startTime" to "0500", "endTime" to "0600", "pricePerHour" to 60000L),
        )
        assertEquals("NORMAL", rule!!.label)
    }
}
