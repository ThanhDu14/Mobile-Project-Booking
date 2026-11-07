package com.example.booking_app.feature.search.domain

import com.example.booking_app.feature.search.domain.model.HomeSort
import com.example.booking_app.feature.search.domain.model.HomeVenue

/**
 * Thứ tự của Trang chủ theo research R7: đánh giá giảm dần, cùng điểm thì nhiều lượt hơn trước; giá tăng dần, cơ sở
 * chưa có giá đứng cuối theo đánh giá. Cuối cùng theo tên để thứ tự ổn định giữa các lần tải.
 */
object HomeVenueSorter {

    private val byRating: Comparator<HomeVenue> =
        compareByDescending<HomeVenue> { it.ratingAvg }.thenByDescending { it.ratingCount }.thenBy { it.name }

    private val byPrice: Comparator<HomeVenue> =
        compareBy<HomeVenue> { it.minPricePerHour == null }.thenBy { it.minPricePerHour ?: 0L }.then(byRating)

    fun sort(venues: List<HomeVenue>, sort: HomeSort): List<HomeVenue> = when (sort) {
        HomeSort.RATING -> venues.sortedWith(byRating)
        HomeSort.PRICE -> venues.sortedWith(byPrice)
    }
}
