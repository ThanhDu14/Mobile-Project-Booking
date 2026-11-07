package com.example.booking_app.core.session

/** Vai trò trong claim `appRole` của ID token (FR-015, research R6). */
enum class AppRole {
    PLAYER,
    OWNER,
    ADMIN;

    companion object {
        /** `null` nghĩa là tài khoản chưa khởi tạo (R4). Giá trị lạ coi là [PLAYER]. */
        fun fromClaim(value: String?): AppRole? {
            if (value == null) return null
            return entries.firstOrNull { it.name == value } ?: PLAYER
        }
    }
}
