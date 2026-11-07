package com.example.booking_app.feature.account.ui.delete

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAccountDeleteBinding
import com.example.booking_app.feature.account.domain.model.DeletionImpact
import com.example.booking_app.feature.account.domain.model.ImpactBooking
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import com.example.booking_app.feature.account.ui.AccountResults
import com.example.booking_app.feature.account.ui.account.formatSlotTime
import com.example.booking_app.feature.account.ui.account.formatVnd
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Xóa tài khoản (US6, FR-018–FR-022, wireframe buoc5). Thành công thì phiên đăng xuất với lý do `ACCOUNT_DELETED`,
 * `MainActivity` tự về trang chủ khách, nên màn hình này không tự điều hướng.
 */
@AndroidEntryPoint
class DeleteAccountFragment : Fragment(R.layout.fragment_account_delete) {

    private val viewModel: DeleteAccountViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAccountDeleteBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.keepButton.setOnClickListener { findNavController().popBackStack() }
        binding.retryButton.setOnClickListener { viewModel.onRetryLoad() }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.confirmInput.doAfterTextChanged { viewModel.onConfirmTextChanged(it?.toString().orEmpty()) }
        binding.ackCheck.setOnCheckedChangeListener { _, checked -> viewModel.onAcknowledgedChanged(checked) }
        binding.reauthButton.setOnClickListener { openReauth() }
        binding.deleteButton.setOnClickListener { viewModel.onDelete() }

        parentFragmentManager.setFragmentResultListener(AccountResults.REAUTH, viewLifecycleOwner) { _, result ->
            if (result.getBoolean(AccountResults.REAUTH_DONE)) viewModel.onReauthDone()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentAccountDeleteBinding, state: DeleteAccountUiState) {
        binding.loading.isVisible = state.impact is ImpactState.Loading
        binding.errorGroup.isVisible = state.impact is ImpactState.Error
        binding.content.isVisible = state.impact is ImpactState.Content
        binding.actions.isVisible = state.impact is ImpactState.Content
        when (val impact = state.impact) {
            is ImpactState.Error -> {
                binding.errorText.setText(impact.message)
                binding.retryButton.isVisible = impact.retryable
            }
            is ImpactState.Content -> renderImpact(binding, impact.impact)
            ImpactState.Loading -> Unit
        }

        renderIdentity(binding, state)
        binding.passwordInput.syncText(state.password)
        binding.passwordLayout.error = state.passwordError?.let(::getString)
        binding.confirmLayout.error = state.confirmError?.let(::getString)
        if (binding.ackCheck.isChecked != state.acknowledged) binding.ackCheck.isChecked = state.acknowledged

        val inputsEnabled = !state.isDeleting
        listOf(binding.passwordInput, binding.confirmInput, binding.ackCheck, binding.reauthButton, binding.keepButton)
            .forEach { it.isEnabled = inputsEnabled }
        binding.deleteButton.isEnabled = state.canDelete
        binding.deleteButton.text = if (state.isDeleting) "" else getString(R.string.account_delete_submit)
        binding.deleteProgress.isVisible = state.isDeleting
    }

    private fun renderIdentity(binding: FragmentAccountDeleteBinding, state: DeleteAccountUiState) {
        val method = (state.reauth as? ReauthState.Required)?.method
        val usePassword = method == ReauthMethod.Password
        binding.identityLabel.setText(
            if (usePassword) R.string.account_delete_password_label else R.string.account_delete_identity_label,
        )
        binding.passwordLayout.isVisible = usePassword
        binding.identityDone.isVisible = state.reauth == ReauthState.Done
        binding.reauthButton.isVisible = method is ReauthMethod.Google || method is ReauthMethod.Phone
        binding.reauthButton.setText(
            if (method is ReauthMethod.Phone) R.string.account_reauth_send_otp else R.string.account_reauth_google,
        )
    }

    private fun renderImpact(binding: FragmentAccountDeleteBinding, impact: DeletionImpact) {
        val res = resources
        val bookingCount = impact.myBookings.size
        binding.impactTitle.text = if (bookingCount + impact.myDropIns.size + impact.waitlistCount == 0) {
            getString(R.string.account_delete_nothing_upcoming)
        } else {
            res.getQuantityString(R.plurals.account_delete_bookings_title, bookingCount, bookingCount)
        }
        binding.impactItems.removeAllViews()
        val lines = impact.myBookings.map(::bookingLine) +
            impact.myDropIns.map {
                getString(
                    R.string.account_delete_dropin,
                    it.venueName,
                    formatSlotTime(it.startAt),
                    formatVnd(it.paidAmount),
                    formatVnd(it.refundAmount),
                )
            } +
            listOfNotNull(
                impact.waitlistCount.takeIf { it > 0 }
                    ?.let { res.getQuantityString(R.plurals.account_delete_waitlist, it, it) },
            )
        lines.forEach { line ->
            val row = layoutInflater.inflate(R.layout.item_account_delete_impact, binding.impactItems, false) as TextView
            row.text = line
            binding.impactItems.addView(row)
        }

        val owner = impact.ownerImpact
        binding.ownerCard.isVisible = owner != null
        if (owner != null) {
            binding.ownerCard.text = getString(
                R.string.account_delete_owner_card,
                owner.venueCount,
                owner.guestBookingCount,
                owner.guestDropInCount,
                owner.guestCount,
            )
        }
        binding.walletNotice.text = getString(R.string.account_delete_wallet_notice, formatVnd(impact.walletBalance))
        binding.ackCheck.text = if (bookingCount == 0) {
            getString(R.string.account_delete_ack)
        } else {
            res.getQuantityString(R.plurals.account_delete_ack_bookings, bookingCount, bookingCount)
        }
    }

    private fun bookingLine(b: ImpactBooking): String {
        val status = getString(
            when (b.status) {
                "HOLD" -> R.string.account_booking_status_hold
                "PENDING" -> R.string.account_booking_status_pending
                else -> R.string.account_booking_status_confirmed
            },
        )
        val time = formatSlotTime(b.startAt)
        return if (b.paidAmount == 0L) {
            getString(R.string.account_delete_booking_unpaid, b.venueName, time, status)
        } else {
            getString(
                R.string.account_delete_booking_paid,
                b.venueName,
                time,
                status,
                formatVnd(b.paidAmount),
                formatVnd(b.refundAmount),
            )
        }
    }

    private fun handle(binding: FragmentAccountDeleteBinding, event: DeleteAccountEvent) {
        when (event) {
            DeleteAccountEvent.OpenReauth -> {
                if ((viewModel.uiState.value.reauth as? ReauthState.Required)?.method == ReauthMethod.Password) {
                    binding.passwordInput.requestFocus()
                } else {
                    openReauth()
                }
            }
            is DeleteAccountEvent.ShowError -> {
                val snackbar = Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                if (event.retryable) snackbar.setAction(R.string.account_retry) { viewModel.onDelete() }
                snackbar.show()
            }
        }
    }

    private fun openReauth() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.deleteAccountFragment) {
            nav.navigate(R.id.action_deleteAccount_to_reauth)
        }
    }

    private fun TextView.syncText(value: String) {
        if (text?.toString() != value) setText(value)
    }
}
