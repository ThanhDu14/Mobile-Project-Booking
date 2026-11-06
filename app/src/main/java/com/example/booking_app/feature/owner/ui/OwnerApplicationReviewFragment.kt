package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerApplicationReviewBinding
import com.example.booking_app.feature.account.domain.model.District
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerApplicationReviewFragment : Fragment(R.layout.fragment_owner_application_review) {
    private var _binding: FragmentOwnerApplicationReviewBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerApplicationViewModel by activityViewModels()
    private var navigating = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerApplicationReviewBinding.bind(view)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        val edit = View.OnClickListener { findNavController().navigate(R.id.action_ownerApplicationReview_to_form) }
        binding.editVenue.setOnClickListener(edit)
        binding.editRepresentative.setOnClickListener(edit)
        binding.editFiles.setOnClickListener(edit)
        binding.submitButton.setOnClickListener { viewModel.submit() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (_binding == null) return@collect
                    val draft = state.draft
                    val district = state.districts.firstOrNull { it.id == draft.venue.districtId }?.name ?: draft.venue.districtId
                    val location = draft.venue.location?.let { "${it.latitude}, ${it.longitude}" }.orEmpty()
                    binding.venueSummary.text = listOf(
                        "Tên\t${draft.venue.name}",
                        "Địa chỉ\t${draft.venue.address}",
                        "Quận/huyện\t$district",
                        "SĐT cơ sở\t${draft.venue.phone}",
                        "Vị trí\t$location",
                        "Số sân\t${draft.venue.courtCount ?: ""}",
                        "Giờ mở cửa\t${draft.venue.openTime} – ${draft.venue.closeTime}",
                    ).joinToString("\n\n")
                    binding.representativeSummary.text = "Họ tên\t${draft.representativeName}\n\nĐiện thoại\t${draft.representativePhone}"
                    val photoText = if (draft.venue.photoPaths.isEmpty()) "Chưa có ảnh" else draft.venue.photoPaths.joinToString("\n") { "▧  ${it.substringAfterLast('/')}" }
                    val docText = if (draft.documentPaths.isEmpty()) "Chưa có giấy tờ" else draft.documentPaths.joinToString("\n") { "▣  ${it.substringAfterLast('/')}" }
                    binding.filesSummary.text = "$photoText\n\n$docText"
                    binding.reviewProgress.isVisible = state.busy
                    binding.submitButton.isEnabled = !state.busy && !state.loading
                    binding.reviewMessage.isVisible = state.message != null || state.errors.isNotEmpty()
                    binding.reviewMessage.text = state.message ?: state.errors.firstOrNull()?.message.orEmpty()
                    if (!navigating && !state.busy && state.status?.ownerStatus == com.example.booking_app.feature.owner.domain.OwnerApplicationState.PENDING) {
                        navigating = true
                        findNavController().navigate(R.id.action_ownerApplicationReview_to_status)
                    }
                }
            }
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
