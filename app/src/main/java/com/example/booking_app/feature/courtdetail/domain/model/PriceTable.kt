package com.example.booking_app.feature.courtdetail.domain.model

import com.example.booking_app.core.schedule.TimeOfDay
import java.time.DayOfWeek

enum class PriceLabel {
    NORMAL,
    PEAK,
    WEEKEND,
    ;

    companion object {
        /** Giá trị lạ → "Giờ thường". */
        fun from(raw: String?): PriceLabel = entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

/** Nhãn nhóm ngày: "Thứ 2" / "Thứ 7, Chủ nhật" / "Thứ 2 – Thứ 6". */
sealed interface DayGroupTitle {
    data class Single(val day: DayOfWeek) : DayGroupTitle
    data class Pair(val first: DayOfWeek, val second: DayOfWeek) : DayGroupTitle
    data class Range(val from: DayOfWeek, val to: DayOfWeek) : DayGroupTitle
}

data class PriceRow(
    val start: TimeOfDay,
    val end: TimeOfDay,
    val label: PriceLabel,
    val pricePerHour: Long,
    val highlighted: Boolean,
    val ruleId: String,
)

data class PriceDayGroup(val days: List<DayOfWeek>, val title: DayGroupTitle, val rows: List<PriceRow>)

/** Giá "Khung bạn chọn". [perHour] `null` → "Liên hệ" (FR-009). */
data class SelectedWindowPrice(val window: SearchWindow, val perHour: Long?)

/** Kết quả của `LoadPriceTableUseCase`. [minPerHour] cho "từ X đ/giờ" ở thanh dưới; `null` khi chưa có giá. */
data class PriceTable(
    val groups: List<PriceDayGroup>,
    val selected: SelectedWindowPrice?,
    val minPerHour: Long?,
)
