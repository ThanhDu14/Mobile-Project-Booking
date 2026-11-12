package com.example.booking_app.feature.dropin.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentDropinDetailBinding
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Chi tiết buổi vãng lai (US1 T029, US2 T041; contracts/ui-screens.md mục 1–2).
 *
 * - Số chỗ cập nhật trực tiếp qua `uiState` (listener gỡ khi Fragment dừng, FR-005).
 * - Không có phần "Người tham gia" (FR-021); `waitlist_container` để trống cho spec 081.
 * - "Đăng ký": đã đăng nhập thì sang bước xác nhận; khách thì hỏi đăng nhập rồi mở `nav_auth` kèm
 *   `returnTo = smashnow://dropin/confirm/{sessionId}`. Đăng nhập xong `MainActivity` đóng `nav_auth` (chi tiết buổi
 *   còn nguyên bên dưới) rồi mở deep link đó, nên người chơi vào thẳng bước xác nhận, Back về lại buổi.
 */
@AndroidEntryPoint
class DropInDetailFragment : Fragment(R.layout.fragment_dropin_detail) {

    private val viewModel: DropInDetailViewModel by viewModels()

    @Inject lateinit var authNavigator: AuthNavigator

    private var loginPrompt: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinDetailBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.registerButton.setOnClickListener { viewModel.onRegisterClicked() }
        binding.viewTicketButton.setOnClickListener { viewModel.onViewTicketClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is DropInDetailEvent.OpenConfirm -> navigateSafely(
                                DropInDetailFragmentDirections.actionDropInDetailToConfirm(event.sessionId),
                            )
                            is DropInDetailEvent.OpenTicket -> navigateSafely(
                                DropInDetailFragmentDirections.actionDropInDetailToTicket(event.sessionId),
                            )
                            is DropInDetailEvent.RequireLogin -> showLoginPrompt(event.sessionId)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        loginPrompt?.dismiss()
        loginPrompt = null
        super.onDestroyView()
    }

    private fun render(binding: FragmentDropinDetailBinding, state: DropInDetailUiState) {
        val coreState: UiState<Unit> = when (state) {
            DropInDetailUiState.Loading -> UiState.Loading
            is DropInDetailUiState.Content -> UiState.Content(Unit)
            DropInDetailUiState.NotFound -> UiState.Empty
            is DropInDetailUiState.Error ->
                UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(message = R.string.dropin_detail_not_found, icon = R.drawable.ic_event_24),
        )
        binding.bottomBar.isVisible = state is DropInDetailUiState.Content
        if (state is DropInDetailUiState.Content) {
            renderSession(binding, state.session)
            renderAction(binding, state.action)
        }
    }

    private fun renderSession(binding: FragmentDropinDetailBinding, session: DropInSession) {
        binding.levelChip.setText(DropInUiText.level(session.level))
        val statusLabel = when {
            session.status == SessionStatus.CANCELLED -> R.string.dropin_label_session_cancelled
            !session.venueActive -> R.string.dropin_label_venue_inactive
            else -> null
        }
        binding.statusChip.isVisible = statusLabel != null
        statusLabel?.let { binding.statusChip.setText(it) }

        binding.venueName.text = session.venueName
        binding.dateTime.text = DropInUiText.dateTimeLine(requireContext(), session.startAt, session.endAt)

        // Số chỗ realtime (FR-005).
        val remaining = session.remaining
        binding.remaining.text = resources.getQuantityString(R.plurals.dropin_detail_remaining, remaining, remaining)
        binding.capacity.text = getString(R.string.dropin_detail_capacity, session.capacity)
        val capacity = session.capacity.coerceAtLeast(1)
        binding.seatsProgress.max = capacity
        binding.seatsProgress.setProgressCompat(session.registeredCount.coerceIn(0, capacity), true)

        binding.courts.text = if (session.courtNames.isNotEmpty()) {
            session.courtNames.joinToString(", ")
        } else {
            getString(R.string.dropin_detail_courts_unknown, session.courtIds.size)
        }

        binding.address.text = session.venueAddress
        binding.directionsButton.setOnClickListener { openDirections(binding, session.venueAddress) }

        val note = session.note?.takeIf { it.isNotBlank() }
        binding.noteRow.isVisible = note != null
        binding.noteDivider.isVisible = note != null
        binding.note.text = note.orEmpty()

        binding.price.text = getString(R.string.dropin_price_per_turn, MoneyFormat.vnd(session.pricePerPerson))
    }

    private fun renderAction(binding: FragmentDropinDetailBinding, action: DetailAction) {
        binding.registerButton.isVisible = action == DetailAction.REGISTER
        binding.viewTicketButton.isVisible = action == DetailAction.VIEW_TICKET
        binding.fullChip.isVisible = action == DetailAction.FULL
        val status = when (action) {
            DetailAction.CLOSED -> R.string.dropin_detail_closed
            DetailAction.OWN_SESSION -> R.string.dropin_detail_own_session
            else -> null
        }
        binding.actionStatus.isVisible = status != null
        status?.let { binding.actionStatus.setText(it) }
        // "Đi tối đa 4 người mỗi lượt" chỉ có nghĩa khi còn đăng ký được.
        binding.maxPartyHint.isVisible = action == DetailAction.REGISTER
    }

    /** Khách bấm "Đăng ký" (T041): hỏi trước, đồng ý thì mở đăng nhập (spec 010) kèm `returnTo`. */
    private fun showLoginPrompt(sessionId: String) {
        loginPrompt?.dismiss()
        loginPrompt = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dropin_detail_login_title)
            .setMessage(R.string.dropin_detail_login_message)
            .setNegativeButton(R.string.dropin_detail_later, null)
            .setPositiveButton(R.string.dropin_detail_login) { _, _ -> openLogin(sessionId) }
            .show()
    }

    private fun openLogin(sessionId: String) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.dropInDetailFragment) return
        authNavigator.openLogin(nav, returnTo = confirmDeepLink(sessionId))
    }

    /** Bấm liên tiếp không mở chồng hai màn hình. */
    private fun navigateSafely(directions: NavDirections) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.dropInDetailFragment) return
        nav.navigate(directions)
    }

    private fun openDirections(binding: FragmentDropinDetailBinding, address: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.dropin_detail_no_map_app, Snackbar.LENGTH_SHORT).show()
        }
    }

    private companion object {
        /** Deep link của `dropInConfirmFragment` trong `nav_dropin.xml`. */
        fun confirmDeepLink(sessionId: String): Uri = Uri.Builder()
            .scheme("smashnow")
            .authority("dropin")
            .appendPath("confirm")
            .appendPath(sessionId)
            .build()
    }
}
