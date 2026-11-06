package com.example.booking_app.feature.admin.domain.model

data class AdminDistrict(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)
