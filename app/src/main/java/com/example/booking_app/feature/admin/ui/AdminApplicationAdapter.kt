package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemAdminApplicationBinding
import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication

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

        @SuppressLint("SetTextI18n")
        fun bind(item: AdminOwnerApplication) {
            binding.textVenueName.text = item.venueName.ifBlank { "Cơ sở chưa đặt tên" }
            binding.textApplicantName.text = "Người đại diện: ${item.applicantName.ifBlank { "Chưa cập nhật" }} (${item.applicantPhone})"
            binding.textVenueAddress.text = "Địa chỉ: ${item.venueAddress.ifBlank { "Chưa có địa chỉ" }}"
            binding.textCourtCount.text = "${item.courtCount} sân"
            binding.chipStatus.text = when (item.status) {
                "APPROVED" -> binding.root.context.getString(R.string.admin_status_approved)
                "REJECTED" -> binding.root.context.getString(R.string.admin_status_rejected)
                else -> binding.root.context.getString(R.string.admin_status_pending)
            }

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
