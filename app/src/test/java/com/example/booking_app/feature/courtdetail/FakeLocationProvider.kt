package com.example.booking_app.feature.courtdetail

import com.example.booking_app.core.location.LocationProvider
import com.google.firebase.firestore.GeoPoint

/** Vị trí giả: đếm số lần gọi [current] để kiểm tra không đọc vị trí khi chưa có quyền. */
class FakeLocationProvider(var permission: Boolean = false, var location: GeoPoint? = null) : LocationProvider {
    var currentCalls = 0

    override fun hasPermission(): Boolean = permission

    override suspend fun current(): GeoPoint? {
        currentCalls++
        return location
    }
}
