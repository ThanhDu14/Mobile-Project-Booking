package com.example.booking_app.core.session

/** Các trường của `users/{uid}` mà tính năng khác cần đọc (plan 080, spec 011). */
data class MyProfile(
    val displayName: String,
    /** `null` là chưa chọn trình độ. */
    val level: String?,
    val favoriteDistrictIds: List<String>,
    val avatarPath: String?,
    val role: String?,
    val accountStatus: String?,
)
