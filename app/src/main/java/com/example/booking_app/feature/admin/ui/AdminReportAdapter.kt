package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemAdminReportBinding
import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminReportAdapter(
    private val onItemClick: (AdminReport) -> Unit,
) : ListAdapter<AdminReport, AdminReportAdapter.ViewHolder>(DiffCallback) {

    private var targetCounts: Map<String, Int> = emptyMap()

    fun updateTargetCounts(reports: List<AdminReport>) {
        targetCounts = reports.groupingBy { it.targetPath }.eachCount()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminReportBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val sameCount = targetCounts[item.targetPath] ?: 1
        holder.bind(item, sameCount)
    }

    class ViewHolder(
        private val binding: ItemAdminReportBinding,
        private val onItemClick: (AdminReport) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())

        @SuppressLint("SetTextI18n")
        fun bind(item: AdminReport, sameTargetCount: Int) {
            val (typeName, iconRes) = when (item.targetType) {
                ReportTargetType.POST -> "BÀI ĐĂNG" to R.drawable.ic_nav_admin_applications
                ReportTargetType.VENUE -> "CƠ SỞ" to R.drawable.ic_nav_admin_home
                ReportTargetType.USER -> "NGƯỜI DÙNG" to R.drawable.ic_nav_admin_users
                ReportTargetType.REVIEW -> "ĐÁNH GIÁ" to R.drawable.ic_nav_admin_reports
                ReportTargetType.MESSAGE -> "TIN NHẮN" to R.drawable.ic_admin_broadcast_megaphone
                else -> item.targetType.name to R.drawable.ic_nav_admin_reports
            }

            binding.textTargetTypeHeader.text = typeName
            binding.imageTargetType.setImageResource(iconRes)

            binding.textReason.text = item.reason.ifBlank { "Nội dung vi phạm" }
            binding.textDetail.text = item.detail.ifBlank { "Không có nội dung mô tả" }

            val dateStr = if (item.createdAt > 0L) dateFormat.format(Date(item.createdAt)) else "09/10 · 08:40"
            binding.textReportDate.text = dateStr

            if (sameTargetCount > 1) {
                binding.layoutSameTargetBadge.isVisible = true
                binding.textSameTargetBadge.text = binding.root.context.getString(R.string.admin_report_same_target_badge, sameTargetCount)
            } else {
                binding.layoutSameTargetBadge.isVisible = false
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