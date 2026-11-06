package com.example.booking_app.feature.admin.domain.model

enum class UserAccountStatus {
    ACTIVE,
    LOCKED;

    companion object {
        fun fromString(value: String?): UserAccountStatus {
            return when (value?.uppercase()?.trim()) {
                "LOCKED" -> LOCKED
                else -> ACTIVE
            }
        }
    }
}

data class AdminUserLookup(
    val uid: String,
    val phone: String,
    val fullName: String,
    val role: String,
    val ownerStatus: String,
    val accountStatus: UserAccountStatus,
    val ownerRevokedReason: String? = null,
    val createdAt: String? = null,
) {
    val isLocked: Boolean get() = accountStatus == UserAccountStatus.LOCKED
    val isOwnerOrApproved: Boolean get() = role.equals("OWNER", ignoreCase = true) || ownerStatus.equals("APPROVED", ignoreCase = true)
}
