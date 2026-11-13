package com.example.booking_app.feature.admin.domain.model

enum class AdminCategoryKind(val collection: String) { DISTRICTS("districts"), AMENITIES("amenities") }

data class AdminDistrict(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val kind: AdminCategoryKind = AdminCategoryKind.DISTRICTS,
)
