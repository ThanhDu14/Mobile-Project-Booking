package com.example.booking_app.feature.admin.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemAdminSystemVoucherBinding
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import java.text.NumberFormat
import java.util.Locale

class AdminSystemVoucherAdapter(
    private val onToggleActive: (AdminSystemVoucher, Boolean) -> Unit,
) : ListAdapter<AdminSystemVoucher, AdminSystemVoucherAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminSystemVoucherBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAdminSystemVoucherBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(voucher: AdminSystemVoucher) {
            val numberFormat = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
            binding.textVoucherCode.text = voucher.code

            if (voucher.isActive) {
                binding.textVoucherStatusTag.setBackgroundResource(R.drawable.bg_admin_chip_approved)
                binding.textVoucherStatusTag.setTextColor(Color.parseColor("#065F46"))
                binding.textVoucherStatusTag.text = binding.root.context.getString(R.string.admin_voucher_status_active)
            } else {
                binding.textVoucherStatusTag.setBackgroundResource(R.drawable.bg_admin_chip_pending)
                binding.textVoucherStatusTag.setTextColor(Color.parseColor("#5F6368"))
                binding.textVoucherStatusTag.text = binding.root.context.getString(R.string.admin_voucher_status_inactive)
            }

            val discountPart = if (voucher.type.equals("PERCENTAGE", ignoreCase = true)) {
                "Giảm ${voucher.value}%, tối đa ${numberFormat.format(voucher.maxDiscount)} đ"
            } else {
                "Giảm ${numberFormat.format(voucher.value)} đ"
            }
            val minOrderPart = "đơn từ ${numberFormat.format(voucher.minOrderTotal)} đ"
            val perUserPart = "mỗi người ${voucher.perUserLimit} lần"
            binding.textVoucherDetail.text = "$discountPart · $minOrderPart · $perUserPart"

            val from = if (voucher.validFrom.length >= 10) voucher.validFrom.take(10) else voucher.validFrom
            val to = if (voucher.validTo.length >= 10) voucher.validTo.take(10) else voucher.validTo
            binding.textVoucherValidity.text = "$from – $to"
            binding.textVoucherUsage.text = binding.root.context.getString(
                R.string.admin_voucher_used_count_format,
                voucher.usedCount,
                voucher.usageLimit
            )

            binding.switchVoucherActive.setOnCheckedChangeListener(null)
            binding.switchVoucherActive.isChecked = voucher.isActive
            binding.switchVoucherActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(voucher, isChecked)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AdminSystemVoucher>() {
        override fun areItemsTheSame(oldItem: AdminSystemVoucher, newItem: AdminSystemVoucher): Boolean {
            return oldItem.code == newItem.code
        }

        override fun areContentsTheSame(oldItem: AdminSystemVoucher, newItem: AdminSystemVoucher): Boolean {
            return oldItem == newItem
        }
    }
}