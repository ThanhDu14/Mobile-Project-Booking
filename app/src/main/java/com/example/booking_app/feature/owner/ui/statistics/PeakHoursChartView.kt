package com.example.booking_app.feature.owner.ui.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.booking_app.R
import com.example.booking_app.feature.owner.domain.statistics.OwnerPeakHour

/**
 * Biểu đồ phút có đặt theo từng giờ. Luôn vẽ đủ một dải giờ cố định (ít nhất 06–22h) với cột nền nhạt,
 * để 1–2 giờ có đặt không bị phóng thành một khối chiếm cả khung.
 */
class PeakHoursChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sn_court) }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sn_lamp) }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sn_surface_sunken) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sn_ink_muted)
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }
    private var minutesByHour: Map<Int, Int> = emptyMap()
    private var hours: IntRange = DEFAULT_RANGE

    fun submit(values: List<OwnerPeakHour>) {
        minutesByHour = values.associate { it.localHour to it.occupiedDurationMinutes }
        hours = rangeFor(values.map { it.localHour })
        contentDescription = values.sortedBy { it.localHour }
            .joinToString(". ") { "${it.localHour}:00, ${it.occupiedDurationMinutes} phút có đặt" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = hours.count()
        if (count == 0) return
        val max = (minutesByHour.values.maxOrNull() ?: 0).coerceAtLeast(60)
        val peak = minutesByHour.values.maxOrNull() ?: 0
        val labelHeight = textPaint.textSize * 2f
        val graphHeight = (height - paddingTop - paddingBottom - labelHeight).coerceAtLeast(0f)
        val slot = (width - paddingLeft - paddingRight).toFloat() / count
        val barWidth = (slot * .62f).coerceAtMost(22f * density)
        val radius = barWidth / 2.5f
        val labelEvery = if (count > 12) 3 else 2
        hours.forEachIndexed { index, hour ->
            val left = paddingLeft + index * slot + (slot - barWidth) / 2
            val bottom = paddingTop + graphHeight
            canvas.drawRoundRect(RectF(left, paddingTop.toFloat(), left + barWidth, bottom), radius, radius, trackPaint)
            val minutes = minutesByHour[hour] ?: 0
            if (minutes > 0) {
                val top = bottom - (graphHeight * minutes / max).coerceAtLeast(barWidth)
                canvas.drawRoundRect(RectF(left, top, left + barWidth, bottom), radius, radius, if (minutes == peak) peakPaint else barPaint)
            }
            if ((hour - hours.first) % labelEvery == 0) {
                canvas.drawText("%02d".format(hour), left + barWidth / 2, bottom + labelHeight * .75f, textPaint)
            }
        }
    }

    internal companion object {
        val DEFAULT_RANGE = 6..22

        /** Dải giờ hiển thị: mặc định 06–22h, nới ra nếu có giờ đặt nằm ngoài. */
        fun rangeFor(hours: List<Int>): IntRange {
            val valid = hours.filter { it in 0..23 }
            if (valid.isEmpty()) return DEFAULT_RANGE
            return minOf(DEFAULT_RANGE.first, valid.min())..maxOf(DEFAULT_RANGE.last, valid.max())
        }
    }
}
