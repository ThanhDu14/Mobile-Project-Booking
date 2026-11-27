package com.example.booking_app.feature.payment.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.databinding.BottomsheetVoucherPickerBinding
import com.example.booking_app.feature.payment.domain.VoucherRules
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Chọn voucher từ danh sách (T024). Dùng chung `PaymentViewModel` của `PaymentFragment` (mở bằng
 * `childFragmentManager`), chọn xong thì áp luôn và đóng.
 */
@AndroidEntryPoint
class VoucherPickerBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: PaymentViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private var binding: BottomsheetVoucherPickerBinding? = null

    private val adapter = VoucherAdapter { voucher ->
        viewModel.applyVoucher(voucher)
        dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomsheetVoucherPickerBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.rvVouchers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVouchers.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadVouchers() }
        viewModel.loadVouchers()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        binding?.rvVouchers?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun render(state: PaymentUiState) {
        val binding = binding ?: return
        val subtotal = state.breakdown?.subtotal ?: 0L
        val items = state.availableVouchers.map { it.toItem(subtotal) }
        binding.progress.isVisible = state.vouchersLoading
        binding.errorGroup.isVisible = !state.vouchersLoading && state.vouchersError != null
        binding.tvError.text = state.vouchersError
        binding.tvEmpty.isVisible = !state.vouchersLoading && state.vouchersError == null && items.isEmpty()
        binding.rvVouchers.isVisible = !state.vouchersLoading && items.isNotEmpty()
        adapter.submitList(items)
    }

    private fun Voucher.toItem(subtotal: Long): VoucherItemUi {
        val rule = when {
            type == VoucherType.FIXED -> getString(R.string.payment_voucher_fixed, MoneyFormat.grouped(value))
            maxDiscount != null -> getString(R.string.payment_voucher_percent_cap, value.toInt(), MoneyFormat.grouped(maxDiscount))
            else -> getString(R.string.payment_voucher_percent, value.toInt())
        }
        val minOrder = if (minOrderTotal > 0) {
            getString(R.string.payment_voucher_min_order_label, MoneyFormat.grouped(minOrderTotal))
        } else {
            getString(R.string.payment_voucher_no_min)
        }
        val validTo = validToMillis?.let { getString(R.string.payment_voucher_valid_to, DATE.format(Instant.ofEpochMilli(it))) }
        return VoucherItemUi(
            voucher = this,
            scopeLabel = getString(
                if (scope == VoucherScope.VENUE) R.string.payment_voucher_scope_venue else R.string.payment_voucher_scope_system,
            ),
            rule = rule,
            condition = listOfNotNull(minOrder, validTo).joinToString(" · "),
            eligible = VoucherRules.check(this, subtotal, System.currentTimeMillis()) is VoucherCheck.Valid,
        )
    }

    companion object {
        const val TAG = "VoucherPickerBottomSheet"
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
    }
}
