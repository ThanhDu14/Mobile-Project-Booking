package com.example.booking_app.feature.account.domain.model

/**
 * Khu vực trong danh mục `districts/{id}` (data-model §3). [isActive] = `false` thì không chọn mới được nhưng vẫn hiện
 * tên ở người đã chọn trước đó (thêm so với data-model §6 để hiện chip "Khu vực đã ngừng").
 */
data class District(val id: String, val name: String, val sortOrder: Int, val isActive: Boolean = true)
