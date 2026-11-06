package com.example.booking_app.feature.booking.domain.usecase

import com.example.booking_app.feature.booking.domain.model.RecurringConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class GenerateRecurringSessionsUseCase @Inject constructor() {

    operator fun invoke(
        config: RecurringConfig,
        today: LocalDate = LocalDate.now(ZONE_VIETNAM),
        currentTime: LocalTime = LocalTime.now(ZONE_VIETNAM)
    ): Result<List<LocalDate>> {
        // 1. Kiểm tra ràng buộc khung giờ (FR-004: 2–6 slot 30 phút, tức 1–3 giờ)
        val slotCount = config.slotCount
        if (slotCount < 2 || slotCount > 6) {
            return Result.failure(
                IllegalArgumentException("Khung giờ mỗi buổi phải từ 2 đến 6 slot (1 đến 3 giờ)")
            )
        }

        // 2. Kiểm tra ràng buộc chu kỳ (FR-006: 4–12 tuần)
        if (config.totalWeeks < 4 || config.totalWeeks > 12) {
            return Result.failure(
                IllegalArgumentException("Chu kỳ đặt phải từ 4 đến 12 tuần")
            )
        }

        // 3. Kiểm tra danh sách thứ đã chọn
        if (config.daysOfWeek.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Phải chọn ít nhất một thứ trong tuần")
            )
        }

        val sessions = mutableListOf<LocalDate>()

        if (config.startNextWeek) {
            // Bắt đầu từ thứ Hai tuần sau
            val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            for (week in 0 until config.totalWeeks) {
                val weekStart = nextMonday.plusWeeks(week.toLong())
                for (isoDay in config.daysOfWeek.sorted()) {
                    val sessionDate = weekStart.plusDays((isoDay - 1).toLong())
                    sessions.add(sessionDate)
                }
            }
        } else {
            // Bắt đầu từ tuần hiện tại: tự động bỏ qua các thứ đã qua và ca đã qua giờ hôm nay
            val startHour = config.startTime.substring(0, 2).toInt()
            val startMinute = config.startTime.substring(2, 4).toInt()
            val sessionStartTime = LocalTime.of(startHour, startMinute)

            // Tìm ngày hợp lệ đầu tiên (firstValidDate)
            var firstValidDate: LocalDate? = null
            for (offset in 0..14) {
                val candidate = today.plusDays(offset.toLong())
                if (candidate.dayOfWeek.value in config.daysOfWeek) {
                    val isPast = if (candidate == today) {
                        sessionStartTime <= currentTime
                    } else {
                        candidate < today
                    }
                    if (!isPast) {
                        firstValidDate = candidate
                        break
                    }
                }
            }

            if (firstValidDate == null) {
                return Result.failure(IllegalStateException("Không tìm thấy ngày bắt đầu hợp lệ"))
            }

            // Sinh buổi bắt đầu từ tuần của ngày hôm nay
            val currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

            for (week in 0 until config.totalWeeks) {
                val weekStart = currentWeekMonday.plusWeeks(week.toLong())
                for (isoDay in config.daysOfWeek.sorted()) {
                    val sessionDate = weekStart.plusDays((isoDay - 1).toLong())

                    val isPast = if (sessionDate == today) {
                        sessionStartTime <= currentTime
                    } else {
                        sessionDate < today
                    }

                    if (!isPast) {
                        sessions.add(sessionDate)
                    }
                }
            }
        }

        return Result.success(sessions)
    }

    companion object {
        val ZONE_VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}
