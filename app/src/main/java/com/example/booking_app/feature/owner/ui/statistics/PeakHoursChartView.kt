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

/** Accessible 60-minute bucket chart. It draws only values returned by the approved report source. */
class PeakHoursChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sn_court) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sn_ink_muted)
        textSize = 12f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private var values: List<OwnerPeakHour> = emptyList()

    fun submit(values: List<OwnerPeakHour>) {
        this.values = values.sortedBy { it.localHour }
        contentDescription = this.values.joinToString(". ") { "${it.localHour}:00, ${it.occupiedDurationMinutes} phút có đặt" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val max = values.maxOf { it.occupiedDurationMinutes }.coerceAtLeast(1)
        val labelHeight = textPaint.textSize * 1.8f
        val graphHeight = (height - paddingTop - paddingBottom - labelHeight).coerceAtLeast(0f)
        val slotWidth = (width - paddingLeft - paddingRight).toFloat() / values.size
        val gap = slotWidth * .18f
        values.forEachIndexed { index, item ->
            val barHeight = graphHeight * item.occupiedDurationMinutes / max
            val left = paddingLeft + index * slotWidth + gap
            val top = paddingTop + graphHeight - barHeight
            canvas.drawRoundRect(RectF(left, top, left + slotWidth - gap * 2, paddingTop + graphHeight), 6f, 6f, barPaint)
            canvas.drawText("%02d".format(item.localHour), left + (slotWidth - gap * 2) / 2, paddingTop + graphHeight + labelHeight * .8f, textPaint)
        }
    }
}
