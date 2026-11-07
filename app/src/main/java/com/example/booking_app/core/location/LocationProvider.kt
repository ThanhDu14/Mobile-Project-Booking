package com.example.booking_app.core.location

import com.google.firebase.firestore.GeoPoint

/**
 * Vị trí hiện tại (spec 020 research R9). Không xin quyền: màn hình nào cần thì tự xin.
 * Chi tiết sân (030) chỉ gọi [current] khi [hasPermission] đã `true`.
 */
interface LocationProvider {
    fun hasPermission(): Boolean

    /** `null` khi chưa có quyền, tắt vị trí hoặc quá 10 giây. */
    suspend fun current(): GeoPoint?
}
