package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import javax.inject.Inject

class OwnerVenueRepositoryImpl @Inject constructor(
    private val source: FirestoreOwnerVenueDataSource
) : OwnerVenueRepository {
    override suspend fun getOwnedVenues(): Result<List<OwnerVenue>> = runCatching { source.getOwnedVenues() }
    override suspend fun getVenue(venueId: String): Result<OwnerVenue> = runCatching { source.getVenue(venueId) }
    override suspend fun updateVenue(venueId: String, fields: VenueEditableFields): Result<Unit> = runCatching { source.updateVenue(venueId, fields) }
    override suspend fun hideVenue(venueId: String): Result<Unit> = runCatching { source.hideVenue(venueId) }
    override suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean): Result<Unit> = runCatching { source.setAcceptsRecurring(venueId, accepts) }
    override suspend fun getCourts(venueId: String): Result<List<OwnerCourt>> = runCatching { source.getCourts(venueId) }
    override suspend fun createCourt(venueId: String, fields: CourtFields): Result<String> = runCatching { source.createCourt(venueId, fields) }
    override suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields): Result<Unit> = runCatching { source.updateCourt(venueId, courtId, fields) }
    override suspend fun deactivateCourt(venueId: String, courtId: String): Result<Unit> = runCatching {
        val court = source.getCourts(venueId).first { it.id == courtId }
        source.updateCourt(venueId, courtId, CourtFields(court.name, court.surfaceType, false, court.sortOrder))
    }
    override suspend fun getPriceRules(venueId: String): Result<List<OwnerPriceRule>> = runCatching { source.getPriceRules(venueId) }
    override suspend fun getSlots(venueId: String, date: String): Result<List<OwnerSlot>> = runCatching { source.getSlots(venueId, date) }

}
