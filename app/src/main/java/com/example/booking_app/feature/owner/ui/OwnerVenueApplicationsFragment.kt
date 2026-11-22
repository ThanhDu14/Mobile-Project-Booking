package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerVenueApplicationsBinding
import com.example.booking_app.feature.owner.domain.OwnerApplicationSnapshot
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VenueApplicationsState(val loading: Boolean = true, val applications: List<OwnerApplicationSnapshot> = emptyList(), val error: String? = null)

@HiltViewModel
class OwnerVenueApplicationsViewModel @Inject constructor(private val repository: OwnerOnboardingRepository) : ViewModel() {
    val state = MutableStateFlow(VenueApplicationsState())
    fun refresh() = viewModelScope.launch {
        state.value = state.value.copy(loading = true, error = null)
        repository.applications().onSuccess { state.value = VenueApplicationsState(false, it) }
            .onFailure { state.value = state.value.copy(loading = false, error = it.message) }
    }
}

@AndroidEntryPoint
class OwnerVenueApplicationsFragment : Fragment(R.layout.fragment_owner_venue_applications) {
    private val viewModel: OwnerVenueApplicationsViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentOwnerVenueApplicationsBinding.bind(view)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.retry.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.message.text = state.error ?: if (state.applications.isEmpty() && !state.loading) getString(R.string.owner_applications_empty) else ""
                    binding.applications.removeAllViews()
                    state.applications.forEach { app ->
                        binding.applications.addView(MaterialButton(requireContext()).apply {
                            val label = when (app.status.name) { "PENDING" -> R.string.owner_status_pending; "REJECTED" -> R.string.owner_status_rejected; else -> R.string.owner_status_approved }
                            text = getString(R.string.owner_application_row, app.draft.venue.name, getString(label))
                            minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                            setOnClickListener { findNavController().navigate(R.id.ownerApplicationStatusFragment, Bundle().apply { putString("applicationId", app.applicationId) }) }
                        })
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); viewModel.refresh() }
}
