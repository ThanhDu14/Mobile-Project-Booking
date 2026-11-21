package com.example.booking_app.feature.owner.domain

import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields

interface OwnerVenueRepository {
    suspend fun getOwnedVenues(): Result<List<OwnerVenue>>
    suspend fun getVenue(venueId: String): Result<OwnerVenue>
    suspend fun updateVenue(venueId: String, fields: VenueEditableFields): Result<Unit>
    suspend fun hideVenue(venueId: String): Result<Unit>
    suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean): Result<Unit>
    suspend fun uploadPhoto(venueId: String, mime: String, bytes: ByteArray): Result<String> =
        Result.failure(IllegalStateException("Chưa thể tải ảnh"))

    suspend fun getCourts(venueId: String): Result<List<OwnerCourt>>
    suspend fun createCourt(venueId: String, fields: CourtFields): Result<String>
    suspend fun createCourt(venueId: String, fields: CourtFields, requestId: String): Result<String> = createCourt(venueId, fields)
    suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields): Result<Unit>
    suspend fun deactivateCourt(venueId: String, courtId: String): Result<Unit>
    suspend fun swapCourtOrder(venueId: String, first: OwnerCourt, second: OwnerCourt): Result<Unit> =
        Result.failure(IllegalStateException("Không thể đổi thứ tự sân"))

    suspend fun getPriceRules(venueId: String): Result<List<OwnerPriceRule>>
    suspend fun createPriceRule(venueId: String, rule: PriceRuleInput): Result<String>
    suspend fun updatePriceRule(venueId: String, ruleId: String, rule: PriceRuleInput): Result<Unit>
    suspend fun deletePriceRule(venueId: String, ruleId: String): Result<Unit>

    suspend fun getSlots(venueId: String, date: String): Result<List<OwnerSlot>>
    suspend fun lockSlots(venueId: String, courtId: String, date: String, slotIds: List<String>, reason: String): Result<Unit>
    suspend fun unlockSlots(venueId: String, courtId: String, slotIds: List<String>): Result<Unit>
}
