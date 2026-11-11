package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.pricing.DayConvention
import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.core.pricing.SlotPricing
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.courtdetail.domain.model.DayGroupTitle
import com.example.booking_app.feature.courtdetail.domain.model.PriceDayGroup
import com.example.booking_app.feature.courtdetail.domain.model.PriceLabel
import com.example.booking_app.feature.courtdetail.domain.model.PriceRow
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import java.time.DayOfWeek

/**
 * Bảng giá nhóm theo ngày (research R4): dựng danh sách dòng của từng ngày Thứ 2 → Chủ nhật, gộp các ngày liền
 * nhau có danh sách giống hệt. Dòng có giờ sai bị bỏ qua. Có khung giờ thì tô các dòng giao với khung, chỉ trong
 * nhóm chứa ngày của khung.
 */
object PriceTableBuilder {

    private data class Row(val start: TimeOfDay, val end: TimeOfDay, val label: PriceLabel, val price: Long, val ruleId: String)

    fun build(rules: List<PriceRule>, window: SearchWindow?): List<PriceDayGroup> {
        val byDay = DayOfWeek.entries.associateWith { day -> rowsOf(rules, day) }
        val highlightedIds = window
            ?.let { SlotPricing.rulesOverlapping(rules, it.date, it.start, it.end) }
            ?.map { it.id }?.toSet()
            .orEmpty()

        val groups = mutableListOf<MutableList<DayOfWeek>>()
        for (day in DayOfWeek.entries) {
            val rows = byDay.getValue(day)
            if (rows.isEmpty()) continue
            val last = groups.lastOrNull()
            if (last != null && last.last().value == day.value - 1 && byDay.getValue(last.last()) == rows) {
                last += day
            } else {
                groups += mutableListOf(day)
            }
        }
        return groups.map { days ->
            val inWindowDay = window != null && window.date.dayOfWeek in days
            PriceDayGroup(
                days = days.toList(),
                title = titleOf(days),
                rows = byDay.getValue(days.first()).map { r ->
                    PriceRow(r.start, r.end, r.label, r.price, inWindowDay && r.ruleId in highlightedIds, r.ruleId)
                },
            )
        }
    }

    private fun rowsOf(rules: List<PriceRule>, day: DayOfWeek): List<Row> {
        val value = DayConvention.fromDayOfWeek(day)
        return rules.filter { value in it.daysOfWeek }
            .mapNotNull { rule ->
                val start = TimeOfDay.parse(rule.startTime) ?: return@mapNotNull null
                val end = TimeOfDay.parseEnd(rule.endTime) ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                Row(start, end, PriceLabel.from(rule.label), rule.pricePerHour, rule.id)
            }
            .sortedWith(compareBy({ it.start }, { it.end }))
    }

    private fun titleOf(days: List<DayOfWeek>): DayGroupTitle = when (days.size) {
        1 -> DayGroupTitle.Single(days.single())
        2 -> DayGroupTitle.Pair(days[0], days[1])
        else -> DayGroupTitle.Range(days.first(), days.last())
    }
}
