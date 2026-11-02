package com.example.booking_app.feature.booking.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemRecurringConflictBinding
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.usecase.ConflictAction
import java.time.format.DateTimeFormatter

class ConflictResolutionAdapter(
    private val onActionChanged: (RecurringSession, ConflictAction) -> Unit
) : ListAdapter<RecurringSession, ConflictResolutionAdapter.ConflictViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConflictViewHolder {
        val binding = ItemRecurringConflictBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConflictViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConflictViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConflictViewHolder(
        private val binding: ItemRecurringConflictBinding
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
            val courtOriginal = session.courtName.ifEmpty { "Sân ưu tiên" }

            binding.tvConflictHeader.text = "$dayName $dateStr: $courtOriginal đã có người đặt"

            val suggestedCourt = session.suggestedCourts.firstOrNull()

            if (session.status == RecurringSessionStatus.MUST_SKIP || suggestedCourt == null) {
                binding.layoutConflictActions.visibility = View.GONE
                binding.tvMustSkipNotice.visibility = View.VISIBLE
                binding.tvConflictSubtitle.text = "Tất cả các sân đều đã kín lịch trong khung giờ này."
            } else {
                binding.layoutConflictActions.visibility = View.VISIBLE
                binding.tvMustSkipNotice.visibility = View.GONE
                binding.tvConflictSubtitle.text = "${suggestedCourt.name} (cùng mặt thảm) còn trống cùng giờ, cùng giá."
                binding.btnChangeCourt.text = "Đổi sang ${suggestedCourt.name}"

                val isExcluded = session.isExcluded || session.status == RecurringSessionStatus.EXCLUDED
                val isChanged = session.courtId == suggestedCourt.id

                if (isChanged) {
                    binding.btnChangeCourt.backgroundTintList = ContextCompat.getColorStateList(context, R.color.sn_court)
                    binding.btnChangeCourt.setTextColor(ContextCompat.getColor(context, R.color.sn_on_court))
                    binding.btnSkipSession.strokeColor = ContextCompat.getColorStateList(context, R.color.sn_line)
                    binding.btnSkipSession.setTextColor(ContextCompat.getColor(context, R.color.sn_ink_muted))
                } else if (isExcluded) {
                    binding.btnChangeCourt.backgroundTintList = ContextCompat.getColorStateList(context, R.color.sn_court_container)
                    binding.btnChangeCourt.setTextColor(ContextCompat.getColor(context, R.color.sn_on_court_container))
                    binding.btnSkipSession.strokeColor = ContextCompat.getColorStateList(context, R.color.sn_danger)
                    binding.btnSkipSession.setTextColor(ContextCompat.getColor(context, R.color.sn_danger))
                } else {
                    binding.btnChangeCourt.backgroundTintList = ContextCompat.getColorStateList(context, R.color.sn_court_container)
                    binding.btnChangeCourt.setTextColor(ContextCompat.getColor(context, R.color.sn_on_court_container))
                    binding.btnSkipSession.strokeColor = ContextCompat.getColorStateList(context, R.color.sn_line)
                    binding.btnSkipSession.setTextColor(ContextCompat.getColor(context, R.color.sn_ink_muted))
                }

                binding.btnChangeCourt.setOnClickListener {
                    onActionChanged(
                        session,
                        ConflictAction.ChangeCourt(
                            newCourtId = suggestedCourt.id,
                            newCourtName = suggestedCourt.name,
                            newSurfaceType = suggestedCourt.surfaceType
                        )
                    )
                }

                binding.btnSkipSession.setOnClickListener {
                    onActionChanged(session, ConflictAction.SkipSession)
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
