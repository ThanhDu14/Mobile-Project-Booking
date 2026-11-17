package com.example.booking_app.feature.search.domain

import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.SlotMath
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

enum class CourtSearchSort { RATING, PRICE_ASC, NEAREST }

data class CourtSearchCriteria(
    val keyword: String = "",
    val districtId: String? = null,
    val priceMin: Long? = null,
    val priceMax: Long? = null,
    val amenityIds: Set<String> = emptySet(),
    val window: BookingSearchWindow? = null,
    val sort: CourtSearchSort = CourtSearchSort.RATING,
) {
    fun hasFilters(): Boolean = keyword.trim().length >= 2 || districtId != null || priceMin != null ||
        priceMax != null || amenityIds.isNotEmpty() || window != null

    fun acceptsPrice(price: Long?): Boolean = when {
        priceMin == null && priceMax == null -> true
        price == null -> false
        else -> (priceMin == null || price >= priceMin) && (priceMax == null || price <= priceMax)
    }
}

data class CourtSearchPage(val venues: List<HomeVenue>, val complete: Boolean, val capped: Boolean)

interface CourtSearchRepository {
    fun search(criteria: CourtSearchCriteria, origin: GeoPoint?): Flow<CourtSearchPage>

    /**
     * Gợi ý nới điều kiện (spec 022 US6): với mỗi biến thể, giá mỗi giờ của từng sân phù hợp
     * (giá theo khung giờ nếu có khung giờ, `null` khi sân chưa có giá). Cùng thứ tự với [variants].
     * Mặc định không có kết quả, để các repository giả trong test không phải cài.
     */
    suspend fun variantPrices(variants: List<CourtSearchCriteria>): List<List<Long?>> = variants.map { emptyList() }
}

internal fun normalizedSearchText(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd').replace(Regex("[^a-z0-9]+"), " ").trim()

internal fun matchesVenueName(keyword: String, name: String): Boolean {
    val tokens = normalizedSearchText(keyword).split(' ').filter(String::isNotEmpty)
    if (keyword.trim().length < 2 || tokens.isEmpty()) return true
    val words = normalizedSearchText(name).split(' ')
    return tokens.all { token -> words.any { it.startsWith(token) } }
}

internal data class OccupiedSlot(val courtId: String, val start: TimeOfDay, val status: String, val expiresAt: Long?) {
    fun isBusyAt(now: Long): Boolean = status == "BOOKED" || status == "BLOCKED" ||
        (status == "HELD" && (expiresAt == null || expiresAt > now))
}

internal fun hasContinuousCourt(
    courtIds: Set<String>,
    window: BookingSearchWindow,
    slots: List<OccupiedSlot>,
    now: Long,
): Boolean {
    val requested = SlotMath.slots(window.start, window.end)
    if (requested.size < 2) return false
    val busyStarts = slots.filter { it.isBusyAt(now) }.groupBy({ it.courtId }, { it.start })
    return courtIds.any { courtId -> requested.none { it in busyStarts[courtId].orEmpty() } }
}
