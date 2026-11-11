package com.example.booking_app.feature.dropin.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInWarning
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Chuỗi hiển thị dùng chung cho các màn hình vãng lai (chuỗi ở `strings_dropin.xml`). */
object DropInUiText {

    @StringRes
    fun level(level: Level?): Int = when (level) {
        Level.BEGINNER -> R.string.dropin_level_beginner
        Level.INTERMEDIATE -> R.string.dropin_level_intermediate
        Level.ADVANCED -> R.string.dropin_level_advanced
        null -> R.string.dropin_level_any
    }

    @StringRes
    fun timeSlot(slot: TimeSlot): Int = when (slot) {
        TimeSlot.MORNING -> R.string.dropin_slot_morning
        TimeSlot.NOON -> R.string.dropin_slot_noon
        TimeSlot.AFTERNOON -> R.string.dropin_slot_afternoon
        TimeSlot.EVENING -> R.string.dropin_slot_evening
    }

    @StringRes
    fun warning(warning: DropInWarning): Int = when (warning) {
        DropInWarning.OVERLAP_DROP_IN -> R.string.dropin_warning_overlap_drop_in
        DropInWarning.OVERLAP_BOOKING -> R.string.dropin_warning_overlap_booking
        DropInWarning.LEVEL_MISMATCH -> R.string.dropin_warning_level_mismatch
    }

    @StringRes
    fun dayOfWeekShort(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY -> R.string.dropin_dow_mon
        DayOfWeek.TUESDAY -> R.string.dropin_dow_tue
        DayOfWeek.WEDNESDAY -> R.string.dropin_dow_wed
        DayOfWeek.THURSDAY -> R.string.dropin_dow_thu
        DayOfWeek.FRIDAY -> R.string.dropin_dow_fri
        DayOfWeek.SATURDAY -> R.string.dropin_dow_sat
        DayOfWeek.SUNDAY -> R.string.dropin_dow_sun
    }

    /** "Hôm nay" / "Mai" khi buổi rơi vào hôm nay hoặc ngày mai theo giờ Việt Nam; ngược lại `null`. */
    @StringRes
    fun relativeDay(start: Instant, now: Instant = Instant.now()): Int? {
        val zone: ZoneId = TimeFormat.ZONE
        val day = start.atZone(zone).toLocalDate()
        val today: LocalDate = now.atZone(zone).toLocalDate()
        return when (day) {
            today -> R.string.dropin_today
            today.plusDays(1) -> R.string.dropin_tomorrow
            else -> null
        }
    }

    /** "Mai, T3 06/10 · 19:00 – 21:00" (wireframe bước 2, 3). */
    fun dateTimeLine(context: Context, start: Instant, end: Instant): String {
        val date = TimeFormat.dayOfWeekDate(start)
        val dated = relativeDay(start)?.let { context.getString(R.string.dropin_relative_date, context.getString(it), date) }
            ?: date
        return context.getString(R.string.dropin_date_time, dated, TimeFormat.timeRange(start, end))
    }

    /** Thông báo cho một lỗi (contracts/edge-functions-dropin.md mục 4). */
    fun error(context: Context, error: Throwable?): String = when (error) {
        is DropInError.SessionFull -> context.getString(R.string.dropin_error_session_full, error.remaining)
        else -> context.getString(errorRes(error))
    }

    @StringRes
    fun errorRes(error: Throwable?): Int = when (error) {
        is DropInError.SessionFull -> R.string.dropin_error_session_full
        is DropInError.SessionStarted -> R.string.dropin_error_session_started
        is DropInError.SessionCancelled -> R.string.dropin_error_session_cancelled
        is DropInError.VenueInactive -> R.string.dropin_error_venue_inactive
        is DropInError.OwnSession -> R.string.dropin_error_own_session
        is DropInError.AlreadyRegistered -> R.string.dropin_error_already_registered
        is DropInError.AlreadyWaitlisted -> R.string.dropin_error_already_waitlisted
        is DropInError.NotCancellable -> R.string.dropin_error_not_cancellable
        is DropInError.NotFound -> R.string.dropin_error_not_found
        is DropInError.AccountLocked -> R.string.dropin_error_account_locked
        is DropInError.InvalidArgument -> R.string.dropin_error_invalid_argument
        is DropInError.Unauthenticated -> R.string.dropin_error_unauthenticated
        is DropInError.Busy -> R.string.dropin_error_busy
        is DropInError.Network -> R.string.dropin_error_network
        else -> R.string.dropin_error_internal
    }
}
