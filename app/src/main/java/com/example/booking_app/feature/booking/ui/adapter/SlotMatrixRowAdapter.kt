package com.example.booking_app.feature.booking.ui.adapter

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
    private val onSlotClick: (TimeSlot) -> Unit
) : ListAdapter<SlotMatrixRow, SlotMatrixRowAdapter.RowViewHolder>(RowDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
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

    inner class RowViewHolder(
        private val binding: ItemSlotMatrixRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SlotMatrixRow) {
            val layout = binding.layoutRowCells
            val context = itemView.context
            val inflater = LayoutInflater.from(context)

            // Tái sử dụng hoặc tạo mới các cell view tương ứng với các sân con
            while (layout.childCount < row.slots.size) {
                val cellBinding = ItemSlotMatrixCellBinding.inflate(inflater, layout, false)
                layout.addView(cellBinding.root)
            }
            while (layout.childCount > row.slots.size) {
                layout.removeViewAt(layout.childCount - 1)
            }

            for (i in row.slots.indices) {
                val slot = row.slots[i]
                val cellView = layout.getChildAt(i)
                val cellBinding = ItemSlotMatrixCellBinding.bind(cellView)

                // Định dạng giờ bắt đầu (vd "17:00")
                val formattedTime = if (slot.startTime.length >= 4) {
                    "${slot.startTime.substring(0, 2)}:${slot.startTime.substring(2, 4)}"
                } else {
                    slot.startTime
                }
                cellBinding.tvCellTime.text = formattedTime

                // Reset các hiệu ứng mặc định
                cellBinding.tvCellTime.paintFlags = cellBinding.tvCellTime.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                cellBinding.root.alpha = 1.0f

                when (slot.status) {
                    SlotStatus.AVAILABLE -> {
                        cellBinding.cardSlotCell.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.booking_slot_available_bg)
                        )
                        cellBinding.cardSlotCell.strokeWidth = 2
                        cellBinding.cardSlotCell.strokeColor = ContextCompat.getColor(context, R.color.booking_slot_available_stroke)
                        cellBinding.tvCellTime.setTextColor(
                            ContextCompat.getColor(context, R.color.booking_slot_available_text)
                        )
                        cellBinding.ivCellIcon.isVisible = false
                        cellBinding.tvCellDropInBadge.isVisible = false
                    }

                    SlotStatus.SELECTED -> {
                        cellBinding.cardSlotCell.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.booking_slot_selected_bg)
                        )
                        cellBinding.cardSlotCell.strokeWidth = 0
                        cellBinding.tvCellTime.setTextColor(
                            ContextCompat.getColor(context, R.color.booking_slot_selected_text)
                        )
                        cellBinding.ivCellIcon.isVisible = true
                        cellBinding.ivCellIcon.setImageResource(R.drawable.ic_check_20)
                        cellBinding.ivCellIcon.setColorFilter(
                            ContextCompat.getColor(context, R.color.booking_slot_selected_text)
                        )
                        cellBinding.tvCellDropInBadge.isVisible = false
                    }

                    SlotStatus.HELD -> {
                        cellBinding.cardSlotCell.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.booking_slot_held_bg)
                        )
                        cellBinding.cardSlotCell.strokeWidth = 2
                        cellBinding.cardSlotCell.strokeColor = ContextCompat.getColor(context, R.color.booking_slot_held_stroke)
                        cellBinding.tvCellTime.setTextColor(
                            ContextCompat.getColor(context, R.color.booking_slot_held_text)
                        )
                        cellBinding.ivCellIcon.isVisible = true
                        cellBinding.ivCellIcon.setImageResource(R.drawable.ic_timer_24)
                        cellBinding.ivCellIcon.setColorFilter(
                            ContextCompat.getColor(context, R.color.booking_slot_held_text)
                        )
                        cellBinding.tvCellDropInBadge.isVisible = false
                    }

                    SlotStatus.BOOKED -> {
                        cellBinding.cardSlotCell.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.booking_slot_booked_bg)
                        )
                        cellBinding.cardSlotCell.strokeWidth = 0
                        cellBinding.tvCellTime.setTextColor(
                            ContextCompat.getColor(context, R.color.booking_slot_booked_text)
                        )
                        // 1i: BOOKED là xám nhạt, KHÔNG gạch ngang
                        cellBinding.ivCellIcon.isVisible = false
                        cellBinding.tvCellDropInBadge.isVisible = false
                        cellBinding.root.alpha = 1.0f
                    }

                    SlotStatus.BLOCKED -> {
                        cellBinding.cardSlotCell.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.booking_slot_blocked_bg)
                        )
                        cellBinding.cardSlotCell.strokeWidth = 2
                        cellBinding.cardSlotCell.strokeColor = ContextCompat.getColor(context, R.color.booking_slot_blocked_stroke)
                        cellBinding.tvCellTime.setTextColor(
                            ContextCompat.getColor(context, R.color.booking_slot_blocked_text)
                        )
                        cellBinding.ivCellIcon.isVisible = true
                        cellBinding.ivCellIcon.setImageResource(R.drawable.ic_lock_16)
                        cellBinding.ivCellIcon.setColorFilter(
                            ContextCompat.getColor(context, R.color.booking_slot_blocked_text)
                        )
                        // 1e: Ô BLOCKED dạng giao hữu hiển thị chip nhỏ dòng 2; ô BLOCKED thường ẩn chip
                        cellBinding.tvCellDropInBadge.isVisible = (slot.blockReason == "DROP_IN")
                        cellBinding.root.alpha = 0.9f
                    }

                    SlotStatus.PAST -> {
                        cellBinding.cardSlotCell.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.booking_slot_past_bg)
                        )
                        cellBinding.cardSlotCell.strokeWidth = 0
                        cellBinding.tvCellTime.setTextColor(
                            ContextCompat.getColor(context, R.color.booking_slot_past_text)
                        )
                        // 1i: PAST giữ mờ ~0.35 + gạch ngang, phân biệt rõ với BOOKED
                        cellBinding.tvCellTime.paintFlags = cellBinding.tvCellTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        cellBinding.ivCellIcon.isVisible = false
                        cellBinding.tvCellDropInBadge.isVisible = false
                        cellBinding.root.alpha = 0.35f
                    }
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
    }
}
