package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminReportDetailBinding
import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ReportAction
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminReportDetailFragment : Fragment(R.layout.fragment_admin_report_detail) {

    private var _binding: FragmentAdminReportDetailBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminReportsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminReportDetailBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonDismiss.setOnClickListener {
            showDismissConfirmation()
        }

        binding.buttonTakeAction.setOnClickListener {
            val report = viewModel.detailState.value.report ?: return@setOnClickListener
            val action = getApplicableAction(report.targetType)
            if (action != null) {
                showActionConfirmation(action)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.detailState.collect(::renderDetailState)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderDetailState(state: AdminReportDetailUiState) {
        val currentBinding = _binding ?: return

        currentBinding.progressBarDetail.isVisible = state.isLoading || state.isSubmitting
        currentBinding.buttonDismiss.isEnabled = !state.isSubmitting
        currentBinding.buttonTakeAction.isEnabled = !state.isSubmitting

        state.report?.let { report ->
            populateReportData(report, state.targetSummary, currentBinding)
        }

        state.errorMessage?.let { error ->
            Snackbar.make(currentBinding.root, error, Snackbar.LENGTH_LONG).show()
            viewModel.clearMessages()
        }

        state.successMessage?.let { success ->
            Toast.makeText(requireContext(), success, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
            findNavController().navigateUp()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateReportData(
        report: AdminReport,
        targetSummary: Map<String, Any?>,
        b: FragmentAdminReportDetailBinding,
    ) {
        b.detailChipTargetType.text = report.targetType.name
        b.detailReason.text = report.reason.ifBlank { "Không có lý do" }
        b.detailDescription.text = report.detail.ifBlank { "Không có chi tiết cụ thể" }
        b.detailTargetPath.text = "Đường dẫn: ${report.targetPath}"

        if (targetSummary.isEmpty()) {
            b.detailTargetSummary.text = "Không có thông tin chi tiết hoặc đối tượng chưa tải được."
        } else {
            val summaryText = targetSummary.entries.joinToString("\n") { (k, v) -> "$k: $v" }
            b.detailTargetSummary.text = summaryText
        }

        val action = getApplicableAction(report.targetType)
        if (action != null) {
            b.buttonTakeAction.isVisible = true
            b.buttonTakeAction.text = when (action) {
                ReportAction.LOCK_VENUE -> getString(R.string.admin_report_action_lock_venue)
                ReportAction.LOCK_USER -> getString(R.string.admin_report_action_lock_user)
                ReportAction.HIDE_CONTENT -> getString(R.string.admin_report_action_hide_content)
                ReportAction.NONE -> ""
            }
        } else {
            b.buttonTakeAction.isVisible = false
        }
    }

    private fun getApplicableAction(targetType: ReportTargetType): ReportAction? {
        return when (targetType) {
            ReportTargetType.VENUE -> ReportAction.LOCK_VENUE
            ReportTargetType.USER -> ReportAction.LOCK_USER
            ReportTargetType.POST, ReportTargetType.MESSAGE, ReportTargetType.REVIEW -> ReportAction.HIDE_CONTENT
            else -> null
        }
    }

    private fun showDismissConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_report_action_dismiss)
            .setMessage(R.string.admin_dismiss_confirm_message)
            .setPositiveButton(R.string.admin_report_action_dismiss) { _, _ ->
                viewModel.submitModeration(ReportAction.NONE)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showActionConfirmation(action: ReportAction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_report_confirm_action_title)
            .setMessage(R.string.admin_report_confirm_action_message)
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                viewModel.submitModeration(action)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
