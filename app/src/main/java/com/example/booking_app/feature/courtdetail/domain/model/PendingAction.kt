package com.example.booking_app.feature.courtdetail.domain.model

/** Thao tác khách bấm trước khi đăng nhập, lưu trong `SavedStateHandle["pendingAction"]` (research R11). */
enum class PendingAction { BOOK, FAVORITE, CHAT }

sealed interface PendingDecision {
    data class Run(val action: PendingAction) : PendingDecision
    data object RejectOwnVenue : PendingDecision
    data object Cancel : PendingDecision
    data object Wait : PendingDecision
}
