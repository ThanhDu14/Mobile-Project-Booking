package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.DialogAdminRejectApplicationBinding
import com.example.booking_app.databinding.FragmentAdminApplicationDetailBinding
import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import com.example.booking_app.feature.admin.domain.model.ReviewAction
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminApplicationDetailFragment : Fragment(R.layout.fragment_admin_application_detail) {

    private var _binding: FragmentAdminApplicationDetailBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminApplicationsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminApplicationDetailBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonApprove.setOnClickListener {
            showApproveConfirmation()
        }

        binding.buttonReject.setOnClickListener {
            showRejectDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.detailState.collect(::renderDetailState)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderDetailState(state: AdminDetailUiState) {
        val currentBinding = _binding ?: return

        currentBinding.progressBarDetail.isVisible = state.isLoading || state.isSubmitting
        currentBinding.buttonApprove.isEnabled = !state.isSubmitting
        currentBinding.buttonReject.isEnabled = !state.isSubmitting

        state.application?.let { app ->
            populateApplicationData(app, currentBinding)
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
    private fun populateApplicationData(app: AdminOwnerApplication, b: FragmentAdminApplicationDetailBinding) {
        b.detailVenueName.text = app.venueName.ifBlank { "Cơ sở chưa đặt tên" }
        b.detailVenueAddress.text = "Địa chỉ: ${app.venueAddress.ifBlank { "Chưa cập nhật" }}"
        b.detailCourtCount.text = "Số sân: ${app.courtCount}"
        b.detailOperatingHours.text = "Giờ hoạt động: ${app.openTime} - ${app.closeTime}"
        b.detailVenuePhone.text = "SĐT cơ sở: ${app.venuePhone.ifBlank { "Chưa cập nhật" }}"

        b.detailApplicantName.text = "Họ tên: ${app.applicantName.ifBlank { "Chưa cập nhật" }}"
        b.detailApplicantPhone.text = "SĐT: ${app.applicantPhone.ifBlank { "Chưa cập nhật" }}"

        b.containerDocuments.removeAllViews()
        if (app.documentPaths.isEmpty()) {
            val emptyText = android.widget.TextView(requireContext()).apply {
                text = "Không có tài liệu đính kèm"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
            b.containerDocuments.addView(emptyText)
        } else {
            app.documentPaths.forEachIndexed { index, path ->
                val docButton = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    val fileName = path.substringAfterLast('/')
                    text = "Giấy tờ ${index + 1}: $fileName"
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = (8 * resources.displayMetrics.density).toInt()
                    }
                    setOnClickListener {
                        openDocument(path)
                    }
                }
                b.containerDocuments.addView(docButton)
            }
        }
    }

    private fun openDocument(path: String) {
        val existingUrl = viewModel.detailState.value.documentUrls[path]
        if (existingUrl != null) {
            launchUrlIntent(existingUrl)
        } else {
            Toast.makeText(requireContext(), "Đang tạo đường dẫn bảo mật...", Toast.LENGTH_SHORT).show()
            viewModel.loadDocumentSignedUrl(path)
        }
    }

    private fun launchUrlIntent(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }.onFailure {
            Toast.makeText(requireContext(), "Không thể mở trình duyệt xem tệp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showApproveConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_approve_button)
            .setMessage("Bạn có chắc chắn muốn phê duyệt hồ sơ đăng ký chủ sân này?")
            .setPositiveButton(R.string.admin_approve_button) { _, _ ->
                viewModel.submitDecision(ReviewAction.APPROVE)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showRejectDialog() {
        val dialogBinding = DialogAdminRejectApplicationBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.inputRejectReason.setText(viewModel.detailState.value.rejectReasonInput)

        var dialog: AlertDialog? = null

        dialogBinding.inputRejectReason.doAfterTextChanged { text ->
            viewModel.updateRejectReasonInput(text?.toString().orEmpty())
        }

        dialogBinding.buttonCancelReject.setOnClickListener {
            dialog?.dismiss()
        }

        dialogBinding.buttonConfirmReject.setOnClickListener {
            val reason = dialogBinding.inputRejectReason.text?.toString().orEmpty().trim()
            if (reason.isEmpty()) {
                dialogBinding.layoutRejectReason.error = "Lý do từ chối không được để trống"
                return@setOnClickListener
            }
            dialogBinding.layoutRejectReason.error = null
            viewModel.updateRejectReasonInput(reason)
            dialog?.dismiss()
            viewModel.submitDecision(ReviewAction.REJECT)
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
