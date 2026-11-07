package com.example.booking_app.feature.booking.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemRecurringSessionSummaryBinding
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecurringSessionSummaryAdapter :
    ListAdapter<RecurringSession, RecurringSessionSummaryAdapter.SessionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemRecurringSessionSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(
        private val binding: ItemRecurringSessionSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: RecurringSession) {
            val context = binding.root.context
            val dayName = when (session.date.dayOfWeek.value) {
                1 -> "T2"
                2 -> "T3"
                3 -> "T4"
                4 -> "T5"
                5 -> "T6"
                6 -> "T7"
                else -> "CN"
            }
            val dateStr = session.date.format(DateTimeFormatter.ofPattern("dd/MM"))
            binding.tvSessionDate.text = "$dayName $dateStr"
            binding.tvSessionCourt.text = session.courtName.ifEmpty { "Sân" }

            val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
            binding.tvSessionPrice.text = "${formatter.format(session.price)} đ"

            val isConflict = session.status == RecurringSessionStatus.SUGGEST_CHANGE_COURT ||
                session.status == RecurringSessionStatus.MUST_SKIP

            if (isConflict) {
                binding.sessionItemLayout.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.sn_lamp_container)
                )
                binding.tvSessionStatusBadge.visibility = View.VISIBLE
                binding.tvSessionStatusBadge.text = "trùng lịch"
                binding.tvSessionStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.sn_lamp_container)
                binding.tvSessionStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.sn_on_lamp_container))
            } else {
                binding.sessionItemLayout.setBackgroundColor(Color.TRANSPARENT)
                if (session.courtId != session.originalCourtId) {
                    binding.tvSessionStatusBadge.visibility = View.VISIBLE
                    binding.tvSessionStatusBadge.text = "Đã đổi sân"
                    binding.tvSessionStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.sn_court_container)
                    binding.tvSessionStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.sn_on_court_container))
                } else {
                    binding.tvSessionStatusBadge.visibility = View.GONE
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecurringSession>() {
        override fun areItemsTheSame(oldItem: RecurringSession, newItem: RecurringSession): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: RecurringSession, newItem: RecurringSession): Boolean {
            return oldItem == newItem
        }
    }
}
