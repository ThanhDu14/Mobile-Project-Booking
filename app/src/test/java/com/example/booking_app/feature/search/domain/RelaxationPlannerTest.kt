package com.example.booking_app.feature.search.domain

import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class RelaxationPlannerTest {
    // 2026-11-15 10:00 giờ Việt Nam.
    private val now = ZonedDateTime.of(LocalDate.of(2026, 11, 15), LocalTime.of(10, 0), BookingWindow.ZONE)
    private val evening = BookingSearchWindow(LocalDate.of(2026, 11, 16), TimeOfDay(19 * 60), TimeOfDay(21 * 60))

    private fun kinds(probes: List<RelaxationProbe>) = probes.map { it.kind }

    @Test
    fun probesFollowPriorityAndOnlyWhatTheCriteriaUse() {
        val criteria = CourtSearchCriteria(
            districtId = "q10", priceMax = 100_000, amenityIds = linkedSetOf("parking", "shower", "drinks"), window = evening,
        )
        val probes = RelaxationPlanner.probes(criteria, now)
        assertEquals(
            listOf(
                RelaxationKind.PRICE, RelaxationKind.LATER, RelaxationKind.EARLIER,
                RelaxationKind.DROP_AMENITY, RelaxationKind.DROP_AMENITY, RelaxationKind.TOMORROW, RelaxationKind.ALL_DISTRICTS,
            ),
            kinds(probes),
        )
        assertEquals(null, probes[0].criteria.priceMax)
        assertEquals(TimeOfDay(20 * 60), probes[1].criteria.window!!.start)
        assertEquals(TimeOfDay(18 * 60), probes[2].criteria.window!!.start)
        // Tiện ích chọn sau cùng bị bỏ trước, tối đa 2.
        assertEquals(listOf("drinks", "shower"), probes.filter { it.kind == RelaxationKind.DROP_AMENITY }.map { it.amenityId })
        assertEquals(LocalDate.of(2026, 11, 17), probes[5].criteria.window!!.date)
        assertEquals(null, probes[6].criteria.districtId)

        // Chỉ có từ khóa: không có gì để nới.
        assertTrue(RelaxationPlanner.probes(CourtSearchCriteria(keyword = "Minh Tâm"), now).isEmpty())
    }

    @Test
    fun timeShiftsStayInsideTheDayAndTheFuture() {
        // 05:00–07:00 hôm nay lúc 10:00 đã qua; dời muộn 1 giờ vẫn đã qua; không thể sớm hơn 00:00.
        val late = BookingSearchWindow(LocalDate.of(2026, 11, 16), TimeOfDay(22 * 60), TimeOfDay(TimeOfDay.END_OF_DAY))
        assertEquals(
            listOf(RelaxationKind.EARLIER, RelaxationKind.TOMORROW),
            kinds(RelaxationPlanner.probes(CourtSearchCriteria(window = late), now)),
        )
        val todaySoon = BookingSearchWindow(LocalDate.of(2026, 11, 15), TimeOfDay(10 * 60 + 30), TimeOfDay(12 * 60 + 30))
        // 09:30 hôm nay đã qua nên không có "sớm hơn".
        assertEquals(
            listOf(RelaxationKind.LATER, RelaxationKind.TOMORROW),
            kinds(RelaxationPlanner.probes(CourtSearchCriteria(window = todaySoon), now)),
        )
        // Ngày thứ 30 là ngày cuối được đặt: không gợi ý ngày hôm sau.
        val lastDay = evening.copy(date = LocalDate.of(2026, 12, 15))
        assertTrue(RelaxationKind.TOMORROW !in kinds(RelaxationPlanner.probes(CourtSearchCriteria(window = lastDay), now)))
    }

    @Test
    fun priceGoesToTheNearestLevelWithVenues() {
        val criteria = CourtSearchCriteria(priceMax = 100_000)
        val probes = RelaxationPlanner.probes(criteria, now)
        val price = RelaxationPlanner.pick(probes, listOf(listOf(150_000L, 120_000L, null, 120_000L))).single()
        assertEquals(RelaxationKind.PRICE, price.kind)
        assertEquals(120_000L, price.criteria.priceMax) // mức thấp nhất có sân
        assertEquals(2, price.count)                     // hai sân 120.000; sân chưa có giá không tính
        // Giá lẻ làm tròn lên nghìn đồng.
        val odd = RelaxationPlanner.pick(probes, listOf(listOf(150_000L, 120_500L))).single()
        assertEquals(121_000L, odd.criteria.priceMax)
        assertEquals(1, odd.count)
    }

    @Test
    fun picksAtMostThreeWithResultsAndPrefersVariety() {
        val criteria = CourtSearchCriteria(
            districtId = "q10", priceMax = 100_000, amenityIds = linkedSetOf("parking"), window = evening,
        )
        val probes = RelaxationPlanner.probes(criteria, now)
        // PRICE, LATER, EARLIER, DROP_AMENITY, TOMORROW, ALL_DISTRICTS
        val prices = listOf(
            listOf(130_000L), listOf(90_000L, 80_000L), listOf(90_000L), emptyList(), listOf(70_000L), listOf(1L, 2L, 3L),
        )
        val picked = RelaxationPlanner.pick(probes, prices)
        // Mỗi nhóm một gợi ý trước: giá, giờ (muộn hơn), ngày mai. "Sớm hơn" cùng nhóm giờ nên nhường chỗ.
        assertEquals(listOf(RelaxationKind.PRICE, RelaxationKind.LATER, RelaxationKind.TOMORROW), picked.map { it.kind })
        assertEquals(listOf(1, 2, 1), picked.map { it.count })

        // Ít nhóm có kết quả thì lấy thêm cùng nhóm.
        val onlyTime = listOf(emptyList(), listOf(1L), listOf(2L), emptyList(), emptyList(), emptyList<Long?>())
        assertEquals(
            listOf(RelaxationKind.LATER, RelaxationKind.EARLIER),
            RelaxationPlanner.pick(probes, onlyTime).map { it.kind },
        )
        // Không cách nào có sân: không gợi ý, màn hình hiện trạng thái rỗng thường (US6-4).
        assertTrue(RelaxationPlanner.pick(probes, probes.map { emptyList() }).isEmpty())
    }
}
