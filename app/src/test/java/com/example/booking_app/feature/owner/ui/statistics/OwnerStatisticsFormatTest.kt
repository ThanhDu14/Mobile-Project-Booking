package com.example.booking_app.feature.owner.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerStatisticsFormatTest {
    @Test fun `percent uses Vietnamese decimal comma and drops trailing zero`() {
        assertEquals("0,1%", OwnerStatisticsFragment.formatPercent(0.1))
        assertEquals("62%", OwnerStatisticsFragment.formatPercent(62.0))
        assertEquals("12,5%", OwnerStatisticsFragment.formatPercent(12.46))
    }

    @Test fun `occupancy bar shows at least one step when anything is booked`() {
        assertEquals(0, OwnerStatisticsFragment.occupancyBar(0.0))
        assertEquals(1, OwnerStatisticsFragment.occupancyBar(0.1))
        assertEquals(100, OwnerStatisticsFragment.occupancyBar(100.0))
    }

    @Test fun `chart keeps a fixed day range and widens for early or late hours`() {
        assertEquals(6..22, PeakHoursChartView.rangeFor(listOf(8)))
        assertEquals(6..22, PeakHoursChartView.rangeFor(emptyList()))
        assertEquals(4..23, PeakHoursChartView.rangeFor(listOf(4, 23)))
    }
}
