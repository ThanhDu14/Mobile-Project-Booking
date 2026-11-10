package com.example.booking_app.core.catalog

/** `amenities/{id}` (spec 020 data-model §2.2). Biểu tượng là drawable `ic_amenity_<iconKey>` (`-` đổi thành `_`). */
data class Amenity(val id: String, val name: String, val iconKey: String, val sortOrder: Int, val isActive: Boolean)
