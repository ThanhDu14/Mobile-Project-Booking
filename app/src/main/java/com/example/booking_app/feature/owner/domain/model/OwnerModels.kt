package com.example.booking_app.feature.owner.domain.model

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.Timestamp

data class OwnerVenue(
    val id: String,
    val ownerId: String,
    val name: String,
    val address: String,
    val districtId: String,
    val location: GeoPoint?,
    val openTime: String,
    val closeTime: String,
    val phone: String,
    val amenityIds: List<String>,
    val photoPaths: List<String>,
    val courtCount: Int,
    val acceptsRecurring: Boolean,
    val status: String
)

data class VenueEditableFields(
    val name: String,
    val address: String,
    val districtId: String,
    val location: GeoPoint?,
    val openTime: String,
    val closeTime: String,
    val phone: String,
    val amenityIds: List<String>,
    val photoPaths: List<String>,
    val courtCount: Int,
    val acceptsRecurring: Boolean,
    val status: String
)

data class OwnerCourt(
    val id: String,
    val name: String,
    val surfaceType: String,
    val isActive: Boolean,
    val sortOrder: Int
)

data class CourtFields(
    val name: String,
    val surfaceType: String,
    val isActive: Boolean,
    val sortOrder: Int
)

data class OwnerPriceRule(
    val id: String,
    val daysOfWeek: List<Int>,
    val startTime: String,
    val endTime: String,
    val pricePerHour: Long,
    val label: String
)

data class OwnerSlot(
    val id: String,
    val venueId: String,
    val courtId: String,
    val date: String,
    val startAt: Timestamp?,
    val endAt: Timestamp?,
    val status: String,
    val blockReason: String?,
    val bookingId: String?,
    val holdBy: String?
)
