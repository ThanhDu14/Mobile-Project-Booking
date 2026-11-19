package com.example.booking_app.feature.chat.ui.conversation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
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
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import com.example.booking_app.feature.chat.domain.chatErrorOf
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatMessageRules
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import com.example.booking_app.feature.chat.ui.report.ReportMessageBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Màn hình cuộc trò chuyện (T029, T034, T040, T046, T052).
 */
@AndroidEntryPoint
class ChatFragment : Fragment(R.layout.fragment_chat) {

    @Inject lateinit var authNavigator: AuthNavigator
    @Inject lateinit var localMediaStore: ChatLocalMediaStore

    private val viewModel: ChatViewModel by viewModels()

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MessageAdapter
    private var lastTailMessageId: String? = null

    /** URI ảnh chụp tạm chờ camera trả về (giữ qua process death, T046). */
    private var pendingCameraUri: Uri? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            localMediaStore.deleteIfOwned(viewModel.uiState.value.pendingImageUri)
            viewModel.onImageSelected(it.toString())
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) {
            val previous = viewModel.uiState.value.pendingImageUri
            if (previous != uri.toString()) {
                localMediaStore.deleteIfOwned(previous)
            }
            viewModel.onImageSelected(uri.toString())
        } else {
            localMediaStore.deleteIfOwned(uri?.toString())
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else viewModel.onCameraPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCameraUri = savedInstanceState?.getString(KEY_CAMERA_URI)?.let(Uri::parse)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraUri?.let { outState.putString(KEY_CAMERA_URI, it.toString()) }
    }

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
        binding.attachImageButton.setOnClickListener { showAttachOptions() }
        binding.sendButton.setOnClickListener { viewModel.onSendClicked() }
        binding.removePendingImageButton.setOnClickListener {
            localMediaStore.deleteIfOwned(viewModel.uiState.value.pendingImageUri)
            viewModel.onPendingImageCleared()
        }

        setFragmentResultListener(ReportMessageBottomSheet.RESULT_KEY_REPORT_SENT) { _, _ ->
            _binding?.let { b ->
                Snackbar.make(b.root, R.string.chat_report_sent, Snackbar.LENGTH_SHORT).show()
            }
        }

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
        val newTailId = items.lastOrNull()?.id
        val shouldScrollToEnd = newTailId != null && (lastTailMessageId == null || newTailId != lastTailMessageId)
        if (newTailId != null) {
            lastTailMessageId = newTailId
        }
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
        binding.headerAvatar.contentDescription = ctx.getString(R.string.chat_cd_avatar, displayTitle)
        if (header.isDeletedOtherParty) {
            binding.headerAvatarInitials.isVisible = false
            binding.headerAvatar.isVisible = true
            binding.headerAvatar.dispose()
            binding.headerAvatar.setImageResource(R.drawable.ic_account_circle_24)
        } else {
            val avatarUrl = ChatUiText.publicImageUrl(header.avatarBucket, header.avatarPath)
            binding.headerAvatarInitials.text = initialsOf(displayTitle).ifEmpty { "?" }
            binding.headerAvatarInitials.isVisible = avatarUrl == null
            binding.headerAvatar.isVisible = avatarUrl != null
            if (avatarUrl != null) {
                binding.headerAvatar.load(avatarUrl) {
                    error(if (header.type == ChatType.GROUP) R.drawable.ic_groups_24 else R.drawable.ic_account_circle_24)
                }
            } else {
                binding.headerAvatar.dispose()
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
        val hideForError = state.header == null && state.messages is UiState.Error
        binding.composerContainer.isVisible = disabled == null && !hideForError
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
        if (hideForError) return

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

    private fun showAttachOptions() {
        val options = arrayOf(
            getString(R.string.chat_attach_camera),
            getString(R.string.chat_attach_gallery),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_attach_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onCameraChosen()
                    1 -> launchGallery()
                }
            }
            .show()
    }

    private fun launchGallery() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun onCameraChosen() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            granted -> launchCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.chat_camera_rationale_title)
                    .setMessage(R.string.chat_camera_rationale_message)
                    .setNegativeButton(R.string.chat_action_cancel, null)
                    .setNeutralButton(R.string.chat_attach_gallery) { _, _ -> launchGallery() }
                    .setPositiveButton(R.string.chat_action_confirm) { _, _ ->
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                    .show()
            }
            else -> requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val ctx = requireContext()
        val dir = File(ctx.cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "chat_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        pendingCameraUri = uri
        runCatching { takePicture.launch(uri) }.onFailure {
            pendingCameraUri = null
            localMediaStore.deleteIfOwned(uri.toString())
            Snackbar.make(binding.root, R.string.chat_camera_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    /** Từ chối quyền camera: hiện giải thích, mở Cài đặt nếu từ chối vĩnh viễn và vẫn cho mở Photo Picker (AC 4.5). */
    private fun showCameraDenied() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_camera_rationale_title)
            .setMessage(R.string.chat_camera_denied_fallback)
            .setNegativeButton(R.string.chat_action_cancel, null)
            .setPositiveButton(R.string.chat_attach_gallery) { _, _ -> launchGallery() }
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            builder.setNeutralButton(R.string.chat_action_open_settings) { _, _ -> openAppSettings() }
        }
        builder.show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null),
        )
        runCatching { startActivity(intent) }
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
                val nav = findNavController()
                if (nav.currentDestination?.id == R.id.chatFragment) {
                    nav.navigate(
                        R.id.action_chat_to_photoViewer,
                        bundleOf("photoUrls" to arrayOf(event.url), "startIndex" to 0),
                    )
                }
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
            ChatEvent.CameraPermissionDenied -> showCameraDenied()
        }
    }

    private companion object {
        const val MENU_MUTE = 1
        const val MENU_BLOCK = 2
        const val KEY_CAMERA_URI = "chat_camera_uri"
        const val CAMERA_DIR = "chat"
    }
}
