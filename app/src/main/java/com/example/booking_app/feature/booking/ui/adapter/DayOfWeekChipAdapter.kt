package com.example.booking_app.feature.booking.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemDayOfWeekChipBinding

class DayOfWeekChipAdapter(
    private val onSelectionChanged: (List<Int>) -> Unit
) : RecyclerView.Adapter<DayOfWeekChipAdapter.DayViewHolder>() {

    data class DayItem(
        val dayOfWeek: Int, // 1 = T2, 7 = CN
        val label: String
    )

    private val days = listOf(
        DayItem(1, "T2"),
        DayItem(2, "T3"),
        DayItem(3, "T4"),
        DayItem(4, "T5"),
        DayItem(5, "T6"),
        DayItem(6, "T7"),
        DayItem(7, "CN")
    )

    private val selectedDays = mutableSetOf<Int>()

    fun getSelectedDays(): List<Int> = selectedDays.sorted()

    fun setSelectedDays(newSelected: Collection<Int>) {
        selectedDays.clear()
        selectedDays.addAll(newSelected)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemDayOfWeekChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class DayViewHolder(
        private val binding: ItemDayOfWeekChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DayItem) {
            val context = binding.root.context
            val isSelected = selectedDays.contains(item.dayOfWeek)
            binding.tvDayName.text = item.label

            val density = context.resources.displayMetrics.density
            if (isSelected) {
                binding.cardDayChip.strokeColor = ContextCompat.getColor(context, R.color.sn_court)
                binding.cardDayChip.strokeWidth = (2 * density).toInt()
                binding.cardDayChip.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sn_court_container))
                binding.tvDayName.setTextColor(ContextCompat.getColor(context, R.color.sn_on_court_container))
                binding.viewActiveDot.visibility = View.VISIBLE
            } else {
                binding.cardDayChip.strokeColor = ContextCompat.getColor(context, R.color.sn_line)
                binding.cardDayChip.strokeWidth = (1 * density).toInt()
                binding.cardDayChip.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sn_surface_raised))
                binding.tvDayName.setTextColor(ContextCompat.getColor(context, R.color.sn_ink_muted))
                binding.viewActiveDot.visibility = View.INVISIBLE
            }

            binding.root.setOnClickListener {
                if (isSelected) {
                    selectedDays.remove(item.dayOfWeek)
                } else {
                    selectedDays.add(item.dayOfWeek)
                }
                notifyItemChanged(bindingAdapterPosition)
                onSelectionChanged(getSelectedDays())
            }
        }
    }
}
