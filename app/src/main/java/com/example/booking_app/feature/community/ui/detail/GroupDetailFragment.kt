package com.example.booking_app.feature.community.ui.detail

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentGroupDetailBinding
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trang nhóm (T029, khung US1). Mở bằng `smashnow://community/group/{groupId}` (thông báo `GROUP_ACTIVITY`) hoặc từ
 * danh sách. Nhóm không còn hoặc bị Rules từ chối hiện "Nhóm không còn tồn tại".
 */
@AndroidEntryPoint
class GroupDetailFragment : Fragment(R.layout.fragment_group_detail) {

    private val viewModel: GroupDetailViewModel by viewModels()

    @Inject lateinit var authNavigator: AuthNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentGroupDetailBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.editButton.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.groupDetailFragment) return@setOnClickListener
            nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToGroupForm(viewModel.groupId))
        }

        binding.joinButton.setOnClickListener { viewModel.onJoinClicked() }
        binding.cancelRequestButton.setOnClickListener { viewModel.onCancelRequestClicked() }
        binding.leaveButton.setOnClickListener { confirmLeave() }
        binding.requestsButton.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.groupDetailFragment) return@setOnClickListener
            nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToJoinRequests(viewModel.groupId))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentGroupDetailBinding, state: GroupDetailUiState) {
        val coreState: UiState<Unit> = when (state) {
            GroupDetailUiState.Loading -> UiState.Loading
            is GroupDetailUiState.Content -> UiState.Content(Unit)
            // "Nhóm không còn tồn tại" là trạng thái rỗng, không có nút thử lại.
            GroupDetailUiState.NotFound -> UiState.Empty
            is GroupDetailUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(message = R.string.community_error_group_not_found, icon = R.drawable.ic_groups_24),
        )
        if (state !is GroupDetailUiState.Content) return

        val group = state.group
        val context = requireContext()
        binding.toolbar.title = group.name
        binding.groupName.text = group.name
        val members = resources.getQuantityString(R.plurals.community_member_count, group.memberCount, group.memberCount)
        binding.groupMeta.text = context.getString(
            R.string.community_group_meta,
            context.getString(CommunityUiText.visibilityLabel(group.visibility)),
            members,
        )
        binding.groupDistrict.text = context.getString(R.string.community_detail_district, state.districtName ?: group.districtId)
        binding.groupDescription.text = group.description.ifBlank { context.getString(R.string.community_detail_no_description) }
        binding.groupAvatar.contentDescription = context.getString(R.string.community_group_avatar_cd, group.name)
        binding.groupAvatar.load(CommunityUiText.avatarUrl(group.avatarPath)) {
            placeholder(R.drawable.ic_groups_24)
            error(R.drawable.ic_groups_24)
        }
        binding.lockedLabel.isVisible = group.isLocked
        binding.editButton.isVisible = state.canEdit
        renderMembershipButtons(binding, state)
    }

    /** T038/T039: nút theo [GroupDetailUiState.Content.joinAction], "Rời nhóm" và "Yêu cầu tham gia (N)". */
    private fun renderMembershipButtons(binding: FragmentGroupDetailBinding, state: GroupDetailUiState.Content) {
        val enabled = !state.busy
        binding.joinButton.isVisible = state.joinAction == JoinAction.JOIN || state.joinAction == JoinAction.REQUEST
        binding.joinButton.setText(
            if (state.joinAction == JoinAction.REQUEST) R.string.community_action_request_join else R.string.community_action_join,
        )
        binding.joinButton.isEnabled = enabled
        binding.pendingNote.isVisible = state.joinAction == JoinAction.PENDING
        binding.pendingNote.contentDescription = getString(R.string.community_label_pending) + ". " + getString(R.string.community_join_pending_note)
        binding.cancelRequestButton.isVisible = state.joinAction == JoinAction.PENDING
        binding.cancelRequestButton.isEnabled = enabled
        binding.leaveButton.isVisible = state.canLeave
        binding.leaveButton.isEnabled = enabled
        binding.requestsButton.isVisible = state.canReviewRequests
        binding.requestsButton.text = getString(R.string.community_action_pending_requests, state.pendingRequestCount)
        binding.joinArea.isVisible = binding.joinButton.isVisible || binding.pendingNote.isVisible ||
            binding.leaveButton.isVisible || binding.requestsButton.isVisible
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.community_confirm_leave_title)
            .setMessage(R.string.community_confirm_leave_message)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_leave) { _, _ -> viewModel.onLeaveConfirmed() }
            .show()
    }

    private fun handle(binding: FragmentGroupDetailBinding, event: GroupDetailEvent) {
        val context = requireContext()
        when (event) {
            is GroupDetailEvent.RequireLogin -> {
                Snackbar.make(binding.root, R.string.community_login_required_to_join, Snackbar.LENGTH_SHORT).show()
                // Đăng nhập xong quay lại đúng trang nhóm (deep link smashnow://community/group/{groupId}).
                authNavigator.openLogin(findNavController(), Uri.parse(event.returnTo))
            }
            is GroupDetailEvent.JoinOutcome -> Snackbar.make(
                binding.root,
                if (event.result == JoinResult.JOINED) R.string.community_msg_joined else R.string.community_msg_requested,
                Snackbar.LENGTH_SHORT,
            ).show()
            GroupDetailEvent.RequestCancelled -> Snackbar.make(binding.root, R.string.community_msg_request_cancelled, Snackbar.LENGTH_SHORT).show()
            GroupDetailEvent.Left -> Snackbar.make(binding.root, R.string.community_msg_left, Snackbar.LENGTH_SHORT).show()
            // TODO US5: mở màn chọn người nhận quyền (GroupMembersFragment) thay cho thông báo.
            is GroupDetailEvent.OpenTransferOwner ->
                Snackbar.make(binding.root, R.string.community_error_owner_must_transfer, Snackbar.LENGTH_LONG).show()
            GroupDetailEvent.SuggestDeleteGroup ->
                Snackbar.make(binding.root, R.string.community_error_owner_must_transfer_sole, Snackbar.LENGTH_LONG).show()
            is GroupDetailEvent.ShowError -> Snackbar.make(binding.root, CommunityUiText.error(context, event.error), Snackbar.LENGTH_LONG).show()
        }
    }
}
