package com.example.booking_app.feature.admin.domain.model

data class AdminSystemStats(
    val totalUsers: Long = 0,
    val totalVenues: Long = 0,
    val totalBookings: Long = 0,
    val totalRevenue: Long = 0,
    val updatedAt: String? = null,
    val hasData: Boolean = false,
)
