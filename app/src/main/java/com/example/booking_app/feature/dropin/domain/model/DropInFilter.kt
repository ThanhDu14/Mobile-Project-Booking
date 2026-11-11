package com.example.booking_app.feature.dropin.domain.model

import java.time.LocalDate

/** Bộ lọc danh sách buổi (FR-003, research R6). Phần logic chip và kiểm tra cài ở T017/T023. */
data class DropInFilter(
    val districtIds: Set<String> = emptySet(),
    val date: LocalDate? = null,
    val timeSlot: TimeSlot? = null,
    val level: Level? = null,
    val maxPrice: Long? = null,
) {
    val isEmpty: Boolean
        get() = districtIds.isEmpty() && date == null && timeSlot == null && level == null && maxPrice == null

    /** Danh sách chip theo thứ tự: quận, ngày, khung giờ, trình độ, giá. */
    fun chips(): List<FilterChip> = buildList {
        districtIds.forEach { add(FilterChip.District(it)) }
        date?.let { add(FilterChip.Date(it)) }
        timeSlot?.let { add(FilterChip.Slot(it)) }
        level?.let { add(FilterChip.Lvl(it)) }
        maxPrice?.let { add(FilterChip.MaxPrice(it)) }
    }

    /** Bỏ một chip, trả bộ lọc mới. */
    fun without(chip: FilterChip): DropInFilter = when (chip) {
        is FilterChip.District -> copy(districtIds = districtIds - chip.districtId)
        is FilterChip.Date -> if (date == chip.date) copy(date = null) else this
        is FilterChip.Slot -> if (timeSlot == chip.timeSlot) copy(timeSlot = null) else this
        is FilterChip.Lvl -> if (level == chip.level) copy(level = null) else this
        is FilterChip.MaxPrice -> if (maxPrice == chip.maxPrice) copy(maxPrice = null) else this
    }

    /** `null` nếu hợp lệ; ngược lại là lỗi (ví dụ quá [MAX_DISTRICTS] quận). */
    fun validate(): FilterError? =
        if (districtIds.size > MAX_DISTRICTS) FilterError.TooManyDistricts(MAX_DISTRICTS) else null

    companion object {
        /** Giới hạn toán tử `in` của Firestore (research R6). */
        const val MAX_DISTRICTS = 15
    }
}

sealed interface FilterChip {
    data class District(val districtId: String) : FilterChip
    data class Date(val date: LocalDate) : FilterChip
    data class Slot(val timeSlot: TimeSlot) : FilterChip
    data class Lvl(val level: Level) : FilterChip
    data class MaxPrice(val maxPrice: Long) : FilterChip
}

sealed interface FilterError {
    data class TooManyDistricts(val max: Int) : FilterError
}
