package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerVenueListBinding
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerVenueListFragment : Fragment(R.layout.fragment_owner_venue_list) {
    private var _binding: FragmentOwnerVenueListBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerVenueListViewModel by viewModels()
    private val adapter = OwnerVenueAdapter(
        onOpen = { venue -> findNavController().navigate(OwnerVenueListFragmentDirections.actionOwnerVenueListToDetail(venue.id)) },
        onHide = { viewModel.hide(it.id) },
        onToggleRecurring = { viewModel.toggleRecurring(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerVenueListBinding.bind(view)
        binding.venues.adapter = adapter
        binding.retry.setOnClickListener { viewModel.load() }
        binding.addVenue.setOnClickListener { binding.formMessage.setText(R.string.owner_venue_add_server_only) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: OwnerVenueListUiState) {
        binding.progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.formMessage.text = when (state.errorKind) {
            OwnerErrorKind.PERMISSION -> getString(R.string.owner_permission_error)
            OwnerErrorKind.NETWORK -> getString(R.string.owner_network_error)
            else -> state.error ?: state.message.orEmpty()
        }
        binding.freshness.text = if (state.isFresh) getString(R.string.owner_data_fresh) else if (state.error != null) getString(R.string.owner_data_not_fresh) else ""
        binding.empty.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        adapter.submitList(state.venues)
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
