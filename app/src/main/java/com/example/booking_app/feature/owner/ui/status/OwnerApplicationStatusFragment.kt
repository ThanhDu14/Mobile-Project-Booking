package com.example.booking_app.feature.owner.ui.status

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerApplicationStatusBinding
import com.example.booking_app.feature.owner.data.OwnerDocumentDownloadApi
import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusFormatter
import com.example.booking_app.feature.owner.domain.OwnerStatus
import com.example.booking_app.feature.owner.ui.form.OwnerApplicationFormViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Displays authoritative status states; it does not infer access from cached applications. */
@AndroidEntryPoint
class OwnerApplicationStatusFragment : Fragment(R.layout.fragment_owner_application_status) {
    private val viewModel: OwnerApplicationStatusViewModel by viewModels()
    private val formViewModel: OwnerApplicationFormViewModel by activityViewModels()
    @Inject lateinit var documentDownloadApi: OwnerDocumentDownloadApi
    private var _binding: FragmentOwnerApplicationStatusBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerApplicationStatusBinding.bind(view)
        binding.retryButton.setOnClickListener { viewModel.beginRefresh() }
        render(viewModel.uiState.value)
        viewModel.uiState.collectWhileStarted(viewLifecycleOwner) { render(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.beginRefresh()
    }

    private fun render(state: OwnerApplicationStatusUiState) {
        binding.loadingView.visibility = if (state is OwnerApplicationStatusUiState.Loading) View.VISIBLE else View.GONE
        binding.contentGroup.visibility = if (state is OwnerApplicationStatusUiState.Content) View.VISIBLE else View.GONE
        binding.emptyView.visibility = if (state is OwnerApplicationStatusUiState.Empty) View.VISIBLE else View.GONE
        binding.errorGroup.visibility = if (state is OwnerApplicationStatusUiState.Error) View.VISIBLE else View.GONE
        if (state is OwnerApplicationStatusUiState.Content) {
            binding.statusTitle.setText(statusLabel(state.value.ownerStatus))
            binding.statusDetails.text = state.value.application?.let { application ->
                val submittedAt = OwnerApplicationStatusFormatter.submittedAt(application.submittedAt)
                    ?: getString(R.string.owner_status_time_unavailable)
                if (application.status == OwnerApplicationReviewStatus.REJECTED) {
                    getString(
                        R.string.owner_status_rejection_details,
                        submittedAt,
                        application.rejectReason ?: getString(R.string.owner_status_reason_unavailable),
                    )
                } else {
                    getString(R.string.owner_status_submitted_at, submittedAt)
                }
            } ?: getString(R.string.owner_status_no_application)
            binding.applicationActionButton.visibility = View.GONE
            binding.applicationActionButton.setOnClickListener(null)
            val documents = state.value.application?.documentPaths.orEmpty()
            binding.documentsButton.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
            binding.documentsButton.setOnClickListener { showDocumentPicker(state.value.application?.id, documents) }
            when (state.value.ownerStatus) {
                OwnerStatus.NONE -> {
                    binding.applicationActionButton.visibility = View.VISIBLE
                    binding.applicationActionButton.setText(R.string.owner_status_start_application)
                    binding.applicationActionButton.setOnClickListener {
                        formViewModel.beginNewApplication()
                        findNavController().navigate(
                            R.id.action_ownerApplicationStatusFragment_to_ownerApplicationFormFragment,
                        )
                    }
                }
                OwnerStatus.REJECTED -> state.value.application?.rejectedDraft?.let { draft ->
                    val application = requireNotNull(state.value.application)
                    binding.applicationActionButton.visibility = View.VISIBLE
                    binding.applicationActionButton.setText(R.string.owner_status_edit_rejected_application)
                    binding.applicationActionButton.setOnClickListener {
                        formViewModel.beginRejectedEdit(
                            applicationId = application.id,
                            rejectReason = application.rejectReason,
                            draft = draft,
                        )
                        findNavController().navigate(
                            R.id.action_ownerApplicationStatusFragment_to_ownerApplicationFormFragment,
                        )
                    }
                }
                else -> Unit
            }
        }
        if (state is OwnerApplicationStatusUiState.Error) binding.errorMessage.text = state.message
    }

    private fun showDocumentPicker(applicationId: String?, paths: List<String>) {
        if (applicationId.isNullOrBlank() || paths.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.owner_status_documents_title)
            .setItems(paths.map { it.substringAfterLast('/') }.toTypedArray()) { _, index ->
                paths.getOrNull(index)?.let { openDocument(applicationId, it) }
            }
            .setNegativeButton(R.string.owner_nav_back, null)
            .show()
    }

    private fun openDocument(applicationId: String, path: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Request a new five-minute URL for every tap; signed URLs are never persisted.
                val url = documentDownloadApi.requestFreshUrl(applicationId, path)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_binding != null) {
                    Snackbar.make(binding.root, R.string.owner_status_document_error, Snackbar.LENGTH_LONG)
                        .setAction(R.string.owner_status_retry) { openDocument(applicationId, path) }
                        .show()
                }
            }
        }
    }

    private fun statusLabel(status: OwnerStatus): Int = when (status) {
        OwnerStatus.NONE -> R.string.owner_status_none
        OwnerStatus.PENDING -> R.string.owner_status_pending
        OwnerStatus.APPROVED -> R.string.owner_status_approved
        OwnerStatus.REJECTED -> R.string.owner_status_rejected
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileStarted(
    owner: androidx.lifecycle.LifecycleOwner,
    render: suspend (T) -> Unit,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { render(it) }
        }
    }
}
