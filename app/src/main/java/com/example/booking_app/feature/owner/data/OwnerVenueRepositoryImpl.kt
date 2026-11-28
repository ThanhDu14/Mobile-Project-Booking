package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import javax.inject.Inject

class OwnerVenueRepositoryImpl @Inject constructor(
    private val source: FirestoreOwnerVenueDataSource,
    private val syncApi: SyncVenuePriceApi,
    private val slotBlocksApi: ManageSlotBlocksApi,
    private val photos: OwnerVenuePhotoApi,
    private val courtsApi: OwnerCourtApi,
) : OwnerVenueRepository {
    override suspend fun getOwnedVenues(): Result<List<OwnerVenue>> = runCatching { source.getOwnedVenues() }
    override suspend fun getVenue(venueId: String): Result<OwnerVenue> = runCatching { source.getVenue(venueId) }
    override suspend fun updateVenue(venueId: String, fields: VenueEditableFields): Result<Unit> = runCatching {
        val mapping = if (fields.photoPaths.any { !it.startsWith("http") && it.split('/').size==3 }) photos.publishLegacy(venueId) else emptyMap()
        source.updateVenue(venueId, fields.copy(photoPaths=fields.photoPaths.map { mapping[it] ?: it }))
    }
    override suspend fun hideVenue(venueId: String): Result<Unit> = runCatching { source.hideVenue(venueId) }
    override suspend fun updatePayoutAccount(venueId: String, account: PayoutAccount): Result<Unit> =
        runCatching { source.updatePayoutAccount(venueId, account) }
    override suspend fun setAcceptsRecurring(venueId: String, accepts: Boolean): Result<Unit> = runCatching { source.setAcceptsRecurring(venueId, accepts) }
    override suspend fun uploadPhoto(venueId: String, mime: String, bytes: ByteArray): Result<String> = runCatching { photos.upload(venueId,mime,bytes) }
    override suspend fun getCourts(venueId: String): Result<List<OwnerCourt>> = runCatching { source.getCourts(venueId) }
    override suspend fun createCourt(venueId: String, fields: CourtFields): Result<String> = createCourt(venueId, fields, java.util.UUID.randomUUID().toString())
    override suspend fun createCourt(venueId: String, fields: CourtFields, requestId: String): Result<String> = runCatching { courtsApi.save(venueId, requestId, fields, create = true) }
    override suspend fun updateCourt(venueId: String, courtId: String, fields: CourtFields): Result<Unit> = runCatching { courtsApi.save(venueId, courtId, fields, create = false); Unit }
    override suspend fun swapCourtOrder(venueId: String, first: OwnerCourt, second: OwnerCourt): Result<Unit> = runCatching { courtsApi.reorder(venueId,first,second) }
    override suspend fun deactivateCourt(venueId: String, courtId: String): Result<Unit> = runCatching {
        val court = source.getCourts(venueId).first { it.id == courtId }
        courtsApi.save(venueId, courtId, CourtFields(court.name, court.surfaceType, false, court.sortOrder), create = false)
        Unit
    }
    override suspend fun getPriceRules(venueId: String): Result<List<OwnerPriceRule>> = runCatching { source.getPriceRules(venueId) }

    override suspend fun createPriceRule(venueId: String, rule: PriceRuleInput): Result<String> = runCatching {
        checkNotNull(syncApi.mutate(venueId, "CREATE", rule = rule).getOrThrow())
    }

    override suspend fun updatePriceRule(venueId: String, ruleId: String, rule: PriceRuleInput): Result<Unit> = runCatching {
        syncApi.mutate(venueId, "UPDATE", ruleId, rule).getOrThrow()
        Unit
    }

    override suspend fun deletePriceRule(venueId: String, ruleId: String): Result<Unit> = runCatching {
        syncApi.mutate(venueId, "DELETE", ruleId).getOrThrow()
        Unit
    }

    override suspend fun getSlots(venueId: String, date: String): Result<List<OwnerSlot>> = runCatching { source.getSlots(venueId, date) }

    override suspend fun lockSlots(
        venueId: String,
        courtId: String,
        date: String,
        slotIds: List<String>,
        reason: String
    ): Result<Unit> = slotBlocksApi.lockSlots(venueId, courtId, date, slotIds, reason)

    override suspend fun unlockSlots(
        venueId: String,
        courtId: String,
        slotIds: List<String>
    ): Result<Unit> = slotBlocksApi.unlockSlots(venueId, courtId, slotIds)
}
