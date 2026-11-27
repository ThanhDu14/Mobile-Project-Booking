package com.example.booking_app.feature.payment.data

import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date

/**
 * Map document voucher (data-model §1.6) sang [Voucher], khớp `toLoadedVoucher` của
 * `supabase/functions/_shared/payment-voucher.ts` để app và server hiểu cùng một voucher:
 * `type` khác `FIXED` là PERCENT (dữ liệu cũ ghi `PERCENTAGE`); `0` ở `maxDiscount`/`usageLimit`/`perUserLimit`
 * nghĩa là không giới hạn; `validFrom`/`validTo` là chuỗi ISO (111/120 ghi) hoặc Timestamp.
 */
internal object VoucherDocumentMapper {

    fun map(code: String, scope: VoucherScope, data: Map<String, Any?>): Voucher = Voucher(
        code = code,
        scope = scope,
        type = if ((data["type"] as? String)?.uppercase() == "FIXED") VoucherType.FIXED else VoucherType.PERCENT,
        value = data["value"].toLongOrNull() ?: 0L,
        maxDiscount = data["maxDiscount"].positiveOrNull(),
        minOrderTotal = data["minOrderTotal"].toLongOrNull() ?: 0L,
        validFromMillis = toMillis(data["validFrom"]),
        validToMillis = toMillis(data["validTo"]),
        usageLimit = data["usageLimit"].positiveOrNull(),
        usedCount = data["usedCount"].toLongOrNull() ?: 0L,
        perUserLimit = data["perUserLimit"].positiveOrNull(),
        isActive = data["isActive"] != false,
    )

    private fun Any?.toLongOrNull(): Long? = when (this) {
        is Number -> toLong()
        is String -> toDoubleOrNull()?.toLong()
        else -> null
    }

    private fun Any?.positiveOrNull(): Long? = toLongOrNull()?.takeIf { it > 0 }

    /** Như `Date.parse` của JS: chuỗi chỉ có ngày tính là 00:00 UTC. */
    fun toMillis(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Number -> value.toLong()
        is String -> parseIso(value.trim())
        else -> null
    }

    private fun parseIso(text: String): Long? {
        if (text.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(text).toEpochMilli() }
            .recoverCatching { LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }
}
