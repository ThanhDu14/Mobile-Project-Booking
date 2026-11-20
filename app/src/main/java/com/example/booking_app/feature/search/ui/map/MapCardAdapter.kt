package com.example.booking_app.feature.search.ui.map

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
import com.example.booking_app.core.location.DistanceFormatter
import com.example.booking_app.databinding.ItemMapVenueCardBinding
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.ui.HomeFormat

/**
 * Thẻ tóm tắt trên bản đồ (spec 021 FR-007) trong ViewPager2: ảnh, tên, quận và khoảng cách (nếu có vị trí), giá theo
 * bộ lọc, điểm đánh giá, vị trí "2/5". Chạm thẻ mở chi tiết sân (FR-009).
 */
class MapCardAdapter(
    private val onClick: (HomeVenue) -> Unit,
) : ListAdapter<HomeVenue, MapCardAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMapVenueCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), position, itemCount)

    inner class Holder(private val binding: ItemMapVenueCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(venue: HomeVenue, position: Int, count: Int) {
            val context = binding.root.context
            binding.name.text = venue.name
            val distance = venue.distanceMeters?.let(DistanceFormatter::format)
            val district = venue.districtName
            val location = if (district != null && distance != null) {
                context.getString(R.string.map_card_distance, district, distance)
            } else district ?: distance
            binding.district.text = location
            binding.district.isVisible = location != null
            val price = HomeFormat.price(context, venue)
            val rating = HomeFormat.rating(context, venue)
            binding.price.text = price
            binding.rating.text = rating
            binding.position.text = context.getString(R.string.map_card_position, position + 1, count)
            binding.position.isVisible = count > 1
            binding.photo.load(venue.photoUrl ?: R.drawable.img_venue_placeholder) {
                placeholder(R.drawable.img_venue_placeholder)
                error(R.drawable.img_venue_placeholder)
            }
            binding.card.contentDescription = location
                ?.let { context.getString(R.string.search_card_description, venue.name, it, price, rating) }
                ?: context.getString(R.string.search_card_description_no_district, venue.name, price, rating)
            binding.card.setOnClickListener { onClick(venue) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeVenue>() {
        override fun areItemsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HomeVenue, newItem: HomeVenue) = oldItem == newItem
    }
}
