package com.example.booking_app.feature.courtdetail.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemCourtDetailPriceGroupBinding
import com.example.booking_app.databinding.ItemCourtDetailPriceRowBinding
import com.example.booking_app.feature.courtdetail.domain.model.DayGroupTitle
import com.example.booking_app.feature.courtdetail.domain.model.PriceDayGroup
import com.example.booking_app.feature.courtdetail.domain.model.PriceLabel
import com.example.booking_app.feature.courtdetail.domain.model.PriceRow
import com.google.android.material.color.MaterialColors
import java.time.DayOfWeek

/** Bảng giá phẳng: tiêu đề nhóm ngày rồi các dòng giá. Dòng tô dùng `?attr/colorSecondaryContainer`. */
class PriceTableAdapter : ListAdapter<PriceTableAdapter.Item, RecyclerView.ViewHolder>(Diff) {

    sealed interface Item {
        data class Group(val title: DayGroupTitle) : Item
        data class Row(val groupIndex: Int, val row: PriceRow) : Item
    }

    fun submitGroups(groups: List<PriceDayGroup>) {
        submitList(
            groups.flatMapIndexed { i, g -> listOf(Item.Group(g.title)) + g.rows.map { Item.Row(i, it) } },
        )
    }

    override fun getItemViewType(position: Int): Int = if (getItem(position) is Item.Group) TYPE_GROUP else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP) {
            GroupHolder(ItemCourtDetailPriceGroupBinding.inflate(inflater, parent, false))
        } else {
            RowHolder(ItemCourtDetailPriceRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.Group -> (holder as GroupHolder).bind(item.title)
            is Item.Row -> (holder as RowHolder).bind(item.row)
        }
    }

    private class GroupHolder(val binding: ItemCourtDetailPriceGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: DayGroupTitle) {
            binding.groupTitle.text = titleText(binding.root.context, title)
        }
    }

    private class RowHolder(val binding: ItemCourtDetailPriceRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PriceRow) {
            val context = binding.root.context
            binding.timeText.text = context.getString(R.string.court_detail_time_range, row.start.format(), row.end.format())
            binding.labelText.setText(
                when (row.label) {
                    PriceLabel.NORMAL -> R.string.court_detail_label_normal
                    PriceLabel.PEAK -> R.string.court_detail_label_peak
                    PriceLabel.WEEKEND -> R.string.court_detail_label_weekend
                },
            )
            binding.priceText.text =
                context.getString(R.string.court_detail_price_per_hour, CourtDetailFormat.vnd(row.pricePerHour))
            binding.root.setBackgroundColor(
                if (row.highlighted) {
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                } else {
                    Color.TRANSPARENT
                },
            )
            binding.root.isSelected = row.highlighted
        }
    }

    private object Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean = when {
            oldItem is Item.Group && newItem is Item.Group -> oldItem.title == newItem.title
            oldItem is Item.Row && newItem is Item.Row ->
                oldItem.groupIndex == newItem.groupIndex && oldItem.row.ruleId == newItem.row.ruleId
            else -> false
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
    }

    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_ROW = 1

        private fun dayName(context: Context, day: DayOfWeek): String = context.getString(
            when (day) {
                DayOfWeek.MONDAY -> R.string.court_detail_day_monday
                DayOfWeek.TUESDAY -> R.string.court_detail_day_tuesday
                DayOfWeek.WEDNESDAY -> R.string.court_detail_day_wednesday
                DayOfWeek.THURSDAY -> R.string.court_detail_day_thursday
                DayOfWeek.FRIDAY -> R.string.court_detail_day_friday
                DayOfWeek.SATURDAY -> R.string.court_detail_day_saturday
                DayOfWeek.SUNDAY -> R.string.court_detail_day_sunday
            },
        )

        fun titleText(context: Context, title: DayGroupTitle): String = when (title) {
            is DayGroupTitle.Single -> dayName(context, title.day)
            is DayGroupTitle.Pair ->
                context.getString(R.string.court_detail_day_pair, dayName(context, title.first), dayName(context, title.second))
            is DayGroupTitle.Range ->
                context.getString(R.string.court_detail_day_range, dayName(context, title.from), dayName(context, title.to))
        }
    }
}
