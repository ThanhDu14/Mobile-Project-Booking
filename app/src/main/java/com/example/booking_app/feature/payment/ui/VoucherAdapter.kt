package com.example.booking_app.feature.payment.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.databinding.ItemPaymentVoucherBinding
import com.example.booking_app.feature.payment.domain.model.Voucher

/** Một dòng trong bottom sheet: chuỗi đã dựng sẵn ở [VoucherPickerBottomSheet]. */
data class VoucherItemUi(
    val voucher: Voucher,
    val scopeLabel: String,
    val rule: String,
    val condition: String,
    /** Đủ điều kiện với tạm tính hiện tại; không đủ thì ẩn nút Dùng. */
    val eligible: Boolean,
)

class VoucherAdapter(
    private val onUse: (Voucher) -> Unit,
) : ListAdapter<VoucherItemUi, VoucherAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPaymentVoucherBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemPaymentVoucherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VoucherItemUi) = with(binding) {
            tvCode.text = item.voucher.code
            tvScope.text = item.scopeLabel
            tvRule.text = item.rule
            tvCondition.text = item.condition
            btnUse.isVisible = item.eligible
            tvNotEligible.isVisible = !item.eligible
            root.alpha = if (item.eligible) 1f else 0.6f
            btnUse.setOnClickListener { onUse(item.voucher) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<VoucherItemUi>() {
        override fun areItemsTheSame(old: VoucherItemUi, new: VoucherItemUi) =
            old.voucher.code == new.voucher.code && old.voucher.scope == new.voucher.scope

        override fun areContentsTheSame(old: VoucherItemUi, new: VoucherItemUi) = old == new
    }
}
