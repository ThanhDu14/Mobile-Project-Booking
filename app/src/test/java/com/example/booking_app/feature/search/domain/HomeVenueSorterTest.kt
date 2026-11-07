package com.example.booking_app.feature.search.domain

import com.example.booking_app.feature.search.domain.model.HomeSort
import com.example.booking_app.feature.search.domain.model.HomeVenue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 020 FR-011, research R7: thứ tự của Trang chủ. */
class HomeVenueSorterTest {

    private fun venue(id: String, avg: Double = 0.0, count: Int = 0, price: Long? = null, name: String = id) =
        HomeVenue(id, name, districtName = null, photoUrl = null, minPricePerHour = price, ratingAvg = avg, ratingCount = count)

    @Test
    fun `danh gia cao nhat, cung diem thi nhieu luot hon dung truoc, chua co danh gia dung cuoi`() {
        val list = listOf(venue("a", 4.5, 10), venue("b", 0.0, 0), venue("c", 4.8, 3), venue("d", 4.5, 40))
        assertEquals(listOf("c", "d", "a", "b"), HomeVenueSorter.sort(list, HomeSort.RATING).map { it.id })
    }

    @Test
    fun `cung diem cung luot thi theo ten de thu tu on dinh`() {
        val list = listOf(venue("x", 4.0, 5, name = "Sân B"), venue("y", 4.0, 5, name = "Sân A"))
        assertEquals(listOf("y", "x"), HomeVenueSorter.sort(list, HomeSort.RATING).map { it.id })
    }

    @Test
    fun `gia thap den cao, chua co gia dung cuoi theo danh gia`() {
        val list = listOf(
            venue("p80", price = 80_000), venue("none-low", avg = 3.0, count = 1), venue("p60", price = 60_000),
            venue("none-high", avg = 4.9, count = 9), venue("p60b", 4.9, 20, price = 60_000),
        )
        assertEquals(
            listOf("p60b", "p60", "p80", "none-high", "none-low"),
            HomeVenueSorter.sort(list, HomeSort.PRICE).map { it.id },
        )
    }
}
