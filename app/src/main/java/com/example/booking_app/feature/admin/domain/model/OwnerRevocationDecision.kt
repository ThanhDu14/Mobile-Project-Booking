package com.example.booking_app.feature.admin.domain.model

data class OwnerRevocationDecision(
    val uid: String,
    val reason: String,
    val requestId: String = java.util.UUID.randomUUID().toString(),
) {
    fun validate(): String? {
        if (uid.isBlank()) return "Mã người dùng không được để trống"
        if (reason.trim().isBlank()) return "Lý do thu hồi không được để trống"
        return null
    }
}
