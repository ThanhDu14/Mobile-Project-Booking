package com.example.booking_app.feature.search.ui

import android.content.Context
import com.example.booking_app.R
import com.example.booking_app.feature.search.domain.model.HomeSort
import com.example.booking_app.feature.search.domain.model.HomeVenue
import java.util.Locale

/** Chuỗi hiển thị của thẻ sân (FR-015). */
internal object HomeFormat {

    /** `60000` → `60.000` (không phụ thuộc locale của máy). */
    fun vnd(amount: Long): String =
        amount.toString().reversed().chunked(3).joinToString(".").reversed()

    fun price(context: Context, venue: HomeVenue): String =
        venue.minPricePerHour?.let { context.getString(R.string.search_price_from, vnd(it)) }
            ?: context.getString(R.string.search_price_contact)

    fun rating(context: Context, venue: HomeVenue): String =
        if (venue.ratingCount <= 0) {
            context.getString(R.string.search_no_rating)
        } else {
            val value = String.format(Locale.ROOT, "%.1f", venue.ratingAvg).replace('.', ',')
            context.getString(R.string.search_rating, value, venue.ratingCount)
        }

    fun sortLabel(sort: HomeSort): Int = when (sort) {
        HomeSort.RATING -> R.string.search_sort_rating
        HomeSort.PRICE -> R.string.search_sort_price
    }
}
