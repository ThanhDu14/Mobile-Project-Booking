package com.example.booking_app.feature.courtdetail

import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueCatalog
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Repository giả: test đẩy giá trị venue bằng [emit], đếm số lần gọi để kiểm tra "đọc lại một mục". */
class FakeVenueDetailRepository : VenueDetailRepository {

    private val venue = MutableSharedFlow<VenueResult>(replay = 1)

    /** Số người đang lắng nghe venue: về 0 nghĩa là listener đã được gỡ. */
    val venueSubscribers: StateFlow<Int> get() = venue.subscriptionCount

    var observeCalls = 0
    var lastCatalog: VenueCatalog? = null
    var loadCourtsCalls = 0
    var loadPriceRulesCalls = 0
    var fetchStatusCalls = 0

    var courts: () -> List<Court> = { emptyList() }
    var priceRules: () -> List<PriceRule> = { emptyList() }
    var serverStatus: suspend () -> VenueAvailability? = { VenueAvailability.ACTIVE }

    suspend fun emit(result: VenueResult) = venue.emit(result)

    override fun observeVenue(venueId: String, catalog: VenueCatalog?): Flow<VenueResult> {
        observeCalls++
        lastCatalog = catalog
        return venue
    }

    override suspend fun loadCourts(venueId: String): List<Court> {
        loadCourtsCalls++
        return courts()
    }

    override suspend fun loadPriceRules(venueId: String): List<PriceRule> {
        loadPriceRulesCalls++
        return priceRules()
    }

    override suspend fun fetchStatusFromServer(venueId: String): VenueAvailability? {
        fetchStatusCalls++
        return serverStatus()
    }
}
