package com.example.booking_app.feature.account.ui.account

import android.os.Bundle
import android.view.View
import android.content.res.ColorStateList
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.core.ui.VerifyEmailBannerEvent
import com.example.booking_app.core.ui.VerifyEmailBannerViewModel
import com.example.booking_app.databinding.FragmentAccountBinding
import com.example.booking_app.feature.account.domain.model.OwnerSection
import com.google.android.material.color.MaterialColors
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Trang Tài khoản (spec 011, wireframe `ui/01_account_buoc1_tai-khoan.png`). */
@AndroidEntryPoint
class AccountFragment : Fragment(R.layout.fragment_account) {

    private val viewModel: AccountViewModel by viewModels()
    private val bannerViewModel: VerifyEmailBannerViewModel by viewModels()

    /** Đang đặt toggle theo state: bỏ qua sự kiện checked để không gọi lại `onModeSelected`. */
    private var renderingMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAccountBinding.bind(view)
        val graph = findNavController().graph
        viewModel.setAvailableDeepLinks(
            (AccountEntry.entries.map { it.deepLink } + OwnerLink.entries.map { it.deepLink })
                .filterTo(HashSet()) { graph.hasDeepLink(it.toUri()) },
        )

        binding.retryButton.setOnClickListener { viewModel.retry() }
        val openEdit = View.OnClickListener { findNavController().navigate(R.id.action_account_to_editProfile) }
        binding.editProfileButton.setOnClickListener(openEdit)
        binding.avatarFrame.setOnClickListener(openEdit)
        binding.verifyEmailBanner.onResendClick = bannerViewModel::onResend
        binding.signOutButton.setOnClickListener { viewModel.onSignOutClick() }
        entryViews(binding).forEach { (entry, row) -> row.setOnClickListener { viewModel.onEntryClick(entry) } }
        binding.ownerActionButton.setOnClickListener { viewModel.onOwnerActionClick() }
        ownerLinkViews(binding).forEach { (link, row) -> row.setOnClickListener { viewModel.onOwnerLinkClick(link) } }
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || renderingMode) return@addOnButtonCheckedListener
            viewModel.onModeSelected(if (checkedId == R.id.modeOwnerButton) AppMode.OWNER else AppMode.PLAYER)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.uiState, bannerViewModel.uiState, ::Pair).collect { (state, banner) ->
                        render(binding, state)
                        val showBanner = (state as? AccountUiState.Content)?.showVerifyBanner == true
                        binding.verifyEmailBanner.render(banner.copy(visible = banner.visible && showBanner))
                    }
                }
                launch { viewModel.events.collect { handle(binding, it) } }
                launch {
                    viewModel.isSwitchingMode.collect { busy ->
                        binding.modePlayerButton.isEnabled = !busy
                        binding.modeOwnerButton.isEnabled = !busy
                    }
                }
                launch {
                    viewModel.isSigningOut.collect { busy ->
                        binding.signOutButton.isEnabled = !busy
                        binding.signOutButton.text = if (busy) "" else getString(R.string.account_sign_out)
                        binding.signOutButton.icon = if (busy) null else
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_logout_24)
                        binding.signOutProgress.isVisible = busy
                    }
                }
                launch {
                    bannerViewModel.events.collect { event ->
                        val message = when (event) {
                            VerifyEmailBannerEvent.Sent -> R.string.auth_verify_banner_sent
                            VerifyEmailBannerEvent.SendFailed -> R.string.account_error_generic
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentAccountBinding, state: AccountUiState) {
        binding.loading.isVisible = state is AccountUiState.Loading
        binding.error.isVisible = state is AccountUiState.Error
        binding.content.isVisible = state is AccountUiState.Content
        if (state !is AccountUiState.Content) return

        val profile = state.profile
        binding.displayName.text = profile.displayName
        binding.contact.text = profile.email ?: profile.phone
        binding.contact.isVisible = binding.contact.text.isNotEmpty()
        binding.avatarInitials.text = initialsOf(profile.displayName)
        val avatarPath = profile.avatarPath
        binding.avatarImage.isVisible = avatarPath != null
        if (avatarPath != null) binding.avatarImage.load(StorageUrls.publicUrl(AVATAR_BUCKET, avatarPath))
        binding.avatarFrame.contentDescription = getString(R.string.account_avatar_description, profile.displayName)
        renderChips(binding, state)
        binding.walletBalance.text = getString(R.string.account_wallet_balance, state.walletBalance)

        entryViews(binding).forEach { (entry, row) -> row.isVisible = entry in state.entries }
        binding.activityGroup.isVisible = ACTIVITY_ENTRIES.any { it in state.entries }
        binding.settingsGroup.isVisible = SETTINGS_ENTRIES.any { it in state.entries }
        binding.walletTopupButton.isVisible = AccountEntry.WALLET_TOPUP in state.entries
        binding.deleteAccountButton.isVisible = AccountEntry.DELETE_ACCOUNT in state.entries
        renderOwnerSection(binding, state)
        renderMode(binding, state)
    }

    /** Mục chủ sân theo R6 (wireframe `buoc1_tai-khoan-bi-thu-hoi-quyen`). Nút chưa có destination thì ẩn (R14). */
    private fun renderOwnerSection(binding: FragmentAccountBinding, state: AccountUiState.Content) {
        val section = state.ownerSection
        binding.ownerCard.isVisible = section != OwnerSection.Hidden && section != OwnerSection.Approved
        val action: Pair<Int, OwnerLink>? = when (section) {
            OwnerSection.Hidden, OwnerSection.Approved -> return
            OwnerSection.CanApply -> {
                ownerTitle(binding, R.string.account_owner_apply_title, R.drawable.ic_store_24, error = false)
                binding.ownerBody.text = getString(R.string.account_owner_apply_body)
                R.string.account_owner_apply_title to OwnerLink.APPLY_NEW
            }
            is OwnerSection.Pending -> {
                ownerTitle(binding, R.string.account_owner_pending_title, R.drawable.ic_timer_24, error = false)
                binding.ownerBody.text = section.submittedAt
                    ?.let { getString(R.string.account_owner_pending_body, formatSubmittedAt(it)) }
                    ?: getString(R.string.account_owner_pending_no_date)
                null
            }
            is OwnerSection.Rejected -> {
                ownerTitle(binding, R.string.account_owner_rejected_title, R.drawable.ic_error_24, error = true)
                binding.ownerBody.text = section.reason?.let { getString(R.string.account_owner_rejected_reason, it) }
                    ?: getString(R.string.account_owner_rejected_no_reason)
                R.string.account_owner_resubmit to OwnerLink.RESUBMIT
            }
            is OwnerSection.Revoked -> {
                ownerTitle(binding, R.string.account_owner_revoked, R.drawable.ic_error_24, error = true)
                binding.ownerBody.text = getString(R.string.account_owner_revoked_reason, section.reason)
                R.string.account_owner_reapply to OwnerLink.APPLY_NEW
            }
        }
        binding.ownerActionButton.isVisible = action != null && action.second in state.ownerLinks
        action?.let { binding.ownerActionButton.setText(it.first) }
    }

    private fun ownerTitle(binding: FragmentAccountBinding, @StringRes text: Int, @DrawableRes icon: Int, error: Boolean) {
        binding.ownerTitle.setText(text)
        binding.ownerTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        val attr = if (error) androidx.appcompat.R.attr.colorError else androidx.appcompat.R.attr.colorPrimary
        val color = MaterialColors.getColor(binding.ownerTitle, attr)
        TextViewCompat.setCompoundDrawableTintList(binding.ownerTitle, ColorStateList.valueOf(color))
    }

    /** Khối chuyển chế độ và nhóm "CHỦ SÂN" chỉ hiện với `OWNER` (wireframe `buoc3`, US4-3). */
    private fun renderMode(binding: FragmentAccountBinding, state: AccountUiState.Content) {
        val mode = state.mode
        binding.modeCard.isVisible = mode != null
        if (mode != null) {
            renderingMode = true
            binding.modeToggle.check(if (mode == AppMode.OWNER) R.id.modeOwnerButton else R.id.modePlayerButton)
            renderingMode = false
        }
        val ownerRows = ownerLinkViews(binding)
        ownerRows.forEach { (link, row) -> row.isVisible = mode != null && link in state.ownerLinks }
        binding.ownerGroup.isVisible = ownerRows.any { it.second.isVisible }
        binding.ownerGroupLabel.isVisible = binding.ownerGroup.isVisible
        binding.playerGroupLabel.isVisible = binding.ownerGroup.isVisible && binding.activityGroup.isVisible
    }

    private fun ownerLinkViews(binding: FragmentAccountBinding): List<Pair<OwnerLink, View>> = listOf(
        OwnerLink.VENUES to binding.entryOwnerVenues,
        OwnerLink.CHAT to binding.entryOwnerChat,
        OwnerLink.APPLICATION to binding.entryOwnerApplication,
    )

    /** Chip trình độ (nền court) và một chip gộp các khu vực "Quận 10 · Quận 3" như wireframe buoc1. */
    private fun renderChips(binding: FragmentAccountBinding, state: AccountUiState.Content) {
        binding.profileChips.removeAllViews()
        if (state.ownerSection == OwnerSection.Approved) {
            binding.profileChips.addView(chip(getString(R.string.account_owner_approved), highlighted = true))
        }
        state.level?.let { level ->
            binding.profileChips.addView(chip(getString(levelLabel(level)), highlighted = true))
        }
        if (state.districtNames.isNotEmpty()) {
            val text = state.districtNames.joinToString(getString(R.string.account_district_separator))
            binding.profileChips.addView(chip(text, highlighted = false))
        }
        binding.profileChips.isVisible = binding.profileChips.isNotEmpty()
    }

    private fun chip(text: String, highlighted: Boolean): Chip =
        Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            this.text = text
            isClickable = false
            isFocusable = false
            setEnsureMinTouchTargetSize(false)
            chipStrokeWidth = 0f
            val background = if (highlighted) R.color.sn_court_container else R.color.sn_surface_sunken
            val foreground = if (highlighted) R.color.sn_on_court_container else R.color.sn_ink_muted
            setChipBackgroundColorResource(background)
            setTextColor(ContextCompat.getColor(context, foreground))
        }

    override fun onResume() {
        super.onResume()
        // Người dùng có thể vừa bấm link xác minh trong email rồi quay lại app (010 research R9).
        bannerViewModel.onResume()
    }

    private fun handle(binding: FragmentAccountBinding, event: AccountEvent) {
        when (event) {
            is AccountEvent.Open -> open(event.entry)
            is AccountEvent.OpenLink -> findNavController().navigate(event.deepLink.toUri())
            is AccountEvent.OwnerRevoked -> {
                // Toggle đã bị bấm sang "Quản lý sân" nhưng `mode` không đổi nên state không phát lại: đặt lại tay.
                (viewModel.uiState.value as? AccountUiState.Content)?.let { renderMode(binding, it) }
                val message = getString(R.string.account_owner_revoked_snackbar, event.reason.orEmpty()).trim()
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
            AccountEvent.ModeSwitchFailed -> {
                (viewModel.uiState.value as? AccountUiState.Content)?.let { renderMode(binding, it) }
                Snackbar.make(binding.root, R.string.account_mode_switch_failed, Snackbar.LENGTH_SHORT).show()
            }
            AccountEvent.ConfirmSignOut -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_sign_out_confirm_title)
                .setMessage(R.string.account_sign_out_confirm_message)
                .setPositiveButton(R.string.account_sign_out) { _, _ -> viewModel.signOut() }
                .setNegativeButton(R.string.account_cancel, null)
                .show()
        }
    }

    /** Màn hình trong `nav_account` đi bằng action (graph được include hai lần); tính năng khác đi bằng deep link. */
    private fun open(entry: AccountEntry) {
        val navController = findNavController()
        when (entry) {
            AccountEntry.CHANGE_PASSWORD -> navController.navigate(R.id.action_account_to_changePassword)
            AccountEntry.CHANGE_EMAIL -> navController.navigate(R.id.action_account_to_changeEmail)
            AccountEntry.DELETE_ACCOUNT -> navController.navigate(R.id.action_account_to_deleteAccount)
            else -> navController.navigate(entry.deepLink.toUri())
        }
    }

    private fun entryViews(binding: FragmentAccountBinding): List<Pair<AccountEntry, View>> = listOf(
        AccountEntry.MY_BOOKINGS to binding.entryMyBookings,
        AccountEntry.MY_DROPINS to binding.entryMyDropins,
        AccountEntry.FAVORITES to binding.entryFavorites,
        AccountEntry.NOTIFICATION_SETTINGS to binding.entryNotificationSettings,
        AccountEntry.CHANGE_PASSWORD to binding.entryChangePassword,
        AccountEntry.CHANGE_EMAIL to binding.entryChangeEmail,
        AccountEntry.LEGAL to binding.entryLegal,
        AccountEntry.WALLET_TOPUP to binding.walletTopupButton,
        AccountEntry.DELETE_ACCOUNT to binding.deleteAccountButton,
    )

    private companion object {
        const val AVATAR_BUCKET = "avatars"
        val ACTIVITY_ENTRIES = setOf(AccountEntry.MY_BOOKINGS, AccountEntry.MY_DROPINS, AccountEntry.FAVORITES)
        val SETTINGS_ENTRIES = setOf(
            AccountEntry.NOTIFICATION_SETTINGS, AccountEntry.CHANGE_PASSWORD, AccountEntry.CHANGE_EMAIL,
            AccountEntry.LEGAL,
        )
    }
}
