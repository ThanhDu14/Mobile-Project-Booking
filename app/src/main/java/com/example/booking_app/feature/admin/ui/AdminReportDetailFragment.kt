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
import com.example.booking_app.feature.admin.domain.model.ReportStatus
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AdminReportDetailFragment : Fragment(R.layout.fragment_admin_report_detail) {

    private var _binding: FragmentAdminReportDetailBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminReportsViewModel by activityViewModels()
    private val dateFormat = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminReportDetailBinding.bind(view)
        viewModel.detailState.value.report?.id?.let(viewModel::loadReportDetail)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.radioGroupConclusion.setOnCheckedChangeListener { _, checkedId ->
            val report = viewModel.detailState.value.report ?: return@setOnCheckedChangeListener
            updateSubmitButtonText(checkedId, report.targetType)
        }

        binding.buttonSubmitConclusion.setOnClickListener {
            val report = viewModel.detailState.value.report ?: return@setOnClickListener
            val isDismiss = binding.radioActionDismiss.isChecked
            if (isDismiss) {
                showDismissConfirmation()
            } else {
                val action = getApplicableAction(report.targetType) ?: ReportAction.HIDE_CONTENT
                showActionConfirmation(action)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.detailState.collect(::renderDetailState)
            }
        }
    }

    private fun updateSubmitButtonText(checkedRadioId: Int, targetType: ReportTargetType) {
        val b = _binding ?: return
        if (checkedRadioId == R.id.radioActionDismiss) {
            b.buttonSubmitConclusion.text = getString(R.string.admin_report_submit_dismiss_and_close)
        } else {
            b.buttonSubmitConclusion.text = when (targetType) {
                ReportTargetType.VENUE, ReportTargetType.USER -> getString(R.string.admin_report_submit_lock_and_close)
                else -> getString(R.string.admin_report_submit_hide_and_close)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderDetailState(state: AdminReportDetailUiState) {
        val currentBinding = _binding ?: return

        currentBinding.progressReportSubmitting.isVisible = state.isLoading || state.isSubmitting
        currentBinding.buttonSubmitConclusion.isEnabled = !state.isSubmitting

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
        val (typeName, _) = when (report.targetType) {
            ReportTargetType.POST -> "Bài đăng" to R.drawable.ic_nav_admin_applications
            ReportTargetType.VENUE -> "Cơ sở" to R.drawable.ic_nav_admin_home
            ReportTargetType.USER -> "Người dùng" to R.drawable.ic_nav_admin_users
            ReportTargetType.REVIEW -> "Đánh giá" to R.drawable.ic_nav_admin_reports
            ReportTargetType.MESSAGE -> "Tin nhắn" to R.drawable.ic_admin_broadcast_megaphone
            else -> report.targetType.name to R.drawable.ic_nav_admin_reports
        }

        b.chipReportTargetType.text = typeName
        b.chipReportStatus.text = if (report.status == ReportStatus.OPEN) getString(R.string.admin_report_tab_open) else getString(R.string.admin_report_tab_closed)

        b.textReportReason.text = report.reason.ifBlank { "Chưa có lý do" }
        b.textReportQuote.text = "“${report.detail.ifBlank { "Chưa có nội dung báo cáo" }}”"

        val dateStr = if (report.createdAt > 0L) dateFormat.format(Date(report.createdAt)) else "Chưa có thời điểm"
        b.textReporterInfo.text = getString(R.string.admin_report_reporter_format, report.reporterId, dateStr)

        // Target Section
        val authorName = targetSummary["authorName"]?.toString() ?: targetSummary["displayName"]?.toString() ?: targetSummary["name"]?.toString() ?: "Chưa có tên"
        b.textTargetAuthorName.text = authorName
        b.textTargetAvatar.text = authorName.take(2).uppercase()
        b.textTargetContext.text = targetSummary["context"]?.toString() ?: targetSummary["email"]?.toString() ?: report.targetPath
        b.textTargetBody.text = targetSummary["content"]?.toString()
            ?: targetSummary["body"]?.toString() ?: targetSummary["address"]?.toString() ?: targetSummary["phone"]?.toString() ?: "Không có nội dung hoặc không đủ quyền đọc"

        // Conclusion setup based on target
        when (report.targetType) {
            ReportTargetType.VENUE -> {
                b.textActionTargetTitle.text = getString(R.string.admin_report_action_lock_venue)
                b.textActionTargetDesc.text = getString(R.string.admin_report_action_lock_venue_desc)
            }
            ReportTargetType.USER -> {
                b.textActionTargetTitle.text = getString(R.string.admin_report_action_lock_user)
                b.textActionTargetDesc.text = getString(R.string.admin_report_action_lock_user_desc)
            }
            else -> {
                b.textActionTargetTitle.text = getString(R.string.admin_report_action_hide_post)
                b.textActionTargetDesc.text = getString(R.string.admin_report_action_hide_post_desc)
            }
        }

        updateSubmitButtonText(b.radioGroupConclusion.checkedRadioButtonId, report.targetType)
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
            .setMessage(R.string.admin_report_single_close_notice)
            .setPositiveButton(R.string.admin_report_submit_dismiss_and_close) { _, _ ->
                viewModel.submitModeration(ReportAction.NONE)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showActionConfirmation(action: ReportAction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_report_confirm_action_title)
            .setMessage(R.string.admin_report_confirm_action_message)
            .setPositiveButton(R.string.admin_report_submit_hide_and_close) { _, _ ->
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
