package com.example.booking_app.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Giờ trong ngày tính bằng phút từ 00:00 (spec 020 research R5). Firestore lưu dạng `HHmm`.
 * `"2400"` (= 1440, mốc cuối ngày) chỉ hợp lệ ở vị trí giờ kết thúc: dùng [parseEnd] (spec 030 R5).
 */
data class TimeOfDay(val minutes: Int) : Comparable<TimeOfDay> {

    init {
        require(minutes in 0..END_OF_DAY) { "minutes ngoài 0..1440: $minutes" }
    }

    val hour: Int get() = minutes / 60
    val minute: Int get() = minutes % 60

    /** `"HH:mm"`; mốc cuối ngày in `"24:00"`. */
    fun format(): String = "%02d:%02d".format(hour, minute)

    /** `"HHmm"` như trong Firestore. */
    fun toHhmm(): String = "%02d%02d".format(hour, minute)

    fun plusMinutes(delta: Int): TimeOfDay = TimeOfDay(minutes + delta)

    /** Thời điểm của giờ này trong [date]; `24:00` là 00:00 ngày hôm sau. */
    fun atDate(date: LocalDate): LocalDateTime = date.atStartOfDay().plusMinutes(minutes.toLong())

    override fun compareTo(other: TimeOfDay): Int = minutes.compareTo(other.minutes)

    override fun toString(): String = format()

    companion object {
        const val END_OF_DAY = 24 * 60

        val MIDNIGHT = TimeOfDay(0)
        val END = TimeOfDay(END_OF_DAY)

        /** Giờ bắt đầu: `"0000"`…`"2359"`. Sai định dạng hoặc `"2400"` → `null`. */
        fun parse(hhmm: String?): TimeOfDay? {
            val m = parseMinutes(hhmm) ?: return null
            return if (m < END_OF_DAY) TimeOfDay(m) else null
        }

        /** Giờ kết thúc: như [parse] và thêm `"2400"`. */
        fun parseEnd(hhmm: String?): TimeOfDay? {
            val m = parseMinutes(hhmm) ?: return null
            return TimeOfDay(m)
        }

        fun of(time: LocalTime): TimeOfDay = TimeOfDay(time.hour * 60 + time.minute)

        private fun parseMinutes(hhmm: String?): Int? {
            if (hhmm == null || hhmm.length != 4 || !hhmm.all { it.isDigit() }) return null
            val h = hhmm.substring(0, 2).toInt()
            val m = hhmm.substring(2).toInt()
            return when {
                h == 24 && m == 0 -> END_OF_DAY
                h in 0..23 && m in 0..59 -> h * 60 + m
                else -> null
            }
        }
    }
}
