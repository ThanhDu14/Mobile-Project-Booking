package com.example.booking_app.feature.payment.domain.repository

import com.example.booking_app.feature.payment.domain.model.PayoutAccount

interface VenuePayoutRepository {
    /** `venues/{venueId}.payoutAccount`; `null` khi chưa nhập đủ 4 trường (data-model §1.7). */
    suspend fun payoutAccount(venueId: String): Result<PayoutAccount?>
}
