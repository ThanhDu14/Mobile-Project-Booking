package com.example.booking_app.core.catalog

import kotlinx.coroutines.flow.StateFlow

/** Danh mục quận và tiện ích, đọc một lần mỗi phiên rồi giữ trong bộ nhớ (spec 020 R2). */
interface CatalogRepository {
    /** `null` khi chưa tải. */
    val districtsFlow: StateFlow<List<District>?>
    val amenitiesFlow: StateFlow<List<Amenity>?>

    /** Sắp theo `sortOrder`, gồm cả mục đã tắt. Ném [com.example.booking_app.core.network.AppError]. */
    suspend fun districts(): List<District>

    suspend fun amenities(): List<Amenity>

    suspend fun isActiveDistrict(id: String): Boolean

    suspend fun isActiveAmenity(id: String): Boolean
}
