package com.example.booking_app.feature.community.ui.detail

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
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentGroupDetailBinding
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Trang nhóm (T029, khung US1). Mở bằng `smashnow://community/group/{groupId}` (thông báo `GROUP_ACTIVITY`) hoặc từ
 * danh sách. Nhóm không còn hoặc bị Rules từ chối hiện "Nhóm không còn tồn tại".
 */
@AndroidEntryPoint
class GroupDetailFragment : Fragment(R.layout.fragment_group_detail) {

    private val viewModel: GroupDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentGroupDetailBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.editButton.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id != R.id.groupDetailFragment) return@setOnClickListener
            nav.navigate(GroupDetailFragmentDirections.actionGroupDetailToGroupForm(viewModel.groupId))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(binding, it) }
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
        binding.groupDistrict.text = context.getString(R.string.community_detail_district, group.districtId)
        binding.groupDescription.text = group.description.ifBlank { context.getString(R.string.community_detail_no_description) }
        binding.groupAvatar.contentDescription = context.getString(R.string.community_group_avatar_cd, group.name)
        binding.groupAvatar.load(CommunityUiText.avatarUrl(group.avatarPath)) {
            placeholder(R.drawable.ic_groups_24)
            error(R.drawable.ic_groups_24)
        }
        binding.lockedLabel.isVisible = group.isLocked
        binding.editButton.isVisible = state.canEdit
    }
}
