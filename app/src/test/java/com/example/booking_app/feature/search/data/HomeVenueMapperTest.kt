package com.example.booking_app.feature.search.data

import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.feature.search.domain.model.HomeVenue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeVenueMapperTest {

    private val resolver = PhotoUrlResolver("https://x.supabase.co")
    private val districts = listOf(District("tan-phu", "Tân Phú", 1, true))

    @Test
    fun `doc du truong, anh dau tien qua resolver, ten quan tu danh muc`() {
        val data = mapOf(
            "name" to "CLB 909", "districtId" to "tan-phu", "photoPaths" to listOf("", "v1/a.jpg", "v1/b.jpg"),
            "minPricePerHour" to 60_000L, "ratingAvg" to 4.6, "ratingCount" to 128L,
        )
        assertEquals(
            HomeVenue(
                id = "v1", name = "CLB 909", districtName = "Tân Phú",
                photoUrl = "https://x.supabase.co/storage/v1/object/public/venue-photos/v1/a.jpg",
                minPricePerHour = 60_000L, ratingAvg = 4.6, ratingCount = 128,
            ),
            HomeVenueMapper.map("v1", data, districts, resolver),
        )
    }

    @Test
    fun `thieu truong thi mac dinh an toan`() {
        val venue = HomeVenueMapper.map("v2", mapOf("name" to "Sân 2", "districtId" to "khac"), districts, resolver)
        assertNull(venue.districtName)
        assertNull(venue.photoUrl)
        assertNull(venue.minPricePerHour)
        assertEquals(0.0, venue.ratingAvg, 0.0)
        assertEquals(0, venue.ratingCount)
    }

    @Test
    fun `gia khong duong thi coi nhu chua co gia`() {
        assertNull(HomeVenueMapper.map("v3", mapOf("minPricePerHour" to 0L), districts, resolver).minPricePerHour)
    }
}
