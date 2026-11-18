package com.example.booking_app.feature.search.domain.map

import com.example.booking_app.core.location.GeoDistance
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.domain.model.LatLng
import com.google.firebase.firestore.GeoPoint
import kotlin.math.cos
import kotlin.math.log2

/**
 * Vùng bản đồ đang xem (spec 021). [north] ≥ [south]; vùng không vắt qua kinh tuyến 180 (TP.HCM), nên [west] ≤ [east].
 */
data class MapViewport(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val zoom: Double,
) {
    val center: LatLng get() = LatLng((north + south) / 2, (east + west) / 2)

    fun contains(point: LatLng): Boolean = point.lat in south..north && point.lng in west..east
}

/** Kết quả chọn sân cho vùng đang xem: [capped] khi vùng có nhiều hơn giới hạn (FR-006). */
data class VisibleVenues(val venues: List<HomeVenue>, val capped: Boolean)

/** Một cụm marker trên màn hình: [venueIds] theo thứ tự đầu vào, tâm tại điểm đầu tiên (FR-003). */
data class MarkerCluster(val venueIds: List<String>, val x: Float, val y: Float) {
    val isSingle: Boolean get() = venueIds.size == 1
}

/** Điểm của một sân trên màn hình (pixel), do lớp giao diện chiếu từ tọa độ theo mức phóng hiện tại. */
data class ScreenPoint(val venueId: String, val x: Float, val y: Float)

object CourtMap {
    /** Trung tâm TP.HCM, mức phóng thấy được vài quận (Clarifications 2026-10-06). */
    val DEFAULT_CENTER = LatLng(10.7769, 106.7009)
    const val DEFAULT_ZOOM = 12.0
    const val FOCUS_ZOOM = 15.0
    const val MAX_VENUES = 100
    const val NEARBY_RADIUS_METERS = 5_000.0
    const val NEARBY_LIMIT = 10

    fun meters(a: LatLng, b: LatLng): Double =
        GeoDistance.meters(GeoPoint(a.lat, a.lng), GeoPoint(b.lat, b.lng))

    /** Sân có tọa độ nằm trong [viewport]; quá [limit] thì giữ các sân gần tâm vùng nhất (FR-002, FR-006). */
    fun visible(venues: List<HomeVenue>, viewport: MapViewport, limit: Int = MAX_VENUES): VisibleVenues {
        val inside = venues.filter { venue -> venue.position?.let(viewport::contains) == true }
        if (inside.size <= limit) return VisibleVenues(inside, capped = false)
        val center = viewport.center
        return VisibleVenues(inside.sortedBy { meters(center, it.position!!) }.take(limit), capped = true)
    }

    /** Thứ tự thẻ khi vuốt: sân đang chọn trước, các sân còn lại theo khoảng cách tới nó (FR-008). */
    fun cardOrder(venues: List<HomeVenue>, selectedId: String): List<HomeVenue> {
        val selected = venues.firstOrNull { it.id == selectedId }?.position ?: return venues
        return venues.sortedWith(
            compareBy<HomeVenue> { it.id != selectedId }.thenBy { venue ->
                venue.position?.let { meters(selected, it) } ?: Double.MAX_VALUE
            },
        )
    }

    /**
     * Gộp các marker gần nhau trên màn hình: mỗi điểm vào cụm đầu tiên có tâm cách nó không quá [radiusPx], không có
     * thì mở cụm mới tại chính nó. Kết quả ổn định theo thứ tự đầu vào (FR-003).
     */
    fun cluster(points: List<ScreenPoint>, radiusPx: Float): List<MarkerCluster> {
        val clusters = mutableListOf<Pair<ScreenPoint, MutableList<String>>>()
        val radius2 = radiusPx * radiusPx
        for (point in points) {
            val target = clusters.firstOrNull { (center, _) ->
                val dx = center.x - point.x
                val dy = center.y - point.y
                dx * dx + dy * dy <= radius2
            }
            if (target != null) target.second += point.venueId else clusters += point to mutableListOf(point.venueId)
        }
        return clusters.map { (center, ids) -> MarkerCluster(ids, center.x, center.y) }
    }

    /**
     * Mức phóng để một điểm cách tâm [distanceMeters] vẫn nằm trong khoảng [radiusDp] quanh tâm (tile 256dp, osmdroid
     * phóng theo mật độ màn hình), giới hạn trong [[minZoom], [maxZoom]]. Dùng cho "Tìm rộng hơn" (FR-018).
     */
    fun zoomToShow(
        distanceMeters: Double,
        latitude: Double,
        radiusDp: Double = 150.0,
        minZoom: Double = 10.0,
        maxZoom: Double = 13.5,
    ): Double {
        if (distanceMeters <= 0.0) return maxZoom
        val metersPerDpAtZoom0 = 156_543.03 * cos(Math.toRadians(latitude))
        val zoom = log2(metersPerDpAtZoom0 * radiusDp / distanceMeters)
        return zoom.coerceIn(minZoom, maxZoom)
    }

    /** "Sân gần bạn": tối đa [limit] sân trong [radiusMeters] quanh [origin], gần trước (FR-016). */
    fun nearby(
        venues: List<HomeVenue>,
        origin: LatLng,
        radiusMeters: Double = NEARBY_RADIUS_METERS,
        limit: Int = NEARBY_LIMIT,
    ): List<HomeVenue> = venues.mapNotNull { venue ->
        val position = venue.position ?: return@mapNotNull null
        val distance = meters(origin, position)
        if (distance <= radiusMeters) venue.copy(distanceMeters = distance) else null
    }.sortedBy { it.distanceMeters }.take(limit)
}
