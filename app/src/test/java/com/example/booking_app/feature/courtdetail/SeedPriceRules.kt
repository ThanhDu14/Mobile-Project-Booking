package com.example.booking_app.feature.courtdetail

import com.example.booking_app.core.pricing.PriceRule

/** Bảng giá của `scripts/seed/venues.json` đổi sang `daysOfWeek` ISO (1 = Thứ 2 … 7 = Chủ nhật). */
object SeedPriceRules {
    private val WEEKDAYS = listOf(1, 2, 3, 4, 5)
    private val WEEKEND = listOf(6, 7)

    private fun rules(venue: String, vararg rows: Triple<List<Int>, Pair<String, String>, Long>) =
        rows.mapIndexed { i, (days, time, price) ->
            PriceRule("$venue-$i", days, time.first, time.second, price, "NORMAL")
        }

    val truongChinh = rules(
        "tc",
        Triple(WEEKDAYS, "0500" to "1600", 50_000),
        Triple(WEEKDAYS, "1600" to "1800", 90_000),
        Triple(WEEKDAYS, "1800" to "2400", 130_000),
        Triple(WEEKEND, "0500" to "2400", 100_000),
    )

    val quangVinh = rules(
        "qv",
        Triple(WEEKDAYS, "0500" to "0800", 70_000),
        Triple(WEEKDAYS, "0800" to "1600", 50_000),
        Triple(listOf(1), "1600" to "2300", 80_000),
        Triple(listOf(2, 3, 4, 5), "1600" to "2300", 90_000),
        Triple(WEEKEND, "0500" to "1400", 80_000),
        Triple(WEEKEND, "1400" to "2300", 90_000),
    )

    val sonTa = rules(
        "st",
        Triple(WEEKDAYS, "0000" to "0500", 80_000),
        Triple(WEEKDAYS, "0500" to "1600", 40_000),
        Triple(WEEKDAYS, "1600" to "1800", 80_000),
        Triple(WEEKDAYS, "1800" to "2200", 100_000),
        Triple(WEEKDAYS, "2200" to "2400", 80_000),
        Triple(listOf(6), "0000" to "0500", 80_000),
        Triple(listOf(6), "0500" to "1600", 70_000),
        Triple(listOf(6), "1600" to "2400", 90_000),
        Triple(listOf(7), "0000" to "2400", 90_000),
    )
}
