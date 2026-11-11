package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.PendingDecision

/** Bảng quyết định thao tác đang chờ của khách (data-model §2.4, research R11). Hàm thuần, không giữ trạng thái. */
object PendingActionResolver {

    fun resolve(
        state: SessionState,
        pending: PendingAction?,
        ownerId: String?,
        currentUid: String?,
        screenVisible: Boolean,
    ): PendingDecision {
        if (pending == null || !screenVisible) return PendingDecision.Wait
        return when (state) {
            is SessionState.SignedIn ->
                if (ownerId != null && ownerId == currentUid && pending != PendingAction.FAVORITE) {
                    PendingDecision.RejectOwnVenue
                } else {
                    PendingDecision.Run(pending)
                }
            // Màn hình hiện lại mà vẫn là khách: người dùng đã thoát đăng nhập giữa chừng (US5-6).
            is SessionState.SignedOut -> PendingDecision.Cancel
            // Phiên đang dựng (Loading, Initializing, NeedsConsent, InitFailed): chờ kết quả cuối.
            else -> PendingDecision.Wait
        }
    }
}
