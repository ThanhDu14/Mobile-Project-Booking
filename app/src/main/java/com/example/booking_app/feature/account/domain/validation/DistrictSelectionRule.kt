package com.example.booking_app.feature.account.domain.validation

/** Khu vực hay chơi: 0–5 phần tử, không trùng (FR-002). Sai thì hiện `account_error_district_max`. */
object DistrictSelectionRule {
    const val MAX = 5

    fun isValid(ids: List<String>): Boolean = ids.size <= MAX && ids.toSet().size == ids.size

    fun canAdd(ids: List<String>): Boolean = ids.size < MAX
}
