package com.example.booking_app.feature.booking.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemDateSelectorBinding
import java.time.DayOfWeek
import java.time.LocalDate

data class DateItem(
    val date: LocalDate,
    val isSelected: Boolean,
    val isToday: Boolean
)

class DateSelectorAdapter(
    private val onDateClick: (LocalDate) -> Unit
) : ListAdapter<DateItem, DateSelectorAdapter.DateViewHolder>(DateDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val binding = ItemDateSelectorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DateViewHolder(
        private val binding: ItemDateSelectorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DateItem) {
            val context = itemView.context

            val zoneVietnam = java.time.ZoneId.of("Asia/Ho_Chi_Minh")
            val today = LocalDate.now(zoneVietnam)
            val tomorrow = today.plusDays(1)

            // Hiển thị Thứ (Hôm nay, Mai, T2..CN) theo Q7
            binding.tvDayOfWeek.text = when {
                item.date.isEqual(today) -> context.getString(R.string.day_today)
                item.date.isEqual(tomorrow) -> context.getString(R.string.day_tomorrow)
                else -> when (item.date.dayOfWeek) {
                    DayOfWeek.MONDAY -> context.getString(R.string.day_mon)
                    DayOfWeek.TUESDAY -> context.getString(R.string.day_tue)
                    DayOfWeek.WEDNESDAY -> context.getString(R.string.day_wed)
                    DayOfWeek.THURSDAY -> context.getString(R.string.day_thu)
                    DayOfWeek.FRIDAY -> context.getString(R.string.day_fri)
                    DayOfWeek.SATURDAY -> context.getString(R.string.day_sat)
                    DayOfWeek.SUNDAY -> context.getString(R.string.day_sun)
                    else -> ""
                }
            }

            // Hiển thị Ngày trong tháng
            binding.tvDayOfMonth.text = item.date.dayOfMonth.toString()

            // Chấm tròn highlight hôm nay
            binding.vTodayDot.isVisible = item.isToday

            // Style khi được chọn vs chưa chọn
            if (item.isSelected) {
                val selectedBg = ContextCompat.getColor(context, R.color.booking_date_selected_bg)
                val selectedText = ContextCompat.getColor(context, R.color.booking_date_selected_text)
                binding.cardDate.setCardBackgroundColor(selectedBg)
                binding.cardDate.strokeColor = selectedBg
                binding.tvDayOfWeek.setTextColor(selectedText)
                binding.tvDayOfMonth.setTextColor(selectedText)
            } else {
                val unselectedBg = ContextCompat.getColor(context, R.color.booking_date_unselected_bg)
                val unselectedStroke = ContextCompat.getColor(context, R.color.booking_date_unselected_stroke)
                val unselectedText = ContextCompat.getColor(context, R.color.booking_date_unselected_text)
                binding.cardDate.setCardBackgroundColor(unselectedBg)
                binding.cardDate.strokeColor = unselectedStroke
                binding.tvDayOfWeek.setTextColor(ContextCompat.getColor(context, R.color.sn_ink_muted))
                binding.tvDayOfMonth.setTextColor(unselectedText)
            }

            binding.cardDate.setOnClickListener {
                if (!item.isSelected) {
                    onDateClick(item.date)
                }
            }
        }
    }

    object DateDiffCallback : DiffUtil.ItemCallback<DateItem>() {
        override fun areItemsTheSame(oldItem: DateItem, newItem: DateItem): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: DateItem, newItem: DateItem): Boolean {
            return oldItem == newItem
        }
    }
}
