package com.example.booking_app.feature.search.data

import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.feature.search.domain.model.HomeVenue

/** Đổi document `venues/{id}` sang [HomeVenue]. Hàm thuần, có unit test. */
object HomeVenueMapper {

    fun map(id: String, data: Map<String, Any?>, districts: List<District>, resolver: PhotoUrlResolver): HomeVenue {
        val districtId = data["districtId"] as? String
        val firstPhoto = (data["photoPaths"] as? List<*>)?.filterIsInstance<String>()?.firstOrNull { it.isNotBlank() }
        return HomeVenue(
            id = id,
            name = data["name"] as? String ?: "",
            districtName = districts.firstOrNull { it.id == districtId }?.name,
            photoUrl = resolver.venuePhoto(firstPhoto),
            minPricePerHour = (data["minPricePerHour"] as? Number)?.toLong()?.takeIf { it > 0 },
            ratingAvg = (data["ratingAvg"] as? Number)?.toDouble() ?: 0.0,
            ratingCount = (data["ratingCount"] as? Number)?.toInt() ?: 0,
        )
    }
}
