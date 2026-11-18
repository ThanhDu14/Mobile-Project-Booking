package com.example.booking_app.feature.booking.ui

import kotlin.math.roundToInt

/**
 * Độ rộng mỗi cột sân con trong lưới chọn giờ (spec 040).
 *
 * Tính từ bề ngang thật của lưới và mật độ màn hình nên đúng với mọi thiết bị:
 * - Cơ sở ít sân (≤ [ALWAYS_FILL_COURTS]) luôn dàn vừa màn hình, các cột rộng bằng nhau.
 * - Nhiều sân nhưng màn hình đủ rộng cho mọi cột ở độ rộng chuẩn (máy tính bảng, màn ngang): cũng dàn vừa.
 * - Còn lại: giữ độ rộng chuẩn [FIXED_CELL_DP] và cuộn ngang như trước.
 */
object SlotMatrixColumns {
    /** Độ rộng chuẩn của một ô (chưa tính margin), khớp item_slot_matrix_cell.xml. */
    const val FIXED_CELL_DP = 80

    /** Margin mỗi bên của ô và header, khớp layout_margin = 3dp. */
    const val CELL_MARGIN_DP = 3

    /** Số sân tối đa luôn được dàn vừa màn hình. */
    const val ALWAYS_FILL_COURTS = 5

    /**
     * @param availablePx bề ngang dành cho lưới (đã trừ padding), px.
     * @return độ rộng ô (chưa tính margin), px.
     */
    fun cellWidthPx(availablePx: Int, courtCount: Int, density: Float): Int {
        fun dp(value: Int) = (value * density).roundToInt()
        val fixed = dp(FIXED_CELL_DP)
        if (courtCount <= 0 || availablePx <= 0) return fixed

        val fill = availablePx / courtCount - 2 * dp(CELL_MARGIN_DP)
        return when {
            fill >= fixed -> fill
            courtCount <= ALWAYS_FILL_COURTS -> fill
            else -> fixed
        }
    }
}
