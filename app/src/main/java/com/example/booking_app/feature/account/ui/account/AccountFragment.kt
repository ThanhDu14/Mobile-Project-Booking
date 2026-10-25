package com.example.booking_app.feature.account.ui.account

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.core.ui.VerifyEmailBannerEvent
import com.example.booking_app.core.ui.VerifyEmailBannerViewModel
import com.example.booking_app.databinding.FragmentAccountBinding
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAccountBinding.bind(view)
        val graph = findNavController().graph
        viewModel.setAvailableDeepLinks(
            AccountEntry.entries.map { it.deepLink }.filterTo(HashSet()) { graph.hasDeepLink(it.toUri()) },
        )

        binding.retryButton.setOnClickListener { viewModel.retry() }
        val openEdit = View.OnClickListener { findNavController().navigate(R.id.action_account_to_editProfile) }
        binding.editProfileButton.setOnClickListener(openEdit)
        binding.avatarFrame.setOnClickListener(openEdit)
        binding.verifyEmailBanner.onResendClick = bannerViewModel::onResend
        binding.signOutButton.setOnClickListener { viewModel.onSignOutClick() }
        entryViews(binding).forEach { (entry, row) -> row.setOnClickListener { viewModel.onEntryClick(entry) } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.uiState, bannerViewModel.uiState, ::Pair).collect { (state, banner) ->
                        render(binding, state)
                        val showBanner = (state as? AccountUiState.Content)?.showVerifyBanner == true
                        binding.verifyEmailBanner.render(banner.copy(visible = banner.visible && showBanner))
                    }
                }
                launch { viewModel.events.collect(::handle) }
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
    }

    /** Chip trình độ (nền court) và một chip gộp các khu vực "Quận 10 · Quận 3" như wireframe buoc1. */
    private fun renderChips(binding: FragmentAccountBinding, state: AccountUiState.Content) {
        binding.profileChips.removeAllViews()
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

    private fun handle(event: AccountEvent) {
        when (event) {
            is AccountEvent.Open -> open(event.entry)
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
