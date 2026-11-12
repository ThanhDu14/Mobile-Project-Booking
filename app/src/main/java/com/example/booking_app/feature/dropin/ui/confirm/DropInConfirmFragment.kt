package com.example.booking_app.feature.dropin.ui.confirm

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentDropinConfirmBinding
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Xác nhận đăng ký (US2, T038; wireframe `08_drop-in_buoc3`). Hiển thị theo [DropInConfirmViewModel]:
 * chọn số người, cách thanh toán, cảnh báo chỉ nhắc (FR-012). Nút "Xác nhận" khóa khi đang gửi; thành công thì mở vé
 * và gỡ màn hình này khỏi back stack (popUpTo trong `nav_dropin.xml`, FR-025).
 */
@AndroidEntryPoint
class DropInConfirmFragment : Fragment(R.layout.fragment_dropin_confirm) {

    private val viewModel: DropInConfirmViewModel by viewModels()

    @Inject lateinit var authNavigator: AuthNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinConfirmBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.partyDecrease.setOnClickListener { viewModel.decrement() }
        binding.partyIncrease.setOnClickListener { viewModel.increment() }
        binding.payMockRow.setOnClickListener { viewModel.setPaymentMethod(PaymentMethod.MOCK_PAY) }
        binding.payAtVenueRow.setOnClickListener { viewModel.setPaymentMethod(PaymentMethod.AT_VENUE) }
        binding.submitButton.setOnClickListener { viewModel.submit() }
        markAsRadio(binding.payMockRow) { binding.payMockRadio.isChecked }
        markAsRadio(binding.payAtVenueRow) { binding.payAtVenueRadio.isChecked }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.events.collect { onEvent(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentDropinConfirmBinding, state: DropInConfirmUiState) {
        val coreState: UiState<Unit> = when (state) {
            DropInConfirmUiState.Loading -> UiState.Loading
            DropInConfirmUiState.NotFound -> UiState.Empty
            is DropInConfirmUiState.LoadError ->
                UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retryLoad)
            is DropInConfirmUiState.Editing,
            is DropInConfirmUiState.Submitting,
            is DropInConfirmUiState.Failed,
            -> UiState.Content(Unit)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(message = R.string.dropin_detail_not_found, icon = R.drawable.ic_error_24),
        )

        val form = when (state) {
            is DropInConfirmUiState.Editing -> state.form
            is DropInConfirmUiState.Submitting -> state.form
            is DropInConfirmUiState.Failed -> state.form
            else -> null
        }
        binding.bottomBar.isVisible = form != null
        binding.bottomDivider.isVisible = form != null
        if (form == null) return

        val submitting = state is DropInConfirmUiState.Submitting
        bindForm(binding, form, editable = !submitting)

        binding.errorText.isVisible = state is DropInConfirmUiState.Failed
        if (state is DropInConfirmUiState.Failed) {
            binding.errorText.text = DropInUiText.error(requireContext(), state.reason)
        }

        binding.submitButton.isEnabled = !submitting && form.canSubmit
        binding.submitButton.setText(if (submitting) R.string.dropin_confirm_submitting else R.string.dropin_confirm_submit)
    }

    private fun bindForm(binding: FragmentDropinConfirmBinding, form: ConfirmForm, editable: Boolean) {
        val context = requireContext()
        val session = form.session

        binding.sessionTitle.text = getString(R.string.dropin_confirm_session_line, session.venueName)
        binding.sessionDateTime.text = DropInUiText.dateTimeLine(context, session.startAt, session.endAt)
        binding.sessionLevelRemaining.text = getString(
            R.string.dropin_confirm_level_remaining,
            getString(DropInUiText.level(session.level)),
            session.remaining,
        )

        binding.partyValue.text = form.partySize.toString()
        binding.partyValue.contentDescription = getString(R.string.dropin_confirm_party_value, form.partySize)
        binding.partyDecrease.isEnabled = editable && form.partySize > DropInConfirmViewModel.MIN_PARTY
        binding.partyIncrease.isEnabled = editable && form.partySize < form.maxParty
        binding.partyLimit.text = if (form.maxParty > 0) {
            getString(R.string.dropin_confirm_party_limit, session.remaining, form.maxParty)
        } else {
            getString(R.string.dropin_confirm_no_seat)
        }

        binding.warningBox.isVisible = form.warnings.isNotEmpty()
        binding.warningText.text = form.warnings.joinToString("\n") { getString(DropInUiText.warning(it)) }

        val mock = form.paymentMethod == PaymentMethod.MOCK_PAY
        binding.payMockRadio.isChecked = mock
        binding.payAtVenueRadio.isChecked = !mock
        binding.payMockRow.isEnabled = editable
        binding.payAtVenueRow.isEnabled = editable

        val total = MoneyFormat.vnd(form.total)
        binding.priceTimes.text = getString(
            R.string.dropin_confirm_price_times,
            MoneyFormat.vnd(session.pricePerPerson),
            form.partySize,
        )
        binding.subtotal.text = total
        binding.total.text = total
        binding.bottomTotal.text = total
        binding.bottomSummary.text = getString(
            if (mock) R.string.dropin_confirm_summary_mock else R.string.dropin_confirm_summary_at_venue,
            form.partySize,
        )
    }

    private fun onEvent(binding: FragmentDropinConfirmBinding, event: DropInConfirmEvent) {
        val nav = findNavController()
        when (event) {
            is DropInConfirmEvent.NavigateToTicket -> {
                if (nav.currentDestination?.id != R.id.dropInConfirmFragment) return
                nav.navigate(
                    DropInConfirmFragmentDirections.actionDropInConfirmToTicket(event.sessionId, true),
                )
            }
            DropInConfirmEvent.NetworkError ->
                Snackbar.make(binding.root, R.string.dropin_error_network, Snackbar.LENGTH_LONG)
                    .setAction(R.string.dropin_retry) { viewModel.submit() }
                    .show()
            is DropInConfirmEvent.RequireLogin -> {
                Snackbar.make(binding.root, R.string.dropin_error_unauthenticated, Snackbar.LENGTH_SHORT).show()
                // Đăng nhập xong quay lại đúng bước xác nhận (deep link khai báo trong nav_dropin.xml).
                authNavigator.openLogin(nav, Uri.parse("smashnow://dropin/confirm/${event.sessionId}"))
            }
        }
    }

    /** Hàng chọn thanh toán đọc như một nút radio cho TalkBack (RadioButton bên trong không nhận focus). */
    private fun markAsRadio(row: View, isChecked: () -> Boolean) {
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = isChecked()
            }
        })
    }
}
