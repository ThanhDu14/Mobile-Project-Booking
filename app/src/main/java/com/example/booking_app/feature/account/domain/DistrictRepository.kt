package com.example.booking_app.feature.account.domain

import com.example.booking_app.feature.account.domain.model.District

/** Danh mục `districts` (data-model §3). Lỗi mạng là `AccountError.Network`. */
interface DistrictRepository {
    /** Khu vực đang hoạt động, sắp theo `sortOrder`. */
    suspend fun activeDistricts(): List<District>

    /** Khu vực theo ID, kể cả đã ngừng (để hiện tên); ID không tồn tại thì không có trong kết quả. */
    suspend fun districtsByIds(ids: Collection<String>): Map<String, District>
}
