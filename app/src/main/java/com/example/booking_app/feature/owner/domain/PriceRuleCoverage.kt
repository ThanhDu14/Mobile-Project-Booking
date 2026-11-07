package com.example.booking_app.feature.owner.domain

import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule

/** Read-only pricing helpers; booking confirmation remains owned by Features 040/050. */
object PriceRuleCoverage {
    fun pricePerSlot(pricePerHour: Long): Long = pricePerHour / 2

    fun ruleFor(dayOfWeek: Int, startTime: String, rules: List<OwnerPriceRule>): OwnerPriceRule? =
        rules.firstOrNull { dayOfWeek in it.daysOfWeek && it.startTime <= startTime && startTime < it.endTime }

    fun missingHalfHourStarts(dayOfWeek: Int, openTime: String, closeTime: String, rules: List<OwnerPriceRule>): List<String> {
        val start = minutes(openTime)
        val end = minutes(closeTime)
        if (start < 0 || end <= start) return emptyList()
        return (start until end step 30).map(::formatTime).filter { ruleFor(dayOfWeek, it, rules) == null }
    }

    fun deletionLeavesCoverageGap(ruleId: String, dayOfWeek: Int, openTime: String, closeTime: String, rules: List<OwnerPriceRule>): Boolean =
        missingHalfHourStarts(dayOfWeek, openTime, closeTime, rules.filterNot { it.id == ruleId }).isNotEmpty()

    private fun minutes(value: String): Int =
        if (value.length == 4 && value.all(Char::isDigit)) value.substring(0, 2).toInt() * 60 + value.substring(2).toInt() else -1

    private fun formatTime(value: Int) = "%02d%02d".format(value / 60, value % 60)
}
