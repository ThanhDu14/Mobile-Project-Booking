package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.DropInError
import javax.inject.Inject

/**
 * T054 (US4, FR-017): hỏi trước số tiền được hoàn nếu hủy lúc này (`cancel-drop-in` với `dryRun: true`, không ghi gì).
 * Không tự thử lại.
 */
fun interface QuoteCancel {
    suspend operator fun invoke(sessionId: String, requestId: String): Result<CancelQuote>
}

class QuoteCancelImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : QuoteCancel {
    override suspend fun invoke(sessionId: String, requestId: String): Result<CancelQuote> {
        if (sessionId.isBlank() || requestId.isBlank()) return Result.failure(DropInError.InvalidArgument())
        return repository.quoteCancel(sessionId, requestId)
    }
}
