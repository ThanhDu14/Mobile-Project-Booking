package com.example.booking_app.feature.payment.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.databinding.FragmentPaymentReceiptBinding
import com.example.booking_app.feature.booking_management.domain.ReceiptToMyBookingsArgs
import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.PaymentReceipt
import com.example.booking_app.feature.payment.domain.model.PaymentStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Hóa đơn điện tử (đã cọc/đã trả) hoặc Phiếu đặt sân (chưa thanh toán) sau khi đơn sang `PENDING` (T033).
 * Đóng màn thì về trước lưới đặt sân, vì giữ chỗ đã chốt thành đơn.
 */
@AndroidEntryPoint
class PaymentReceiptFragment : Fragment() {

    private val viewModel: ReceiptViewModel by viewModels()
    private var _binding: FragmentPaymentReceiptBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentPaymentReceiptBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { leave() }
        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.btnViewBookings.setOnClickListener {
            findNavController().navigate(
                R.id.action_receipt_to_myBookings,
                bundleOf("args" to ReceiptToMyBookingsArgs(viewModel.bookingId, viewModel.isRecurring, "PENDING")),
            )
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = leave()
            },
        )
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

    /** Bỏ cả luồng đặt sân (lưới → xác nhận → thanh toán) khỏi back stack; không có thì lùi bình thường. */
    private fun leave() {
        val nav = findNavController()
        if (!nav.popBackStack(R.id.bookingSlotGridFragment, true)) nav.navigateUp()
    }

    private fun render(state: ReceiptUiState) = with(binding) {
        progress.isVisible = state is ReceiptUiState.Loading
        errorGroup.isVisible = state is ReceiptUiState.Error
        content.isVisible = state is ReceiptUiState.Content
        btnViewBookings.isEnabled = state is ReceiptUiState.Content
        val receipt = (state as? ReceiptUiState.Content)?.receipt ?: return@with
        val paid = receipt.status == PaymentStatus.PAID || receipt.status == PaymentStatus.DEPOSITED
        tvTitle.setText(if (paid) R.string.receipt_title_paid else R.string.receipt_title_unpaid)
        tvHeaderTitle.setText(if (paid) R.string.receipt_title_paid else R.string.receipt_title_unpaid)
        tvStatus.setText(
            when (receipt.status) {
                PaymentStatus.PAID -> R.string.receipt_status_paid
                PaymentStatus.DEPOSITED -> R.string.receipt_status_deposited
                else -> R.string.receipt_status_unpaid
            },
        )
        tvVenueName.text = receipt.venueName
        itemsContainer.removeAllViews()
        receipt.items.forEach { itemsContainer.addView(line(getString(R.string.payment_item_bullet, it))) }
        renderAmounts(receipt)
    }

    private fun renderAmounts(r: PaymentReceipt) {
        val rows = binding.amountRows
        rows.removeAllViews()
        val code = if (r.recurringGroupId != null) R.string.receipt_group_code else R.string.receipt_booking_code
        rows.addView(row(getString(code), r.bookingId.take(8).uppercase()))
        rows.addView(row(getString(R.string.receipt_payment_method), getString(methodLabel(r.option))))
        rows.addView(row(getString(R.string.payment_subtotal), vnd(r.subtotal)))
        if (r.discount > 0) {
            val label = r.voucherCode?.let { getString(R.string.payment_discount_with_code, it) }
                ?: getString(R.string.payment_discount)
            rows.addView(row(label, getString(R.string.payment_amount_minus_vnd, MoneyFormat.grouped(r.discount))))
        }
        rows.addView(row(getString(R.string.payment_total), vnd(r.total), bold = true))
        rows.addView(row(getString(R.string.receipt_paid_amount), vnd(r.paidAmount)))
        rows.addView(row(getString(R.string.receipt_remaining_amount), vnd(r.remainingAmount)))
    }

    private fun methodLabel(option: PaymentOption) = when (option) {
        PaymentOption.AT_VENUE -> R.string.payment_option_at_venue
        PaymentOption.TRANSFER_DEPOSIT -> R.string.payment_option_deposit
        PaymentOption.TRANSFER_FULL -> R.string.payment_option_full
    }

    private fun line(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_SmashNow_Body)
        setTextColor(requireContext().getColor(R.color.sn_ink_muted))
        setPadding(0, 4, 0, 4)
    }

    private fun row(label: String, value: String, bold: Boolean = false) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 6, 0, 6)
        addView(
            TextView(context).apply {
                text = label
                setTextColor(context.getColor(if (bold) R.color.sn_ink else R.color.sn_ink_muted))
                textSize = 14f
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            TextView(context).apply {
                text = value
                setTextColor(context.getColor(R.color.sn_ink))
                textSize = if (bold) 16f else 14f
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
    }

    private fun vnd(amount: Long) = getString(R.string.payment_amount_vnd, MoneyFormat.grouped(amount))
}
