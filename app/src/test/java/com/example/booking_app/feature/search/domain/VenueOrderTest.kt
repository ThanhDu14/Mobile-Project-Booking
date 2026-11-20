package com.example.booking_app.feature.search.domain

import com.example.booking_app.feature.search.domain.model.HomeSort
import com.example.booking_app.feature.search.domain.model.HomeVenue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 020 FR-011: Trang chủ và Tìm kiếm xếp các sân bằng điểm theo cùng một thứ tự. */
class VenueOrderTest {

    private fun venue(id: String, name: String, avg: Double = 4.0, count: Int = 5) =
        HomeVenue(id, name, districtName = null, photoUrl = null, minPricePerHour = null, ratingAvg = avg, ratingCount = count)

    @Test
    fun tiedVenuesAreOrderedByNameIgnoringCaseThenById() {
        val list = listOf(
            venue("a1", "sân Bình"), venue("z9", "Sân An"), venue("b2", "Sân Bình"), venue("c3", "Sân Bình"),
        )
        assertEquals(listOf("z9", "a1", "b2", "c3"), list.sortedWith(VenueOrder.byRating).map { it.id })
    }

    @Test
    fun homeAndSearchAgreeOnTiedVenues() {
        val list = listOf(
            venue("v3", "Sân Cầu"), venue("v1", "Sân Bóng"), venue("v2", "Sân An"),
            venue("v4", "Sân Mới", avg = 0.0, count = 0), venue("v5", "Sân Tốt", avg = 4.8, count = 2),
        )
        val home = HomeVenueSorter.sort(list, HomeSort.RATING).map { it.id }
        assertEquals(listOf("v5", "v2", "v1", "v3", "v4"), home)
        assertEquals(home, list.sortedWith(VenueOrder.byRating).map { it.id })
    }
}
