package com.example.booking_app.feature.account.domain.model

/** Trình độ tự đánh giá (`users.level`); `null` là chưa chọn. */
enum class PlayerLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    ;

    companion object {
        /** Giá trị lạ (dữ liệu cũ) coi như chưa chọn. */
        fun fromValue(value: String?): PlayerLevel? = entries.firstOrNull { it.name == value }
    }
}
