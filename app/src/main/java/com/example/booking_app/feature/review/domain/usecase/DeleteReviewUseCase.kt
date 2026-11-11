package com.example.booking_app.feature.review.domain.usecase

import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Xóa đánh giá của mình, bất cứ lúc nào (US5, FR-008, T065). Trả `null` khi thành công, ngược lại là lỗi. */
class DeleteReviewUseCase @Inject constructor(
    private val repository: ReviewRepository,
) {
    suspend operator fun invoke(reviewId: String): ReviewError? = try {
        repository.delete(reviewId)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: ReviewException) {
        e.error
    } catch (e: Exception) {
        ReviewError.INTERNAL
    }
}
