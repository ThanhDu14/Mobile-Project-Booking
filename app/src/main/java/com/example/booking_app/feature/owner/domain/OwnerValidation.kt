package com.example.booking_app.feature.owner.domain

import com.google.firebase.firestore.GeoPoint

data class OwnerFieldError(val field: String, val message: String)

data class VenueInput(
    val name: String,
    val address: String,
    val districtId: String,
    val location: GeoPoint?,
    val openTime: String,
    val closeTime: String,
    val phone: String,
    val amenityIds: List<String>,
    val photoPaths: List<String>,
    val courtCount: Int,
    val acceptsRecurring: Boolean = true
)

data class CourtInput(
    val name: String,
    val surfaceType: String,
    val sortOrder: Int
)

data class PriceRuleInput(
    val daysOfWeek: List<Int>,
    val startTime: String,
    val endTime: String,
    val pricePerHour: Long,
    val label: String
)

data class SlotBlockInput(
    val courtId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val reason: String
)

object OwnerValidation {
    private val phonePattern = Regex("^(0[35789][0-9]{8}|\\+84[35789][0-9]{8})$")
    private val datePattern = Regex("^[0-9]{8}$")
    private val labels = setOf("NORMAL", "PEAK", "WEEKEND")
    private val blockReasons = setOf("MAINTENANCE", "EVENT", "WALK_IN")
    private val surfaces = setOf("WOOD", "PU", "MAT")

    fun venue(input: VenueInput): List<OwnerFieldError> = buildList {
        required(input.name, "name")
        required(input.address, "address")
        required(input.districtId, "districtId")
        if (input.location == null || input.location.latitude !in -90.0..90.0 || input.location.longitude !in -180.0..180.0) {
            add(OwnerFieldError("location", "Vị trí không hợp lệ"))
        }
        time(input.openTime, "openTime")
        time(input.closeTime, "closeTime")
        if (validTime(input.openTime) && validTime(input.closeTime) && input.openTime >= input.closeTime) {
            add(OwnerFieldError("closeTime", "Giờ đóng cửa phải sau giờ mở cửa"))
        }
        if (!phonePattern.matches(input.phone)) add(OwnerFieldError("phone", "Số điện thoại Việt Nam không hợp lệ"))
        if (input.photoPaths.isEmpty()) add(OwnerFieldError("photoPaths", "Cần ít nhất một ảnh"))
        if (input.courtCount <= 0) add(OwnerFieldError("courtCount", "Số sân phải là số nguyên dương"))
    }

    fun court(input: CourtInput): List<OwnerFieldError> = buildList {
        required(input.name, "name")
        if (input.surfaceType !in surfaces) add(OwnerFieldError("surfaceType", "Loại mặt sân không hợp lệ"))
        if (input.sortOrder < 0) add(OwnerFieldError("sortOrder", "Thứ tự hiển thị không hợp lệ"))
    }

    fun priceRule(input: PriceRuleInput, existing: List<PriceRuleInput> = emptyList()): List<OwnerFieldError> = buildList {
        if (input.daysOfWeek.isEmpty() || input.daysOfWeek.any { it !in 1..7 } || input.daysOfWeek.toSet().size != input.daysOfWeek.size) {
            add(OwnerFieldError("daysOfWeek", "Ngày trong tuần phải là các giá trị 1–7, không trùng"))
        }
        halfHourTime(input.startTime, "startTime")
        halfHourTime(input.endTime, "endTime")
        if (validHalfHour(input.startTime) && validHalfHour(input.endTime) && input.startTime >= input.endTime) {
            add(OwnerFieldError("endTime", "Giờ kết thúc phải sau giờ bắt đầu và không qua nửa đêm"))
        }
        if (input.pricePerHour <= 0 || input.pricePerHour % 2_000L != 0L) {
            add(OwnerFieldError("pricePerHour", "Giá phải dương và chia hết cho 2.000"))
        }
        if (input.label !in labels) add(OwnerFieldError("label", "Nhãn bảng giá không hợp lệ"))
        if (validDays(input.daysOfWeek) && validHalfHour(input.startTime) && validHalfHour(input.endTime) && input.startTime < input.endTime) {
            val overlaps = existing.any { other ->
                other.daysOfWeek.any(input.daysOfWeek::contains) && input.startTime < other.endTime && other.startTime < input.endTime
            }
            if (overlaps) add(OwnerFieldError("timeRange", "Khoảng giờ bị chồng với bảng giá hiện có"))
        }
    }

    fun slotBlock(input: SlotBlockInput): List<OwnerFieldError> = buildList {
        required(input.courtId, "courtId")
        if (!datePattern.matches(input.date)) add(OwnerFieldError("date", "Ngày phải có dạng yyyyMMdd"))
        halfHourTime(input.startTime, "startTime")
        halfHourTime(input.endTime, "endTime")
        if (validHalfHour(input.startTime) && validHalfHour(input.endTime) && input.startTime >= input.endTime) {
            add(OwnerFieldError("endTime", "Khoảng khóa phải tăng và không qua nửa đêm"))
        }
        if (input.reason !in blockReasons) add(OwnerFieldError("reason", "Lý do khóa không hợp lệ"))
    }

    fun slotIds(input: SlotBlockInput): List<String> {
        require(slotBlock(input).isEmpty()) { "Slot block input is invalid" }
        val start = minutes(input.startTime)
        val end = minutes(input.endTime)
        return (start until end step 30).map { "${input.courtId}_${input.date}_${formatTime(it)}" }
    }

    private fun MutableList<OwnerFieldError>.required(value: String, field: String) {
        if (value.isBlank()) add(OwnerFieldError(field, "Không được để trống"))
    }

    private fun MutableList<OwnerFieldError>.time(value: String, field: String) {
        if (!validTime(value)) add(OwnerFieldError(field, "Giờ phải có dạng HHmm hợp lệ"))
    }

    private fun MutableList<OwnerFieldError>.halfHourTime(value: String, field: String) {
        if (!validHalfHour(value)) add(OwnerFieldError(field, "Giờ phải là mốc 00 hoặc 30 phút"))
    }

    private fun validDays(days: List<Int>) = days.isNotEmpty() && days.all { it in 1..7 } && days.toSet().size == days.size
    private fun validTime(value: String): Boolean = value.length == 4 && value.all(Char::isDigit) && value.substring(0, 2).toInt() in 0..23 && value.substring(2).toInt() in 0..59
    private fun validHalfHour(value: String): Boolean = validTime(value) && value.substring(2).toInt() in setOf(0, 30)
    private fun minutes(value: String) = value.substring(0, 2).toInt() * 60 + value.substring(2).toInt()
    private fun formatTime(minutes: Int) = "%02d%02d".format(minutes / 60, minutes % 60)
}
