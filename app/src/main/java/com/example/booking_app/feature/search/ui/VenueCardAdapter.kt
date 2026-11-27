package com.example.booking_app.feature.search.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.location.DistanceFormatter
import com.example.booking_app.databinding.ItemSearchVenueBinding
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.android.material.color.MaterialColors

/**
 * Danh sách thẻ sân (FR-015). Bấm thẻ gọi [onClick] với id cơ sở (FR-017). Trên máy tính bảng thẻ của sân đang chọn
 * trên bản đồ được tô nền ([selectedId], spec 021 FR-023).
 */
class VenueCardAdapter(
    private val onClick: (HomeVenue) -> Unit,
) : ListAdapter<HomeVenue, VenueCardAdapter.Holder>(Diff) {

    var selectedId: String? = null
        set(value) {
            if (field == value) return
            val old = field
            field = value
            listOf(old, value).forEach { id ->
                val index = currentList.indexOfFirst { it.id == id }
                if (id != null && index >= 0) notifyItemChanged(index)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSearchVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemSearchVenueBinding) : RecyclerView.ViewHolder(binding.root) {
        private val normalBackground = binding.root.cardBackgroundColor
        private val normalStroke = binding.root.strokeWidth
        private val normalStrokeColor = binding.root.strokeColorStateList

        fun bind(venue: HomeVenue) {
            val context = binding.root.context
            binding.name.text = venue.name
            val district = venue.districtName
            val distance = venue.distanceMeters?.let(DistanceFormatter::format)
            val location = if (district != null && distance != null) {
                context.getString(R.string.search_result_distance, district, distance)
            } else district ?: distance
            binding.district.text = location
            binding.district.isVisible = location != null
            val price = HomeFormat.price(context, venue)
            val rating = HomeFormat.rating(context, venue)
            binding.price.text = price
            binding.rating.text = rating
            binding.photo.load(venue.photoUrl ?: R.drawable.img_venue_placeholder) {
                placeholder(R.drawable.img_venue_placeholder)
                error(R.drawable.img_venue_placeholder)
            }
            binding.root.contentDescription = location
                ?.let { context.getString(R.string.search_card_description, venue.name, it, price, rating) }
                ?: context.getString(R.string.search_card_description_no_district, venue.name, price, rating)
            binding.root.setOnClickListener { onClick(venue) }

            val selected = venue.id == selectedId
            val card = binding.root
            if (selected) {
                card.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSecondaryContainer))
                card.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)))
                card.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            } else {
                card.setCardBackgroundColor(normalBackground)
                card.setStrokeColor(normalStrokeColor)
                card.strokeWidth = normalStroke
            }
            ViewCompat.setStateDescription(card, if (selected) context.getString(R.string.map_card_selected) else null)
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeVenue>() {
        override fun areItemsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem == newItem
    }
}
