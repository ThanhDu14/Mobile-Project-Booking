package com.example.booking_app.core.pricing

import com.example.booking_app.core.schedule.TimeOfDay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Ca giá dùng chung với 020, 030, 040 (spec 030 quickstart §3, SC-002). */
class SlotPricingVectorTest {

    @Test
    fun `file vector dung quy uoc ngay hien tai`() {
        assertEquals(DayConvention.CURRENT, PriceVectors.load().dayConvention)
    }

    @Test
    fun `moi ca P1 den P9 ra dung tong tien va gia moi gio`() {
        val cases = PriceVectors.load().cases
        assertEquals(9, cases.size)
        cases.forEach { c ->
            val total = SlotPricing.priceForWindow(c.rules, c.date, c.start, c.end)
            assertEquals("${c.id} tổng", c.expectedTotal, total)
            val perHour = total?.let { SlotPricing.perHour(it, c.start, c.end) }
            assertEquals("${c.id} đ/giờ", c.expectedPerHour, perHour)
        }
    }

    @Test
    fun `P3 khung co phan truoc 05h tra null`() {
        val p3 = PriceVectors.load().cases.single { it.id == "P3" }
        assertNull(SlotPricing.priceForWindow(p3.rules, p3.date, p3.start, p3.end))
    }
}

/** Đọc `pricing/window-price-vectors.json`; test của spec khác dùng lại đối tượng này. */
object PriceVectors {
    data class Case(
        val id: String,
        val venueId: String,
        val rules: List<PriceRule>,
        val date: LocalDate,
        val start: TimeOfDay,
        val end: TimeOfDay,
        val expectedTotal: Long?,
        val expectedPerHour: Long?,
        val highlightedRuleIndexes: List<Int>,
    )

    data class File(val dayConvention: String, val cases: List<Case>)

    fun load(): File {
        val text = javaClass.classLoader!!.getResource("pricing/window-price-vectors.json")!!.readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject.toCase() }
        return File(root.getValue("dayConvention").jsonPrimitive.content, cases)
    }

    private fun JsonObject.toCase(): Case {
        val id = str("id")
        val rules = getValue("rules").jsonArray.mapIndexed { i, r ->
            val o = r.jsonObject
            PriceRule(
                id = "$id-rule-$i",
                daysOfWeek = o.getValue("daysOfWeek").jsonArray.map { it.jsonPrimitive.int },
                startTime = o.str("startTime"),
                endTime = o.str("endTime"),
                pricePerHour = o.getValue("pricePerHour").jsonPrimitive.long,
                label = o.str("label"),
            )
        }
        return Case(
            id = id,
            venueId = str("venueId"),
            rules = rules,
            date = LocalDate.parse(str("date"), DateTimeFormatter.BASIC_ISO_DATE),
            start = TimeOfDay.parse(str("start"))!!,
            end = TimeOfDay.parseEnd(str("end"))!!,
            expectedTotal = getValue("expectedTotal").jsonPrimitive.longOrNull,
            expectedPerHour = getValue("expectedPerHour").jsonPrimitive.longOrNull,
            highlightedRuleIndexes = getValue("highlightedRuleIndexes").jsonArray.map { it.jsonPrimitive.int },
        )
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
