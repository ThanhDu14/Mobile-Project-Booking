package com.example.booking_app.core.schedule

/** Khung giờ 30 phút dùng chung cho tìm kiếm (020), chi tiết sân (030), lưới giờ (040), khóa slot (111). */
object SlotMath {
    const val STEP_MINUTES = 30

    fun isAligned(time: TimeOfDay): Boolean = time.minutes % STEP_MINUTES == 0

    /** Giờ bắt đầu của mọi khung 30 phút trong `[start, end)`. */
    fun slots(start: TimeOfDay, end: TimeOfDay): List<TimeOfDay> =
        (start.minutes until end.minutes step STEP_MINUTES).map(::TimeOfDay)
}
