package com.example.booking_app.feature.review.domain

import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import kotlinx.coroutines.flow.Flow

/**
 * Sân yêu thích của người dùng đang đăng nhập (contracts/ui-screens.md §2). Nhóm khác inject
 * interface này qua Hilt, ví dụ nút trái tim ở chi tiết sân (spec 030).
 */
interface FavoriteRepository {

    /** Phát lại mỗi khi trạng thái đổi, kể cả thay đổi chưa gửi lên server (offline, research R7). */
    fun observeIsFavorite(venueId: String): Flow<Boolean>

    /**
     * Danh sách mới lưu trước, tối đa [limit] sân. Muốn xem thêm thì gọi lại với [limit] lớn hơn.
     * Phát [com.example.booking_app.core.network.AppError.NotSignedIn] khi chưa đăng nhập.
     */
    fun observeFavorites(limit: Int = PAGE_SIZE): Flow<List<FavoriteVenue>>

    /**
     * Lưu hoặc bỏ yêu thích. Khi offline, thay đổi được áp dụng ngay vào cache và hàm chỉ trả về
     * sau khi có mạng; lỗi Rules trả [Result.failure].
     */
    suspend fun setFavorite(venue: VenueSummary, favorite: Boolean): Result<Unit>

    companion object {
        const val PAGE_SIZE = 20
    }
}
