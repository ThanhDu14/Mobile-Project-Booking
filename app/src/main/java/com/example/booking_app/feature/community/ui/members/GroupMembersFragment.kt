package com.example.booking_app.feature.community.ui.members

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentGroupMembersBinding
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** T062 + T063: danh sách thành viên nhóm và chế độ chọn người nhận quyền chủ nhóm. */
@AndroidEntryPoint
class GroupMembersFragment : Fragment(R.layout.fragment_group_members) {

    private val viewModel: GroupMembersViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentGroupMembersBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setTitle(
            if (viewModel.selectTransferTarget) R.string.community_title_transfer_owner else R.string.community_title_members,
        )
        binding.transferHint.isVisible = viewModel.selectTransferTarget

        val adapter = GroupMemberAdapter(object : MemberActions {
            override fun onMakeAdmin(member: GroupMember) = confirmMakeAdmin(member)
            override fun onRemoveAdmin(member: GroupMember) = confirmRemoveAdmin(member)
            override fun onTransferOwner(member: GroupMember) = confirmTransferOwner(member)
            override fun onRemoveMember(member: GroupMember) = confirmRemoveMember(member)
        })
        adapter.selectTransferTarget = viewModel.selectTransferTarget
        binding.memberList.layoutManager = LinearLayoutManager(requireContext())
        binding.memberList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, adapter, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentGroupMembersBinding, adapter: GroupMemberAdapter, state: GroupMembersUiState) {
        val coreState: UiState<Unit> = when (state) {
            GroupMembersUiState.Loading -> UiState.Loading
            is GroupMembersUiState.Content -> UiState.Content(Unit)
            GroupMembersUiState.Empty -> UiState.Empty
            is GroupMembersUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.memberList,
            EmptyStateSpec(message = R.string.community_empty_members, icon = R.drawable.ic_groups_24),
        )
        val content = state as? GroupMembersUiState.Content
        adapter.submitList(content?.items.orEmpty())
    }

    private fun displayNameOf(member: GroupMember): String =
        member.displayName.ifBlank { getString(R.string.community_join_request_default_name) }

    private fun confirmMakeAdmin(member: GroupMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.community_confirm_make_admin_title, displayNameOf(member)))
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_confirm) { _, _ -> viewModel.onMakeAdminConfirmed(member.userId) }
            .show()
    }

    private fun confirmRemoveAdmin(member: GroupMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.community_confirm_remove_admin_title, displayNameOf(member)))
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_confirm) { _, _ -> viewModel.onRemoveAdminConfirmed(member.userId) }
            .show()
    }

    private fun confirmTransferOwner(member: GroupMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.community_confirm_transfer_title, displayNameOf(member)))
            .setMessage(R.string.community_confirm_transfer_message)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_confirm) { _, _ -> viewModel.onTransferOwnerConfirmed(member.userId) }
            .show()
    }

    private fun confirmRemoveMember(member: GroupMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.community_confirm_remove_title, displayNameOf(member)))
            .setMessage(R.string.community_confirm_remove_message)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_remove_member) { _, _ -> viewModel.onRemoveConfirmed(member.userId) }
            .show()
    }

    private fun handle(binding: FragmentGroupMembersBinding, event: GroupMembersEvent) {
        when (event) {
            GroupMembersEvent.RoleUpdated ->
                Snackbar.make(binding.root, R.string.community_msg_role_updated, Snackbar.LENGTH_SHORT).show()
            GroupMembersEvent.OwnerTransferred -> {
                Snackbar.make(binding.root, R.string.community_msg_owner_transferred, Snackbar.LENGTH_LONG).show()
                if (viewModel.selectTransferTarget) findNavController().navigateUp()
            }
            GroupMembersEvent.MemberRemoved ->
                Snackbar.make(binding.root, R.string.community_msg_member_removed, Snackbar.LENGTH_SHORT).show()
            is GroupMembersEvent.ShowError ->
                Snackbar.make(binding.root, CommunityUiText.error(requireContext(), event.error), Snackbar.LENGTH_LONG).show()
        }
    }
}
