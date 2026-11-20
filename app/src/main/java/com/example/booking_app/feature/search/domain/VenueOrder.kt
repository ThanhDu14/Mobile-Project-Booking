package com.example.booking_app.feature.search.domain

import com.example.booking_app.feature.search.domain.model.HomeVenue

/**
 * Thứ tự theo đánh giá dùng chung cho Trang chủ và Tìm kiếm (spec 020 FR-011, research R7): sân đã có đánh giá
 * trước, điểm giảm dần, cùng điểm thì nhiều lượt hơn trước. Cuối cùng theo tên (không phân biệt hoa thường) rồi
 * theo id, để hai màn hình xếp các sân bằng điểm giống nhau và thứ tự không đổi giữa các lần tải.
 */
object VenueOrder {

    val byRating: Comparator<HomeVenue> =
        compareByDescending<HomeVenue> { it.ratingCount > 0 }
            .thenByDescending { it.ratingAvg }
            .thenByDescending { it.ratingCount }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id }
}
