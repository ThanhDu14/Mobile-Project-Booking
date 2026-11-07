package com.example.booking_app.core.ui.photo

/**
 * Phép tính thuần của [ZoomableImageView] (spec 030 research R8), có unit test.
 *
 * Toạ độ "nội dung" là ảnh đã vừa khung (fit center) ở tỉ lệ 1×, đặt giữa khung. Điểm nội dung `x` hiện ở
 * `x * scale + tx` trên màn hình.
 */
object ZoomMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    const val DOUBLE_TAP_SCALE = 2.5f
    private const val EPSILON = 0.01f

    data class Transform(val scale: Float, val tx: Float, val ty: Float) {
        /** Điểm nội dung đang nằm dưới điểm màn hình `(x, y)`. */
        fun toContent(x: Float, y: Float): Pair<Float, Float> = (x - tx) / scale to (y - ty) / scale

        companion object {
            val IDENTITY = Transform(1f, 0f, 0f)
        }
    }

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Nhân tỉ lệ với [factor] (đã kẹp) quanh tiêu điểm, giữ nguyên điểm nội dung dưới ngón tay. */
    fun zoomAround(t: Transform, factor: Float, focusX: Float, focusY: Float): Transform {
        val newScale = clampScale(t.scale * factor)
        val ratio = newScale / t.scale
        return Transform(
            scale = newScale,
            tx = focusX - (focusX - t.tx) * ratio,
            ty = focusY - (focusY - t.ty) * ratio,
        )
    }

    /**
     * Kẹp vị trí để không lộ nền: chiều nào ảnh lớn hơn khung thì mép ảnh không vào trong khung;
     * chiều nào nhỏ hơn thì căn giữa. [contentW], [contentH] là kích thước ảnh ở 1×.
     */
    fun clampTranslation(t: Transform, viewW: Float, viewH: Float, contentW: Float, contentH: Float): Transform =
        t.copy(
            tx = clampAxis(t.tx, t.scale, viewW, contentW),
            ty = clampAxis(t.ty, t.scale, viewH, contentH),
        )

    private fun clampAxis(translation: Float, scale: Float, view: Float, content: Float): Float {
        val origin = (view - content) / 2f // mép ảnh ở 1×
        val size = content * scale
        return if (size <= view) {
            (view - size) / 2f - origin * scale
        } else {
            val max = -origin * scale // mép đầu ảnh = 0
            val min = view - size - origin * scale // mép cuối ảnh = view
            translation.coerceIn(min, max)
        }
    }

    /** Ở 1× không giữ thao tác vuốt ngang để `ViewPager2` chuyển ảnh. */
    fun canPanHorizontally(t: Transform, viewW: Float, contentW: Float): Boolean =
        t.scale > MIN_SCALE + EPSILON && contentW * t.scale > viewW + EPSILON

    /** Chạm hai lần: 1× → 2,5×, đang phóng → 1×. */
    fun nextDoubleTapScale(current: Float): Float =
        if (current > MIN_SCALE + EPSILON) MIN_SCALE else DOUBLE_TAP_SCALE
}
