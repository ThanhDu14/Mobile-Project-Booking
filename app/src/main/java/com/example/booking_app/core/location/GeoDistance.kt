package com.example.booking_app.core.location

import com.google.firebase.firestore.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Khoảng cách đường chim bay (haversine), đủ chính xác cho "1,2 km" (spec 020 FR-013). */
object GeoDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun meters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }
}
