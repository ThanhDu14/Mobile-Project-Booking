package com.example.booking_app.feature.owner.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemOwnerVenueBinding
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.feature.owner.domain.model.OwnerVenue

class OwnerVenueAdapter(
    private val onOpen: (OwnerVenue) -> Unit,
    private val onHide: (OwnerVenue) -> Unit,
    private val onToggleRecurring: (OwnerVenue) -> Unit,
    private val onNavigate: (OwnerVenue, String) -> Unit = { _, _ -> },
    private val photoUrl: (OwnerVenue) -> String? = { null },
) : ListAdapter<OwnerVenue, OwnerVenueAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemOwnerVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemOwnerVenueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OwnerVenue) {
            binding.name.text = item.name
            binding.address.text = item.address
            val context = binding.root.context
            binding.meta.text = context.getString(R.string.owner_venue_meta, item.courtCount, clock(item.openTime), clock(item.closeTime))
            binding.photo.load(photoUrl(item) ?: R.drawable.img_venue_placeholder) {
                placeholder(R.drawable.img_venue_placeholder)
                error(R.drawable.img_venue_placeholder)
            }
            val (label, background, foreground) = when (item.status) {
                "ACTIVE" -> Triple(R.string.owner_status_active, R.color.sn_court_container, R.color.sn_on_court_container)
                "HIDDEN" -> Triple(R.string.owner_hidden_label, R.color.sn_lamp_container, R.color.sn_on_lamp_container)
                else -> Triple(R.string.owner_locked_label, R.color.sn_danger, R.color.sn_on_danger)
            }
            binding.status.setText(label)
            binding.status.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
            binding.status.setTextColor(ContextCompat.getColor(context, foreground))
            binding.recurring.setOnCheckedChangeListener(null)
            binding.recurring.isChecked = item.acceptsRecurring
            binding.recurring.isEnabled = item.status != "LOCKED"
            binding.hide.isVisible = item.status == "ACTIVE"
            binding.hide.setOnClickListener { onHide(item) }
            binding.recurring.setOnCheckedChangeListener { _, checked ->
                if (checked != item.acceptsRecurring) onToggleRecurring(item)
            }
            binding.info.setOnClickListener { onOpen(item) }
            binding.courts.setOnClickListener { onNavigate(item,"courts") }
            binding.prices.setOnClickListener { onNavigate(item,"prices") }
            binding.slots.setOnClickListener { onNavigate(item,"slots") }
            binding.dropIns.setOnClickListener { onNavigate(item,"dropin") }
            binding.vouchers.setOnClickListener { onNavigate(item,"vouchers") }
            binding.statistics.setOnClickListener { onNavigate(item,"statistics") }
        }
    }

    companion object {
        /** Giờ lưu dạng "HHmm" (vd "0530") hoặc đã có dấu ":"; hiển thị "05:30". */
        internal fun clock(raw: String): String =
            if (raw.length == 4 && raw.all(Char::isDigit)) "${raw.take(2)}:${raw.drop(2)}" else raw

        private val DIFF = object : DiffUtil.ItemCallback<OwnerVenue>() {
            override fun areItemsTheSame(a: OwnerVenue, b: OwnerVenue) = a.id == b.id
            override fun areContentsTheSame(a: OwnerVenue, b: OwnerVenue) = a == b
        }
    }
}
