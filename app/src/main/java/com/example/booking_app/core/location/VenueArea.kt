package com.example.booking_app.core.location

/**
 * Vùng hợp lệ cho tọa độ cơ sở: khung bao phần đất liền Việt Nam cùng các đảo ven bờ (Phú Quốc, Côn Đảo).
 * Chỉ để loại tọa độ nhập sai rõ ràng như (90, 90) hay (0, 0), không phải ranh giới chính xác.
 */
object VenueArea {
    private val LATITUDE = 8.0..24.0
    private val LONGITUDE = 102.0..110.0

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in LATITUDE && longitude in LONGITUDE
}
