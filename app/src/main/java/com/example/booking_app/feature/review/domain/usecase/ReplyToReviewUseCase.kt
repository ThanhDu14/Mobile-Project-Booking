package com.example.booking_app.feature.review.domain.usecase

import com.example.booking_app.feature.review.domain.ReviewError
import com.example.booking_app.feature.review.domain.ReviewException
import com.example.booking_app.feature.review.domain.ReviewRepository
import com.example.booking_app.feature.review.domain.model.OwnerReply
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface ReplyResult {
    data class Sent(val reply: OwnerReply) : ReplyResult

    /** Lỗi ở client, chưa gọi server: phản hồi rỗng hoặc quá [ReplyToReviewUseCase.MAX_REPLY] ký tự. */
    data class Invalid(val empty: Boolean, val tooLong: Boolean) : ReplyResult

    data class Failed(val error: ReviewError) : ReplyResult
}

/** Chủ sân tạo hoặc sửa phản hồi (US4, FR-018, FR-019). */
class ReplyToReviewUseCase @Inject constructor(
    private val repository: ReviewRepository,
) {
    suspend operator fun invoke(reviewId: String, text: String): ReplyResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_REPLY) {
            return ReplyResult.Invalid(empty = trimmed.isEmpty(), tooLong = trimmed.length > MAX_REPLY)
        }
        return try {
            ReplyResult.Sent(repository.reply(reviewId, trimmed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReviewException) {
            ReplyResult.Failed(e.error)
        } catch (e: Exception) {
            ReplyResult.Failed(ReviewError.INTERNAL)
        }
    }

    companion object {
        const val MAX_REPLY = 500
    }
}
