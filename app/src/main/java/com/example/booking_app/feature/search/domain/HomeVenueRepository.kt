package com.example.booking_app.feature.search.domain

import com.example.booking_app.feature.search.domain.model.HomeVenue

interface HomeVenueRepository {
    /**
     * Mọi cơ sở đang hoạt động (tối đa [MAX_VENUES]), chưa sắp xếp. Ném [com.example.booking_app.core.network.AppError].
     * Trang chủ sắp xếp và chia trang trên máy (chiến lược S2 của research R3), nên không cần index ghép.
     */
    suspend fun activeVenues(): List<HomeVenue>

    companion object {
        const val MAX_VENUES = 150L
    }
}
