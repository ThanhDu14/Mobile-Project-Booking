package com.example.booking_app.feature.courtdetail

import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.model.VenueRatingSummary
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.feature.courtdetail.domain.model.LatLng
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

/** Mốc giờ Việt Nam đổi sang epoch millis cho `FakeClock`. */
fun vnMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(BookingWindow.ZONE).toInstant().toEpochMilli()

fun testVenue(
    id: String = "demo-truong-chinh-909",
    openTime: String = "0500",
    closeTime: String = "2400",
    amenities: List<Amenity>? = emptyList(),
    availability: VenueAvailability = VenueAvailability.ACTIVE,
    ownerId: String = "demo-owner-1",
    phone: String? = "0944xxx486",
    location: LatLng? = LatLng(10.8128, 106.6283),
    rating: VenueRatingSummary = VenueRatingSummary.EMPTY,
    isFromCache: Boolean = false,
    photoUrls: List<String> = listOf("https://a/1.jpg", "https://a/2.jpg", "https://a/3.jpg"),
) = VenueDetail(
    id = id,
    name = "CLB Cầu Lông Trường Chinh 909",
    address = "909/6 Trường Chinh",
    districtName = "Tân Phú",
    location = location,
    openTime = openTime,
    closeTime = closeTime,
    phone = phone,
    amenities = amenities,
    photoUrls = photoUrls,
    coverPhotoPath = photoUrls.firstOrNull(),
    ownerId = ownerId,
    rating = rating,
    availability = availability,
    isFromCache = isFromCache,
)

class FakeCatalogRepository(
    var districtList: List<District> = listOf(District("tan-phu", "Tân Phú", 16, true)),
    var amenityList: List<Amenity> = listOf(Amenity("parking", "Gửi xe", "parking", 1, true)),
) : CatalogRepository {
    var fail: Throwable? = null
    override val districtsFlow: StateFlow<List<District>?> = MutableStateFlow(null)
    override val amenitiesFlow: StateFlow<List<Amenity>?> = MutableStateFlow(null)
    override suspend fun districts(): List<District> = fail?.let { throw it } ?: districtList
    override suspend fun amenities(): List<Amenity> = fail?.let { throw it } ?: amenityList
    override suspend fun isActiveDistrict(id: String) = districts().any { it.id == id && it.isActive }
    override suspend fun isActiveAmenity(id: String) = amenities().any { it.id == id && it.isActive }
}
