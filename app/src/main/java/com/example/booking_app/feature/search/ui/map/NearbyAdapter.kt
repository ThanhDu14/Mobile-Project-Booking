package com.example.booking_app.feature.search.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.location.DistanceFormatter
import com.example.booking_app.databinding.ItemNearbyVenueBinding
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.ui.HomeFormat

/** Dải "Sân gần bạn" (spec 021 FR-016): chạm một sân mở chi tiết sân. */
class NearbyAdapter(
    private val onClick: (HomeVenue) -> Unit,
) : ListAdapter<HomeVenue, NearbyAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemNearbyVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemNearbyVenueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(venue: HomeVenue) {
            val context = binding.root.context
            val distance = venue.distanceMeters?.let(DistanceFormatter::format)
            val price = HomeFormat.price(context, venue)
            binding.name.text = venue.name
            binding.distance.text = distance?.let { context.getString(R.string.map_card_distance, it, price) } ?: price
            binding.photo.load(venue.photoUrl ?: R.drawable.img_venue_placeholder) {
                placeholder(R.drawable.img_venue_placeholder)
                error(R.drawable.img_venue_placeholder)
            }
            binding.root.contentDescription = listOfNotNull(venue.name, distance, price).joinToString(", ")
            binding.root.setOnClickListener { onClick(venue) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeVenue>() {
        override fun areItemsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem == newItem
    }
}
