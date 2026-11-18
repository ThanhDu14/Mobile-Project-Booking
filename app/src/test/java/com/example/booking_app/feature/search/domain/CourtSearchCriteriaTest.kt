package com.example.booking_app.feature.search.domain

import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Tiêu chí tìm sân (spec 020 T025, T040, T057): khi nào là "có lọc", lọc giá, so khớp tên không dấu. */
class CourtSearchCriteriaTest {

    @Test
    fun sortAloneOrAOneLetterKeywordIsNotAFilter() {
        assertFalse(CourtSearchCriteria().hasFilters())
        assertFalse(CourtSearchCriteria(sort = CourtSearchSort.PRICE_ASC).hasFilters())
        assertFalse(CourtSearchCriteria(keyword = " a ").hasFilters())
        assertTrue(CourtSearchCriteria(keyword = "mi").hasFilters())
        assertTrue(CourtSearchCriteria(districtId = "q10").hasFilters())
        assertTrue(CourtSearchCriteria(priceMax = 100_000).hasFilters())
        assertTrue(CourtSearchCriteria(amenityIds = setOf("parking")).hasFilters())
        val window = BookingSearchWindow(LocalDate.of(2026, 11, 19), TimeOfDay(19 * 60), TimeOfDay(21 * 60))
        assertTrue(CourtSearchCriteria(window = window).hasFilters())
    }

    @Test
    fun priceFilterIsInclusiveAndExcludesVenuesWithoutAPrice() {
        assertTrue(CourtSearchCriteria().acceptsPrice(null))
        val range = CourtSearchCriteria(priceMin = 50_000, priceMax = 100_000)
        assertTrue(range.acceptsPrice(50_000))
        assertTrue(range.acceptsPrice(100_000))
        assertFalse(range.acceptsPrice(49_999))
        assertFalse(range.acceptsPrice(100_001))
        // Sân "Liên hệ" (chưa có giá) không lọt vào khi đang lọc giá.
        assertFalse(range.acceptsPrice(null))
        assertTrue(CourtSearchCriteria(priceMax = 40_000).acceptsPrice(40_000))
        assertTrue(CourtSearchCriteria(priceMin = 60_000).acceptsPrice(200_000))
    }

    @Test
    fun namesMatchWithoutAccentsByWordPrefix() {
        val name = "Sân Cầu Lông Minh Tâm CN1 - Nguyễn Văn Quá"
        assertTrue(matchesVenueName("minh tam", name))
        assertTrue(matchesVenueName("MINH TÂM", name))
        assertTrue(matchesVenueName("ngu van", name))      // mỗi từ khóa là tiền tố của một từ
        assertTrue(matchesVenueName("cn1", name))
        assertFalse(matchesVenueName("inh tam", name))     // không khớp giữa từ
        assertFalse(matchesVenueName("minh hoa", name))     // mọi từ khóa đều phải khớp
        assertTrue(matchesVenueName("dong", "Sân Đông Hưng")) // đ → d
        assertTrue(matchesVenueName("x", name))             // dưới 2 ký tự: không lọc
        assertEquals("san cau long minh tam cn1 nguyen van qua", normalizedSearchText(name))
    }

    @Test
    fun pricesAreRoundedToWholeThousandsWithoutHidingWantedVenues() {
        // "dưới 55k5" và giá gõ tay lẻ: trần làm tròn lên, sàn làm tròn xuống (Rules lịch sử chỉ nhận bội số 1.000).
        val rounded = CourtSearchCriteria(priceMin = 40_500, priceMax = 55_500).withRoundedPrices()
        assertEquals(40_000L, rounded.priceMin)
        assertEquals(56_000L, rounded.priceMax)
        assertEquals(null, CourtSearchCriteria(priceMin = 500).withRoundedPrices().priceMin)
        assertEquals(1_000L, CourtSearchCriteria(priceMax = 1).withRoundedPrices().priceMax)
        val whole = CourtSearchCriteria(priceMin = 50_000, priceMax = 100_000)
        assertTrue(whole.withRoundedPrices() === whole)
    }
}
