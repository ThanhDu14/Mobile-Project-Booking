package com.example.booking_app.feature.account.ui.account

import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Chữ cái đầu cho vòng tròn khi chưa có ảnh: tên Việt đặt tên riêng ở cuối nên lấy hai từ cuối
 * ("Lê Hoàng Nam" → "HN", wireframe buoc1); một từ thì lấy một chữ.
 */
fun initialsOf(displayName: String): String =
    displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .takeLast(2).joinToString("") { it.first().toString() }.uppercase()

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
