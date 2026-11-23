package com.example.booking_app.feature.payment.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.databinding.FragmentPaymentBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn thanh toán spec 050 (T025). US1: tóm tắt đơn + áp/gỡ voucher. Nút xác nhận chưa chốt đơn
 * (US2 nối `booking-payment-confirm`/`booking-payment-transfer`).
 */
@AndroidEntryPoint
class PaymentFragment : Fragment() {

    private val viewModel: PaymentViewModel by viewModels()
    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var renderedItems: List<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentPaymentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderedItems = null
        with(binding) {
            btnBack.setOnClickListener { findNavController().navigateUp() }
            btnErrorBack.setOnClickListener { findNavController().navigateUp() }
            btnApplyVoucher.setOnClickListener { applyTypedCode() }
            etVoucher.setOnEditorActionListener { _, actionId, _ ->
                (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) applyTypedCode() }
            }
            etVoucher.doAfterTextChanged { if (tvVoucherError.isVisible) viewModel.clearVoucherError() }
            btnRemoveVoucher.setOnClickListener { viewModel.removeVoucher() }
            btnChooseVoucher.setOnClickListener {
                if (childFragmentManager.findFragmentByTag(VoucherPickerBottomSheet.TAG) == null) {
                    VoucherPickerBottomSheet().show(childFragmentManager, VoucherPickerBottomSheet.TAG)
                }
            }
            btnConfirm.setOnClickListener {
                Snackbar.make(root, R.string.payment_confirm_coming, Snackbar.LENGTH_SHORT).show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun applyTypedCode() {
        viewModel.applyCode(binding.etVoucher.text?.toString().orEmpty())
    }

    private fun render(state: PaymentUiState) = with(binding) {
        progress.isVisible = state.loading
        errorGroup.isVisible = !state.loading && state.error != null
        tvError.text = state.error
        val summary = state.summary
        val breakdown = state.breakdown
        val showContent = !state.loading && state.error == null && summary != null && breakdown != null
        content.isVisible = showContent
        bottomBar.isVisible = showContent
        if (!showContent) return@with

        tvVenueName.text = summary!!.venueName
        tvVenueAddress.text = summary.venueAddress
        tvVenueAddress.isVisible = summary.venueAddress.isNotBlank()
        tvSummaryHeader.text = if (summary.isRecurring) {
            getString(R.string.payment_recurring_sessions, summary.totalSessions)
        } else {
            getString(R.string.payment_slots_count, summary.slotCount)
        }
        renderItems(summary.items)

        val voucher = state.voucher
        voucherInputRow.isVisible = voucher == null
        voucherAppliedRow.isVisible = voucher != null
        btnChooseVoucher.isVisible = voucher == null
        if (voucher != null) {
            tvVoucherApplied.text = getString(R.string.payment_voucher_applied, voucher.code, MoneyFormat.grouped(voucher.discount))
        }
        btnApplyVoucher.isEnabled = !state.applyingVoucher
        btnApplyVoucher.text = if (state.applyingVoucher) "" else getString(R.string.payment_voucher_apply)
        voucherProgress.isVisible = state.applyingVoucher
        tvVoucherError.isVisible = state.voucherError != null
        tvVoucherError.text = state.voucherError

        tvSubtotal.text = vnd(breakdown!!.subtotal)
        tvDiscountLabel.text = voucher?.let { getString(R.string.payment_discount_with_code, it.code) }
            ?: getString(R.string.payment_discount)
        tvDiscount.text = if (breakdown.discount > 0) {
            getString(R.string.payment_amount_minus_vnd, MoneyFormat.grouped(breakdown.discount))
        } else {
            vnd(0)
        }
        tvTotal.text = vnd(breakdown.total)
        tvFreeBadge.isVisible = breakdown.isFree
        tvFreeNote.isVisible = breakdown.isFree
        tvBottomTotal.text = if (breakdown.isFree) getString(R.string.payment_free_badge) else vnd(breakdown.total)
    }

    /** Danh sách sân/buổi không đổi trong phiên, chỉ dựng lại khi khác. */
    private fun renderItems(items: List<String>) {
        if (items == renderedItems) return
        renderedItems = items
        val container = binding.itemsContainer
        container.removeAllViews()
        items.forEach { line ->
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.payment_item_bullet, line)
                    setTextAppearance(R.style.TextAppearance_SmashNow_Body)
                    setTextColor(requireContext().getColor(R.color.sn_ink_muted))
                    setPadding(0, 4, 0, 4)
                },
            )
        }
        container.isVisible = items.isNotEmpty()
    }

    private fun vnd(amount: Long) = getString(R.string.payment_amount_vnd, MoneyFormat.grouped(amount))
}
