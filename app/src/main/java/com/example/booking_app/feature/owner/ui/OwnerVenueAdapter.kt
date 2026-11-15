package com.example.booking_app.feature.owner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemOwnerVenueBinding
import com.example.booking_app.feature.owner.domain.model.OwnerVenue

class OwnerVenueAdapter(
    private val onOpen: (OwnerVenue) -> Unit,
    private val onHide: (OwnerVenue) -> Unit,
    private val onToggleRecurring: (OwnerVenue) -> Unit,
    private val onNavigate: (OwnerVenue, String) -> Unit = { _, _ -> },
) : ListAdapter<OwnerVenue, OwnerVenueAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemOwnerVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemOwnerVenueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OwnerVenue) {
            binding.name.text = item.name
            binding.address.text = item.address
            binding.status.setText(when(item.status) { "ACTIVE"->R.string.owner_status_active; "HIDDEN"->R.string.owner_hidden_label; else->R.string.owner_locked_label })
            binding.recurring.setOnCheckedChangeListener(null)
            binding.recurring.isChecked = item.acceptsRecurring
            binding.recurring.isEnabled = item.status != "LOCKED"
            binding.hide.isEnabled = item.status == "ACTIVE"
            binding.root.setOnClickListener { onOpen(item) }
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
        private val DIFF = object : DiffUtil.ItemCallback<OwnerVenue>() {
            override fun areItemsTheSame(a: OwnerVenue, b: OwnerVenue) = a.id == b.id
            override fun areContentsTheSame(a: OwnerVenue, b: OwnerVenue) = a == b
        }
    }
}
