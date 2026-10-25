package com.example.booking_app.core.session

import java.time.Instant

/**
 * Các trường của `users/{uid}` mà tính năng khác cần đọc (plan 080, spec 011).
 * Trường thêm ở spec 011 có giá trị mặc định để code đã tạo `MyProfile` không phải sửa.
 */
data class MyProfile(
    val displayName: String,
    /** `null` là chưa chọn trình độ. */
    val level: String?,
    val favoriteDistrictIds: List<String>,
    val avatarPath: String?,
    val role: String?,
    val accountStatus: String?,
    // [011] research R1
    val email: String? = null,
    val phone: String? = null,
    /** Số dư ví giả lập, VND (FR-001a). */
    val walletBalance: Long = 0,
    /** `NONE`, `PENDING`, `APPROVED`, `REJECTED` (research R6). */
    val ownerStatus: String = "NONE",
    val ownerRevokedReason: String? = null,
    /** Khác `null` là tài khoản đang được xóa (spec 011 R11). */
    val deletionRequestedAt: Instant? = null,
)
