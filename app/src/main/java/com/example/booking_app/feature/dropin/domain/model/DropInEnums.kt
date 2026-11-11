package com.example.booking_app.feature.dropin.domain.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Khung giờ theo giờ bắt đầu của buổi, giờ `Asia/Ho_Chi_Minh` (FR-003, data-model mục 1). */
enum class TimeSlot {
    MORNING, NOON, AFTERNOON, EVENING;

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        private val MORNING_START: LocalTime = LocalTime.of(5, 0)
        private val NOON_START: LocalTime = LocalTime.of(11, 0)
        private val AFTERNOON_START: LocalTime = LocalTime.of(13, 0)
        private val EVENING_START: LocalTime = LocalTime.of(18, 0)
        private val EVENING_END: LocalTime = LocalTime.of(23, 0)

        /**
         * Sáng [05:00, 11:00), Trưa [11:00, 13:00), Chiều [13:00, 18:00), Tối [18:00, 23:00).
         * Ngoài các khung này trả `null`. Khớp `deriveSessionFields()` ở Edge Function.
         */
        fun of(start: LocalTime): TimeSlot? = when {
            start < MORNING_START -> null
            start < NOON_START -> MORNING
            start < AFTERNOON_START -> NOON
            start < EVENING_START -> AFTERNOON
            start < EVENING_END -> EVENING
            else -> null
        }

        /** Theo giờ bắt đầu ở múi `Asia/Ho_Chi_Minh`. */
        fun of(startAt: Instant): TimeSlot? = of(startAt.atZone(ZONE).toLocalTime())
    }
}

/** Cùng enum với `users.level`. */
enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }

enum class SessionStatus { OPEN, FULL, CANCELLED, COMPLETED }

enum class RegistrationStatus { REGISTERED, WAITLISTED, CANCELLED, CHECKED_IN }

enum class PaymentMethod { MOCK_PAY, AT_VENUE }

enum class PaymentStatus { PAID, UNPAID, REFUNDED }

enum class CancelledBy { USER, OWNER, SYSTEM }
