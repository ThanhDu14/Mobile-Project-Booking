package com.example.booking_app.feature.owner.ui.review

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerApplicationReviewBinding
import com.example.booking_app.feature.owner.ui.form.OwnerApplicationFormViewModel

/** Shows the retained applicant-owned draft. It never displays raw object paths. */
class OwnerApplicationReviewFragment : Fragment(R.layout.fragment_owner_application_review) {
    private val viewModel: OwnerApplicationFormViewModel by activityViewModels()
    private var _binding: FragmentOwnerApplicationReviewBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerApplicationReviewBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.editButton.setOnClickListener { findNavController().navigateUp() }
        val draft = viewModel.uiState.value.draft
        if (draft == null) {
            findNavController().navigateUp()
            return
        }
        val venue = draft.venue
        val location = getString(
            R.string.owner_review_location_summary,
            venue.latitude?.toString().orEmpty(),
            venue.longitude?.toString().orEmpty(),
        )
        val courtCount = venue.courtCount?.let { getString(R.string.owner_review_courts_summary, it) }.orEmpty()
        binding.venueSummary.text = getString(
            R.string.owner_review_venue_summary,
            venue.name,
            venue.address,
            venue.districtId,
            venue.phone,
            location,
            courtCount,
            venue.openTime,
            venue.closeTime,
        )
        binding.representativeSummary.text = getString(
            R.string.owner_review_representative_summary,
            draft.representativeName,
            draft.representativePhone,
        )
        binding.venuePhotosSummary.text = attachmentSummary(draft.venue.photoPaths)
        binding.documentsSummary.text = attachmentSummary(draft.documentPaths)
    }

    private fun attachmentSummary(paths: List<String>): String = paths
        .map(String::trim)
        .filter(String::isNotEmpty)
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(separator = "\n") { path ->
            getString(
                R.string.owner_review_attachment_line,
                path.substringAfterLast('/'),
                getString(R.string.owner_review_file_path_pending_upload),
            )
        }
        ?: getString(R.string.owner_review_empty_attachments)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
