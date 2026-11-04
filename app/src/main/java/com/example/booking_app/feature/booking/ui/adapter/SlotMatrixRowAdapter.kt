package com.example.booking_app.feature.booking.ui.adapter

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemSlotMatrixCellBinding
import com.example.booking_app.databinding.ItemSlotMatrixRowBinding
import com.example.booking_app.feature.booking.domain.model.SlotStatus
import com.example.booking_app.feature.booking.domain.model.TimeSlot
import com.example.booking_app.feature.booking.ui.SlotMatrixRow

class SlotMatrixRowAdapter(
    private var onSlotClick: (TimeSlot) -> Unit
) : ListAdapter<SlotMatrixRow, SlotMatrixRowAdapter.RowViewHolder>(RowDiffCallback) {

    fun setOnSlotClickListener(listener: (TimeSlot) -> Unit) {
        this.onSlotClick = listener
    }

    private var colorHolder: ColorHolder? = null

    private class ColorHolder(context: Context) {
        val availableBg = ContextCompat.getColor(context, R.color.booking_slot_available_bg)
        val availableStroke = ContextCompat.getColor(context, R.color.booking_slot_available_stroke)
        val availableText = ContextCompat.getColor(context, R.color.booking_slot_available_text)

        val selectedBg = ContextCompat.getColor(context, R.color.booking_slot_selected_bg)
        val selectedText = ContextCompat.getColor(context, R.color.booking_slot_selected_text)

        val heldBg = ContextCompat.getColor(context, R.color.booking_slot_held_bg)
        val heldStroke = ContextCompat.getColor(context, R.color.booking_slot_held_stroke)
        val heldText = ContextCompat.getColor(context, R.color.booking_slot_held_text)

        val bookedBg = ContextCompat.getColor(context, R.color.booking_slot_booked_bg)
        val bookedText = ContextCompat.getColor(context, R.color.booking_slot_booked_text)

        val blockedBg = ContextCompat.getColor(context, R.color.booking_slot_blocked_bg)
        val blockedStroke = ContextCompat.getColor(context, R.color.booking_slot_blocked_stroke)
        val blockedText = ContextCompat.getColor(context, R.color.booking_slot_blocked_text)

        val pastBg = ContextCompat.getColor(context, R.color.booking_slot_past_bg)
        val pastText = ContextCompat.getColor(context, R.color.booking_slot_past_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        if (colorHolder == null) {
            colorHolder = ColorHolder(parent.context.applicationContext)
        }
        val binding = ItemSlotMatrixRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            holder.bind(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class RowViewHolder(
        private val binding: ItemSlotMatrixRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val cellBindings = ArrayList<ItemSlotMatrixCellBinding>()

        fun bind(row: SlotMatrixRow) {
            val layout = binding.layoutRowCells
            val context = itemView.context
            val colors = colorHolder ?: ColorHolder(context.applicationContext).also { colorHolder = it }
            val inflater = LayoutInflater.from(context)

            // Tái sử dụng hoặc tạo mới các cell view tương ứng với các sân con
            while (cellBindings.size < row.slots.size) {
                val cellBinding = ItemSlotMatrixCellBinding.inflate(inflater, layout, false)
                layout.addView(cellBinding.root)
                cellBindings.add(cellBinding)
            }
            while (cellBindings.size > row.slots.size) {
                val lastIdx = cellBindings.size - 1
                layout.removeViewAt(lastIdx)
                cellBindings.removeAt(lastIdx)
            }

            for (i in row.slots.indices) {
                val slot = row.slots[i]
                val cellBinding = cellBindings[i]

                // Định dạng giờ bắt đầu (vd "17:00")
                val formattedTime = if (slot.startTime.length >= 4) {
                    "${slot.startTime.substring(0, 2)}:${slot.startTime.substring(2, 4)}"
                } else {
                    slot.startTime
                }

                val prevStatus = cellBinding.root.tag as? SlotStatus
                val isDropIn = slot.blockReason == "DROP_IN"

                // Chỉ cập nhật styling khi status hoặc drop-in thay đổi (tránh setCardBackgroundColor / strokeColor dư thừa)
                if (prevStatus != slot.status || (slot.status == SlotStatus.BLOCKED && cellBinding.tvCellDropInBadge.isVisible != isDropIn)) {
                    cellBinding.root.tag = slot.status

                    // Reset các hiệu ứng mặc định
                    cellBinding.tvCellTime.paintFlags = cellBinding.tvCellTime.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    cellBinding.root.alpha = 1.0f

                    when (slot.status) {
                        SlotStatus.AVAILABLE -> {
                            cellBinding.cardSlotCell.setCardBackgroundColor(colors.availableBg)
                            cellBinding.cardSlotCell.strokeWidth = 2
                            cellBinding.cardSlotCell.strokeColor = colors.availableStroke
                            cellBinding.tvCellTime.setTextColor(colors.availableText)
                            cellBinding.ivCellIcon.isVisible = false
                            cellBinding.tvCellDropInBadge.isVisible = false
                        }

                        SlotStatus.SELECTED -> {
                            cellBinding.cardSlotCell.setCardBackgroundColor(colors.selectedBg)
                            cellBinding.cardSlotCell.strokeWidth = 0
                            cellBinding.tvCellTime.setTextColor(colors.selectedText)
                            cellBinding.ivCellIcon.isVisible = true
                            cellBinding.ivCellIcon.setImageResource(R.drawable.ic_check_20)
                            cellBinding.ivCellIcon.setColorFilter(colors.selectedText)
                            cellBinding.tvCellDropInBadge.isVisible = false
                        }

                        SlotStatus.HELD -> {
                            cellBinding.cardSlotCell.setCardBackgroundColor(colors.heldBg)
                            cellBinding.cardSlotCell.strokeWidth = 2
                            cellBinding.cardSlotCell.strokeColor = colors.heldStroke
                            cellBinding.tvCellTime.setTextColor(colors.heldText)
                            cellBinding.ivCellIcon.isVisible = true
                            cellBinding.ivCellIcon.setImageResource(R.drawable.ic_timer_24)
                            cellBinding.ivCellIcon.setColorFilter(colors.heldText)
                            cellBinding.tvCellDropInBadge.isVisible = false
                        }

                        SlotStatus.BOOKED -> {
                            cellBinding.cardSlotCell.setCardBackgroundColor(colors.bookedBg)
                            cellBinding.cardSlotCell.strokeWidth = 0
                            cellBinding.tvCellTime.setTextColor(colors.bookedText)
                            cellBinding.ivCellIcon.isVisible = false
                            cellBinding.tvCellDropInBadge.isVisible = false
                            cellBinding.root.alpha = 1.0f
                        }

                        SlotStatus.BLOCKED -> {
                            cellBinding.cardSlotCell.setCardBackgroundColor(colors.blockedBg)
                            cellBinding.cardSlotCell.strokeWidth = 2
                            cellBinding.cardSlotCell.strokeColor = colors.blockedStroke
                            cellBinding.tvCellTime.setTextColor(colors.blockedText)
                            cellBinding.ivCellIcon.isVisible = true
                            cellBinding.ivCellIcon.setImageResource(R.drawable.ic_lock_16)
                            cellBinding.ivCellIcon.setColorFilter(colors.blockedText)
                            cellBinding.tvCellDropInBadge.isVisible = isDropIn
                            cellBinding.root.alpha = 0.9f
                        }

                        SlotStatus.PAST -> {
                            cellBinding.cardSlotCell.setCardBackgroundColor(colors.pastBg)
                            cellBinding.cardSlotCell.strokeWidth = 0
                            cellBinding.tvCellTime.setTextColor(colors.pastText)
                            cellBinding.tvCellTime.paintFlags = cellBinding.tvCellTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                            cellBinding.ivCellIcon.isVisible = false
                            cellBinding.tvCellDropInBadge.isVisible = false
                            cellBinding.root.alpha = 0.35f
                        }
                    }
                }

                if (cellBinding.tvCellTime.text != formattedTime) {
                    cellBinding.tvCellTime.text = formattedTime
                }

                cellBinding.root.setOnClickListener {
                    onSlotClick(slot)
                }
            }
        }
    }

    object RowDiffCallback : DiffUtil.ItemCallback<SlotMatrixRow>() {
        override fun areItemsTheSame(oldItem: SlotMatrixRow, newItem: SlotMatrixRow): Boolean {
            return oldItem.timeRaw == newItem.timeRaw
        }

        override fun areContentsTheSame(oldItem: SlotMatrixRow, newItem: SlotMatrixRow): Boolean {
            if (oldItem === newItem) return true
            if (oldItem.slots.size != newItem.slots.size) return false
            for (i in oldItem.slots.indices) {
                val s1 = oldItem.slots[i]
                val s2 = newItem.slots[i]
                if (s1.slotId != s2.slotId ||
                    s1.status != s2.status ||
                    s1.price != s2.price ||
                    s1.blockReason != s2.blockReason
                ) {
                    return false
                }
            }
            return true
        }

        override fun getChangePayload(oldItem: SlotMatrixRow, newItem: SlotMatrixRow): Any? {
            return PAYLOAD_UPDATE
        }
    }

    companion object {
        private const val PAYLOAD_UPDATE = 1
    }
}
