package com.example.booking_app.feature.account.data

import com.example.booking_app.feature.account.domain.model.District

interface DistrictDataSource {
    /** `districts where isActive == true order by sortOrder`. Ném `AppError`. */
    suspend fun listActive(): List<District>

    /** Một khu vực, kể cả đã ngừng; không tồn tại thì `null`. */
    suspend fun get(id: String): District?
}
