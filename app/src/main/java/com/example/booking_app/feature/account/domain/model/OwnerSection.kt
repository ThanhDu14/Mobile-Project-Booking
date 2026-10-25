package com.example.booking_app.feature.account.domain.model

import java.time.Instant

/** Mục chủ sân trên trang Tài khoản, suy ra theo bảng ở research R6. */
sealed interface OwnerSection {
    data object Hidden : OwnerSection

    data object CanApply : OwnerSection

    data class Revoked(val reason: String) : OwnerSection

    data class Pending(val submittedAt: Instant?) : OwnerSection

    data class Rejected(val reason: String?) : OwnerSection

    data object Approved : OwnerSection
}
