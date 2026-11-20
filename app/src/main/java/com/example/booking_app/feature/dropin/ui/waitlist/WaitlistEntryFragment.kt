package com.example.booking_app.feature.dropin.ui.waitlist

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentDropinWaitlistBinding
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Lượt chờ của tôi (spec 081 US1, US3, US4, T024, T041, T048; wireframe `08_drop-in_buoc1_hang-cho`).
 *
 * - Vị trí N / tổng cập nhật trực tiếp (listener gỡ khi Fragment dừng).
 * - "Rời hàng chờ": hộp thoại xác nhận "Không mất phí"; mất mạng thì mở lại hộp thoại kèm lỗi, bấm lại gửi cùng `requestId`.
 * - Được đẩy lên thì mở vé và bỏ màn hình này khỏi back stack; hàng chờ đóng hoặc đã rời thì hiện kết quả cuối kèm
 *   "Tìm buổi khác".
 */
@AndroidEntryPoint
class WaitlistEntryFragment : Fragment(R.layout.fragment_dropin_waitlist) {

    private val viewModel: WaitlistEntryViewModel by viewModels()

    private var leaveDialog: AlertDialog? = null

    /** Lần lỗi đã hiện trong hộp thoại, để phát lại cùng một trạng thái không mở hộp thoại nhiều lần. */
    private var shownLeaveError: Throwable? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinWaitlistBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.leaveButton.setOnClickListener { showLeaveDialog(error = null) }
        binding.findOtherButton.setOnClickListener { openList() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.events.collect { onEvent(it) } }
            }
        }
    }

    override fun onDestroyView() {
        leaveDialog?.dismiss()
        leaveDialog = null
        super.onDestroyView()
    }

    private fun render(binding: FragmentDropinWaitlistBinding, state: WaitlistEntryUiState) {
        val coreState: UiState<Unit> = when (state) {
            WaitlistEntryUiState.Loading -> UiState.Loading
            WaitlistEntryUiState.Empty -> UiState.Empty
            is WaitlistEntryUiState.Error ->
                UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retry)
            is WaitlistEntryUiState.Content,
            is WaitlistEntryUiState.Leaving,
            WaitlistEntryUiState.Left,
            is WaitlistEntryUiState.Ended,
            -> UiState.Content(Unit)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(
                message = R.string.dropin_waitlist_empty,
                icon = R.drawable.ic_hourglass_24,
                actionText = R.string.dropin_waitlist_find_other,
                onAction = ::openList,
            ),
        )

        val content = when (state) {
            is WaitlistEntryUiState.Content -> state
            is WaitlistEntryUiState.Leaving -> state.content
            else -> null
        }
        val ended = state is WaitlistEntryUiState.Left || state is WaitlistEntryUiState.Ended
        binding.waitingGroup.isVisible = content != null
        binding.endedGroup.isVisible = ended
        binding.bottomBar.isVisible = content != null
        binding.bottomDivider.isVisible = content != null

        if (content != null) bindContent(binding, content, leaving = state is WaitlistEntryUiState.Leaving)
        when (state) {
            WaitlistEntryUiState.Left -> bindEnded(binding, WaitlistEndReason.LEFT, left = true)
            is WaitlistEntryUiState.Ended -> bindEnded(binding, state.reason, left = false)
            else -> Unit
        }

        // Lỗi của lần xác nhận rời trước (mất mạng, bận): mở lại hộp thoại kèm lỗi để thử lại cùng requestId.
        val leaveError = (state as? WaitlistEntryUiState.Content)?.leaveError
        if (leaveError == null) {
            shownLeaveError = null
        } else if (leaveError !== shownLeaveError) {
            shownLeaveError = leaveError
            showLeaveDialog(error = leaveError)
        }
        if (state is WaitlistEntryUiState.Left || state is WaitlistEntryUiState.Ended) leaveDialog?.dismiss()
    }

    private fun bindContent(binding: FragmentDropinWaitlistBinding, content: WaitlistEntryUiState.Content, leaving: Boolean) {
        binding.positionValue.text = getString(R.string.dropin_waitlist_entry_position, content.position, content.total)
        binding.positionBox.contentDescription =
            getString(R.string.dropin_waitlist_entry_position_description, content.position, content.total)

        val session = content.session
        binding.venueName.text = session?.let { getString(R.string.dropin_confirm_session_line, it.venueName) }.orEmpty()
        binding.dateTime.text = session
            ?.let { DropInUiText.dateTimeLine(requireContext(), it.startAt, it.endAt) }
            .orEmpty()
        binding.partyValue.text = getString(R.string.dropin_mine_party, content.partySize)
        binding.paymentValue.setText(
            when (content.paymentMethod) {
                PaymentMethod.MOCK_PAY -> R.string.dropin_confirm_pay_mock
                PaymentMethod.AT_VENUE -> R.string.dropin_confirm_pay_at_venue
            },
        )
        binding.amountValue.text = MoneyFormat.vnd(content.amount)

        binding.leaveButton.isEnabled = !leaving
        binding.leaveButton.setText(if (leaving) R.string.dropin_waitlist_leaving else R.string.dropin_waitlist_leave)
    }

    private fun bindEnded(binding: FragmentDropinWaitlistBinding, reason: WaitlistEndReason?, left: Boolean) {
        val title = when (reason) {
            WaitlistEndReason.LEFT -> R.string.dropin_waitlist_status_left
            WaitlistEndReason.AUTO_LEFT_OVERLAP -> R.string.dropin_waitlist_status_left_overlap
            WaitlistEndReason.CLOSED -> R.string.dropin_waitlist_status_not_admitted
            WaitlistEndReason.SESSION_CANCELLED -> R.string.dropin_waitlist_status_session_cancelled
            null -> R.string.dropin_waitlist_status_not_admitted
        }
        val message = when {
            left -> R.string.dropin_waitlist_left_message
            reason == WaitlistEndReason.LEFT -> R.string.dropin_waitlist_ended_left
            reason == WaitlistEndReason.AUTO_LEFT_OVERLAP -> R.string.dropin_waitlist_ended_overlap
            reason == WaitlistEndReason.CLOSED -> R.string.dropin_waitlist_ended_closed
            reason == WaitlistEndReason.SESSION_CANCELLED -> R.string.dropin_waitlist_ended_session_cancelled
            else -> R.string.dropin_waitlist_ended_unknown
        }
        binding.endedTitle.setText(if (left) R.string.dropin_waitlist_left_title else title)
        binding.endedMessage.setText(message)
    }

    private fun showLeaveDialog(error: Throwable?) {
        leaveDialog?.dismiss()
        val message = if (error == null) {
            getString(R.string.dropin_waitlist_leave_dialog_message)
        } else {
            leaveErrorText(error)
        }
        leaveDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dropin_waitlist_leave_dialog_title)
            .setMessage(message)
            .setNegativeButton(R.string.dropin_waitlist_leave_keep, null)
            .setPositiveButton(
                if (error == null) R.string.dropin_waitlist_leave_confirm else R.string.dropin_retry,
            ) { _, _ -> viewModel.confirmLeave() }
            .show()
    }

    private fun leaveErrorText(error: Throwable): String = when (error) {
        is DropInError.Network ->
            getString(R.string.dropin_waitlist_leave_error_network)
        else -> DropInUiText.error(requireContext(), error)
    }

    private fun onEvent(event: WaitlistEntryEvent) {
        when (event) {
            is WaitlistEntryEvent.Promoted -> openTicket(event.sessionId)
            is WaitlistEntryEvent.AlreadyPromoted -> {
                Toast.makeText(requireContext(), R.string.dropin_waitlist_error_already_promoted, Toast.LENGTH_LONG).show()
                openTicket(event.sessionId)
            }
        }
    }

    /** Được nhận vào buổi: sang vé và gỡ màn hình lượt chờ khỏi back stack (AC 2.4). */
    private fun openTicket(sessionId: String) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.waitlistEntryFragment) return
        leaveDialog?.dismiss()
        nav.navigate(WaitlistEntryFragmentDirections.actionWaitlistToTicket(sessionId))
    }

    private fun openList() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.waitlistEntryFragment) return
        nav.navigate(WaitlistEntryFragmentDirections.actionWaitlistToList())
    }
}
