package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.courtdetail.domain.LatestReviewsSource
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Tối đa 3 đánh giá mới nhất cho mục tổng quan (FR-014). Ném [com.example.booking_app.core.network.AppError]. */
class LoadLatestReviewsUseCase @Inject constructor(
    private val source: LatestReviewsSource,
) {
    suspend operator fun invoke(venueId: String): List<ReviewPreview> {
        val reviews = try {
            source.latest(venueId, LatestReviewsSource.DEFAULT_LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toAppError()
        }
        // Nguồn đã sắp xếp; sắp lại để bản cài đặt khác (070) không làm lệch thứ tự. Thiếu ngày tạo xếp cuối.
        return reviews
            .sortedWith(compareByDescending(nullsFirst()) { it.createdAt })
            .take(LatestReviewsSource.DEFAULT_LIMIT)
    }
}
