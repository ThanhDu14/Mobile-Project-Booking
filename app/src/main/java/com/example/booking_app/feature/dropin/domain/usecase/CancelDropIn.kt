package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import javax.inject.Inject

/**
 * T054 (US4): hủy lượt của mình. Gửi lại cùng [requestId] (sau mất mạng/bận) trả kết quả cũ, không trả chỗ hai lần
 * (FR-018). Không tự thử lại; ViewModel giữ [requestId].
 */
fun interface CancelDropIn {
    suspend operator fun invoke(sessionId: String, requestId: String): Result<DropInRegistration>
}

class CancelDropInImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : CancelDropIn {
    override suspend fun invoke(sessionId: String, requestId: String): Result<DropInRegistration> {
        if (sessionId.isBlank() || requestId.isBlank()) return Result.failure(DropInError.InvalidArgument())
        return repository.cancel(sessionId, requestId)
    }
}
