package com.example.booking_app.feature.review.domain.usecase

import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.feature.review.domain.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Kết quả bấm trái tim: đổi ngay, rồi có thể bị trả về trạng thái cũ khi ghi lỗi. */
sealed interface FavoriteToggle {
    data class Changed(val isFavorite: Boolean) : FavoriteToggle

    /** Ghi thất bại (Rules từ chối, hết phiên...): UI trả về [isFavorite] và báo snackbar (FR-021). */
    data class Reverted(val isFavorite: Boolean, val error: Throwable) : FavoriteToggle
}

/**
 * Đổi trạng thái yêu thích theo kiểu lạc quan (research R7): phát [FavoriteToggle.Changed] trước khi
 * ghi để trái tim đổi ngay kể cả khi offline (SC-006).
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val repository: FavoriteRepository,
) {
    operator fun invoke(venue: VenueSummary, isFavorite: Boolean): Flow<FavoriteToggle> = flow {
        val target = !isFavorite
        emit(FavoriteToggle.Changed(target))
        repository.setFavorite(venue, target).onFailure { error ->
            emit(FavoriteToggle.Reverted(isFavorite, error))
        }
    }
}
