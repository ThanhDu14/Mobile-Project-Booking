package com.example.booking_app.feature.booking.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotMatrixColumnsTest {
    // Galaxy A54: 1080 px, mật độ 2,8125 → khoảng 384 dp; lưới trừ 16 dp mỗi bên còn 352 dp = 990 px.
    private val density = 2.8125f
    private val available = 990
    private fun dp(value: Int) = Math.round(value * density)
    private val margins = 2 * dp(SlotMatrixColumns.CELL_MARGIN_DP)

    @Test
    fun fewCourtsShareTheWholeWidth() {
        for (courts in 1..5) {
            val cell = SlotMatrixColumns.cellWidthPx(available, courts, density)
            val used = courts * (cell + margins)
            // Dàn vừa: không tràn, phần dư chỉ là lẻ px do chia.
            assertTrue("$courts sân dùng $used px", used <= available && available - used < courts)
        }
        // 3 sân: mỗi ô rộng hơn ô chuẩn 80 dp.
        assertTrue(SlotMatrixColumns.cellWidthPx(available, 3, density) > dp(80))
    }

    @Test
    fun manyCourtsKeepTheFixedWidthAndScroll() {
        assertEquals(dp(80), SlotMatrixColumns.cellWidthPx(available, 6, density))
        assertEquals(dp(80), SlotMatrixColumns.cellWidthPx(available, 12, density))
    }

    @Test
    fun wideScreensFillWhenEveryColumnFitsAtTheFixedWidth() {
        // Màn ngang khoảng 800 dp: 8 sân vẫn đủ chỗ cho 86 dp mỗi cột nên dàn đều.
        val landscape = dp(800)
        val cell = SlotMatrixColumns.cellWidthPx(landscape, 8, density)
        assertEquals(landscape / 8 - margins, cell)
        assertTrue(cell >= dp(80))
    }

    @Test
    fun fewCourtsFillOnEveryCommonScreenWidth() {
        // Máy nhỏ 320 dp, phổ biến 360/384/412 dp, gập mở 600 dp, tablet 800 dp; mật độ khác nhau.
        for ((widthDp, d) in listOf(320 to 2f, 360 to 3f, 384 to 2.8125f, 412 to 2.625f, 600 to 2.5f, 800 to 2f)) {
            val avail = Math.round((widthDp - 32) * d)
            val m = 2 * Math.round(SlotMatrixColumns.CELL_MARGIN_DP * d)
            for (courts in 1..5) {
                val used = courts * (SlotMatrixColumns.cellWidthPx(avail, courts, d) + m)
                assertTrue("$widthDp dp, $courts sân: $used/$avail px", used <= avail && avail - used < courts)
            }
        }
    }

    @Test
    fun unknownWidthOrNoCourtsKeepsTheFixedWidth() {
        assertEquals(dp(80), SlotMatrixColumns.cellWidthPx(0, 3, density))
        assertEquals(dp(80), SlotMatrixColumns.cellWidthPx(available, 0, density))
    }
}
