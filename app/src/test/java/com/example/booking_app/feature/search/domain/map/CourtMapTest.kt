package com.example.booking_app.feature.search.domain.map

import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logic bản đồ sân (spec 021 FR-002, FR-003, FR-006, FR-008, FR-016). Tọa độ lấy từ dữ liệu demo. */
class CourtMapTest {
    private fun venue(id: String, lat: Double?, lng: Double?) =
        HomeVenue(id, id, null, null, 50_000, 0.0, 0, position = if (lat != null && lng != null) LatLng(lat, lng) else null)

    private val truongChinh = venue("909", 10.8128, 106.6283)   // Tân Phú
    private val k3 = venue("k3", 10.8020, 106.6530)             // Tân Bình
    private val pam = venue("pam", 10.8640, 106.6600)           // Quận 12
    private val noLocation = venue("none", null, null)
    private val tanPhuView = MapViewport(north = 10.83, south = 10.79, east = 106.67, west = 106.60, zoom = 14.0)

    @Test
    fun onlyVenuesWithAPositionInsideTheViewportAreShown() {
        val shown = CourtMap.visible(listOf(truongChinh, k3, pam, noLocation), tanPhuView)
        assertEquals(listOf("909", "k3"), shown.venues.map { it.id })
        assertFalse(shown.capped)
    }

    @Test
    fun tooManyVenuesKeepTheOnesNearestTheCenter() {
        val center = tanPhuView.center
        val crowd = (1..5).map { i -> venue("v$i", center.lat + i * 0.001, center.lng) }
        val shown = CourtMap.visible(crowd.reversed(), tanPhuView, limit = 3)
        assertTrue(shown.capped)
        assertEquals(listOf("v1", "v2", "v3"), shown.venues.map { it.id })
    }

    @Test
    fun cardsStartWithTheSelectedVenueThenNearestToIt() {
        val order = CourtMap.cardOrder(listOf(pam, k3, truongChinh, noLocation), selectedId = "909")
        assertEquals(listOf("909", "k3", "pam", "none"), order.map { it.id })
    }

    @Test
    fun nearbyMarkersMergeIntoClustersAndFarOnesStayApart() {
        val clusters = CourtMap.cluster(
            listOf(ScreenPoint("a", 100f, 100f), ScreenPoint("b", 120f, 110f), ScreenPoint("c", 400f, 100f),
                ScreenPoint("d", 105f, 140f)),
            radiusPx = 48f,
        )
        assertEquals(listOf(listOf("a", "b", "d"), listOf("c")), clusters.map { it.venueIds })
        assertEquals(100f, clusters[0].x)
        assertTrue(clusters[1].isSingle)
        assertTrue(CourtMap.cluster(emptyList(), 48f).isEmpty())
    }

    @Test
    fun nearbyKeepsUpToTenWithinFiveKilometresNearestFirst() {
        val origin = LatLng(10.8128, 106.6283)
        val near = CourtMap.nearby(listOf(pam, k3, truongChinh, noLocation), origin)
        // PAM ở Quận 12 cách khoảng 6 km: ngoài bán kính 5 km.
        assertEquals(listOf("909", "k3"), near.map { it.id })
        assertEquals(0.0, near[0].distanceMeters!!, 1.0)
        assertTrue(near[1].distanceMeters!! in 2_000.0..3_500.0)

        val many = (1..12).map { venue("n$it", origin.lat + it * 0.0005, origin.lng) }
        assertEquals((1..10).map { "n$it" }, CourtMap.nearby(many, origin).map { it.id })
    }

    @Test
    fun widerSearchZoomsOutJustEnoughToReachTheNearestVenue() {
        // Dĩ An → sân gần nhất ở Quận 12 khoảng 13 km: phóng xa hơn mức 12 cố định trước đây.
        val far = CourtMap.zoomToShow(13_000.0, 10.9)
        assertTrue(far in 10.0..11.5)
        // Càng gần thì phóng càng gần, nhưng không vượt mức 13,5.
        assertTrue(CourtMap.zoomToShow(3_000.0, 10.8) > far)
        assertEquals(13.5, CourtMap.zoomToShow(100.0, 10.8), 0.0)
        assertEquals(10.0, CourtMap.zoomToShow(200_000.0, 10.8), 0.0)
    }
}
