package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.feature.courtdetail.domain.FavoriteGateway
import com.example.booking_app.feature.review.domain.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Gọi thẳng `FavoriteRepository` của 070 (`users/{uid}/favorites/{venueId}`, cache offline của Firestore). */
class ReviewFavoriteGateway @Inject constructor(
    private val repository: FavoriteRepository,
) : FavoriteGateway {

    override fun observeIsFavorite(venueId: String): Flow<Boolean> = repository.observeIsFavorite(venueId)

    override suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit> =
        repository.setFavorite(venue, favorite)
}
