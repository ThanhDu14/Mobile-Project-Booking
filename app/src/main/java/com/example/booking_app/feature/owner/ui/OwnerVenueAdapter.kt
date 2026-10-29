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
    private val onToggleRecurring: (OwnerVenue) -> Unit
) : ListAdapter<OwnerVenue, OwnerVenueAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemOwnerVenueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemOwnerVenueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OwnerVenue) {
            binding.name.text = item.name
            binding.address.text = item.address
            binding.status.text = item.status
            binding.recurring.isChecked = item.acceptsRecurring
            binding.root.setOnClickListener { onOpen(item) }
            binding.hide.setOnClickListener { onHide(item) }
            binding.recurring.setOnCheckedChangeListener { _, checked ->
                if (checked != item.acceptsRecurring) onToggleRecurring(item)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OwnerVenue>() {
            override fun areItemsTheSame(a: OwnerVenue, b: OwnerVenue) = a.id == b.id
            override fun areContentsTheSame(a: OwnerVenue, b: OwnerVenue) = a == b
        }
    }
}
