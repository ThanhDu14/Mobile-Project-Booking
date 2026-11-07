package com.example.booking_app.feature.review

import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.feature.review.domain.FavoriteRepository
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Repository giả: [favorites] là danh sách trên server, [loadError] làm `observeFavorites` lỗi. */
class FakeFavoriteRepository : FavoriteRepository {
    val favorites = MutableStateFlow<List<FavoriteVenue>>(emptyList())
    var loadError: Throwable? = null
    var setResult: Result<Unit> = Result.success(Unit)
    val setCalls = mutableListOf<Pair<String, Boolean>>()
    val requestedLimits = mutableListOf<Int>()

    override fun observeIsFavorite(venueId: String): Flow<Boolean> =
        favorites.map { list -> list.any { it.venueId == venueId } }

    override fun observeFavorites(limit: Int): Flow<List<FavoriteVenue>> = flow {
        requestedLimits += limit
        loadError?.let { throw it }
        favorites.collect { emit(it.take(limit)) }
    }

    override suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit> {
        setCalls += venue.venueId to favorite
        if (setResult.isSuccess && !favorite) {
            favorites.value = favorites.value.filterNot { it.venueId == venue.venueId }
        }
        return setResult
    }
}

fun favoriteVenue(
    id: String,
    availability: FavoriteVenue.Availability = FavoriteVenue.Availability.ACTIVE,
) = FavoriteVenue(
    venueId = id,
    name = "Sân $id",
    photoUrl = null,
    address = "Địa chỉ $id",
    ratingAvg = 4.5,
    availability = availability,
)
