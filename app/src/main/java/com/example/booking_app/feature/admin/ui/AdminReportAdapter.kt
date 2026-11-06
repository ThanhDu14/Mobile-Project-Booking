package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.databinding.ItemAdminReportBinding
import com.example.booking_app.feature.admin.domain.model.AdminReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminReportAdapter(
    private val onItemClick: (AdminReport) -> Unit,
) : ListAdapter<AdminReport, AdminReportAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminReportBinding.inflate(
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
        private val binding: ItemAdminReportBinding,
        private val onItemClick: (AdminReport) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        @SuppressLint("SetTextI18n")
        fun bind(item: AdminReport) {
            binding.chipTargetType.text = item.targetType.name
            binding.textReason.text = item.reason.ifBlank { "Không có lý do cụ thể" }
            binding.textDetail.text = item.detail.ifBlank { "Không có chi tiết bổ sung" }
            binding.textTargetPath.text = "Đối tượng: ${item.targetPath}"

            if (item.createdAt > 0L) {
                binding.textReportDate.text = dateFormat.format(Date(item.createdAt))
            } else {
                binding.textReportDate.text = ""
            }

            binding.cardReport.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AdminReport>() {
        override fun areItemsTheSame(oldItem: AdminReport, newItem: AdminReport): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AdminReport, newItem: AdminReport): Boolean {
            return oldItem == newItem
        }
    }
}
