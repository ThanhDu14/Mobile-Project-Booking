package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerApplicationStatusBinding
import com.example.booking_app.feature.owner.domain.OwnerApplicationState
import androidx.core.content.ContextCompat
import coil3.load
import coil3.request.crossfade
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class OwnerApplicationStatusFragment : Fragment(R.layout.fragment_owner_application_status) {
    private var _binding: FragmentOwnerApplicationStatusBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerApplicationViewModel by activityViewModels()
    private var state: OwnerApplicationState? = null
    private var role: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerApplicationStatusBinding.bind(view)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.statusAction.setOnClickListener { continueForCurrentState() }
        binding.statusMessage.setOnClickListener { viewModel.refreshStatus() }
        viewModel.refreshStatus()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(ui: OwnerApplicationUiState) {
        if (_binding == null) return
        binding.statusProgress.isVisible = ui.loading
        val status = ui.status?.ownerStatus
        state = status
        role = ui.status?.role
        if (ui.message != null && ui.status == null) {
            binding.statusMessage.text = "${ui.message}  ·  ${getString(R.string.owner_refresh)}"
            binding.statusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.sn_danger))
            binding.statusAction.setText(R.string.owner_refresh)
            binding.statusAction.setOnClickListener { viewModel.refreshStatus() }
            binding.submittedCard.isVisible = false
            return
        }
        binding.statusMessage.text = ui.message.orEmpty()
        binding.statusAction.setOnClickListener { continueForCurrentState() }
        val app = ui.status?.application
        binding.submittedCard.isVisible = status in setOf(OwnerApplicationState.PENDING, OwnerApplicationState.REJECTED, OwnerApplicationState.APPROVED) && app != null
        renderDocuments(app?.applicationId, app?.draft?.documentPaths.orEmpty(), ui.statusFresh)
        when (status) {
            OwnerApplicationState.PENDING -> {
                applyBannerColors(R.color.sn_lamp_container, R.color.sn_on_lamp_container, R.drawable.ic_hourglass_24)
                binding.statusTitle.setText(R.string.owner_status_pending)
                binding.statusSubtitle.text = app?.submittedAt?.let { getString(R.string.owner_submitted_at, formatDate(it), getString(R.string.owner_status_pending_message)) } ?: getString(R.string.owner_status_pending_message)
                binding.statusAction.setText(R.string.owner_continue_player)
                binding.rejectReason.isVisible = false
                binding.timeline.isVisible = true
                bindSubmittedInfo(app?.draft)
                binding.timelineSubmitted.text = app?.submittedAt?.let { formatTimelineDate(it) } ?: ""
                binding.timelineReview.text = getString(R.string.owner_timeline_review_hint)
                binding.timelineResult.text = getString(R.string.owner_timeline_result)
            }
            OwnerApplicationState.REJECTED -> {
                applyBannerColors(R.color.sn_danger, R.color.sn_on_danger, R.drawable.ic_error_24)
                binding.statusTitle.setText(R.string.owner_status_rejected)
                binding.statusSubtitle.setText(R.string.owner_status_rejected_message)
                binding.statusAction.setText(R.string.owner_resubmit)
                binding.rejectReason.isVisible = app?.rejectReason != null
                binding.rejectReason.text = app?.rejectReason
                binding.timeline.isVisible = false
                bindSubmittedInfo(app?.draft)
            }
            OwnerApplicationState.APPROVED -> {
                applyBannerColors(R.color.sn_court_container, R.color.sn_on_court_container, R.drawable.ic_check_20)
                binding.statusTitle.setText(R.string.owner_status_approved)
                binding.statusSubtitle.setText(R.string.owner_status_approved_message)
                binding.statusAction.setText(R.string.owner_continue_management)
                binding.rejectReason.isVisible = false
                binding.timeline.isVisible = false
                bindSubmittedInfo(app?.draft)
            }
            OwnerApplicationState.NONE, null -> {
                applyBannerColors(R.color.sn_surface_sunken, R.color.sn_ink, R.drawable.ic_info_24)
                binding.statusTitle.setText(R.string.owner_status_none)
                binding.statusSubtitle.text = ""
                binding.statusAction.setText(R.string.owner_apply_new)
                binding.submittedCard.isVisible = false
                binding.timeline.isVisible = false
            }
        }
        if (!ui.statusFresh) {
            binding.statusAction.setText(R.string.owner_refresh)
            binding.statusAction.setOnClickListener { viewModel.refreshStatus() }
            if (ui.message != null) binding.statusMessage.text = getString(R.string.owner_status_stale, ui.message)
        }
    }

    private fun renderDocuments(applicationId: String?, paths: List<String>, statusFresh: Boolean) {
        binding.statusDocuments.removeAllViews()
        binding.statusDocuments.isVisible = applicationId != null && paths.isNotEmpty()
        paths.forEach { path ->
            binding.statusDocuments.addView(MaterialButton(requireContext(), null, android.R.attr.borderlessButtonStyle).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.sn_court))
                text = getString(R.string.owner_view_document, path.substringAfterLast('/'))
                contentDescription = getString(R.string.owner_view_document_named, path.substringAfterLast('/'))
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_description_24)
                iconTint = ContextCompat.getColorStateList(requireContext(), R.color.sn_court)
                isEnabled = statusFresh
                setOnClickListener { openDocument(applicationId.orEmpty(), path) }
            })
        }
    }

    private fun openDocument(applicationId: String, path: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.statusProgress.isVisible = true
            viewModel.signedDocumentUrl(applicationId, path).onSuccess { signedUrl ->
                if (_binding == null) return@onSuccess
                binding.statusProgress.isVisible = false
                val image = ImageView(requireContext()).apply {
                    contentDescription = getString(R.string.owner_document_preview)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.owner_document_preview_height),
                    )
                    load(signedUrl) { crossfade(true) }
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(path.substringAfterLast('/'))
                    .setView(image)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.owner_download_document) { _, _ -> downloadDocument(applicationId, path) }
                    .show()
            }.onFailure {
                if (_binding == null) return@onFailure
                binding.statusProgress.isVisible = false
                Snackbar.make(binding.root, R.string.owner_document_load_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadDocument(applicationId: String, path: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.statusProgress.isVisible = true
            val fileName = path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val bytes = viewModel.downloadDocument(applicationId, path).getOrNull()
            if (_binding == null) return@launch
            if (bytes == null) {
                binding.statusProgress.isVisible = false
                Snackbar.make(binding.root, R.string.owner_document_load_error, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val saved = runCatching { saveDocumentToDownloads(fileName, mimeType, bytes) }
            if (_binding == null) return@launch
            binding.statusProgress.isVisible = false
            if (saved.isSuccess) {
                Snackbar.make(binding.root, R.string.owner_download_started, Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(binding.root, R.string.owner_document_load_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun saveDocumentToDownloads(fileName: String, mimeType: String, bytes: ByteArray) {
        val resolver = requireContext().applicationContext.contentResolver
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            try {
                requireNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    private fun applyBannerColors(background: Int, foreground: Int, iconRes: Int) {
        binding.statusBanner.setCardBackgroundColor(ContextCompat.getColor(requireContext(), background))
        val color = ContextCompat.getColor(requireContext(), foreground)
        binding.statusTitle.setTextColor(color)
        binding.statusSubtitle.setTextColor(color)
        binding.statusIcon.setImageResource(iconRes)
        binding.statusIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
        val iconBgColor = when (background) {
            R.color.sn_lamp_container -> ContextCompat.getColor(requireContext(), R.color.sn_lamp)
            R.color.sn_court_container -> ContextCompat.getColor(requireContext(), R.color.sn_court)
            else -> ContextCompat.getColor(requireContext(), background)
        }
        binding.statusIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(iconBgColor)
    }

    private fun bindSubmittedInfo(draft: com.example.booking_app.feature.owner.domain.OwnerApplicationDraft?) {
        if (draft == null) return
        binding.submittedVenueName.text = draft.venue.name
        val district = viewModel.uiState.value.districts.firstOrNull { it.id == draft.venue.districtId }?.name ?: draft.venue.districtId
        binding.submittedVenueAddress.text = if (district.isNotBlank() && !draft.venue.address.contains(district)) {
            "${draft.venue.address}, $district"
        } else {
            draft.venue.address
        }
        binding.submittedCourtCount.text = draft.venue.courtCount?.toString() ?: ""
        val open = formatTime(draft.venue.openTime)
        val close = formatTime(draft.venue.closeTime)
        binding.submittedOpeningHours.text = "$open – $close"
        binding.submittedDocCount.text = "${draft.documentPaths.size} tệp"
        binding.statusSummary.text = summary(draft)
    }

    private fun formatTime(time: String): String {
        val trimmed = time.trim()
        return if (trimmed.length == 4 && !trimmed.contains(":")) {
            "${trimmed.substring(0, 2)}:${trimmed.substring(2)}"
        } else {
            trimmed
        }
    }

    private fun formatTimelineDate(value: String): String = runCatching {
        DateTimeFormatter.ofPattern("dd/MM · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    }.getOrDefault(value)

    private fun continueForCurrentState() {
        if (_binding == null) return
        if (!viewModel.uiState.value.statusFresh) {
            viewModel.refreshStatus()
            return
        }
        if ((state == OwnerApplicationState.APPROVED && role != "OWNER") ||
            (state != null && state != OwnerApplicationState.APPROVED && role != "PLAYER")
        ) {
            viewModel.refreshStatus()
            return
        }
        when (state) {
            OwnerApplicationState.NONE, null -> findNavController().navigate(R.id.action_ownerApplicationStatus_to_form, Bundle().apply { putString("mode", "new") })
            OwnerApplicationState.REJECTED -> findNavController().navigate(R.id.action_ownerApplicationStatus_to_form, Bundle().apply { putString("mode", "resubmit") })
            OwnerApplicationState.PENDING -> {
                if (!findNavController().popBackStack(R.id.accountFragment, false)) findNavController().navigateUp()
            }
            OwnerApplicationState.APPROVED -> viewLifecycleOwner.lifecycleScope.launch {
                runCatching { FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await() }
                findNavController().navigate("smashnow://owner/venues".toUri())
            }
        }
    }

    private fun summary(draft: com.example.booking_app.feature.owner.domain.OwnerApplicationDraft): String = listOf(
        "Cơ sở\t${draft.venue.name}",
        "Địa chỉ\t${draft.venue.address}",
        "Số sân\t${draft.venue.courtCount ?: ""}",
        "Giờ mở cửa\t${draft.venue.openTime} – ${draft.venue.closeTime}",
        "Giấy tờ\t${draft.documentPaths.size} tệp",
    ).joinToString("\n\n")

    private fun formatDate(value: String): String = runCatching {
        DateTimeFormatter.ofPattern("HH:mm · dd/MM").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    }.getOrDefault(value)

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
