package com.example.booking_app.feature.courtdetail.ui

import android.content.Context
import com.example.booking_app.R
import com.example.booking_app.feature.courtdetail.domain.model.CourtSummary
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import com.example.booking_app.feature.courtdetail.domain.model.SurfaceType
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Định dạng hiển thị của chi tiết sân, không phụ thuộc locale của máy. */
internal object CourtDetailFormat {

    private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

    /** `120000` → `120.000`. */
    fun vnd(amount: Long): String {
        val digits = abs(amount).toString().reversed().chunked(3).joinToString(".").reversed()
        return if (amount < 0) "-$digits" else digits
    }

    /** `4.6` → `4,6`. */
    fun rating(value: Double): String = String.format(java.util.Locale.ROOT, "%.1f", value).replace('.', ',')

    fun surface(context: Context, type: SurfaceType): String = context.getString(
        when (type) {
            SurfaceType.WOOD -> R.string.court_detail_surface_wood
            SurfaceType.PU -> R.string.court_detail_surface_pu
            SurfaceType.MAT -> R.string.court_detail_surface_mat
            SurfaceType.OTHER -> R.string.court_detail_surface_other
        },
    )

    /** "PU" khi chỉ một loại mặt sân, "3 Thảm, 1 Gỗ" khi nhiều loại. */
    fun surfaces(context: Context, summary: CourtSummary): String =
        if (summary.bySurface.size == 1) {
            surface(context, summary.bySurface.single().first)
        } else {
            summary.bySurface.joinToString(", ") { (type, count) ->
                context.getString(R.string.court_detail_surface_count, count, surface(context, type))
            }
        }

    fun shortDay(context: Context, day: DayOfWeek): String = context.getString(
        when (day) {
            DayOfWeek.MONDAY -> R.string.court_detail_day_short_monday
            DayOfWeek.TUESDAY -> R.string.court_detail_day_short_tuesday
            DayOfWeek.WEDNESDAY -> R.string.court_detail_day_short_wednesday
            DayOfWeek.THURSDAY -> R.string.court_detail_day_short_thursday
            DayOfWeek.FRIDAY -> R.string.court_detail_day_short_friday
            DayOfWeek.SATURDAY -> R.string.court_detail_day_short_saturday
            DayOfWeek.SUNDAY -> R.string.court_detail_day_short_sunday
        },
    )

    /** "T3, 27/10 · 19:00 – 21:00". */
    fun window(context: Context, w: SearchWindow): String = context.getString(
        R.string.court_detail_selected_window_detail,
        "${shortDay(context, w.date.dayOfWeek)}, ${w.date.format(DAY_MONTH)}",
        w.start.format(),
        w.end.format(),
    )
}
