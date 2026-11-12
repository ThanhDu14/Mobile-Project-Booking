package com.example.booking_app.feature.community.ui.common

import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Tên quận theo `districtId` cho thẻ nhóm, trang nhóm và chip khu vực (danh mục đã được `CatalogRepository` giữ trong bộ nhớ). */
class DistrictNames @Inject constructor(private val catalog: CatalogRepository) {

    /** Quận đang bật, theo `sortOrder`; lỗi tải thì rỗng (màn hình vẫn dùng được, chỉ thiếu tên). */
    suspend fun active(): List<District> = all().filter { it.isActive }.sortedBy { it.sortOrder }

    /** `districtId` → tên (gồm cả quận đã tắt, để nhóm cũ vẫn hiện đúng tên). */
    suspend fun byId(): Map<String, String> = all().associate { it.id to it.name }

    private suspend fun all(): List<District> = try {
        catalog.districts()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
}
