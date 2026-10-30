package com.example.booking_app.feature.account.domain.model

/** Cách xác nhận lại danh tính, chọn theo `providerData` (research R8). */
sealed interface ReauthMethod {
    data object Password : ReauthMethod

    data object Google : ReauthMethod

    data class Phone(val phoneE164: String) : ReauthMethod
}
