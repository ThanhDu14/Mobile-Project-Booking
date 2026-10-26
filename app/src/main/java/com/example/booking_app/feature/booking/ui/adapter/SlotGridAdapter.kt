package com.example.booking_app.feature.booking.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemSlotGridBinding
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class SlotGridAdapter(
    private val onSlotClick: (TimeSlot) -> Unit
) : ListAdapter<TimeSlot, SlotGridAdapter.SlotViewHolder>(SlotDiffCallback) {

    private val priceFormat = DecimalFormat("#,###", DecimalFormatSymbols(Locale.GERMANY))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val binding = ItemSlotGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SlotViewHolder(
        private val binding: ItemSlotGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(slot: TimeSlot) {
            val context = itemView.context

            // Định dạng giờ hiển thị "HH:mm - HH:mm"
            val startH = if (slot.startTime.length >= 2) slot.startTime.substring(0, 2) else "00"
            val startM = if (slot.startTime.length >= 4) slot.startTime.substring(2, 4) else "00"
            val endH = if (slot.endTime.length >= 2) slot.endTime.substring(0, 2) else "00"
            val endM = if (slot.endTime.length >= 4) slot.endTime.substring(2, 4) else "00"
            binding.tvSlotTime.text = "$startH:$startM - $endH:$endM"

            // Định dạng tiền VND
            binding.tvSlotPrice.text = "${priceFormat.format(slot.price)}đ"

            // Thiết lập trạng thái hiển thị
            when (slot.status) {
                SlotStatus.AVAILABLE -> {
                    binding.cardSlot.alpha = 1.0f
                    binding.cardSlot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.booking_slot_available_bg)
                    )
                    binding.cardSlot.strokeColor = ContextCompat.getColor(
                        context, R.color.booking_slot_available_stroke
                    )
                    binding.tvSlotTime.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_available_text)
                    )
                    binding.tvSlotPrice.setTextColor(
                        ContextCompat.getColor(context, R.color.sn_ink_muted)
                    )
                    binding.tvSlotBadge.isVisible = false
                    binding.tvSlotBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                }

                SlotStatus.SELECTED -> {
                    binding.cardSlot.alpha = 1.0f
                    binding.cardSlot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.booking_slot_selected_bg)
                    )
                    binding.cardSlot.strokeColor = ContextCompat.getColor(
                        context, R.color.booking_slot_selected_stroke
                    )
                    binding.tvSlotTime.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_selected_text)
                    )
                    binding.tvSlotPrice.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_selected_text)
                    )
                    binding.tvSlotBadge.isVisible = false
                    binding.tvSlotBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                }

                SlotStatus.HELD -> {
                    binding.cardSlot.alpha = 0.80f
                    binding.cardSlot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.booking_slot_held_bg)
                    )
                    binding.cardSlot.strokeColor = ContextCompat.getColor(
                        context, R.color.booking_slot_held_stroke
                    )
                    binding.tvSlotTime.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_held_text)
                    )
                    binding.tvSlotPrice.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_held_text)
                    )
                    binding.tvSlotBadge.isVisible = true
                    binding.tvSlotBadge.text = context.getString(R.string.booking_status_held_badge)
                    binding.tvSlotBadge.backgroundTintList = ContextCompat.getColorStateList(
                        context, R.color.sn_lamp
                    )
                    binding.tvSlotBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.sn_on_lamp)
                    )
                    binding.tvSlotBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                }

                SlotStatus.BOOKED -> {
                    binding.cardSlot.alpha = 0.55f
                    binding.cardSlot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.booking_slot_booked_bg)
                    )
                    binding.cardSlot.strokeColor = ContextCompat.getColor(
                        context, R.color.booking_slot_booked_stroke
                    )
                    binding.tvSlotTime.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_booked_text)
                    )
                    binding.tvSlotPrice.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_booked_text)
                    )
                    binding.tvSlotBadge.isVisible = true
                    binding.tvSlotBadge.text = context.getString(R.string.booking_status_booked_badge)
                    binding.tvSlotBadge.backgroundTintList = ContextCompat.getColorStateList(
                        context, R.color.sn_surface_sunken
                    )
                    binding.tvSlotBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_booked_text)
                    )
                    binding.tvSlotBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                }

                SlotStatus.BLOCKED -> {
                    binding.cardSlot.alpha = 0.50f
                    binding.cardSlot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.booking_slot_blocked_bg)
                    )
                    binding.cardSlot.strokeColor = ContextCompat.getColor(
                        context, R.color.booking_slot_blocked_stroke
                    )
                    binding.tvSlotTime.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_blocked_text)
                    )
                    binding.tvSlotPrice.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_blocked_text)
                    )
                    binding.tvSlotBadge.isVisible = true
                    val lockDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock_16)
                    binding.tvSlotBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(lockDrawable, null, null, null)
                    binding.tvSlotBadge.compoundDrawablePadding = (3 * context.resources.displayMetrics.density).toInt()
                    binding.tvSlotBadge.text = if (slot.blockReason == "DROP_IN") {
                        context.getString(R.string.booking_status_drop_in_badge)
                    } else {
                        context.getString(R.string.booking_status_blocked_badge)
                    }
                    binding.tvSlotBadge.backgroundTintList = ContextCompat.getColorStateList(
                        context, R.color.sn_line
                    )
                    binding.tvSlotBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_blocked_text)
                    )
                }

                SlotStatus.PAST -> {
                    binding.cardSlot.alpha = 0.35f
                    binding.cardSlot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.booking_slot_past_bg)
                    )
                    binding.cardSlot.strokeColor = ContextCompat.getColor(
                        context, R.color.booking_slot_past_stroke
                    )
                    binding.tvSlotTime.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_past_text)
                    )
                    binding.tvSlotPrice.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_past_text)
                    )
                    binding.tvSlotBadge.isVisible = true
                    binding.tvSlotBadge.text = context.getString(R.string.booking_status_past_badge)
                    binding.tvSlotBadge.backgroundTintList = ContextCompat.getColorStateList(
                        context, R.color.sn_line
                    )
                    binding.tvSlotBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.booking_slot_past_text)
                    )
                    binding.tvSlotBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                }
            }

            // Mọi slot đều có thể click để phát tín hiệu phản hồi
            binding.cardSlot.isClickable = true
            binding.cardSlot.isFocusable = true
            binding.cardSlot.setOnClickListener {
                onSlotClick(slot)
            }
        }
    }

    object SlotDiffCallback : DiffUtil.ItemCallback<TimeSlot>() {
        override fun areItemsTheSame(oldItem: TimeSlot, newItem: TimeSlot): Boolean {
            return oldItem.slotId == newItem.slotId
        }

        override fun areContentsTheSame(oldItem: TimeSlot, newItem: TimeSlot): Boolean {
            return oldItem == newItem
        }
    }
}
