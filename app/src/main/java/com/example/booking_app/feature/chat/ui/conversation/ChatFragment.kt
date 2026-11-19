package com.example.booking_app.feature.chat.ui.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.session.initialsOf
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentChatBinding
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.chatErrorOf
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatMessageRules
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Màn hình cuộc trò chuyện (T029, T034, T040, T046, T052).
 */
@AndroidEntryPoint
class ChatFragment : Fragment(R.layout.fragment_chat) {

    @Inject lateinit var authNavigator: AuthNavigator

    private val viewModel: ChatViewModel by viewModels()

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MessageAdapter
    private var lastMessageCount = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChatBinding.bind(view)

        adapter = MessageAdapter(
            onRetryClick = { viewModel.onRetryMessage(it.id) },
            onDeleteClick = { viewModel.onDiscardMessage(it.id) },
            onPhotoClick = { viewModel.onPhotoClicked(it) },
            onMessageLongClick = ::showMessageActions,
        )

        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messageList.layoutManager = layoutManager
        binding.messageList.adapter = adapter

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (dy < 0 && firstVisible in 0..2) {
                    viewModel.loadOlder()
                }
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (adapter.itemCount > 0 && lastVisible >= adapter.itemCount - 1) {
                    viewModel.onLatestMessageVisible()
                }
            }
        })

        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.moreButton.setOnClickListener { showConversationMenu(it) }
        binding.viewVenueButton.setOnClickListener { viewModel.onVenueClicked() }
        binding.messageInput.doAfterTextChanged { viewModel.onComposerChanged(it?.toString().orEmpty()) }
        binding.sendButton.setOnClickListener { viewModel.onSendClicked() }
        binding.removePendingImageButton.setOnClickListener { viewModel.onPendingImageCleared() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handle) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenVisible()
    }

    override fun onStop() {
        viewModel.onScreenHidden()
        super.onStop()
    }

    override fun onDestroyView() {
        binding.messageList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: ChatUiState) {
        renderHeader(state.header)
        binding.offlineBanner.isVisible = state.isOffline
        binding.loadOlderProgress.isVisible = state.loadingOlder

        val coreState: UiState<Unit> = when (val m = state.messages) {
            UiState.Loading -> UiState.Loading
            is UiState.Content -> UiState.Content(Unit)
            UiState.Empty -> UiState.Empty
            is UiState.Error -> UiState.Error(
                message = ChatUiText.error(requireContext(), chatErrorOf(m.message)),
                retry = m.retry,
            )
        }
        binding.stateViews.render(
            coreState,
            binding.messagesContainer,
            EmptyStateSpec(message = R.string.chat_empty_messages, icon = R.drawable.ic_chat_24),
        )

        adapter.isGroupChat = state.header?.type == ChatType.GROUP
        adapter.imageUrls = state.imageUrls

        val items = (state.messages as? UiState.Content)?.data.orEmpty()
        val shouldScrollToEnd = items.size > lastMessageCount
        lastMessageCount = items.size
        adapter.submitList(items) {
            val b = _binding ?: return@submitList
            if (items.isNotEmpty() && shouldScrollToEnd) {
                b.messageList.scrollToPosition(items.lastIndex)
                viewModel.onLatestMessageVisible()
            } else if (items.isNotEmpty()) {
                val lm = b.messageList.layoutManager as? LinearLayoutManager
                if (lm != null && lm.findLastVisibleItemPosition() >= items.lastIndex - 1) {
                    viewModel.onLatestMessageVisible()
                }
            }
        }

        renderComposer(state)
    }

    private fun renderHeader(header: ChatHeaderUi?) {
        if (header == null) return
        val ctx = requireContext()
        val displayTitle = when {
            header.type == ChatType.GROUP -> header.title.ifBlank { ctx.getString(R.string.chat_default_group_name) }
            header.isDeletedOtherParty -> ctx.getString(R.string.chat_deleted_user)
            else -> header.title.ifBlank { ctx.getString(R.string.chat_default_user_name) }
        }
        binding.headerTitle.text = displayTitle
        binding.headerAvatarInitials.text = if (header.isDeletedOtherParty) "?" else initialsOf(displayTitle)
        val avatarUrl = if (header.isDeletedOtherParty) null else ChatUiText.publicImageUrl(header.avatarBucket, header.avatarPath)
        binding.headerAvatar.contentDescription = ctx.getString(R.string.chat_cd_avatar, displayTitle)
        binding.headerAvatar.isVisible = avatarUrl != null
        if (avatarUrl != null) {
            binding.headerAvatar.load(avatarUrl) {
                error(R.drawable.ic_account_circle_24)
            }
        }

        val subtitle = when {
            header.type == ChatType.GROUP && (header.groupMemberCount ?: 0) > 0 ->
                resources.getQuantityString(R.plurals.chat_group_member_count, header.groupMemberCount!!, header.groupMemberCount)
            header.type == ChatType.DIRECT && header.isOwnerView -> ctx.getString(R.string.chat_role_customer_subtitle)
            header.type == ChatType.DIRECT -> ctx.getString(R.string.chat_role_owner_subtitle)
            else -> null
        }
        binding.headerSubtitle.isVisible = !subtitle.isNullOrBlank()
        binding.headerSubtitle.text = subtitle

        val venue = if (header.type == ChatType.DIRECT) header.venue else null
        binding.venueBanner.isVisible = venue != null
        if (venue != null) {
            val venueDisplayName = venue.name.ifBlank { ctx.getString(R.string.chat_default_venue_name) }
            binding.venueLabel.setText(
                if (header.isOwnerView) R.string.chat_customer_asking_about else R.string.chat_asking_about,
            )
            binding.venueName.text = venueDisplayName
            binding.venuePhoto.contentDescription = ctx.getString(R.string.chat_cd_venue_photo, venueDisplayName)
            val venueUrl = ChatUiText.publicImageUrl("venue-photos", venue.photoPath)
            if (venueUrl != null) {
                binding.venuePhoto.load(venueUrl) {
                    placeholder(R.drawable.bg_venue_placeholder)
                    error(R.drawable.bg_venue_placeholder)
                }
            } else {
                binding.venuePhoto.setImageResource(R.drawable.bg_venue_placeholder)
            }
            binding.viewVenueButton.isVisible = !header.isOwnerView
        }

        binding.messageInput.setHint(
            when {
                header.type == ChatType.GROUP -> R.string.chat_input_hint_group
                header.isOwnerView -> R.string.chat_input_hint_customer
                else -> R.string.chat_input_hint_owner
            },
        )
    }

    private fun renderComposer(state: ChatUiState) {
        val disabled = state.disabledReason
        binding.composerContainer.isVisible = disabled == null
        binding.disabledBanner.isVisible = disabled != null
        if (disabled != null) {
            binding.disabledBanner.setText(
                when (disabled) {
                    ComposerDisabledReason.READ_ONLY -> R.string.chat_read_only
                    ComposerDisabledReason.GROUP_LOCKED -> R.string.chat_group_locked
                    ComposerDisabledReason.BLOCKED -> R.string.chat_blocked_composer
                },
            )
            return
        }

        if (binding.messageInput.text?.toString() != state.composerText) {
            binding.messageInput.setText(state.composerText)
            binding.messageInput.setSelection(state.composerText.length)
        }
        binding.pendingImageContainer.isVisible = state.pendingImageUri != null
        if (state.pendingImageUri != null) {
            binding.pendingImagePreview.load(state.pendingImageUri)
        }
        binding.charCounter.isVisible = state.showCounter
        if (state.showCounter) {
            binding.charCounter.text = getString(
                R.string.chat_input_counter,
                state.rawCharCount,
                ChatMessageRules.MAX_TEXT_LENGTH,
            )
            val colorAttr = if (state.isOverLimit) androidx.appcompat.R.attr.colorError else com.google.android.material.R.attr.colorOnSurfaceVariant
            val typed = android.util.TypedValue()
            requireContext().theme.resolveAttribute(colorAttr, typed, true)
            binding.charCounter.setTextColor(typed.data)
        }
        binding.sendButton.isEnabled = state.canSubmit
        binding.sendButton.alpha = if (state.canSubmit) 1f else 0.4f
    }

    private fun showConversationMenu(anchor: View) {
        val header = viewModel.uiState.value.header ?: return
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(
            0,
            MENU_MUTE,
            0,
            if (header.muted) R.string.chat_menu_unmute else R.string.chat_menu_mute,
        )
        if (header.type == ChatType.DIRECT) {
            popup.menu.add(
                0,
                MENU_BLOCK,
                1,
                if (header.blockedOther) R.string.chat_menu_unblock else R.string.chat_menu_block,
            )
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_MUTE -> {
                    viewModel.onToggleMuted()
                    true
                }
                MENU_BLOCK -> {
                    confirmToggleBlock(header.blockedOther)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmToggleBlock(currentlyBlocked: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (currentlyBlocked) R.string.chat_confirm_unblock_title else R.string.chat_confirm_block_title)
            .setMessage(if (currentlyBlocked) R.string.chat_confirm_unblock_message else R.string.chat_confirm_block_message)
            .setNegativeButton(R.string.chat_action_cancel, null)
            .setPositiveButton(if (currentlyBlocked) R.string.chat_menu_unblock else R.string.chat_menu_block) { _, _ ->
                viewModel.onToggleBlockedConfirmed()
            }
            .show()
    }

    private fun showMessageActions(message: ChatMessage) {
        val actions = viewModel.availableActionsFor(message)
        if (actions.isEmpty()) return
        val labels = actions.map { action ->
            when (action) {
                MessageMenuAction.COPY -> getString(R.string.chat_action_copy)
                MessageMenuAction.RECALL -> getString(R.string.chat_menu_recall)
                MessageMenuAction.REPORT -> getString(R.string.chat_menu_report)
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setItems(labels) { _, which ->
                when (actions[which]) {
                    MessageMenuAction.COPY -> copyMessage(message.text)
                    MessageMenuAction.RECALL -> confirmRecall(message.id)
                    MessageMenuAction.REPORT -> viewModel.onReportMessageSelected(message.id)
                }
            }
            .show()
    }

    private fun copyMessage(text: String) {
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.chat_title_list), text))
        Snackbar.make(binding.root, R.string.chat_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmRecall(messageId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_confirm_recall_title)
            .setMessage(R.string.chat_confirm_recall_message)
            .setNegativeButton(R.string.chat_action_cancel, null)
            .setPositiveButton(R.string.chat_menu_recall) { _, _ ->
                viewModel.onRecallConfirmed(messageId)
            }
            .show()
    }

    private fun handle(event: ChatEvent) {
        when (event) {
            is ChatEvent.RequireLogin -> {
                authNavigator.openLogin(findNavController(), returnTo = Uri.parse(event.returnTo))
            }
            ChatEvent.KickedOut -> {
                Snackbar.make(binding.root, R.string.chat_removed_notice, Snackbar.LENGTH_LONG).show()
                findNavController().navigateUp()
            }
            is ChatEvent.ShowError -> {
                Snackbar.make(binding.root, ChatUiText.error(requireContext(), event.error), Snackbar.LENGTH_LONG).show()
            }
            is ChatEvent.ShowMessage -> {
                Snackbar.make(binding.root, event.resId, Snackbar.LENGTH_SHORT).show()
            }
            is ChatEvent.OpenPhoto -> {
                findNavController().navigate(
                    R.id.action_chat_to_photoViewer,
                    bundleOf("photoUrls" to arrayOf(event.url), "startIndex" to 0),
                )
            }
            is ChatEvent.OpenVenue -> {
                runCatching {
                    findNavController().navigate(Uri.parse("smashnow://venue/${Uri.encode(event.venueId)}"))
                }
            }
            is ChatEvent.OpenReport -> {
                findNavController().navigate(
                    R.id.action_chat_to_reportMessage,
                    bundleOf("chatId" to event.chatId, "messageId" to event.messageId),
                )
            }
        }
    }

    private companion object {
        const val MENU_MUTE = 1
        const val MENU_BLOCK = 2
    }
}
