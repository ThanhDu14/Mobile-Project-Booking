package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AdminApplicationDetailFragment : Fragment(R.layout.fragment_admin_application_detail) {

    private var _binding: FragmentAdminApplicationDetailBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminApplicationsViewModel by activityViewModels()
    private val dateFormat = SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault())

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
            showRejectBottomSheet()
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
        b.textRegisteredVenueName.text = app.venueName.ifBlank { "Chưa đặt tên" }
        b.detailVenueAddress.text = app.venueAddress.ifBlank { "Chưa cập nhật" }
        b.detailVenueDistrict.text = app.districtId.ifBlank { "Quận 10" }
        b.detailCourtCount?.text = "${app.courtCount}"
        b.detailOperatingHours.text = "${app.openTime} - ${app.closeTime}"
        b.detailVenuePhone.text = app.venuePhone.ifBlank { "Chưa cập nhật" }
        b.detailCoordinates?.text = "Đang cập nhật"

        b.detailApplicantName.text = app.applicantName.ifBlank { "Chưa cập nhật" }
        b.detailApplicantPhone.text = app.applicantPhone.ifBlank { "Chưa cập nhật" }
        b.detailApplicantEmail.text = app.applicantPhone.ifBlank { "Chưa cập nhật" }

        val dateStr = if (app.submittedAt > 0) dateFormat.format(Date(app.submittedAt)) else "Mới nộp"
        val waitHours = if (app.submittedAt > 0) {
            val diffMs = System.currentTimeMillis() - app.submittedAt
            (diffMs / (1000 * 60 * 60)).coerceAtLeast(0)
        } else 0
        b.textDetailWaitTime.text = getString(R.string.admin_submitted_wait_time, dateStr, "${waitHours} giờ")

        when (app.status) {
            "APPROVED" -> {
                b.chipStatusDetail.setBackgroundResource(R.drawable.bg_admin_chip_approved)
                b.chipStatusDetail.setTextColor(Color.parseColor("#065F46"))
                b.chipStatusDetail.text = getString(R.string.admin_status_approved)
            }
            "REJECTED" -> {
                b.chipStatusDetail.setBackgroundResource(R.drawable.bg_admin_chip_rejected)
                b.chipStatusDetail.setTextColor(Color.parseColor("#991B1B"))
                b.chipStatusDetail.text = getString(R.string.admin_status_rejected)
            }
            else -> {
                b.chipStatusDetail.setBackgroundResource(R.drawable.bg_admin_chip_pending)
                b.chipStatusDetail.setTextColor(Color.parseColor("#92400E"))
                b.chipStatusDetail.text = getString(R.string.admin_status_pending)
            }
        }

        b.chipResubmittedDetail.isVisible = !app.rejectReason.isNullOrBlank()
        b.cardPreviousReview.isVisible = !app.rejectReason.isNullOrBlank()
        if (b.cardPreviousReview.isVisible) {
            b.textPreviousReview.text = getString(R.string.admin_previous_review_rejected, "03/10: ${app.rejectReason}")
        }

        b.textLinkMap?.setOnClickListener {
            val geoUri = Uri.parse("geo:10.7731,106.6672?q=10.7731,106.6672(${Uri.encode(app.venueName)})")
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
        }

        b.cardDocBusinessLicense.setOnClickListener {
            openDocument(app.documentPaths.firstOrNull())
        }

        b.cardDocSignage.setOnClickListener {
            openDocument(app.photoPaths.firstOrNull() ?: app.documentPaths.getOrNull(1))
        }

        b.containerDocuments.removeAllViews()
        if (app.documentPaths.size > 2) {
            app.documentPaths.drop(2).forEachIndexed { index, path ->
                val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "${getString(R.string.admin_view_document)} #${index + 3}"
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    setOnClickListener { openDocument(path) }
                }
                b.containerDocuments.addView(btn)
            }
        }
    }

    private fun openDocument(path: String?) {
        if (path.isNullOrBlank()) {
            Snackbar.make(binding.root, "Chưa có liên kết tài liệu", Snackbar.LENGTH_SHORT).show()
            return
        }
        val url = viewModel.detailState.value.documentUrls[path] ?: path
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }.onFailure {
            Snackbar.make(binding.root, "Không thể mở tài liệu: $url", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showApproveConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_approve_button)
            .setMessage(R.string.admin_approval_notice)
            .setPositiveButton(R.string.admin_approve_button) { _, _ ->
                viewModel.submitDecision(ReviewAction.APPROVE)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showRejectBottomSheet() {
        val currentApp = viewModel.detailState.value.application ?: return
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAdminRejectApplicationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.textDialogMessage.text = getString(
            R.string.admin_reject_dialog_description,
            currentApp.venueName.ifBlank { "Cơ sở" }
        )

        dialogBinding.chipBlurryDoc.setOnClickListener {
            val current = dialogBinding.inputRejectReason.text?.toString().orEmpty()
            dialogBinding.inputRejectReason.setText(
                if (current.isBlank()) "Ảnh giấy phép kinh doanh vẫn bị lóa ở góc phải, không đọc được ngày cấp."
                else "$current. Ảnh giấy tờ bị mờ."
            )
        }

        dialogBinding.chipMissingDoc.setOnClickListener {
            val current = dialogBinding.inputRejectReason.text?.toString().orEmpty()
            dialogBinding.inputRejectReason.setText(
                if (current.isBlank()) "Hồ sơ còn thiếu ảnh chụp biển hiệu cơ sở."
                else "$current. Thiếu giấy tờ bổ sung."
            )
        }

        dialogBinding.chipAddressMismatch.setOnClickListener {
            val current = dialogBinding.inputRejectReason.text?.toString().orEmpty()
            dialogBinding.inputRejectReason.setText(
                if (current.isBlank()) "Địa chỉ khai báo không khớp với địa chỉ ghi trên giấy phép kinh doanh."
                else "$current. Địa chỉ không khớp."
            )
        }

        dialogBinding.buttonCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonConfirmReject.setOnClickListener {
            val reason = dialogBinding.inputRejectReason.text?.toString()?.trim().orEmpty()
            if (reason.isBlank()) {
                dialogBinding.layoutRejectReason.error = getString(R.string.admin_reject_reason_required_note)
            } else {
                dialogBinding.layoutRejectReason.error = null
                dialog.dismiss()
                viewModel.updateRejectReasonInput(reason)
                viewModel.submitDecision(ReviewAction.REJECT)
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}