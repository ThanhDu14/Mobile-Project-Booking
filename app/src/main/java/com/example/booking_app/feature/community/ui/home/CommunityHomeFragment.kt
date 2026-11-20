package com.example.booking_app.feature.community.ui.home

import android.graphics.Typeface
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentCommunityHomeBinding
import com.example.booking_app.feature.chat.ui.list.ChatListFragment
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.example.booking_app.feature.community.ui.search.GroupAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cộng đồng (T028 + spec 091 L3): điều khiển 2 mục "Nhóm | Tin nhắn" (kèm huy hiệu số cuộc chưa đọc);
 * mục "Nhóm" gồm "Nhóm của tôi" và "Khám phá", mục "Tin nhắn" nhúng [ChatListFragment] chế độ PLAYER.
 */
@AndroidEntryPoint
class CommunityHomeFragment : Fragment(R.layout.fragment_community_home) {

    private val viewModel: CommunityHomeViewModel by viewModels()

    @Inject lateinit var avatarSource: AccountAvatarSource

    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator

    @Inject lateinit var authNavigator: AuthNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCommunityHomeBinding.bind(view)
        binding.header.tabTitle.setText(R.string.community_title_home)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)
        val createButton = addCreateButton(binding)

        val adapter = GroupAdapter { viewModel.onGroupClicked(it.group.id) }
        binding.groupList.layoutManager = LinearLayoutManager(requireContext())
        binding.groupList.adapter = adapter

        binding.sectionGroups.setOnClickListener { viewModel.selectSection(CommunityHomeSection.GROUPS) }
        binding.sectionMessages.setOnClickListener { viewModel.selectSection(CommunityHomeSection.MESSAGES) }

        binding.tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.selectTab(if (checkedId == R.id.tabDiscover) CommunityHomeTab.DISCOVER else CommunityHomeTab.MY_GROUPS)
        }

        childFragmentManager.setFragmentResultListener(ChatListFragment.RESULT_KEY_OPEN_DISCOVER, viewLifecycleOwner) { _, _ ->
            viewModel.selectSection(CommunityHomeSection.GROUPS)
            viewModel.selectTab(CommunityHomeTab.DISCOVER)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.selectedSection, viewModel.unreadChatCount) { section, unread -> section to unread }
                        .collect { (section, unread) -> renderSection(binding, createButton, section, unread) }
                }
                launch {
                    combine(viewModel.myGroups, viewModel.selectedTab) { state, tab -> state to tab }
                        .collect { (state, tab) -> render(binding, adapter, state, tab) }
                }
                launch { viewModel.events.collect { handle(it, binding) } }
            }
        }
    }

    /** "Tạo nhóm" ở khu vực hành động của header dùng chung (đúng wireframe). */
    private fun addCreateButton(binding: FragmentCommunityHomeBinding): MaterialButton {
        val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialIconButtonFilledTonalStyle)
        button.setText(R.string.community_action_create_group)
        button.setIconResource(R.drawable.ic_add_24)
        button.minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_min)
        button.setOnClickListener { viewModel.onCreateClicked() }
        binding.header.tabActions.addView(button)
        return button
    }

    private fun renderSection(
        binding: FragmentCommunityHomeBinding,
        createButton: MaterialButton,
        section: CommunityHomeSection,
        unreadCount: Int,
    ) {
        val showMessages = section == CommunityHomeSection.MESSAGES
        binding.groupsSectionContainer.isVisible = !showMessages
        binding.messagesContainer.isVisible = showMessages
        createButton.isVisible = !showMessages

        binding.sectionGroups.isSelected = !showMessages
        binding.sectionMessages.isSelected = showMessages
        binding.sectionGroups.setBackgroundResource(if (!showMessages) R.drawable.bg_community_segment_active else 0)
        binding.sectionMessages.setBackgroundResource(if (showMessages) R.drawable.bg_community_segment_active else 0)

        val activeColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val inactiveColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        binding.sectionGroups.setTextColor(if (!showMessages) activeColor else inactiveColor)
        binding.sectionGroups.setTypeface(null, if (!showMessages) Typeface.BOLD else Typeface.NORMAL)
        binding.sectionMessagesLabel.setTextColor(if (showMessages) activeColor else inactiveColor)
        binding.sectionMessagesLabel.setTypeface(null, if (showMessages) Typeface.BOLD else Typeface.NORMAL)

        binding.messagesUnreadBadge.isVisible = unreadCount > 0
        if (unreadCount > 0) {
            binding.messagesUnreadBadge.text = if (unreadCount > 99) getString(R.string.chat_unread_overflow) else unreadCount.toString()
            binding.sectionMessages.contentDescription = getString(R.string.chat_cd_messages_tab_unread, unreadCount)
        } else {
            binding.sectionMessages.contentDescription = getString(R.string.chat_cd_messages_tab)
        }

        if (showMessages && childFragmentManager.findFragmentByTag(TAG_MESSAGES) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.messagesContainer, ChatListFragment.newEmbeddedPlayer(), TAG_MESSAGES)
                .commit()
        }
    }

    private fun render(
        binding: FragmentCommunityHomeBinding,
        adapter: GroupAdapter,
        state: MyGroupsUiState,
        tab: CommunityHomeTab,
    ) {
        val checkedId = if (tab == CommunityHomeTab.DISCOVER) R.id.tabDiscover else R.id.tabMyGroups
        if (binding.tabGroup.checkedButtonId != checkedId) binding.tabGroup.check(checkedId)

        val discover = tab == CommunityHomeTab.DISCOVER
        binding.discoverContainer.isVisible = discover
        binding.stateViews.root.isVisible = !discover
        if (discover) {
            binding.groupList.isVisible = false
            return
        }

        val coreState: UiState<Unit> = when (state) {
            MyGroupsUiState.Loading -> UiState.Loading
            is MyGroupsUiState.Content -> UiState.Content(Unit)
            MyGroupsUiState.Empty -> UiState.Empty
            is MyGroupsUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.groupList,
            EmptyStateSpec(
                message = R.string.community_empty_my_groups,
                icon = R.drawable.ic_groups_24,
                actionText = R.string.community_action_create_group,
                onAction = viewModel::onCreateClicked,
            ),
        )
        adapter.submitList((state as? MyGroupsUiState.Content)?.cards.orEmpty())
    }

    private fun handle(event: CommunityHomeEvent, binding: FragmentCommunityHomeBinding) {
        val nav = findNavController()
        when (event) {
            CommunityHomeEvent.OpenCreateForm -> {
                if (nav.currentDestination?.id != R.id.communityHomeFragment) return
                nav.navigate(CommunityHomeFragmentDirections.actionCommunityHomeToGroupForm(null))
            }
            is CommunityHomeEvent.OpenGroup -> {
                if (nav.currentDestination?.id != R.id.communityHomeFragment) return
                nav.navigate(CommunityHomeFragmentDirections.actionCommunityHomeToGroupDetail(event.groupId))
            }
            CommunityHomeEvent.RequireLogin -> {
                Snackbar.make(binding.root, R.string.community_login_required_to_create, Snackbar.LENGTH_SHORT).show()
                // Đăng nhập xong quay lại màn Cộng đồng (deep link khai báo trong nav_community.xml).
                authNavigator.openLogin(nav, Uri.parse("smashnow://community"))
            }
        }
    }

    private companion object {
        const val TAG_MESSAGES = "community_messages"
    }
}
