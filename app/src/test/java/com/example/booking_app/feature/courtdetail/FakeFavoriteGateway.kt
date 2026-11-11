package com.example.booking_app.feature.courtdetail

import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.feature.courtdetail.domain.FavoriteGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Yêu thích giả: ghi thành công thì cập nhật [favorite] như cache Firestore; [failNext] làm lần ghi sau lỗi. */
class FakeFavoriteGateway(initial: Boolean = false) : FavoriteGateway {

    val favorite = MutableStateFlow(initial)
    val calls = mutableListOf<Pair<VenueSummary, Boolean>>()
    var failNext: Throwable? = null
    var observeCalls = 0

    override fun observeIsFavorite(venueId: String): Flow<Boolean> {
        observeCalls++
        return favorite
    }

    override suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit> {
        calls += venue to favorite
        failNext?.let {
            failNext = null
            return Result.failure(it)
        }
        this.favorite.value = favorite
        return Result.success(Unit)
    }
}
