package com.example.booking_app.feature.account.ui.account

import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tiền VND nhóm 3 chữ số bằng dấu chấm: `50000` → `50.000` (không phụ thuộc locale của máy). */
fun formatVnd(amount: Long): String {
    val digits = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (amount < 0) "-$digits" else digits
}

@StringRes
fun levelLabel(level: PlayerLevel): Int = when (level) {
    PlayerLevel.BEGINNER -> R.string.account_level_beginner
    PlayerLevel.INTERMEDIATE -> R.string.account_level_intermediate
    PlayerLevel.ADVANCED -> R.string.account_level_advanced
}

private val SUBMITTED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

/** Ngày nộp hồ sơ chủ sân: `10:00, 20/10/2026` theo múi giờ của máy. */
fun formatSubmittedAt(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    SUBMITTED_AT_FORMAT.format(instant.atZone(zone))

private val SLOT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM, HH:mm", Locale.forLanguageTag("vi-VN"))

/** Giờ chơi trên màn hình Xóa tài khoản: `T3 06/10, 19:00` (wireframe buoc5), Chủ nhật là `CN`. */
fun formatSlotTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = instant.atZone(zone)
    val day = time.dayOfWeek.value // 1 = thứ Hai … 7 = Chủ nhật
    val weekday = if (day == 7) "CN" else "T${day + 1}"
    return "$weekday ${SLOT_TIME_FORMAT.format(time)}"
}
