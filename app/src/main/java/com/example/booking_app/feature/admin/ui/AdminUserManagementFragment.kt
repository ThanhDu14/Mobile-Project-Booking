package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.DialogAdminRevokeOwnerBinding
import com.example.booking_app.databinding.FragmentAdminUserManagementBinding
import com.example.booking_app.feature.admin.domain.model.AdminUserLookup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminUserManagementFragment : Fragment() {

    private var _binding: FragmentAdminUserManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminUserVenueViewModel by viewModels()
    private var currentUser: AdminUserLookup? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonSearchUser.setOnClickListener {
            performSearch()
        }

        binding.inputUserSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.buttonToggleLock.setOnClickListener {
            val user = currentUser ?: return@setOnClickListener
            confirmToggleLock(user)
        }

        binding.buttonRevokeOwner.setOnClickListener {
            val user = currentUser ?: return@setOnClickListener
            showRevokeOwnerDialog(user)
        }

        observeViewModel()
    }

    private fun performSearch() {
        val query = binding.inputUserSearch.text?.toString().orEmpty()
        viewModel.searchUser(query)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userLookupState.collect { state ->
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

    private fun renderLookupState(state: UserLookupUiState) {
        when (state) {
            UserLookupUiState.Idle -> {
                binding.progressUserLoading.isVisible = false
                binding.textUserNotFound.isVisible = false
                binding.textUserError.isVisible = false
                binding.cardUserResult.isVisible = false
                currentUser = null
            }
            UserLookupUiState.Loading -> {
                binding.progressUserLoading.isVisible = true
                binding.textUserNotFound.isVisible = false
                binding.textUserError.isVisible = false
                binding.cardUserResult.isVisible = false
            }
            UserLookupUiState.NotFound -> {
                binding.progressUserLoading.isVisible = false
                binding.textUserNotFound.isVisible = true
                binding.textUserError.isVisible = false
                binding.cardUserResult.isVisible = false
                currentUser = null
            }
            is UserLookupUiState.Error -> {
                binding.progressUserLoading.isVisible = false
                binding.textUserNotFound.isVisible = false
                binding.textUserError.isVisible = true
                binding.textUserError.text = state.message
                binding.cardUserResult.isVisible = false
                currentUser = null
            }
            is UserLookupUiState.Success -> {
                binding.progressUserLoading.isVisible = false
                binding.textUserNotFound.isVisible = false
                binding.textUserError.isVisible = false
                binding.cardUserResult.isVisible = true
                bindUserCard(state.user)
            }
        }
    }

    private fun bindUserCard(user: AdminUserLookup) {
        currentUser = user
        binding.textUserFullName.text = user.fullName.ifBlank { "Chưa cập nhật" }
        binding.textUserPhone.text = user.phone.ifBlank { "Chưa có SĐT" }
        binding.textUserUid.text = user.uid

        binding.chipUserRole.text = "Vai trò: ${user.role}"
        binding.chipOwnerStatus.text = "Chủ sân: ${user.ownerStatus}"
        binding.chipAccountStatus.text = "Tài khoản: ${user.accountStatus.name}"

        if (!user.ownerRevokedReason.isNullOrBlank()) {
            binding.textRevokedReason.isVisible = true
            binding.textRevokedReason.text = "Lý do thu hồi trước đây: ${user.ownerRevokedReason}"
        } else {
            binding.textRevokedReason.isVisible = false
        }

        if (user.isLocked) {
            binding.buttonToggleLock.setText(R.string.admin_user_unlock_button)
        } else {
            binding.buttonToggleLock.setText(R.string.admin_user_lock_button)
        }

        binding.buttonRevokeOwner.isVisible = user.isOwnerOrApproved
    }

    private fun renderActionState(state: AdminActionUiState) {
        when (state) {
            AdminActionUiState.Idle -> {
                binding.buttonToggleLock.isEnabled = true
                binding.buttonRevokeOwner.isEnabled = true
            }
            AdminActionUiState.Loading -> {
                binding.buttonToggleLock.isEnabled = false
                binding.buttonRevokeOwner.isEnabled = false
            }
            is AdminActionUiState.Success -> {
                binding.buttonToggleLock.isEnabled = true
                binding.buttonRevokeOwner.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearActionState()
            }
            is AdminActionUiState.Error -> {
                binding.buttonToggleLock.isEnabled = true
                binding.buttonRevokeOwner.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.clearActionState()
            }
        }
    }

    private fun confirmToggleLock(user: AdminUserLookup) {
        val title = if (user.isLocked) {
            getString(R.string.admin_confirm_unlock_title)
        } else {
            getString(R.string.admin_confirm_lock_title)
        }
        val message = if (user.isLocked) {
            getString(R.string.admin_confirm_unlock_user_format, user.fullName)
        } else {
            getString(R.string.admin_confirm_lock_user_format, user.fullName)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                viewModel.toggleAccountLock(user)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showRevokeOwnerDialog(user: AdminUserLookup) {
        val dialogBinding = DialogAdminRevokeOwnerBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonCancelRevoke.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonConfirmRevoke.setOnClickListener {
            val reason = dialogBinding.inputRevokeReason.text?.toString().orEmpty()
            if (reason.trim().isEmpty()) {
                dialogBinding.layoutRevokeReason.error = getString(R.string.admin_revoke_reason_hint)
                return@setOnClickListener
            }
            dialogBinding.layoutRevokeReason.error = null
            dialog.dismiss()
            viewModel.revokeOwner(user.uid, reason)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
