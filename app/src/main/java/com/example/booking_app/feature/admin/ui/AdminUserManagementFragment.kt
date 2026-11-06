package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var adapter: AdminUserAdapter
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

        adapter = AdminUserAdapter { user ->
            showUserActionBottomSheet(user)
        }

        binding.recyclerUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerUsers.adapter = adapter

        binding.inputUserSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.chipGroupUserFilter.setOnCheckedStateChangeListener { _, _ ->
            applyFilter()
        }

        observeViewModel()
    }

    private fun performSearch() {
        val query = binding.inputUserSearch.text?.toString()?.trim().orEmpty()
        if (query.isNotBlank()) {
            viewModel.searchUser(query)
        }
    }

    private fun applyFilter() {
        val user = currentUser ?: return
        val checkedFilter = binding.chipGroupUserFilter.checkedChipId
        val matchFilter = when (checkedFilter) {
            R.id.chipFilterOwners -> user.isOwnerOrApproved
            R.id.chipFilterLocked -> user.isLocked
            else -> true
        }

        if (matchFilter) {
            adapter.submitList(listOf(user))
            binding.recyclerUsers.isVisible = true
            binding.textUserNotFound.isVisible = false
        } else {
            adapter.submitList(emptyList())
            binding.recyclerUsers.isVisible = false
            binding.textUserNotFound.isVisible = true
            binding.textUserNotFound.text = "Không có tài khoản phù hợp với bộ lọc đã chọn"
        }
    }

    private fun showUserActionBottomSheet(user: AdminUserLookup) {
        val actions = mutableListOf<String>()
        if (user.isLocked) {
            actions.add(getString(R.string.admin_user_unlock_button))
        } else {
            actions.add(getString(R.string.admin_user_lock_button))
        }

        if (user.isOwnerOrApproved) {
            actions.add(getString(R.string.admin_user_revoke_owner_button))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(user.fullName.ifBlank { user.phone })
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    getString(R.string.admin_user_lock_button) -> confirmToggleLock(user)
                    getString(R.string.admin_user_unlock_button) -> confirmToggleLock(user)
                    getString(R.string.admin_user_revoke_owner_button) -> showRevokeOwnerDialog(user)
                }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun confirmToggleLock(user: AdminUserLookup) {
        val message = if (user.isLocked) "Bạn có chắc muốn mở khóa tài khoản này?" else "Bạn có chắc muốn khóa tài khoản này?"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (user.isLocked) R.string.admin_user_unlock_button else R.string.admin_user_lock_button)
            .setMessage(message)
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                viewModel.toggleAccountLock(user)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showRevokeOwnerDialog(user: AdminUserLookup) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_revoke_owner, null)
        val dialogBinding = DialogAdminRevokeOwnerBinding.bind(dialogView)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogBinding.buttonCancelRevoke.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonConfirmRevoke.setOnClickListener {
            val reason = dialogBinding.inputRevokeReason.text?.toString()?.trim().orEmpty()
            if (reason.isEmpty()) {
                dialogBinding.layoutRevokeReason.error = getString(R.string.admin_revoke_reason_hint)
            } else {
                dialogBinding.layoutRevokeReason.error = null
                dialog.dismiss()
                viewModel.revokeOwner(user.uid, reason)
            }
        }

        dialog.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userLookupState.collect { state ->
                        when (state) {
                            UserLookupUiState.Idle -> {
                                binding.progressUserLoading.isVisible = false
                                binding.textUserNotFound.text = "Nhập số điện thoại, email hoặc UID để tra cứu tài khoản."
                                binding.textUserNotFound.isVisible = true
                                binding.recyclerUsers.isVisible = false
                                adapter.submitList(emptyList())
                                currentUser = null
                            }
                            UserLookupUiState.Loading -> {
                                binding.progressUserLoading.isVisible = true
                                binding.textUserNotFound.isVisible = false
                                binding.recyclerUsers.isVisible = false
                            }
                            is UserLookupUiState.Success -> {
                                binding.progressUserLoading.isVisible = false
                                currentUser = state.user
                                applyFilter()
                            }
                            UserLookupUiState.NotFound -> {
                                binding.progressUserLoading.isVisible = false
                                binding.textUserNotFound.text = getString(R.string.admin_not_found_user)
                                binding.textUserNotFound.isVisible = true
                                binding.recyclerUsers.isVisible = false
                                adapter.submitList(emptyList())
                                currentUser = null
                            }
                            is UserLookupUiState.Error -> {
                                binding.progressUserLoading.isVisible = false
                                binding.textUserNotFound.text = state.message
                                binding.textUserNotFound.isVisible = true
                                binding.recyclerUsers.isVisible = false
                                adapter.submitList(emptyList())
                                currentUser = null
                            }
                        }
                    }
                }

                launch {
                    viewModel.actionState.collect { state ->
                        when (state) {
                            AdminActionUiState.Idle -> Unit
                            AdminActionUiState.Loading -> Unit
                            is AdminActionUiState.Success -> {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.clearActionState()
                            }
                            is AdminActionUiState.Error -> {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
