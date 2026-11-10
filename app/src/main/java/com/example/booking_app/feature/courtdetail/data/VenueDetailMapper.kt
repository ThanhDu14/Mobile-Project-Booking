package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.core.model.VenueRatingSummary
import com.example.booking_app.feature.courtdetail.domain.model.LatLng
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueCatalog
import com.example.booking_app.feature.courtdetail.domain.model.VenueDetail
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.google.firebase.firestore.GeoPoint

/** Đổi document `venues/{id}` sang [VenueResult] (data-model §1.1, research R10). Hàm thuần, có unit test. */
object VenueDetailMapper {

    fun map(
        id: String,
        data: Map<String, Any?>?,
        exists: Boolean,
        isFromCache: Boolean,
        catalog: VenueCatalog?,
        resolver: PhotoUrlResolver,
    ): VenueResult {
        if (!exists || data == null) return VenueResult.Gone
        val availability = availabilityOf(data["status"] as? String)
        if (availability == VenueAvailability.GONE) return VenueResult.Gone

        val districtId = data["districtId"] as? String
        val amenityIds = (data["amenityIds"] as? List<*>)?.filterIsInstance<String>().orEmpty().toSet()
        val photoPaths = (data["photoPaths"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            .filter { it.isNotBlank() }

        @Suppress("UNCHECKED_CAST")
        val ratingSums = (data["ratingSums"] as? Map<String, Any?>)
            ?.mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }
            ?.toMap()

        return VenueResult.Found(
            VenueDetail(
                id = id,
                name = data["name"] as? String ?: "",
                address = data["address"] as? String ?: "",
                districtName = catalog?.districts?.firstOrNull { it.id == districtId }?.name,
                location = (data["location"] as? GeoPoint)
                    ?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
                    ?.let { LatLng(it.latitude, it.longitude) },
                openTime = data["openTime"] as? String ?: "",
                closeTime = data["closeTime"] as? String ?: "",
                phone = (data["phone"] as? String)?.takeIf { it.isNotBlank() },
                amenities = catalog?.amenities?.filter { it.isActive && it.id in amenityIds },
                photoUrls = photoPaths.mapNotNull(resolver::venuePhoto),
                coverPhotoPath = photoPaths.firstOrNull(),
                ownerId = data["ownerId"] as? String ?: "",
                rating = VenueRatingSummary.fromVenue(
                    ratingAvg = (data["ratingAvg"] as? Number)?.toDouble(),
                    ratingCount = (data["ratingCount"] as? Number)?.toLong(),
                    ratingSums = ratingSums,
                ),
                availability = availability,
                isFromCache = isFromCache,
            ),
        )
    }

    /** Trạng thái lạ coi như tạm ngừng (không cho đặt). */
    fun availabilityOf(status: String?): VenueAvailability = when (status) {
        "ACTIVE" -> VenueAvailability.ACTIVE
        "DELETED" -> VenueAvailability.GONE
        else -> VenueAvailability.SUSPENDED
    }
}
