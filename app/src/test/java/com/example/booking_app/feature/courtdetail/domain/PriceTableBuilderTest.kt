package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.courtdetail.SeedPriceRules
import com.example.booking_app.feature.courtdetail.domain.model.DayGroupTitle
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate

class PriceTableBuilderTest {

    private fun t(hhmm: String) = TimeOfDay.parseEnd(hhmm)!!

    /** Thứ ba 27/10/2026, 17:00–19:00. */
    private val tuesdayWindow = SearchWindow(LocalDate.of(2026, 10, 27), t("1700"), t("1900"))

    @Test
    fun `demo-quang-vinh co 3 nhom`() {
        val groups = PriceTableBuilder.build(SeedPriceRules.quangVinh, null)
        assertEquals(
            listOf(
                DayGroupTitle.Single(MONDAY),
                DayGroupTitle.Range(TUESDAY, FRIDAY),
                DayGroupTitle.Pair(SATURDAY, SUNDAY),
            ),
            groups.map { it.title },
        )
        assertEquals(listOf(80_000L), groups[0].rows.filter { it.start == t("1600") }.map { it.pricePerHour })
        assertEquals(listOf(90_000L), groups[1].rows.filter { it.start == t("1600") }.map { it.pricePerHour })
    }

    @Test
    fun `demo-son-ta tach Thu 7 va Chu nhat`() {
        val titles = PriceTableBuilder.build(SeedPriceRules.sonTa, null).map { it.title }
        assertEquals(
            listOf(DayGroupTitle.Range(MONDAY, FRIDAY), DayGroupTitle.Single(SATURDAY), DayGroupTitle.Single(SUNDAY)),
            titles,
        )
    }

    @Test
    fun `demo-truong-chinh-909 co 2 nhom`() {
        val groups = PriceTableBuilder.build(SeedPriceRules.truongChinh, null)
        assertEquals(listOf(DayGroupTitle.Range(MONDAY, FRIDAY), DayGroupTitle.Pair(SATURDAY, SUNDAY)), groups.map { it.title })
    }

    @Test
    fun `moi ngay nam trong dung mot nhom va dong sap theo gio bat dau`() {
        listOf(SeedPriceRules.truongChinh, SeedPriceRules.quangVinh, SeedPriceRules.sonTa).forEach { rules ->
            val groups = PriceTableBuilder.build(rules.shuffled(java.util.Random(7)), null)
            assertEquals(DayOfWeek.entries.toList(), groups.flatMap { it.days })
            groups.forEach { g -> assertEquals(g.rows.sortedBy { it.start }, g.rows) }
        }
    }

    @Test
    fun `gio ket thuc 2400 in 24h00`() {
        val last = PriceTableBuilder.build(SeedPriceRules.truongChinh, null).first().rows.last()
        assertEquals("24:00", last.end.format())
    }

    @Test
    fun `co khung T3 17h-19h thi to dung hai dong`() {
        val groups = PriceTableBuilder.build(SeedPriceRules.truongChinh, tuesdayWindow)
        val highlighted = groups.flatMap { g -> g.rows.filter { it.highlighted }.map { g.title to it.ruleId } }
        assertEquals(
            listOf(DayGroupTitle.Range(MONDAY, FRIDAY) to "tc-1", DayGroupTitle.Range(MONDAY, FRIDAY) to "tc-2"),
            highlighted,
        )
    }

    @Test
    fun `chi to trong nhom chua ngay cua khung`() {
        // Thứ ba 05:00–07:00: dòng T2–T6 05:00–08:00 có ở nhóm "Thứ 2" và "Thứ 3 – Thứ 6" nhưng chỉ tô ở nhóm sau.
        val window = SearchWindow(LocalDate.of(2026, 10, 27), t("0500"), t("0700"))
        val groups = PriceTableBuilder.build(SeedPriceRules.quangVinh, window)
        assertTrue(groups[0].rows.none { it.highlighted })
        assertEquals(listOf("qv-0"), groups[1].rows.filter { it.highlighted }.map { it.ruleId })
    }

    @Test
    fun `khong co khung thi khong dong nao duoc to`() {
        val groups = PriceTableBuilder.build(SeedPriceRules.quangVinh, null)
        assertTrue(groups.all { g -> g.rows.none { it.highlighted } })
    }

    @Test
    fun `khong co rule thi danh sach rong`() {
        assertEquals(emptyList<Any>(), PriceTableBuilder.build(emptyList(), tuesdayWindow))
    }
}
