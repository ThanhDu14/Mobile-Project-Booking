package com.example.booking_app.feature.owner.fixture

import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.google.firebase.firestore.GeoPoint

object OwnerFixtures {
    const val OWNER_A = "owner-a"
    const val OWNER_B = "owner-b"
    const val PLAYER = "player"
    const val VENUE_A = "venue-a"

    val venue = OwnerVenue(
        id = VENUE_A, ownerId = OWNER_A, name = "Sân A", address = "Quận 1",
        districtId = "district-1", location = GeoPoint(10.77, 106.7),
        openTime = "0600", closeTime = "2200", phone = "0912345678",
        amenityIds = emptyList(), photoPaths = listOf("venue-a/photo.jpg"),
        courtCount = 2, acceptsRecurring = true, status = "ACTIVE"
    )

    val courts = listOf(
        OwnerCourt("court-1", "Sân 1", "MAT", true, 0),
        OwnerCourt("court-2", "Sân 2", "PU", false, 1)
    )

    val ownedVenueDocument = mapOf("ownerId" to OWNER_A, "status" to "ACTIVE")
    val courtDocument = mapOf("name" to "Sân 1", "surfaceType" to "MAT", "isActive" to true, "sortOrder" to 0)
    val priceRule = OwnerPriceRule("rule-1", listOf(1, 2), "0600", "0800", 40_000, "NORMAL")
    val absentSlotDocument: Map<String, Any?>? = null
    val heldSlotDocument = slot("HELD", null)
    val bookedSlotDocument = slot("BOOKED", null)
    val blockedSlotDocument = slot("BLOCKED", "MAINTENANCE")

    fun editableVenue(acceptsRecurring: Boolean = venue.acceptsRecurring, status: String = venue.status) = VenueEditableFields(
        venue.name, venue.address, venue.districtId, venue.location, venue.openTime, venue.closeTime,
        venue.phone, venue.amenityIds, venue.photoPaths, venue.courtCount, acceptsRecurring, status
    )

    fun courtFields(court: OwnerCourt = courts.first()) = CourtFields(court.name, court.surfaceType, court.isActive, court.sortOrder)

    private fun slot(status: String, blockReason: String?) = mapOf(
        "venueId" to VENUE_A, "courtId" to "court-1", "date" to "20261029",
        "startAt" to "0600", "endAt" to "0630", "status" to status, "blockReason" to blockReason
    )
}
