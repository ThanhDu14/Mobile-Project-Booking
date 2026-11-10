package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.feature.courtdetail.domain.model.LatLng
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueCatalog
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VenueDetailMapperTest {

    private val resolver = PhotoUrlResolver("http://supabase.local")
    private val catalog = VenueCatalog(
        districts = listOf(District("tan-binh", "Tân Bình", 15, true)),
        amenities = listOf(
            Amenity("parking", "Gửi xe", "parking", 1, true),
            Amenity("drinks", "Nước uống", "drinks", 2, true),
            Amenity("sauna", "Xông hơi", "sauna", 3, false),
        ),
    )

    private fun venue(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "name" to "Sân cầu lông K3",
        "address" to "147 Nguyễn Đức Thuận",
        "districtId" to "tan-binh",
        "location" to GeoPoint(10.8057, 106.6412),
        "openTime" to "0400",
        "closeTime" to "2400",
        "phone" to "0916xxx188",
        "amenityIds" to listOf("drinks", "parking"),
        "photoPaths" to listOf("https://m-files.alobo.vn/a.jpg", "demo-k3/b.jpg"),
        "ownerId" to "demo-owner-1",
        "ratingAvg" to 4.5,
        "ratingCount" to 2L,
        "ratingSums" to mapOf("court" to 9L, "cleanliness" to 8L, "service" to 10L),
        "status" to "ACTIVE",
    ) + overrides

    private fun found(data: Map<String, Any?>, fromCache: Boolean = false, cat: VenueCatalog? = catalog) =
        (VenueDetailMapper.map("demo-k3", data, exists = true, isFromCache = fromCache, catalog = cat, resolver = resolver)
            as VenueResult.Found).venue

    @Test
    fun `status ACTIVE, HIDDEN, LOCKED`() {
        assertEquals(VenueAvailability.ACTIVE, found(venue()).availability)
        assertEquals(VenueAvailability.SUSPENDED, found(venue("status" to "HIDDEN")).availability)
        assertEquals(VenueAvailability.SUSPENDED, found(venue("status" to "LOCKED")).availability)
    }

    @Test
    fun `DELETED hoac khong ton tai la Gone`() {
        assertEquals(
            VenueResult.Gone,
            VenueDetailMapper.map("x", venue("status" to "DELETED"), true, false, catalog, resolver),
        )
        assertEquals(VenueResult.Gone, VenueDetailMapper.map("x", null, false, false, catalog, resolver))
    }

    @Test
    fun `photoPaths giu thu tu, bo path rong, URL day du giu nguyen`() {
        val v = found(venue("photoPaths" to listOf("https://m-files.alobo.vn/a.jpg", " ", "demo-k3/b.jpg", "")))
        assertEquals(
            listOf("https://m-files.alobo.vn/a.jpg", "http://supabase.local/storage/v1/object/public/venue-photos/demo-k3/b.jpg"),
            v.photoUrls,
        )
        assertEquals("https://m-files.alobo.vn/a.jpg", v.coverPhotoPath)
    }

    @Test
    fun `amenityIds khong co hoac da tat trong danh muc bi bo, giu thu tu danh muc`() {
        val v = found(venue("amenityIds" to listOf("drinks", "khong-co", "sauna", "parking")))
        assertEquals(listOf("parking", "drinks"), v.amenities!!.map { it.id })
    }

    @Test
    fun `danh muc loi thi amenities null va khong co ten quan`() {
        val v = found(venue(), cat = null)
        assertNull(v.amenities)
        assertNull(v.districtName)
    }

    @Test
    fun `ten quan lay tu danh muc, quan la thi null`() {
        assertEquals("Tân Bình", found(venue()).districtName)
        assertNull(found(venue("districtId" to "q99")).districtName)
    }

    @Test
    fun `location thieu hoac 0,0 la null`() {
        assertEquals(LatLng(10.8057, 106.6412), found(venue()).location)
        assertNull(found(venue("location" to null)).location)
        assertNull(found(venue("location" to GeoPoint(0.0, 0.0))).location)
    }

    @Test
    fun `ratingSums thieu thi tieu chi null`() {
        val withSums = found(venue()).rating
        assertEquals(4.5, withSums.courtAvg!!, 0.0)
        val noSums = found(venue("ratingSums" to null)).rating
        assertEquals(2, noSums.count)
        assertNull(noSums.courtAvg)
    }

    @Test
    fun `isFromCache truyen ra VenueDetail`() {
        assertTrue(found(venue(), fromCache = true).isFromCache)
    }

    @Test
    fun `cac truong co ban`() {
        val v = found(venue())
        assertEquals("demo-k3", v.id)
        assertEquals("Sân cầu lông K3", v.name)
        assertEquals("0400", v.openTime)
        assertEquals("2400", v.closeTime)
        assertEquals("0916xxx188", v.phone)
        assertEquals("demo-owner-1", v.ownerId)
    }
}
