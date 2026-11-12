package com.example.booking_app.feature.dropin.ui.ticket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.databinding.BottomSheetDropinCancelBinding
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.CancelReason
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Bottom sheet hủy đăng ký (US4, T055). Mở từ màn hình vé bằng [show], không phải destination của nav graph. Hủy xong
 * hoặc không thể hủy nữa thì trả kết quả qua Fragment Result [RESULT_KEY] cho màn hình vé hiện Snackbar; vé tự cập
 * nhật nhờ nghe realtime đăng ký.
 */
@AndroidEntryPoint
class CancelDropInBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: CancelDropInViewModel by viewModels()

    private var _binding: BottomSheetDropinCancelBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDropinCancelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cancelKeep.setOnClickListener { dismiss() }
        binding.cancelConfirm.setOnClickListener {
            if (viewModel.uiState.value is CancelDropInUiState.QuoteFailed) viewModel.retryQuote() else viewModel.confirm()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CancelDropInEvent.Close -> finish(
                                message = DropInUiText.error(requireContext(), event.error),
                                cancelled = false,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: CancelDropInUiState) {
        val quote: CancelQuote? = when (state) {
            is CancelDropInUiState.Quote -> state.quote
            is CancelDropInUiState.Cancelling -> state.quote
            else -> null
        }
        val busy = state is CancelDropInUiState.Quoting || state is CancelDropInUiState.Cancelling
        isCancelable = state !is CancelDropInUiState.Cancelling

        binding.cancelQuoting.isVisible = state is CancelDropInUiState.Quoting
        binding.cancelQuoteGroup.isVisible = quote != null
        if (quote != null) {
            binding.cancelQuoteTitle.text = when (CancelQuoteText.of(quote)) {
                CancelQuoteText.REFUND_FULL ->
                    getString(R.string.dropin_cancel_refund_full, MoneyFormat.vnd(quote.refundAmount))
                CancelQuoteText.NO_REFUND -> getString(R.string.dropin_cancel_no_refund_title)
                CancelQuoteText.FREE -> getString(R.string.dropin_cancel_free)
            }
            binding.cancelQuoteNote.setText(
                when (quote.reason) {
                    CancelReason.EARLY -> R.string.dropin_cancel_note_early
                    CancelReason.SESSION_CHANGED -> R.string.dropin_cancel_note_changed
                    CancelReason.LATE -> R.string.dropin_cancel_note_late
                    CancelReason.AT_VENUE -> R.string.dropin_cancel_note_free
                },
            )
        }

        val error = when (state) {
            is CancelDropInUiState.Quote -> state.error
            is CancelDropInUiState.QuoteFailed -> state.error
            else -> null
        }
        binding.cancelError.isVisible = error != null
        binding.cancelError.text = error?.let { errorText(it) }

        binding.cancelKeep.isEnabled = state !is CancelDropInUiState.Cancelling
        binding.cancelConfirm.isEnabled = !busy && state !is CancelDropInUiState.Done
        binding.cancelConfirm.text = when {
            state is CancelDropInUiState.Cancelling -> getString(R.string.dropin_cancel_cancelling)
            state is CancelDropInUiState.QuoteFailed -> getString(R.string.dropin_retry)
            state is CancelDropInUiState.Quote && state.canRetry -> getString(R.string.dropin_retry)
            else -> getString(R.string.dropin_cancel_confirm)
        }

        if (state is CancelDropInUiState.Done) {
            finish(
                message = if (state.refundAmount > 0) {
                    getString(R.string.dropin_cancel_done_refund, MoneyFormat.vnd(state.refundAmount))
                } else {
                    getString(R.string.dropin_cancel_done)
                },
                cancelled = true,
            )
        }
    }

    private fun errorText(error: Throwable): String = when (error) {
        is DropInError.Network -> getString(R.string.dropin_cancel_error_network)
        else -> DropInUiText.error(requireContext(), error)
    }

    private fun finish(message: String, cancelled: Boolean) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_MESSAGE to message, RESULT_CANCELLED to cancelled))
        dismissAllowingStateLoss()
    }

    companion object {
        const val TAG = "CancelDropInBottomSheet"
        const val RESULT_KEY = "dropin_cancel_result"
        const val RESULT_MESSAGE = "message"
        const val RESULT_CANCELLED = "cancelled"

        /** Mở sheet trong [fragmentManager] (dùng `childFragmentManager` của màn hình vé); đang mở thì bỏ qua. */
        fun show(fragmentManager: FragmentManager, sessionId: String) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            CancelDropInBottomSheet().apply {
                arguments = bundleOf(CancelDropInViewModel.ARG_SESSION_ID to sessionId)
            }.show(fragmentManager, TAG)
        }
    }
}
