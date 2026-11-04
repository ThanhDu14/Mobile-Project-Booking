package com.example.booking_app.core.location

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `haversine sai so duoi 1 phan tram voi hai diem o TP HCM`() {
        // Chợ Bến Thành → Nhà thờ Đức Bà: khoảng 822 m (tính độc lập bằng xấp xỉ equirectangular).
        val benThanh = GeoPoint(10.772450, 106.698060)
        val ducBa = GeoPoint(10.779785, 106.699018)
        val d = GeoDistance.meters(benThanh, ducBa)
        assertEquals(822.0, d, 822.0 * 0.01)
    }

    @Test
    fun `cung mot diem la 0`() {
        val p = GeoPoint(10.8, 106.6)
        assertEquals(0.0, GeoDistance.meters(p, p), 0.0)
    }

    @Test
    fun `mot do kinh tuyen o xich dao khoang 111 km`() {
        assertEquals(111_195.0, GeoDistance.meters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 111_195.0 * 0.01)
    }
}
