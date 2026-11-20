package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.model.WaitlistJoinResult
import javax.inject.Inject

/**
 * Spec 081 US1 (FR-001, FR-002): vào hàng chờ của buổi đã đủ người. Không tự thử lại; ViewModel giữ `requestId`.
 * Người chơi được vào hàng chờ nhiều buổi trùng giờ, chỉ cảnh báo: ViewModel dùng [CheckDropInWarnings] như đăng ký
 * thường, use case này không chặn vì trùng giờ.
 */
fun interface JoinWaitlist {
    suspend operator fun invoke(req: RegisterRequest): Result<WaitlistJoinResult>
}

class JoinWaitlistImpl @Inject constructor(
    private val repository: DropInRegistrationRepository,
) : JoinWaitlist {
    override suspend fun invoke(req: RegisterRequest): Result<WaitlistJoinResult> {
        if (req.partySize !in RegisterDropInImpl.MIN_PARTY..RegisterDropInImpl.MAX_PARTY ||
            req.requestId.isBlank() ||
            req.sessionId.isBlank()
        ) {
            return Result.failure(DropInError.InvalidArgument())
        }
        return repository.joinWaitlist(req.copy(joinWaitlist = true))
    }
}
