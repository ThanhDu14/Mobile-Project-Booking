package com.example.booking_app.feature.payment.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.databinding.FragmentPaymentTransferBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn chuyển khoản VietQR (spec 050 US4, T047). Back hoặc "Đổi sang thanh toán tại sân" về `PaymentFragment`
 * (giữ chỗ vẫn còn, chốt lại bằng cùng `requestId`). Hết hạn thì nút đổi thành "Chọn lại khung giờ".
 */
@AndroidEntryPoint
class TransferFragment : Fragment() {

    private val viewModel: TransferViewModel by viewModels()
    private var _binding: FragmentPaymentTransferBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var qrLoadedFor: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentPaymentTransferBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        qrLoadedFor = null
        with(binding) {
            btnBack.setOnClickListener { findNavController().navigateUp() }
            btnCopyAccount.setOnClickListener { copy(tvAccountNumber.text) }
            btnCopyCode.setOnClickListener { copy(tvPaymentCode.text) }
            btnSwitchToVenue.setOnClickListener {
                if (viewModel.uiState.value.expired) backToGrid() else findNavController().navigateUp()
            }
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

    private fun render(state: TransferUiState) = with(binding) {
        val i = state.instruction ?: return@with
        val amount = getString(R.string.payment_amount_vnd, MoneyFormat.grouped(i.amountDue))
        tvAmountBig.text = amount
        tvAmount.text = amount
        tvBank.text = i.payoutAccount.bankShortName
        tvAccountNumber.text = i.payoutAccount.accountNumber
        tvAccountHolder.text = i.payoutAccount.accountHolderName
        tvPaymentCode.text = i.paymentCode
        ivQr.contentDescription = getString(
            R.string.transfer_qr_description, MoneyFormat.grouped(i.amountDue), i.payoutAccount.accountHolderName, i.paymentCode,
        )
        if (qrLoadedFor != i.qrUrl) {
            qrLoadedFor = i.qrUrl
            qrProgress.isVisible = true
            ivQr.load(i.qrUrl) {
                crossfade(true)
                listener(onSuccess = { _, _ -> _binding?.qrProgress?.isVisible = false },
                    onError = { _, _ -> _binding?.qrProgress?.isVisible = false })
            }
        }
        val s = state.holdRemainingSeconds
        tvCountdown.text = getString(R.string.transfer_countdown, "%02d:%02d".format(s / 60, s % 60))
        tvCountdown.isVisible = !state.expired
        tvExpired.isVisible = state.expired
        waitingRow.isVisible = !state.expired
        ivQr.alpha = if (state.expired) 0.3f else 1f
        btnSwitchToVenue.setText(if (state.expired) R.string.transfer_back_to_grid else R.string.transfer_switch_to_venue)
    }

    private fun handle(effect: PaymentEffect) {
        when (effect) {
            is PaymentEffect.ShowMessage -> Snackbar.make(binding.root, effect.text, Snackbar.LENGTH_LONG).show()
            is PaymentEffect.OpenReceipt -> {
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.transferFragment) return
                nav.navigate(
                    R.id.action_transfer_to_receipt,
                    bundleOf("bookingId" to effect.receipt.bookingId, "isRecurring" to viewModel.isRecurring),
                )
            }
            is PaymentEffect.OpenTransfer -> Unit
        }
    }

    private fun copy(text: CharSequence) {
        requireContext().getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.payment_transfer_title), text))
        Snackbar.make(binding.root, viewModel.copiedMessage(), Snackbar.LENGTH_SHORT).show()
    }

    /** Giữ chỗ đã hết: bỏ luồng thanh toán, về lưới khung giờ để chọn lại. */
    private fun backToGrid() {
        val nav = findNavController()
        if (!nav.popBackStack(R.id.bookingSlotGridFragment, false)) nav.navigateUp()
    }
}
