package com.example.booking_app.feature.owner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.databinding.ItemOwnerPriceRuleBinding
import com.example.booking_app.feature.owner.domain.model.OwnerPriceRule

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
            binding.ruleLabel.text = item.label
            binding.rulePrice.text = "%,d đ/h (%,d đ/slot)".format(item.pricePerHour, item.pricePerHour / 2)
            val daysText = item.daysOfWeek.sorted().joinToString(", ") { day ->
                if (day == 7) "CN" else "T${day + 1}"
            }
            binding.ruleDaysAndTime.text = "%s | %s - %s".format(daysText, item.startTime, item.endTime)
            binding.editRule.setOnClickListener { onEdit(item) }
            binding.deleteRule.setOnClickListener { onDelete(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<OwnerPriceRule>() {
        override fun areItemsTheSame(oldItem: OwnerPriceRule, newItem: OwnerPriceRule): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: OwnerPriceRule, newItem: OwnerPriceRule): Boolean = oldItem == newItem
    }
}
