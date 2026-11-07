package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.core.pricing.SlotPricing
import com.example.booking_app.feature.courtdetail.domain.PriceTableBuilder
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.PriceTable
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import com.example.booking_app.feature.courtdetail.domain.model.SelectedWindowPrice
import javax.inject.Inject

/**
 * Bảng giá và giá "Khung bạn chọn" (FR-008, FR-009). Giá khung tính bằng `SlotPricing` của `core/pricing`, giống
 * thẻ kết quả 020 và lưới giờ 040 (SC-002). [table] thuần để ViewModel dựng lại khi khung giờ hết hạn mà không
 * đọc lại Firestore.
 */
class LoadPriceTableUseCase @Inject constructor(private val repository: VenueDetailRepository) {

    suspend fun rules(venueId: String): List<PriceRule> = repository.loadPriceRules(venueId)

    suspend fun load(venueId: String, window: SearchWindow?): PriceTable = table(rules(venueId), window)

    fun table(rules: List<PriceRule>, window: SearchWindow?): PriceTable = PriceTable(
        groups = PriceTableBuilder.build(rules, window),
        selected = window?.let { w ->
            val perHour = SlotPricing.priceForWindow(rules, w.date, w.start, w.end)
                ?.let { total -> SlotPricing.perHour(total, w.start, w.end) }
            SelectedWindowPrice(w, perHour)
        },
        minPerHour = rules.filter { it.pricePerHour > 0 }.minOfOrNull { it.pricePerHour },
    )
}
