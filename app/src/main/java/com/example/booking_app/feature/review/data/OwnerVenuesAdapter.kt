package com.example.booking_app.feature.review.data

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.review.domain.OwnerVenuesProvider
import com.example.booking_app.feature.review.domain.model.VenueOption
import javax.inject.Inject

/**
 * Nối với interface domain của spec 111 (D) theo constitution nguyên tắc I: chỉ dùng
 * `feature/owner/domain`, không import `ui/` hay `data/` của nhóm khác.
 */
class OwnerVenuesAdapter @Inject constructor(
    private val ownerVenues: OwnerVenueRepository,
) : OwnerVenuesProvider {

    override suspend fun ownedVenues(): List<VenueOption> =
        ownerVenues.getOwnedVenues().getOrThrow()
            .filter { it.status != STATUS_DELETED }
            .map { VenueOption(it.id, it.name) }

    private companion object {
        const val STATUS_DELETED = "DELETED"
    }
}
