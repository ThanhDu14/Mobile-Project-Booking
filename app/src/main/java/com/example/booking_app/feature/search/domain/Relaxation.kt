package com.example.booking_app.feature.search.domain

import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import java.time.ZonedDateTime

/** Các cách nới điều kiện khi tìm không ra sân (spec 022 US6, FR-022). */
enum class RelaxationKind { PRICE, LATER, EARLIER, DROP_AMENITY, TOMORROW, ALL_DISTRICTS }

/** Một gợi ý đã có kết quả: bấm vào thì áp dụng [criteria]. */
data class Relaxation(
    val kind: RelaxationKind,
    val criteria: CourtSearchCriteria,
    val count: Int,
    /** Tiện ích bị bỏ, chỉ có với [RelaxationKind.DROP_AMENITY]. */
    val amenityId: String? = null,
)

/**
 * Biến thể cần đếm thử. Với [RelaxationKind.PRICE], [criteria] bỏ trần giá; mức giá gợi ý được
 * chọn sau khi biết giá của các sân phù hợp ([RelaxationPlanner.pick]).
 */
data class RelaxationProbe(val kind: RelaxationKind, val criteria: CourtSearchCriteria, val amenityId: String? = null)

/**
 * Lập và chọn gợi ý nới điều kiện, hoàn toàn trong app: không gọi AI, không trừ lượt (US6-2).
 * Người dùng chọn (2026-11-18): áp dụng cho mọi lần tìm có lọc, nới giá tới mức gần nhất có sân,
 * thêm "Mọi quận" và "Cùng giờ ngày mai" ngoài các cách trong spec.
 */
object RelaxationPlanner {
    const val MAX_SUGGESTIONS = 3
    private const val SHIFT_MINUTES = 60
    private const val MAX_DROPPED_AMENITIES = 2

    /** Các biến thể theo thứ tự ưu tiên. Biến thể không còn đặt được (giờ đã qua, quá 30 ngày) bị bỏ. */
    fun probes(criteria: CourtSearchCriteria, now: ZonedDateTime): List<RelaxationProbe> {
        val probes = mutableListOf<RelaxationProbe>()
        if (criteria.priceMax != null) {
            probes += RelaxationProbe(RelaxationKind.PRICE, criteria.copy(priceMax = null))
        }
        criteria.window?.let { window ->
            if (window.end.minutes + SHIFT_MINUTES <= TimeOfDay.END_OF_DAY) {
                val later = window.copy(start = window.start.plusMinutes(SHIFT_MINUTES), end = window.end.plusMinutes(SHIFT_MINUTES))
                if (later.isBookableAt(now)) probes += RelaxationProbe(RelaxationKind.LATER, criteria.copy(window = later))
            }
            if (window.start.minutes - SHIFT_MINUTES >= 0) {
                val earlier = window.copy(start = window.start.plusMinutes(-SHIFT_MINUTES), end = window.end.plusMinutes(-SHIFT_MINUTES))
                if (earlier.isBookableAt(now)) probes += RelaxationProbe(RelaxationKind.EARLIER, criteria.copy(window = earlier))
            }
        }
        // Tiện ích chọn sau cùng được bỏ trước; thứ tự Set giữ thứ tự người dùng chọn.
        criteria.amenityIds.reversed().take(MAX_DROPPED_AMENITIES).forEach { id ->
            probes += RelaxationProbe(RelaxationKind.DROP_AMENITY, criteria.copy(amenityIds = criteria.amenityIds - id), id)
        }
        criteria.window?.let { window ->
            val tomorrow = window.copy(date = window.date.plusDays(1))
            if (BookingWindow.isDateAllowed(tomorrow.date, now.toLocalDate()) && tomorrow.isBookableAt(now)) {
                probes += RelaxationProbe(RelaxationKind.TOMORROW, criteria.copy(window = tomorrow))
            }
        }
        if (criteria.districtId != null) {
            probes += RelaxationProbe(RelaxationKind.ALL_DISTRICTS, criteria.copy(districtId = null))
        }
        return probes
    }

    /**
     * Chọn tối đa [limit] gợi ý có ít nhất 1 sân (US6-1). Lượt đầu lấy mỗi nhóm một gợi ý (giá, giờ,
     * tiện ích, ngày, khu vực) để gợi ý đa dạng; còn chỗ thì lấy thêm theo thứ tự ưu tiên.
     * [prices] cùng thứ tự với [probes]: giá mỗi giờ của từng sân phù hợp (`null` khi sân chưa có giá).
     */
    fun pick(probes: List<RelaxationProbe>, prices: List<List<Long?>>, limit: Int = MAX_SUGGESTIONS): List<Relaxation> {
        val candidates = probes.zip(prices).mapNotNull { (probe, found) -> toRelaxation(probe, found) }
        val chosen = mutableListOf<Relaxation>()
        val groups = mutableSetOf<Int>()
        for (relaxation in candidates) {
            if (chosen.size == limit) break
            if (groups.add(group(relaxation.kind))) chosen += relaxation
        }
        for (relaxation in candidates) {
            if (chosen.size == limit) break
            if (relaxation !in chosen) chosen += relaxation
        }
        return candidates.filter { it in chosen }
    }

    private fun toRelaxation(probe: RelaxationProbe, found: List<Long?>): Relaxation? {
        if (probe.kind != RelaxationKind.PRICE) {
            return found.size.takeIf { it > 0 }?.let { Relaxation(probe.kind, probe.criteria, it, probe.amenityId) }
        }
        // Mức giá gần nhất có sân: giá thấp nhất trong các sân tìm được (đều vượt trần cũ, vì biến thể
        // chỉ khác bản gốc ở trần giá mà bản gốc ra 0 sân), làm tròn lên nghìn đồng.
        val level = found.filterNotNull().minOrNull() ?: return null
        val rounded = (level + 999) / 1000 * 1000
        val count = found.count { it != null && it <= rounded }
        return Relaxation(RelaxationKind.PRICE, probe.criteria.copy(priceMax = rounded), count)
    }

    private fun group(kind: RelaxationKind): Int = when (kind) {
        RelaxationKind.PRICE -> 0
        RelaxationKind.LATER, RelaxationKind.EARLIER -> 1
        RelaxationKind.DROP_AMENITY -> 2
        RelaxationKind.TOMORROW -> 3
        RelaxationKind.ALL_DISTRICTS -> 4
    }
}
