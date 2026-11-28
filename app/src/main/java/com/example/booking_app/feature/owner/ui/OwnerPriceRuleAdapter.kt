package com.example.booking_app.feature.owner.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemOwnerPriceRuleBinding
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule
import java.text.NumberFormat
import java.util.Locale

class OwnerPriceRuleAdapter(
    private val onEdit: (OwnerPriceRule) -> Unit,
    private val onDelete: (OwnerPriceRule) -> Unit
) : ListAdapter<OwnerPriceRule, OwnerPriceRuleAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOwnerPriceRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemOwnerPriceRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OwnerPriceRule) {
            val context = binding.root.context
            val (label, chip, ink) = when (item.label) {
                "PEAK" -> Triple(R.string.owner_price_peak_label, R.color.sn_lamp_container, R.color.sn_on_lamp_container)
                "WEEKEND" -> Triple(R.string.owner_price_weekend_label, R.color.sn_surface_sunken, R.color.sn_ink)
                else -> Triple(R.string.owner_price_normal_label, R.color.sn_court_container, R.color.sn_on_court_container)
            }
            binding.ruleLabel.setText(label)
            binding.ruleLabel.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, chip))
            binding.ruleLabel.setTextColor(ContextCompat.getColor(context, ink))
            binding.rulePrice.text = context.getString(R.string.owner_price_per_hour_value, VND.format(item.pricePerHour))
            binding.ruleDaysAndTime.text = context.getString(
                R.string.owner_price_days_time,
                formatDays(item.daysOfWeek),
                OwnerVenueAdapter.clock(item.startTime),
                OwnerVenueAdapter.clock(item.endTime),
            )
            binding.editRule.setOnClickListener { onEdit(item) }
            binding.deleteRule.setOnClickListener { onDelete(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<OwnerPriceRule>() {
        override fun areItemsTheSame(oldItem: OwnerPriceRule, newItem: OwnerPriceRule): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: OwnerPriceRule, newItem: OwnerPriceRule): Boolean = oldItem == newItem
    }

    companion object {
        private val VND: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))

        /** 1 = Thứ Hai … 7 = Chủ nhật. Ba ngày liền nhau trở lên gộp thành khoảng: [1,2,3,4,5] → "T2–T6". */
        internal fun formatDays(days: List<Int>): String {
            fun name(day: Int) = if (day == 7) "CN" else "T${day + 1}"
            val sorted = days.distinct().sorted()
            val parts = mutableListOf<String>()
            var i = 0
            while (i < sorted.size) {
                var j = i
                while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
                if (j - i >= 2) parts += "${name(sorted[i])}–${name(sorted[j])}" else (i..j).forEach { parts += name(sorted[it]) }
                i = j + 1
            }
            return parts.joinToString(", ")
        }
    }
}
