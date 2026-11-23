package com.example.booking_app.feature.payment.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.os.bundleOf
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
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn thanh toán spec 050. US1: tóm tắt đơn + áp/gỡ voucher. US2: chọn cách trả, chốt đơn trả tại sân rồi mở
 * hóa đơn. Chuyển khoản (US4) mở màn QR sau.
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
            rowAtVenue.setOnClickListener { viewModel.select(PaymentOption.AT_VENUE) }
            rowDeposit.setOnClickListener { viewModel.select(PaymentOption.TRANSFER_DEPOSIT) }
            rowFull.setOnClickListener { viewModel.select(PaymentOption.TRANSFER_FULL) }
            btnConfirm.setOnClickListener { viewModel.confirm() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.effects.collect(::handle) }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun handle(effect: PaymentEffect) {
        when (effect) {
            is PaymentEffect.ShowMessage -> Snackbar.make(binding.root, effect.text, Snackbar.LENGTH_LONG).show()
            is PaymentEffect.OpenReceipt -> findNavController().navigate(
                R.id.action_payment_to_receipt,
                bundleOf(
                    "bookingId" to effect.receipt.bookingId,
                    "isRecurring" to (effect.receipt.recurringGroupId != null),
                ),
            )
        }
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

        // Cách thanh toán: ẩn khi miễn phí; chuyển khoản chỉ hiện khi cơ sở có tài khoản nhận tiền.
        methodsSection.isVisible = !breakdown.isFree
        transferGroup.isVisible = state.transferSupported
        tvTransferUnsupported.isVisible = !state.transferSupported
        rbAtVenue.isChecked = state.selected == PaymentOption.AT_VENUE
        rbDeposit.isChecked = state.selected == PaymentOption.TRANSFER_DEPOSIT
        rbFull.isChecked = state.selected == PaymentOption.TRANSFER_FULL
        tvAtVenueDesc.text = getString(R.string.payment_option_at_venue_desc, MoneyFormat.grouped(breakdown.total))
        tvDepositDesc.text = getString(
            R.string.payment_option_deposit_desc,
            MoneyFormat.grouped(breakdown.depositAmount),
            MoneyFormat.grouped(breakdown.remainingAfterDeposit),
        )
        tvFullDesc.text = getString(R.string.payment_option_full_desc, MoneyFormat.grouped(breakdown.total))

        val option = if (breakdown.isFree) PaymentOption.AT_VENUE else state.selected
        tvBottomLabel.text = getString(
            when (option) {
                PaymentOption.AT_VENUE -> R.string.payment_bottom_at_venue
                PaymentOption.TRANSFER_DEPOSIT -> R.string.payment_bottom_deposit
                PaymentOption.TRANSFER_FULL -> R.string.payment_bottom_full
            },
        )
        tvBottomTotal.text = when {
            breakdown.isFree -> getString(R.string.payment_free_badge)
            option == PaymentOption.AT_VENUE -> vnd(breakdown.total)
            else -> vnd(breakdown.amountDue(option))
        }
        btnConfirm.isEnabled = !state.submitting && !state.expired && !state.applyingVoucher
        btnConfirm.text = when {
            state.submitting -> getString(R.string.payment_submitting)
            breakdown.isFree -> getString(R.string.payment_confirm_free_button)
            option == PaymentOption.AT_VENUE -> getString(R.string.payment_confirm_button)
            else -> getString(R.string.payment_transfer_button, MoneyFormat.grouped(breakdown.amountDue(option)))
        }
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
