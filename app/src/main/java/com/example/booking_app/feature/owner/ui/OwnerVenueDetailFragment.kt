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
import com.example.booking_app.databinding.FragmentOwnerVenueDetailBinding
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerVenueDetailFragment : Fragment(R.layout.fragment_owner_venue_detail) {
    private var _binding: FragmentOwnerVenueDetailBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerVenueDetailViewModel by viewModels()
    private var editingCourtId: String? = null
    private val courtAdapter = OwnerCourtAdapter(
        onEdit = { court ->
            editingCourtId = court.id
            binding.courtName.setText(court.name)
            binding.courtSurface.setText(court.surfaceType)
        },
        onDeactivate = { viewModel.deactivateCourt(it.id) },
        onMove = { court, direction -> viewModel.moveCourt(court.id, direction) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerVenueDetailBinding.bind(view)
        binding.courts.adapter = courtAdapter
        binding.retry.setOnClickListener { viewModel.load() }
        binding.save.setOnClickListener { saveVenue() }
        binding.addCourt.setOnClickListener { addCourt() }
        binding.managePrices.setOnClickListener {
            findNavController().navigate(R.id.action_ownerVenueDetail_to_priceRule, Bundle().apply {
                putString("venueId", requireArguments().getString("venueId"))
            })
        }
        binding.blockSlots.setOnClickListener {
            viewModel.uiState.value.courts.firstOrNull()?.let { court ->
                findNavController().navigate(R.id.action_ownerVenueDetail_to_slotBlock, Bundle().apply {
                    putString("venueId", requireArguments().getString("venueId"))
                    putString("courtId", court.id)
                })
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    private fun saveVenue() {
        val current = viewModel.uiState.value.venue ?: return
        viewModel.save(VenueEditableFields(
            name = binding.name.text.toString(), address = binding.address.text.toString(),
            districtId = current.districtId, location = current.location,
            openTime = binding.openTime.text.toString(), closeTime = binding.closeTime.text.toString(),
            phone = binding.phone.text.toString(), amenityIds = current.amenityIds,
            photoPaths = current.photoPaths, courtCount = current.courtCount,
            acceptsRecurring = binding.recurring.isChecked, status = current.status
        ))
    }

    private fun addCourt() {
        val name = binding.courtName.text.toString()
        val surface = binding.courtSurface.text.toString().uppercase()
        val id = editingCourtId
        if (id == null) viewModel.addCourt(CourtFields(name, surface, true, viewModel.uiState.value.courts.size))
        else {
            val court = viewModel.uiState.value.courts.firstOrNull { it.id == id }
            if (court != null) viewModel.updateCourt(id, CourtFields(name, surface, court.isActive, court.sortOrder))
            editingCourtId = null
        }
    }

    private fun render(state: OwnerVenueDetailUiState) {
        binding.progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.message.text = when (state.errorKind) {
            OwnerErrorKind.PERMISSION -> getString(R.string.owner_permission_error)
            OwnerErrorKind.NETWORK -> getString(R.string.owner_network_error)
            else -> state.error ?: state.message.orEmpty()
        }
        binding.freshness.text = if (state.isFresh) getString(R.string.owner_data_fresh) else if (state.error != null) getString(R.string.owner_data_not_fresh) else ""
        state.venue?.let {
            val draft = state.draft
            if (!binding.name.hasFocus()) binding.name.setText(draft?.name ?: it.name)
            if (!binding.address.hasFocus()) binding.address.setText(draft?.address ?: it.address)
            if (!binding.openTime.hasFocus()) binding.openTime.setText(draft?.openTime ?: it.openTime)
            if (!binding.closeTime.hasFocus()) binding.closeTime.setText(draft?.closeTime ?: it.closeTime)
            if (!binding.phone.hasFocus()) binding.phone.setText(draft?.phone ?: it.phone)
            binding.recurring.isChecked = draft?.acceptsRecurring ?: it.acceptsRecurring
        }
        courtAdapter.submitList(state.courts)
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
