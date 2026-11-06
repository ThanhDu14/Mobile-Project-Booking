package com.example.booking_app.feature.admin.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

            val discountText = if (voucher.type.equals("PERCENTAGE", ignoreCase = true)) {
                "Giảm ${voucher.value}% (Tối đa ${numberFormat.format(voucher.maxDiscount)} đ)"
            } else {
                "Giảm ${numberFormat.format(voucher.value)} đ"
            }
            binding.textVoucherDiscount.text = discountText

            val from = if (voucher.validFrom.length >= 10) voucher.validFrom.take(10) else voucher.validFrom
            val to = if (voucher.validTo.length >= 10) voucher.validTo.take(10) else voucher.validTo
            binding.textVoucherValidity.text = "Hiệu lực: $from - $to"
            binding.textVoucherUsage.text = "Lượt dùng: ${voucher.usedCount} / ${voucher.usageLimit}"

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
