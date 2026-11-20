package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import javax.inject.Inject

/**
 * Spec 081 US3 (FR-012, FR-013): rời hàng chờ, không mất phí. Gửi lại cùng [requestId] (sau mất mạng) trả kết quả cũ.
 * Lượt đã được đẩy lên thì thất bại với [DropInError.AlreadyPromoted] và đăng ký không bị hủy.
 */
fun interface LeaveWaitlist {
    suspend operator fun invoke(sessionId: String, requestId: String): Result<DropInRegistration>
}

class LeaveWaitlistImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : LeaveWaitlist {
    override suspend fun invoke(sessionId: String, requestId: String): Result<DropInRegistration> {
        if (sessionId.isBlank() || requestId.isBlank()) return Result.failure(DropInError.InvalidArgument())
        return repository.leaveWaitlist(sessionId, requestId)
    }
}
