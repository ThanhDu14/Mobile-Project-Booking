package com.example.booking_app.feature.booking.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemCourtTabBinding
import com.example.booking_app.feature.booking.domain.model.Court

data class CourtTabItem(
    val court: Court,
    val isSelected: Boolean
)

class CourtTabAdapter(
    private val onCourtClick: (Court) -> Unit
) : ListAdapter<CourtTabItem, CourtTabAdapter.CourtViewHolder>(CourtDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourtViewHolder {
        val binding = ItemCourtTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CourtViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CourtViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CourtViewHolder(
        private val binding: ItemCourtTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CourtTabItem) {
            val context = itemView.context
            val court = item.court

            binding.tvCourtName.text = court.name
            binding.tvSurfaceType.text = "• ${court.surfaceType}"

            if (item.isSelected) {
                binding.cardCourt.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.booking_court_tab_selected_bg)
                )
                binding.cardCourt.strokeColor = ContextCompat.getColor(
                    context, R.color.booking_court_tab_selected_stroke
                )
                binding.tvCourtName.setTextColor(
                    ContextCompat.getColor(context, R.color.booking_court_tab_selected_text)
                )
            } else {
                binding.cardCourt.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.booking_court_tab_unselected_bg)
                )
                binding.cardCourt.strokeColor = ContextCompat.getColor(
                    context, R.color.booking_court_tab_unselected_stroke
                )
                binding.tvCourtName.setTextColor(
                    ContextCompat.getColor(context, R.color.booking_court_tab_unselected_text)
                )
            }

            binding.cardCourt.setOnClickListener {
                if (!item.isSelected) {
                    onCourtClick(court)
                }
            }
        }
    }

    object CourtDiffCallback : DiffUtil.ItemCallback<CourtTabItem>() {
        override fun areItemsTheSame(oldItem: CourtTabItem, newItem: CourtTabItem): Boolean {
            return oldItem.court.id == newItem.court.id
        }

        override fun areContentsTheSame(oldItem: CourtTabItem, newItem: CourtTabItem): Boolean {
            return oldItem == newItem
        }
    }
}
