package com.example.booking_app.feature.owner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemOwnerSlotGridBinding

class OwnerSlotGridAdapter(
    private val onSlotClick: (OwnerSlotItem) -> Unit
) : ListAdapter<OwnerSlotItem, OwnerSlotGridAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOwnerSlotGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemOwnerSlotGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OwnerSlotItem) {
            val context = binding.root.context
            binding.slotTime.text = "${item.startTime.take(2)}:${item.startTime.takeLast(2)}"
            binding.slotCheckbox.isChecked = item.isSelected

            val isSelectable = item.status == "AVAILABLE" || item.isOwnerRemovable

            binding.root.isEnabled = isSelectable
            binding.slotCheckbox.isEnabled = isSelectable

            when (item.status) {
                "AVAILABLE" -> {
                    binding.slotStatus.text = context.getString(R.string.owner_slot_status_available)
                    binding.slotStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                    binding.slotReason.visibility = View.GONE
                }
                "BLOCKED" -> {
                    val reasonText = when(item.blockReason) {"MAINTENANCE"->context.getString(R.string.owner_reason_maintenance);"EVENT"->context.getString(R.string.owner_reason_event);"WALK_IN"->context.getString(R.string.owner_reason_walk_in);"DROP_IN"->context.getString(R.string.owner_today_action_open_dropin);else->""}
                    binding.slotStatus.text = context.getString(R.string.owner_slot_status_blocked)
                    binding.slotStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                    if (reasonText.isNotEmpty()) {
                        binding.slotReason.visibility = View.VISIBLE
                        binding.slotReason.text = reasonText
                    } else {
                        binding.slotReason.visibility = View.GONE
                    }
                }
                "BOOKED" -> {
                    binding.slotStatus.text = context.getString(R.string.owner_slot_status_booked)
                    binding.slotStatus.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    binding.slotReason.visibility = View.GONE
                }
                "HELD" -> {
                    binding.slotStatus.text = context.getString(R.string.owner_slot_status_held)
                    binding.slotStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                    binding.slotReason.visibility = View.GONE
                }
                else -> {
                    binding.slotStatus.text = item.status
                    binding.slotReason.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener {
                if (isSelectable) {
                    onSlotClick(item)
                }
            }
            binding.slotCheckbox.contentDescription = "${item.displayTime} · ${binding.slotStatus.text}"
            binding.slotCheckbox.setOnClickListener { if (isSelectable) onSlotClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<OwnerSlotItem>() {
        override fun areItemsTheSame(oldItem: OwnerSlotItem, newItem: OwnerSlotItem): Boolean =
            oldItem.slotId == newItem.slotId

        override fun areContentsTheSame(oldItem: OwnerSlotItem, newItem: OwnerSlotItem): Boolean =
            oldItem == newItem
    }
}
