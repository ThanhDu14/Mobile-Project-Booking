package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.DialogAdminUnlockVenueBinding
import com.example.booking_app.databinding.FragmentAdminVenueManagementBinding
import com.example.booking_app.feature.admin.domain.model.AdminVenueItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminVenueManagementFragment : Fragment() {

    private var _binding: FragmentAdminVenueManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminUserVenueViewModel by viewModels()
    private var currentVenue: AdminVenueItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminVenueManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonSearchVenue.setOnClickListener {
            performSearch()
        }

        binding.inputVenueSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.buttonLockVenue.setOnClickListener {
            val venue = currentVenue ?: return@setOnClickListener
            confirmStatusChange(venue, "LOCKED", getString(R.string.admin_venue_confirm_lock))
        }

        binding.buttonUnlockActive.setOnClickListener {
            val venue = currentVenue ?: return@setOnClickListener
            showUnlockBottomSheet(venue)
        }

        binding.buttonUnlockHidden.setOnClickListener {
            val venue = currentVenue ?: return@setOnClickListener
            showUnlockBottomSheet(venue)
        }

        observeViewModel()
    }

    private fun performSearch() {
        val query = binding.inputVenueSearch.text?.toString().orEmpty()
        viewModel.searchVenue(query)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.venueLookupState.collect { state ->
                        renderLookupState(state)
                    }
                }
                launch {
                    viewModel.actionState.collect { state ->
                        renderActionState(state)
                    }
                }
            }
        }
    }

    private fun renderLookupState(state: VenueLookupUiState) {
        when (state) {
            VenueLookupUiState.Idle -> {
                binding.progressVenueLoading.isVisible = false
                binding.cardVenueResult.isVisible = false
                binding.textVenueNotFound.isVisible = false
                binding.textVenueError.isVisible = false
            }
            VenueLookupUiState.Loading -> {
                binding.progressVenueLoading.isVisible = true
                binding.cardVenueResult.isVisible = false
                binding.textVenueNotFound.isVisible = false
                binding.textVenueError.isVisible = false
            }
            is VenueLookupUiState.Success -> {
                binding.progressVenueLoading.isVisible = false
                binding.cardVenueResult.isVisible = true
                binding.textVenueNotFound.isVisible = false
                binding.textVenueError.isVisible = false
                currentVenue = state.venue
                renderVenue(state.venue)
            }
            VenueLookupUiState.NotFound -> {
                binding.progressVenueLoading.isVisible = false
                binding.cardVenueResult.isVisible = false
                binding.textVenueNotFound.isVisible = true
                binding.textVenueError.isVisible = false
                currentVenue = null
            }
            is VenueLookupUiState.Error -> {
                binding.progressVenueLoading.isVisible = false
                binding.cardVenueResult.isVisible = false
                binding.textVenueNotFound.isVisible = false
                binding.textVenueError.text = state.message
                binding.textVenueError.isVisible = true
                currentVenue = null
            }
        }
    }

    private fun renderVenue(venue: AdminVenueItem) {
        binding.textVenueName.text = venue.name
        binding.textVenueAddress.text = venue.address.ifBlank { "Chưa cập nhật địa chỉ" }
        binding.textVenueOwnerId.text = venue.ownerId
        binding.chipVenueStatus.text = venue.status

        if (venue.isLocked) {
            binding.buttonLockVenue.isVisible = false
            binding.layoutUnlockOptions.isVisible = true
        } else {
            binding.buttonLockVenue.isVisible = true
            binding.layoutUnlockOptions.isVisible = false
        }
    }

    private fun showUnlockBottomSheet(venue: AdminVenueItem) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAdminUnlockVenueBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.textVenueUnlockName.text = venue.name
        dialogBinding.textVenueUnlockReason.text = "Cơ sở đang ở trạng thái LOCKED."

        dialogBinding.buttonCloseUnlock.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonConfirmUnlock.setOnClickListener {
            val targetStatus = if (dialogBinding.radioOptionActive.isChecked) "ACTIVE" else "HIDDEN"
            dialog.dismiss()
            viewModel.setVenueStatus(venue.venueId, targetStatus)
        }

        dialog.show()
    }

    private fun renderActionState(state: AdminActionUiState) {
        when (state) {
            AdminActionUiState.Idle -> {
                binding.buttonLockVenue.isEnabled = true
                binding.buttonUnlockActive.isEnabled = true
                binding.buttonUnlockHidden.isEnabled = true
            }
            AdminActionUiState.Loading -> {
                binding.buttonLockVenue.isEnabled = false
                binding.buttonUnlockActive.isEnabled = false
                binding.buttonUnlockHidden.isEnabled = false
            }
            is AdminActionUiState.Success -> {
                binding.buttonLockVenue.isEnabled = true
                binding.buttonUnlockActive.isEnabled = true
                binding.buttonUnlockHidden.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearActionState()
            }
            is AdminActionUiState.Error -> {
                binding.buttonLockVenue.isEnabled = true
                binding.buttonUnlockActive.isEnabled = true
                binding.buttonUnlockHidden.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.clearActionState()
            }
        }
    }

    private fun confirmStatusChange(venue: AdminVenueItem, targetStatus: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_venue_confirm_status_title)
            .setMessage(message)
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                viewModel.setVenueStatus(venue.venueId, targetStatus)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}