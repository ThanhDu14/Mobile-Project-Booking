package com.example.booking_app.feature.account.domain.model

import java.time.Instant

/** Phần của hồ sơ chủ sân gần nhất (`ownerApplications`, spec 110) mà trang Tài khoản cần (research R6). */
data class OwnerApplicationSummary(
    val status: String,
    val submittedAt: Instant?,
    val rejectReason: String?,
)
