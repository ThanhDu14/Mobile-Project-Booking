package com.example.booking_app.feature.search.domain

import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

/** Một lần tìm đã lưu trong `users/{uid}/searchHistory` (spec 020 US4, data-model §2.1). */
data class SearchHistoryEntry(val id: String, val criteria: CourtSearchCriteria)

/**
 * Lịch sử tìm kiếm của người đã đăng nhập. Chỉ ghi khi [CourtSearchCriteria.hasFilters]
 * (từ khóa hợp lệ hoặc ít nhất một bộ lọc; sắp xếp riêng không tính). Không lưu vị trí (FR-014).
 */
interface SearchHistoryRepository {
    /** Tối đa [SearchHistory.LIMIT] mục, mới nhất trước. */
    fun observe(uid: String): Flow<List<SearchHistoryEntry>>

    /**
     * Ghi một lần tìm; cùng tổ hợp thì cập nhật đúng document đó (lên đầu, không trùng).
     * [prune] = danh sách đã đủ 10 mục và đây là tổ hợp mới: xóa mục cũ hơn mục thứ 10.
     */
    suspend fun record(uid: String, criteria: CourtSearchCriteria, prune: Boolean)

    suspend fun delete(uid: String, id: String)

    suspend fun clear(uid: String, ids: List<String>)
}

object SearchHistory {
    const val LIMIT = 10

    /**
     * ID document tất định (research R11): 20 ký tự hex đầu của SHA-256 trên chuỗi chuẩn hóa
     * (từ khóa bỏ dấu và hoa thường, quận, khoảng giá, khung giờ, tiện ích đã sắp xếp, cách sắp xếp).
     */
    fun idOf(criteria: CourtSearchCriteria): String {
        val window = criteria.window
        val key = listOf(
            normalizedSearchText(criteria.keyword),
            criteria.districtId.orEmpty(),
            criteria.priceMin?.toString().orEmpty(),
            criteria.priceMax?.toString().orEmpty(),
            window?.dateKey().orEmpty(),
            window?.start?.toHhmm().orEmpty(),
            window?.end?.toHhmm().orEmpty(),
            criteria.amenityIds.sorted().joinToString(","),
            criteria.sort.name,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
    }

    fun worthSaving(criteria: CourtSearchCriteria): Boolean = criteria.hasFilters()
}
