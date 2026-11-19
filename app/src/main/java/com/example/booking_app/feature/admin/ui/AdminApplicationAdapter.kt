package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemAdminApplicationBinding
import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminApplicationAdapter(
    private val onItemClick: (AdminOwnerApplication) -> Unit,
) : ListAdapter<AdminOwnerApplication, AdminApplicationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminApplicationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAdminApplicationBinding,
        private val onItemClick: (AdminOwnerApplication) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())

        @SuppressLint("SetTextI18n")
        fun bind(item: AdminOwnerApplication) {
            binding.textVenueName.text = item.venueName.ifBlank { "Cơ sở chưa đặt tên" }
            
            val applicant = item.applicantName.ifBlank { "Người nộp" }
            val district = item.districtName.ifBlank { item.districtId.ifBlank { "Chưa có quận/huyện" } }
            binding.textApplicantAndDistrict.text = "$applicant · $district"

            when (item.status) {
                "APPROVED" -> {
                    binding.chipStatus.setBackgroundResource(R.drawable.bg_admin_chip_approved)
                    binding.chipStatus.setTextColor(Color.parseColor("#065F46"))
                    binding.chipStatus.text = binding.root.context.getString(R.string.admin_status_approved)
                }
                "REJECTED" -> {
                    binding.chipStatus.setBackgroundResource(R.drawable.bg_admin_chip_rejected)
                    binding.chipStatus.setTextColor(Color.parseColor("#991B1B"))
                    binding.chipStatus.text = binding.root.context.getString(R.string.admin_status_rejected)
                }
                else -> {
                    binding.chipStatus.setBackgroundResource(R.drawable.bg_admin_chip_pending)
                    binding.chipStatus.setTextColor(Color.parseColor("#92400E"))
                    binding.chipStatus.text = binding.root.context.getString(R.string.admin_status_pending)
                }
            }

            // If resubmitted
            binding.chipResubmitted.isVisible = item.status == "PENDING" && !item.rejectReason.isNullOrBlank()
            if (binding.chipResubmitted.isVisible) {
                binding.chipResubmitted.text = "Có lần từ chối trước"
            }

            val docCount = item.documentPaths.size
            binding.textDocCount.text = binding.root.context.getString(R.string.admin_docs_count, docCount)

            val dateStr = if (item.submittedAt > 0) dateFormat.format(Date(item.submittedAt)) else "Chưa có thời điểm"
            binding.textSubmittedAt.text = binding.root.context.getString(R.string.admin_submitted_at, dateStr)

            binding.cardApplication.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AdminOwnerApplication>() {
        override fun areItemsTheSame(oldItem: AdminOwnerApplication, newItem: AdminOwnerApplication): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AdminOwnerApplication, newItem: AdminOwnerApplication): Boolean {
            return oldItem == newItem
        }
    }
}
