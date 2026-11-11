package com.example.booking_app.feature.review.domain

import com.example.booking_app.feature.review.domain.model.VenueOption

/**
 * Danh sách cơ sở của chủ sân đang đăng nhập (US4). Lấy qua interface của spec 111
 * (`OwnerVenueRepository.getOwnedVenues()`), cài đặt ở
 * [com.example.booking_app.feature.review.data.OwnerVenuesAdapter].
 */
interface OwnerVenuesProvider {
    suspend fun ownedVenues(): List<VenueOption>
}
