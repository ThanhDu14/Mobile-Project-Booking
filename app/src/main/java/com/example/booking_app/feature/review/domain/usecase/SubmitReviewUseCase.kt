package com.example.booking_app.feature.review.domain.usecase

import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewDraft
import com.example.booking_app.feature.review.domain.model.SubmittedReview
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlin.math.roundToLong

sealed interface SubmitReviewResult {
    data class Submitted(val review: SubmittedReview) : SubmitReviewResult

    /** Lỗi ở client, chưa gọi server: [missing] là tiêu chí chưa chấm (FR-003, US1 kịch bản 3). */
    data class Invalid(val missing: List<ReviewCriterion>, val commentTooLong: Boolean) : SubmitReviewResult

    data class Failed(val error: ReviewError) : SubmitReviewResult
}

/** Kiểm tra bản nháp ở client rồi gửi (T033). Server vẫn kiểm tra lại toàn bộ. */
class SubmitReviewUseCase @Inject constructor(
    private val repository: ReviewRepository,
) {
    suspend operator fun invoke(draft: ReviewDraft): SubmitReviewResult {
        val comment = draft.comment.trim()
        val missing = missingCriteria(draft)
        val commentTooLong = comment.length > MAX_COMMENT
        if (missing.isNotEmpty() || commentTooLong) return SubmitReviewResult.Invalid(missing, commentTooLong)

        val ratings = Ratings(draft.court!!, draft.cleanliness!!, draft.service!!)
        return try {
            SubmitReviewResult.Submitted(
                repository.create(
                    bookingId = draft.bookingId,
                    ratings = ratings,
                    comment = comment,
                    photoPaths = draft.photos.mapNotNull { it.remotePath },
                    sessionId = draft.sessionId,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReviewException) {
            SubmitReviewResult.Failed(e.error)
        } catch (e: Exception) {
            SubmitReviewResult.Failed(ReviewError.INTERNAL)
        }
    }

    companion object {
        const val MAX_COMMENT = 1000

        fun missingCriteria(draft: ReviewDraft): List<ReviewCriterion> = buildList {
            if (draft.court !in 1..5) add(ReviewCriterion.COURT)
            if (draft.cleanliness !in 1..5) add(ReviewCriterion.CLEANLINESS)
            if (draft.service !in 1..5) add(ReviewCriterion.SERVICE)
        }

        /**
         * Điểm tổng = trung bình 3 tiêu chí làm tròn 1 chữ số, giống `computeOverall` ở
         * `_shared/reviews.ts`; `null` khi chưa chấm đủ ("— / 5" trên màn hình).
         */
        fun overallOf(draft: ReviewDraft): Double? {
            if (missingCriteria(draft).isNotEmpty()) return null
            val sum = draft.court!! + draft.cleanliness!! + draft.service!!
            return (sum * 10.0 / 3).roundToLong() / 10.0
        }
    }
}
