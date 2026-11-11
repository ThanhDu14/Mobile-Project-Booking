package com.example.booking_app.feature.review.domain.usecase

import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.ReviewDraft
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Sửa đánh giá của mình (US5, T065). Kiểm tra ở client giống [SubmitReviewUseCase]; server kiểm lại hạn
 * 7 ngày (`REVIEW_WINDOW_CLOSED`) và quyền (`NOT_OWNER`).
 */
class UpdateReviewUseCase @Inject constructor(
    private val repository: ReviewRepository,
) {
    suspend operator fun invoke(reviewId: String, draft: ReviewDraft): SubmitReviewResult {
        val comment = draft.comment.trim()
        val missing = SubmitReviewUseCase.missingCriteria(draft)
        val commentTooLong = comment.length > SubmitReviewUseCase.MAX_COMMENT
        if (missing.isNotEmpty() || commentTooLong) return SubmitReviewResult.Invalid(missing, commentTooLong)

        val ratings = Ratings(draft.court!!, draft.cleanliness!!, draft.service!!)
        return try {
            SubmitReviewResult.Submitted(
                repository.update(reviewId, ratings, comment, draft.photos.mapNotNull { it.remotePath }),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReviewException) {
            SubmitReviewResult.Failed(e.error)
        } catch (e: Exception) {
            SubmitReviewResult.Failed(ReviewError.INTERNAL)
        }
    }
}
