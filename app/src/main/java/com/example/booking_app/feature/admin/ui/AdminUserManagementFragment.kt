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

        binding.textUserNotFound.setOnClickListener { viewModel.loadUsers(more = viewModel.usersState.value.nextPageToken.isNotEmpty()) }
        binding.recyclerUsers.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !rv.canScrollVertically(1)) viewModel.loadUsers(more = true)
            }
        })
        viewModel.loadUsers()
        observeViewModel()
    }

    private fun performSearch() { applyFilter() }

    private fun applyFilter() {
        val filter = when (binding.chipGroupUserFilter.checkedChipId) {
            R.id.chipFilterOwners -> "OWNERS"
            R.id.chipFilterLocked -> "LOCKED"
            else -> "ALL"
        }
        viewModel.loadUsers(binding.inputUserSearch.text?.toString()?.trim().orEmpty(), filter)
    }

    private fun showUserActionBottomSheet(user: AdminUserLookup) {
        if (viewModel.actionState.value == AdminActionUiState.Loading) return
        val actions = mutableListOf("Thông tin tài khoản")
        if (user.isLocked) {
            actions.add(getString(R.string.admin_user_unlock_button))
        } else {
            actions.add(getString(R.string.admin_user_lock_button))
        }

        if (user.isOwnerOrApproved) {
            actions.add(getString(R.string.admin_user_revoke_owner_button))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(user.fullName.ifBlank { user.uid })

            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Thông tin tài khoản" -> MaterialAlertDialogBuilder(requireContext()).setTitle(user.fullName.ifBlank { user.uid }).setMessage(listOf(user.uid, user.email, user.phone, user.role, user.accountStatus.name, user.ownerRevokedReason.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")).setPositiveButton(android.R.string.ok, null).show()
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
                    viewModel.usersState.collect { state ->
                        binding.progressUserLoading.isVisible = state.isLoading
                        binding.recyclerUsers.isVisible = state.users.isNotEmpty()
                        adapter.submitList(state.users)
                        binding.textUserNotFound.isVisible = state.error != null || (!state.isLoading && state.users.isEmpty()) || state.nextPageToken.isNotEmpty()
                        binding.textUserNotFound.text = state.error?.let { "$it · Thử lại" }
                            ?: if (state.nextPageToken.isNotEmpty()) "Tải thêm kết quả" else getString(R.string.admin_not_found_user)
                    }
                }

                launch {
                    viewModel.actionState.collect { state ->
                        when (state) {
                            AdminActionUiState.Idle -> Unit
                            AdminActionUiState.Loading -> Unit
                            is AdminActionUiState.Success -> {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.loadUsers()
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
