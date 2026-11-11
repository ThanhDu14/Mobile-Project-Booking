package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.model.VenueSummary
import kotlinx.coroutines.flow.Flow

/**
 * Cổng tới tính năng yêu thích của 070 (contracts/navigation.md §3). 030 chỉ phụ thuộc interface này để test
 * ViewModel bằng bản giả; bản thật là [com.example.booking_app.feature.courtdetail.data.ReviewFavoriteGateway].
 */
interface FavoriteGateway {

    /** Chỉ gọi khi đã đăng nhập. */
    fun observeIsFavorite(venueId: String): Flow<Boolean>

    suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit>
}
