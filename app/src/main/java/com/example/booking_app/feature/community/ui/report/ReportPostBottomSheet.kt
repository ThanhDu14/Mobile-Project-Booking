package com.example.booking_app.feature.community.ui.report

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
import com.example.booking_app.databinding.BottomSheetReportPostBinding
import com.example.booking_app.feature.community.domain.model.ReportReason
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** T068: bottom sheet báo cáo bài đăng (wireframe 09_community-chat_buoc3_bao-cao-bai-dang.png). */
@AndroidEntryPoint
class ReportPostBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: ReportPostViewModel by viewModels()
    private var binding: BottomSheetReportPostBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = BottomSheetReportPostBinding.inflate(inflater, container, false)
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

    private fun render(binding: BottomSheetReportPostBinding, state: ReportPostState) {
        val checkedId = when (state.selectedReason) {
            ReportReason.SPAM -> R.id.reasonSpam
            ReportReason.OFFENSIVE -> R.id.reasonOffensive
            ReportReason.SCAM -> R.id.reasonScam
            ReportReason.INAPPROPRIATE -> R.id.reasonInappropriate
            ReportReason.OTHER -> R.id.reasonOther
            null -> View.NO_ID
        }
        if (checkedId != View.NO_ID && binding.reasonGroup.checkedRadioButtonId != checkedId) {
            binding.reasonGroup.check(checkedId)
        }
        binding.reasonError.isVisible = state.reasonError
        binding.detailContainer.isVisible = state.showDetailField
        if (binding.detailInput.text?.toString() != state.detail) {
            binding.detailInput.setText(state.detail)
        }
        binding.detailLayout.error = when (state.detailError) {
            ReportDetailError.REQUIRED -> getString(R.string.community_report_detail_required)
            ReportDetailError.TOO_LONG -> getString(R.string.community_report_detail_too_long)
            null -> null
        }
        val editable = !state.submitting
        for (i in 0 until binding.reasonGroup.childCount) {
            binding.reasonGroup.getChildAt(i).isEnabled = editable
        }
        binding.detailInput.isEnabled = editable
        binding.submitButton.isEnabled = editable
        binding.submitButton.setText(
            if (state.submitting) R.string.community_report_sending else R.string.community_action_send_report,
        )
    }

    private fun handle(binding: BottomSheetReportPostBinding, event: ReportPostEvent) {
        when (event) {
            ReportPostEvent.Sent -> {
                setFragmentResult(RESULT_KEY_REPORT_SENT, bundleOf())
                dismiss()
            }
            is ReportPostEvent.ShowError -> {
                Snackbar.make(binding.root, CommunityUiText.error(requireContext(), event.error), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val RESULT_KEY_REPORT_SENT = "community_report_sent"
    }
}
