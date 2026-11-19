package com.example.booking_app.feature.community.ui.detail

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.example.booking_app.feature.community.domain.model.PostType
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.example.booking_app.feature.community.ui.composer.PostComposerFragment
import com.example.booking_app.feature.community.ui.report.ReportPostBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trang nhóm và bảng tin (T029 + T038 + T050). Mở bằng `smashnow://community/group/{groupId}` hoặc từ danh sách.
 * Người ngoài chỉ thấy giới thiệu + nút tham gia; thành viên thấy bảng tin, nút đăng bài và menu bài đăng.
 */
@AndroidEntryPoint
class GroupDetailFragment : Fragment(R.layout.fragment_group_detail) {

    private val viewModel: GroupDetailViewModel by viewModels()

    @Inject lateinit var authNavigator: AuthNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentGroupDetailBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setFragmentResultListener(PostComposerFragment.RESULT_KEY_POST_SAVED) { _, _ ->
            viewModel.refreshFeed()
        }
        setFragmentResultListener(ReportPostBottomSheet.RESULT_KEY_REPORT_SENT) { _, _ ->
            Snackbar.make(binding.root, R.string.community_report_sent, Snackbar.LENGTH_SHORT).show()
        }

        val postAdapter = PostAdapter(object : PostActions {
            override fun onPhotoClick(urls: List<String>, startIndex: Int) {
                if (urls.isEmpty()) return
                findNavController().navigate(
                    R.id.action_global_photoViewer,
                    bundleOf("photoUrls" to urls.toTypedArray(), "startIndex" to startIndex),
                )
            }

            override fun onEditPost(postId: String, type: PostType) = openComposer(postId = postId, type = type)

            override fun onDeletePost(postId: String) = confirmDeletePost(postId)

            override fun onToggleAttendance(postId: String) = viewModel.onToggleAttendance(postId)

            override fun onViewAttendees(postId: String) {
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.groupDetailFragment) return
                nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToEventAttendees(viewModel.groupId, postId))
            }

            override fun onHidePost(postId: String) = confirmHidePost(postId)

            override fun onUnhidePost(postId: String) = viewModel.onUnhidePost(postId)

            override fun onReportPost(postId: String) {
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.groupDetailFragment) return
                nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToReportPost(viewModel.groupId, postId))
            }
        })
        binding.feedList.layoutManager = LinearLayoutManager(requireContext())
        binding.feedList.adapter = postAdapter

        binding.content.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
                val child = v.getChildAt(0) ?: return@OnScrollChangeListener
                if (scrollY + v.height >= child.measuredHeight - LOAD_MORE_THRESHOLD_PX) {
                    viewModel.loadMoreFeed()
                }
            },
        )
        binding.feedLoadMoreRetry.setOnClickListener { viewModel.retryLoadMoreFeed() }

        binding.editButton.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.groupDetailFragment) return@setOnClickListener
            nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToGroupForm(viewModel.groupId))
        }
        binding.newPostButton.setOnClickListener { openComposer(postId = null, type = PostType.POST) }
        binding.newEventButton.setOnClickListener { openComposer(postId = null, type = PostType.EVENT) }
        binding.chatButton.setOnClickListener { openGroupChat(binding) }
        binding.membersButton.setOnClickListener { openMembers(selectTransferTarget = false) }
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
                launch { viewModel.state.collect { render(binding, postAdapter, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun openComposer(postId: String?, type: PostType) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.groupDetailFragment) return
        nav.navigate(
            GroupDetailFragmentDirections.actionGroupDetailToPostComposer(
                groupId = viewModel.groupId,
                postId = postId,
                type = type.name,
            ),
        )
    }

    private fun openGroupChat(binding: FragmentGroupDetailBinding) {
        val uri = Uri.parse("smashnow://chat/group/${viewModel.groupId}")
        runCatching { findNavController().navigate(uri) }.onFailure {
            Snackbar.make(binding.root, R.string.community_chat_coming_soon, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openMembers(selectTransferTarget: Boolean) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.groupDetailFragment) return
        nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToGroupMembers(viewModel.groupId, selectTransferTarget))
    }

    private fun render(binding: FragmentGroupDetailBinding, postAdapter: PostAdapter, state: GroupDetailUiState) {
        val coreState: UiState<Unit> = when (state) {
            GroupDetailUiState.Loading -> UiState.Loading
            is GroupDetailUiState.Content -> UiState.Content(Unit)
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
        binding.memberActionsRow.isVisible = state.showFeed
        binding.editButton.isVisible = state.canEdit
        renderMembershipButtons(binding, state)
        renderFeed(binding, postAdapter, state)
    }

    private fun renderFeed(binding: FragmentGroupDetailBinding, postAdapter: PostAdapter, state: GroupDetailUiState.Content) {
        binding.feedArea.isVisible = state.showFeed
        if (!state.showFeed) {
            postAdapter.submitList(emptyList())
            return
        }
        binding.composerBar.isVisible = state.canPost

        val feedState = state.feed
        val feedCoreState: UiState<Unit> = when (feedState) {
            FeedUiState.Loading -> UiState.Loading
            is FeedUiState.Content -> UiState.Content(Unit)
            FeedUiState.Empty -> UiState.Empty
            is FeedUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), feedState.cause), viewModel::retryFeed)
        }
        binding.feedStateViews.render(
            feedCoreState,
            binding.feedList,
            EmptyStateSpec(message = R.string.community_empty_feed, icon = R.drawable.ic_edit_24),
        )
        val content = feedState as? FeedUiState.Content
        postAdapter.submitList(content?.items.orEmpty())
        binding.feedLoadMoreProgress.isVisible = content?.loadingMore == true
        binding.feedLoadMoreRetry.isVisible = content?.loadMoreError != null
        binding.feedFooterNote.isVisible = content != null && content.items.isNotEmpty() && !content.canLoadMore
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

    private fun confirmDeletePost(postId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.community_confirm_delete_post_title)
            .setMessage(R.string.community_confirm_delete_post_message)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_delete) { _, _ -> viewModel.onDeletePostConfirmed(postId) }
            .show()
    }

    private fun confirmHidePost(postId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.community_confirm_hide_post_title)
            .setMessage(R.string.community_confirm_hide_post_message)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_hide) { _, _ -> viewModel.onHidePostConfirmed(postId) }
            .show()
    }

    private fun suggestDeleteGroup() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.community_suggest_delete_title)
            .setMessage(R.string.community_error_owner_must_transfer_sole)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_delete_group) { _, _ ->
                val nav = findNavController()
                if (nav.currentDestination?.id == R.id.groupDetailFragment) {
                    nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToGroupForm(viewModel.groupId))
                }
            }
            .show()
    }

    private fun handle(binding: FragmentGroupDetailBinding, event: GroupDetailEvent) {
        val context = requireContext()
        when (event) {
            is GroupDetailEvent.RequireLogin -> {
                Snackbar.make(binding.root, R.string.community_login_required_to_join, Snackbar.LENGTH_SHORT).show()
                authNavigator.openLogin(findNavController(), Uri.parse(event.returnTo))
            }
            is GroupDetailEvent.JoinOutcome -> Snackbar.make(
                binding.root,
                if (event.result == JoinResult.JOINED) R.string.community_msg_joined else R.string.community_msg_requested,
                Snackbar.LENGTH_SHORT,
            ).show()
            GroupDetailEvent.RequestCancelled -> Snackbar.make(binding.root, R.string.community_msg_request_cancelled, Snackbar.LENGTH_SHORT).show()
            GroupDetailEvent.Left -> Snackbar.make(binding.root, R.string.community_msg_left, Snackbar.LENGTH_SHORT).show()
            GroupDetailEvent.PostDeleted -> Snackbar.make(binding.root, R.string.community_msg_post_deleted, Snackbar.LENGTH_SHORT).show()
            GroupDetailEvent.PostHidden -> Snackbar.make(binding.root, R.string.community_msg_post_hidden, Snackbar.LENGTH_SHORT).show()
            GroupDetailEvent.PostUnhidden -> Snackbar.make(binding.root, R.string.community_msg_post_unhidden, Snackbar.LENGTH_SHORT).show()
            is GroupDetailEvent.OpenTransferOwner -> {
                Snackbar.make(binding.root, R.string.community_error_owner_must_transfer, Snackbar.LENGTH_SHORT).show()
                openMembers(selectTransferTarget = true)
            }
            GroupDetailEvent.SuggestDeleteGroup -> suggestDeleteGroup()
            is GroupDetailEvent.ShowError -> Snackbar.make(binding.root, CommunityUiText.error(context, event.error), Snackbar.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD_PX = 400
    }
}
