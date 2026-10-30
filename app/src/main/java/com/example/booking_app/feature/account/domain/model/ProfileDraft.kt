package com.example.booking_app.feature.account.domain.model

/** Bản đang sửa của hồ sơ; khác bản đã lưu là có thay đổi chưa lưu (FR-006). */
data class ProfileDraft(
    val displayName: String,
    val level: PlayerLevel?,
    val districtIds: List<String>,
)
