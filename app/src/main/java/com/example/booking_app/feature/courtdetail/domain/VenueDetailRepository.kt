package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueCatalog
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import kotlinx.coroutines.flow.Flow

/** Đọc dữ liệu một cơ sở cho chi tiết sân (research R2). Chỉ đọc. */
interface VenueDetailRepository {

    /**
     * Lắng nghe `venues/{venueId}` (gỡ listener khi không còn người thu thập). [catalog] dùng để gắn tên quận và
     * tiện ích; `null` = danh mục tải lỗi.
     */
    fun observeVenue(venueId: String, catalog: VenueCatalog?): Flow<VenueResult>

    /** Ném [com.example.booking_app.core.network.AppError]. */
    suspend fun loadCourts(venueId: String): List<Court>

    suspend fun loadPriceRules(venueId: String): List<PriceRule>

    /** Đọc lại trạng thái từ server (FR-023); `null` khi mất mạng hoặc quá hạn. */
    suspend fun fetchStatusFromServer(venueId: String): VenueAvailability?
}
