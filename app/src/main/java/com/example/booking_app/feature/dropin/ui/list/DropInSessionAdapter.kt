package com.example.booking_app.feature.dropin.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.databinding.ItemDropinSessionBinding
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.ui.common.DropInUiText

/** Thẻ buổi trong danh sách (T026, FR-002). */
class DropInSessionAdapter(
    private val onClick: (DropInSession) -> Unit,
) : ListAdapter<DropInSession, DropInSessionAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDropinSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDropinSessionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: DropInSession) {
            val context = binding.root.context
            val start = session.startAt.atZone(TimeFormat.ZONE)
            binding.dayOfWeek.setText(DropInUiText.dayOfWeekShort(start.dayOfWeek))
            binding.dayOfMonth.text = "%02d".format(start.dayOfMonth)
            val relative = DropInUiText.relativeDay(session.startAt)
            binding.relativeDay.isVisible = relative != null
            relative?.let(binding.relativeDay::setText)

            val timeRange = TimeFormat.timeRange(session.startAt, session.endAt)
            binding.timeRange.text = timeRange
            binding.venueDistrict.text =
                context.getString(R.string.dropin_venue_district, session.venueName, session.districtName)
            val level = context.getString(DropInUiText.level(session.level))
            binding.levelChip.text = level

            val full = session.status == SessionStatus.FULL || session.remaining <= 0
            binding.fullChip.isVisible = full
            val remaining = context.getString(R.string.dropin_list_remaining, session.remaining, session.capacity)
            binding.remaining.isVisible = !full
            binding.remaining.text = remaining
            val price = MoneyFormat.vnd(session.pricePerPerson)
            binding.price.text = price

            binding.root.contentDescription = context.getString(
                R.string.dropin_list_item_description,
                TimeFormat.dayOfWeekDate(session.startAt),
                timeRange,
                binding.venueDistrict.text,
                level,
                if (full) context.getString(R.string.dropin_label_full) else remaining,
                context.getString(R.string.dropin_price_per_turn, price),
            )
            binding.root.setOnClickListener { onClick(session) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DropInSession>() {
        override fun areItemsTheSame(oldItem: DropInSession, newItem: DropInSession) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DropInSession, newItem: DropInSession) = oldItem == newItem
    }
}
