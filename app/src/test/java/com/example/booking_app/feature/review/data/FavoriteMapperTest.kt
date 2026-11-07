package com.example.booking_app.feature.review.data

import com.example.booking_app.feature.review.data.dto.FavoriteDto
import com.example.booking_app.feature.review.data.dto.FavoriteVenueStateDto
import com.example.booking_app.feature.review.data.dto.toDomain
import com.example.booking_app.feature.review.domain.model.FavoriteVenue.Availability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Trạng thái sân trong danh sách yêu thích (FR-024, research R6). */
class FavoriteMapperTest {

    private val dto = FavoriteDto("v1", "Tên lúc lưu", "v1/a.jpg", "Địa chỉ lúc lưu", null)
    private val keepPath: (String?) -> String? = { it }

    @Test
    fun `san ACTIVE - lay ten, anh, diem moi nhat`() {
        val venue = FavoriteVenueStateDto(true, "ACTIVE", "Tên mới", "v1/b.jpg", "Địa chỉ mới", 4.2)
        val result = dto.toDomain(venue, keepPath)
        assertEquals(Availability.ACTIVE, result.availability)
        assertEquals("Tên mới", result.name)
        assertEquals("v1/b.jpg", result.photoUrl)
        assertEquals(4.2, result.ratingAvg!!, 0.0)
    }

    @Test
    fun `san HIDDEN, LOCKED, DELETED - SUSPENDED va giu snapshot`() {
        listOf("HIDDEN", "LOCKED", "DELETED").forEach { status ->
            val result = dto.toDomain(FavoriteVenueStateDto(true, status, "Tên mới", null, null, 4.0), keepPath)
            assertEquals(status, Availability.SUSPENDED, result.availability)
            assertEquals("Tên lúc lưu", result.name)
            assertNull(result.ratingAvg)
        }
    }

    @Test
    fun `san khong ton tai - GONE, hien tu snapshot`() {
        val result = dto.toDomain(FavoriteVenueStateDto(exists = false), keepPath)
        assertEquals(Availability.GONE, result.availability)
        assertEquals("Tên lúc lưu", result.name)
        assertEquals("Địa chỉ lúc lưu", result.address)
    }

    @Test
    fun `khong doc duoc san (offline) - hien snapshot, coi nhu dang hoat dong`() {
        val result = dto.toDomain(null, keepPath)
        assertEquals(Availability.ACTIVE, result.availability)
        assertEquals("Tên lúc lưu", result.name)
        assertNull(result.ratingAvg)
    }

    @Test
    fun `path anh ghep thanh URL public, URL http giu nguyen`() {
        assertEquals(
            "http://10.0.2.2:54321/storage/v1/object/public/venue-photos/v1/a.jpg",
            ReviewPhotoUrls.venuePhoto("v1/a.jpg", baseUrl = "http://10.0.2.2:54321/"),
        )
        assertEquals("https://x.com/a.jpg", ReviewPhotoUrls.venuePhoto("https://x.com/a.jpg", baseUrl = "http://h"))
        assertNull(ReviewPhotoUrls.venuePhoto(null, baseUrl = "http://h"))
    }
}
