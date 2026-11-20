package com.example.booking_app.feature.chat.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.toChatError
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Chuỗi hiển thị và định dạng dùng chung cho màn hình chat (múi giờ `Asia/Ho_Chi_Minh`). */
object ChatUiText {

    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)

    fun error(context: Context, error: Throwable?): String =
        context.getString(errorRes(error?.toChatError()))

    @StringRes
    fun errorRes(error: ChatError?): Int = when (error) {
        is ChatError.InvalidArgument -> R.string.chat_error_invalid_argument
        is ChatError.PhotoMissing -> R.string.chat_error_photo_missing
        is ChatError.Unauthenticated -> R.string.chat_error_unauthenticated
        is ChatError.AccountLocked -> R.string.chat_error_account_locked
        is ChatError.NotParticipant -> R.string.chat_error_not_participant
        is ChatError.Blocked -> R.string.chat_error_blocked
        is ChatError.Forbidden -> R.string.chat_error_forbidden
        is ChatError.OwnVenue -> R.string.chat_error_own_venue
        is ChatError.OwnMessage -> R.string.chat_error_own_message
        is ChatError.ChatNotFound -> R.string.chat_error_chat_not_found
        is ChatError.MessageNotFound -> R.string.chat_error_message_not_found
        is ChatError.ChatReadOnly -> R.string.chat_error_chat_read_only
        is ChatError.VenueInactive -> R.string.chat_error_venue_inactive
        is ChatError.OwnerNotApproved -> R.string.chat_error_owner_not_approved
        is ChatError.RecallWindowPassed -> R.string.chat_error_recall_window_passed
        is ChatError.AlreadyReported -> R.string.chat_error_already_reported
        is ChatError.GroupLocked -> R.string.chat_error_group_locked
        is ChatError.Busy -> R.string.chat_error_busy
        is ChatError.Network -> R.string.chat_error_network
        is ChatError.ImageTooLarge -> R.string.chat_error_image_too_large
        is ChatError.ImageUnreadable -> R.string.chat_error_image_unreadable
        else -> R.string.chat_error_unknown
    }

    /** Giờ hiển thị trên bong bóng tin nhắn (`HH:mm` theo `Asia/Ho_Chi_Minh`). */
    fun messageTime(instant: Instant?): String = instant?.let(TimeFormat::time).orEmpty()

    /** Thời gian hiển thị ở dòng danh sách chat: `HH:mm` (hôm nay), "Hôm qua", `T2`..`CN` (trong tuần), `dd/MM`. */
    fun listTime(context: Context, instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return ""
        val msgDate = TimeFormat.localDate(instant)
        val today = TimeFormat.localDate(now)
        val daysBetween = ChronoUnit.DAYS.between(msgDate, today)
        return when {
            daysBetween <= 0L -> TimeFormat.time(instant)
            daysBetween == 1L -> context.getString(R.string.chat_yesterday)
            daysBetween in 2L..6L -> TimeFormat.dayOfWeekShort(msgDate.dayOfWeek)
            else -> DAY_MONTH.format(msgDate)
        }
    }

    /** Định dạng số chưa đọc: `0` → null, `1..99` → số, `> 99` → `"99+"`. */
    fun unreadBadge(count: Int): String? = when {
        count <= 0 -> null
        count > 99 -> "99+"
        else -> count.toString()
    }

    /** URL ảnh công khai trên Supabase Storage (giữ nguyên nếu dữ liệu seed đã là URL đầy đủ). */
    fun publicImageUrl(bucket: String, path: String?): String? {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return StorageUrls.publicUrl(bucket, trimmed)
    }

    fun formatDateDivider(context: Context, date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> context.getString(R.string.chat_today)
        1L -> context.getString(R.string.chat_yesterday)
        else -> DAY_MONTH.format(date)
    }
}
