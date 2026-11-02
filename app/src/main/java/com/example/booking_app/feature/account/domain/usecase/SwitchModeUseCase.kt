package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.AppModeController
import com.example.booking_app.core.session.OwnerRevokedException
import com.example.booking_app.feature.account.domain.model.AccountError
import javax.inject.Inject

/** Chuyển "Người chơi" ↔ "Quản lý sân" (research R7). Quyền đã bị thu hồi thì trả `AccountError.OwnerRevoked`. */
class SwitchModeUseCase @Inject constructor(
    private val appModeController: AppModeController,
) {
    /** [revokedReason] là `MyProfile.ownerRevokedReason` mới nhất, để báo lý do khi bị chặn (FR-012). */
    suspend operator fun invoke(target: AppMode, revokedReason: String?): Result<AppMode> =
        appModeController.switchTo(target).recoverCatching { error ->
            throw if (error is OwnerRevokedException) AccountError.OwnerRevoked(revokedReason) else error
        }
}
