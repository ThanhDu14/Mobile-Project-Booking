package com.example.booking_app.feature.owner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemOwnerCourtBinding
import com.example.booking_app.feature.owner.domain.model.OwnerCourt

class OwnerCourtAdapter(
    private val onEdit: (OwnerCourt) -> Unit,
    private val onDeactivate: (OwnerCourt) -> Unit,
    private val onMove: (OwnerCourt, Int) -> Unit
) : ListAdapter<OwnerCourt, OwnerCourtAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemOwnerCourtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemOwnerCourtBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OwnerCourt) {
            binding.name.text = item.name
            binding.surface.text = item.surfaceType
            binding.active.text = binding.root.context.getString(
                if (item.isActive) R.string.owner_status_active else R.string.owner_status_inactive
            )
            binding.deactivate.isEnabled = item.isActive
            binding.edit.setOnClickListener { onEdit(item) }
            binding.deactivate.setOnClickListener { onDeactivate(item) }
            binding.up.setOnClickListener { onMove(item, -1) }
            binding.down.setOnClickListener { onMove(item, 1) }
        }
    }
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OwnerCourt>() {
            override fun areItemsTheSame(a: OwnerCourt, b: OwnerCourt) = a.id == b.id
            override fun areContentsTheSame(a: OwnerCourt, b: OwnerCourt) = a == b
        }
    }
}
