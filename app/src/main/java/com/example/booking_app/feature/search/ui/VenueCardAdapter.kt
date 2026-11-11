package com.example.booking_app.feature.search.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemSearchVenueBinding
import com.example.booking_app.feature.search.domain.model.HomeVenue

/** Danh sách thẻ sân (FR-015). Bấm thẻ gọi [onClick] với id cơ sở (FR-017). */
class VenueCardAdapter(
    private val onClick: (HomeVenue) -> Unit,
) : ListAdapter<HomeVenue, VenueCardAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSearchVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemSearchVenueBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(venue: HomeVenue) {
            val context = binding.root.context
            binding.name.text = venue.name
            binding.district.text = venue.districtName
            binding.district.isVisible = venue.districtName != null
            val price = HomeFormat.price(context, venue)
            val rating = HomeFormat.rating(context, venue)
            binding.price.text = price
            binding.rating.text = rating
            binding.photo.load(venue.photoUrl ?: R.drawable.img_venue_placeholder) {
                placeholder(R.drawable.img_venue_placeholder)
                error(R.drawable.img_venue_placeholder)
            }
            binding.root.contentDescription = venue.districtName
                ?.let { context.getString(R.string.search_card_description, venue.name, it, price, rating) }
                ?: context.getString(R.string.search_card_description_no_district, venue.name, price, rating)
            binding.root.setOnClickListener { onClick(venue) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeVenue>() {
        override fun areItemsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem == newItem
    }
}
