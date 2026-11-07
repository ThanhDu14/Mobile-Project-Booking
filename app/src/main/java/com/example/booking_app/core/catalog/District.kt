package com.example.booking_app.core.catalog

/** `districts/{id}` (spec 020 data-model §2.2). */
data class District(val id: String, val name: String, val sortOrder: Int, val isActive: Boolean)
