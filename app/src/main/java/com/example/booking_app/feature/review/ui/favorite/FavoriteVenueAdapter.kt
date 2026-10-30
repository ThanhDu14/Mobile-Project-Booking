package com.example.booking_app.feature.review.ui.favorite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.placeholder
import coil3.request.error
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemFavoriteVenueBinding
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import com.example.booking_app.feature.review.domain.model.FavoriteVenue.Availability
import java.text.NumberFormat
import java.util.Locale

class FavoriteVenueAdapter(
    private val onVenueClick: (FavoriteVenue) -> Unit,
    private val onUnfavorite: (FavoriteVenue) -> Unit,
) : ListAdapter<FavoriteVenue, FavoriteVenueAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemFavoriteVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemFavoriteVenueBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(venue: FavoriteVenue) = with(binding) {
            val context = root.context
            venueName.text = venue.name
            venueAddress.text = venue.address
            venuePhoto.load(venue.photoUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_venue_placeholder)
                error(R.drawable.bg_venue_placeholder)
            }

            val active = venue.availability == Availability.ACTIVE
            venueRating.isVisible = active
            venueRating.text = venue.ratingAvg?.let(ratingFormat::format)
                ?: context.getString(R.string.favorite_no_rating)

            venueStatus.isVisible = !active
            when (venue.availability) {
                Availability.SUSPENDED -> venueStatus.setText(R.string.favorite_venue_suspended)
                Availability.GONE -> venueStatus.setText(R.string.favorite_venue_gone)
                Availability.ACTIVE -> Unit
            }

            // Sân tạm ngừng hoặc không còn: không mở đặt sân, chỉ còn bỏ yêu thích (FR-024).
            root.isClickable = active
            root.isFocusable = active
            root.setOnClickListener(if (active) { _ -> onVenueClick(venue) } else null)
            root.alpha = if (active) 1f else 0.72f

            favoriteButton.isFavorite = true
            favoriteButton.setOnClickListener { onUnfavorite(venue) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<FavoriteVenue>() {
        override fun areItemsTheSame(oldItem: FavoriteVenue, newItem: FavoriteVenue) = oldItem.venueId == newItem.venueId
        override fun areContentsTheSame(oldItem: FavoriteVenue, newItem: FavoriteVenue) = oldItem == newItem
    }

    private companion object {
        val ratingFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("vi", "VN")).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }
}
