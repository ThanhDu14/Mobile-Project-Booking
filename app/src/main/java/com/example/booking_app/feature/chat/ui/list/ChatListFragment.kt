package com.example.booking_app.feature.chat.ui.list

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentChatListBinding
import com.example.booking_app.feature.chat.domain.chatErrorOf
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Danh sách cuộc trò chuyện: chế độ `PLAYER` ("Tin nhắn") hoặc `OWNER` ("Tin nhắn khách hàng").
 */
@AndroidEntryPoint
open class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    @Inject lateinit var authNavigator: AuthNavigator

    private val viewModel: ChatListViewModel by viewModels()

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatAdapter

    private val isEmbedded: Boolean
        get() = arguments?.getBoolean(ARG_EMBEDDED, false) == true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChatListBinding.bind(view)

        binding.toolbar.isVisible = !isEmbedded
        binding.toolbarDivider.isVisible = !isEmbedded
        binding.toolbar.setTitle(
            if (viewModel.mode == ChatMode.OWNER) R.string.chat_title_owner else R.string.chat_title_list,
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ChatAdapter(
            mode = viewModel.mode,
            currentUidProvider = { viewModel.currentUid },
            onChatClick = { viewModel.onChatClicked(it.id) },
        )
        val lm = LinearLayoutManager(requireContext())
        binding.chatList.layoutManager = lm
        binding.chatList.adapter = adapter
        binding.chatList.addItemDecoration(DividerItemDecoration(requireContext(), lm.orientation))

        val isOwner = viewModel.mode == ChatMode.OWNER
        binding.emptyTitle.setText(if (isOwner) R.string.chat_empty_owner_title else R.string.chat_empty_player_title)
        binding.emptySubtitle.setText(if (isOwner) R.string.chat_empty_owner_subtitle else R.string.chat_empty_player_subtitle)
        binding.emptyJoinGroupButton.isVisible = !isOwner
        binding.emptyFindVenueButton.isVisible = !isOwner
        binding.emptyJoinGroupButton.setOnClickListener { viewModel.onJoinGroupClicked() }
        binding.emptyFindVenueButton.setOnClickListener { viewModel.onFindVenueClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handle) }
            }
        }
    }

    override fun onDestroyView() {
        binding.chatList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: UiState<List<ChatSummary>>) {
        val isEmpty = state is UiState.Empty
        binding.emptyContainer.isVisible = isEmpty
        binding.stateViews.root.isVisible = !isEmpty
        binding.listFooter.isVisible = isEmbedded && state is UiState.Content

        if (isEmpty) {
            binding.chatList.isVisible = false
            adapter.submitList(emptyList())
            return
        }

        val coreState: UiState<Unit> = when (state) {
            UiState.Loading -> UiState.Loading
            is UiState.Content -> UiState.Content(Unit)
            UiState.Empty -> UiState.Empty
            is UiState.Error -> UiState.Error(
                message = ChatUiText.error(requireContext(), chatErrorOf(state.message)),
                retry = state.retry,
            )
        }
        binding.stateViews.render(
            coreState,
            binding.chatList,
            EmptyStateSpec(message = R.string.chat_empty_player_title, icon = R.drawable.ic_chat_24),
        )
        adapter.submitList((state as? UiState.Content)?.data.orEmpty())
    }

    protected open fun openChat(chatId: String) {
        val nav = findNavController()
        when (nav.currentDestination?.id) {
            R.id.chatListFragment -> nav.navigate(ChatListFragmentDirections.actionChatListToChat(chatId = chatId))
            R.id.communityHomeFragment -> nav.navigate(Uri.parse("smashnow://chat/${Uri.encode(chatId)}"))
        }
    }

    private fun handle(event: ChatListEvent) {
        when (event) {
            is ChatListEvent.OpenChat -> openChat(event.chatId)
            is ChatListEvent.RequireLogin -> {
                val returnTo = if (isEmbedded) "smashnow://community" else event.returnTo
                authNavigator.openLogin(findNavController(), returnTo = Uri.parse(returnTo))
            }
            ChatListEvent.OpenJoinGroup -> {
                if (isEmbedded) {
                    setFragmentResult(RESULT_KEY_OPEN_DISCOVER, bundleOf())
                } else {
                    findNavController().navigate(Uri.parse("smashnow://community"))
                }
            }
            ChatListEvent.OpenFindVenue -> {
                val nav = findNavController()
                if (!nav.popBackStack(R.id.courtSearchFragment, false)) {
                    nav.navigate(Uri.parse("smashnow://search"))
                }
            }
        }
    }

    companion object {
        const val ARG_EMBEDDED = "embedded"
        const val RESULT_KEY_OPEN_DISCOVER = "chat_list_open_discover"

        /** Bản nhúng trong mục "Tin nhắn" của màn Cộng đồng (chế độ PLAYER, không hiện toolbar riêng). */
        fun newEmbeddedPlayer(): ChatListFragment = ChatListFragment().apply {
            arguments = bundleOf(
                ChatListViewModel.ARG_MODE to ChatMode.PLAYER.name,
                ARG_EMBEDDED to true,
            )
        }
    }
}

/** Subclass riêng cho destination `@+id/ownerChatListFragment` để tránh xung đột sinh mã Safe Args. */
@AndroidEntryPoint
class OwnerChatListFragment : ChatListFragment() {
    override fun openChat(chatId: String) {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.ownerChatListFragment) {
            nav.navigate(OwnerChatListFragmentDirections.actionOwnerChatListToChat(chatId = chatId))
        }
    }
}
