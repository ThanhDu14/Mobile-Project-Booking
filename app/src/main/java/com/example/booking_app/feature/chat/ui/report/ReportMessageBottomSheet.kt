package com.example.booking_app.feature.chat.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.BottomSheetReportMessageBinding
import com.example.booking_app.feature.chat.domain.model.ReportReason
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Bottom sheet báo cáo tin nhắn vi phạm (T052, FR-014). */
@AndroidEntryPoint
class ReportMessageBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: ReportMessageViewModel by viewModels()
    private var binding: BottomSheetReportMessageBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = BottomSheetReportMessageBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.closeButton.setOnClickListener { dismiss() }
        b.reasonGroup.setOnCheckedChangeListener { _, checkedId ->
            val reason = when (checkedId) {
                R.id.reasonSpam -> ReportReason.SPAM
                R.id.reasonOffensive -> ReportReason.OFFENSIVE
                R.id.reasonScam -> ReportReason.SCAM
                R.id.reasonInappropriate -> ReportReason.INAPPROPRIATE
                R.id.reasonOther -> ReportReason.OTHER
                else -> null
            }
            if (reason != null) viewModel.onReasonSelected(reason)
        }
        b.detailInput.doAfterTextChanged { viewModel.onDetailChanged(it?.toString().orEmpty()) }
        b.submitButton.setOnClickListener { viewModel.submit() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(b, it) } }
                launch { viewModel.events.collect { handle(b, it) } }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(b: BottomSheetReportMessageBinding, state: ReportMessageState) {
        val checkedId = when (state.selectedReason) {
            ReportReason.SPAM -> R.id.reasonSpam
            ReportReason.OFFENSIVE -> R.id.reasonOffensive
            ReportReason.SCAM -> R.id.reasonScam
            ReportReason.INAPPROPRIATE -> R.id.reasonInappropriate
            ReportReason.OTHER -> R.id.reasonOther
            null -> View.NO_ID
        }
        if (checkedId != View.NO_ID && b.reasonGroup.checkedRadioButtonId != checkedId) {
            b.reasonGroup.check(checkedId)
        }
        b.reasonError.isVisible = state.reasonError
        b.detailContainer.isVisible = state.showDetailField
        if (b.detailInput.text?.toString() != state.detail) {
            b.detailInput.setText(state.detail)
            b.detailInput.setSelection(state.detail.length)
        }
        b.detailLayout.error = when (state.detailError) {
            ReportMessageDetailError.REQUIRED -> getString(R.string.chat_report_detail_required)
            ReportMessageDetailError.TOO_LONG -> getString(R.string.chat_report_detail_too_long)
            null -> null
        }
        val editable = !state.submitting
        for (i in 0 until b.reasonGroup.childCount) {
            b.reasonGroup.getChildAt(i).isEnabled = editable
        }
        b.detailInput.isEnabled = editable
        b.submitError.isVisible = state.submitError != null
        if (state.submitError != null) {
            b.submitError.text = ChatUiText.error(requireContext(), state.submitError)
        }
        b.submitButton.isEnabled = editable
        b.submitButton.setText(
            when {
                state.submitting -> R.string.chat_report_sending
                state.submitError != null -> R.string.chat_report_retry
                else -> R.string.chat_report_send
            },
        )
    }

    private fun handle(b: BottomSheetReportMessageBinding, event: ReportMessageEvent) {
        when (event) {
            ReportMessageEvent.Sent -> {
                setFragmentResult(RESULT_KEY_REPORT_SENT, bundleOf())
                dismiss()
            }
            is ReportMessageEvent.ShowError -> {
                Snackbar.make(b.root, ChatUiText.error(requireContext(), event.error), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val RESULT_KEY_REPORT_SENT = "chat_report_sent"
    }
}
