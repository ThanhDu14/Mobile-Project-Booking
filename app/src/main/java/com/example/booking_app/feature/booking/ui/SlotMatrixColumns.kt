package com.example.booking_app.feature.booking.ui

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Độ rộng mỗi cột sân con trong lưới chọn giờ (spec 040).
 *
 * Tính từ bề ngang thật của lưới và mật độ màn hình nên đúng với mọi thiết bị:
 * - Cơ sở ít sân (≤ [ALWAYS_FILL_COURTS]) luôn dàn vừa màn hình, các cột rộng bằng nhau.
 * - Nhiều sân nhưng màn hình đủ rộng cho mọi cột ở độ rộng chuẩn (máy tính bảng, màn ngang): cũng dàn vừa.
 * - Khi dàn vừa, ô không rộng quá `maxCellDp` nếu có (màn ngang dùng [LANDSCAPE_MAX_CELL_DP]); lưới được căn giữa.
 * - Còn lại (nhiều sân, phải cuộn ngang): chọn độ rộng gần [FIXED_CELL_DP] nhất sao cho màn hình
 *   thấy đúng N cột rưỡi. Cột bị cắt đôi ở mép phải cho người dùng biết còn sân để cuộn sang.
 */
object SlotMatrixColumns {
    /** Độ rộng chuẩn của một ô (chưa tính margin), khớp item_slot_matrix_cell.xml. */
    const val FIXED_CELL_DP = 80

    /** Margin mỗi bên của ô và header, khớp layout_margin = 3dp. */
    const val CELL_MARGIN_DP = 3

    /** Số sân tối đa luôn được dàn vừa màn hình. */
    const val ALWAYS_FILL_COURTS = 5

    /** Ô rộng nhất khi xoay ngang, tránh vài sân thành dải ô quá dài. */
    const val LANDSCAPE_MAX_CELL_DP = 160

    /**
     * @param availablePx bề ngang dành cho lưới (đã trừ padding), px.
     * @param maxCellDp độ rộng ô tối đa khi dàn vừa; null = không giới hạn.
     * @return độ rộng ô (chưa tính margin), px.
     */
    fun cellWidthPx(availablePx: Int, courtCount: Int, density: Float, maxCellDp: Int? = null): Int {
        fun dp(value: Int) = (value * density).roundToInt()
        val fixed = dp(FIXED_CELL_DP)
        if (courtCount <= 0 || availablePx <= 0) return fixed
        val margins = 2 * dp(CELL_MARGIN_DP)

        val fill = availablePx / courtCount - margins
        if (fill >= fixed || courtCount <= ALWAYS_FILL_COURTS) {
            return if (maxCellDp != null) minOf(fill, dp(maxCellDp)) else fill
        }

        // Hé cột: số cột nguyên gần nhất để mỗi ô vẫn xấp xỉ độ rộng chuẩn, cộng thêm nửa cột.
        val whole = max(1, (availablePx.toFloat() / (fixed + margins) - 0.5f).roundToInt())
        return (availablePx / (whole + 0.5f)).toInt() - margins
    }
}
