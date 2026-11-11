package com.example.booking_app.feature.dropin.ui.ticket

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentDropinTicketBinding
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Vé vãng lai (US2, T040; wireframe `08_drop-in_buoc4`). QR vẽ bằng [QrRenderer] cạnh 240dp trên nền trắng, mã vé
 * chọn để sao chép được, trạng thái thanh toán (AC 2.3, 2.4). Độ sáng màn hình tăng tối đa khi vé hiện và trả lại như
 * cũ khi rời màn hình (contracts/ui-screens.md mục 1).
 */
@AndroidEntryPoint
class DropInTicketFragment : Fragment(R.layout.fragment_dropin_ticket) {

    private val viewModel: DropInTicketViewModel by viewModels()

    /** QR đã vẽ, giữ lại để không vẽ lại mỗi lần realtime đẩy cập nhật. */
    private var qrCache: Pair<String, Bitmap>? = null

    /** Độ sáng của cửa sổ trước khi mở vé; `null` khi chưa ghi đè. */
    private var previousBrightness: Float? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinTicketBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.successBanner.isVisible = viewModel.justRegistered
        binding.copyCodeButton.setOnClickListener { copyTicketCode(binding.ticketCode.text?.toString()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(binding, it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        raiseBrightness()
    }

    override fun onStop() {
        restoreBrightness()
        super.onStop()
    }

    override fun onDestroyView() {
        restoreBrightness()
        super.onDestroyView()
    }

    private fun render(binding: FragmentDropinTicketBinding, state: DropInTicketUiState) {
        val coreState: UiState<Unit> = when (state) {
            DropInTicketUiState.Loading -> UiState.Loading
            DropInTicketUiState.NotFound -> UiState.Empty
            is DropInTicketUiState.Error ->
                UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retry)
            is DropInTicketUiState.Content -> UiState.Content(Unit)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(message = R.string.dropin_ticket_not_found, icon = R.drawable.ic_confirmation_number_20),
        )
        if (state is DropInTicketUiState.Content) bindContent(binding, state)
    }

    private fun bindContent(binding: FragmentDropinTicketBinding, state: DropInTicketUiState.Content) {
        val registration = state.registration
        val session = state.session

        renderLabels(binding, state.labels)

        binding.venueName.isVisible = session != null
        binding.venueName.text = session?.venueName
        val start = session?.startAt ?: registration.sessionStartAt
        val end = session?.endAt ?: registration.sessionEndAt
        binding.sessionDateTime.isVisible = start != null && end != null
        if (start != null && end != null) {
            binding.sessionDateTime.text = getString(
                R.string.dropin_date_time,
                TimeFormat.dayOfWeekDate(start),
                TimeFormat.timeRange(start, end),
            )
        }

        // QR hoặc lý do không có QR.
        val token = state.qrToken
        binding.qrFrame.isVisible = token != null
        binding.noQrText.isVisible = token == null
        if (token != null) {
            binding.qrImage.setImageBitmap(qrBitmap(token))
            binding.qrImage.contentDescription =
                getString(R.string.dropin_ticket_qr_description, registration.ticketCode.orEmpty())
        } else {
            binding.qrImage.setImageDrawable(null)
            binding.noQrText.setText(
                when (state.noQrReason) {
                    NoQrReason.SESSION_CANCELLED -> R.string.dropin_ticket_no_qr_cancelled
                    NoQrReason.INACTIVE -> R.string.dropin_ticket_no_qr_inactive
                    NoQrReason.PENDING, null -> R.string.dropin_ticket_qr_pending
                },
            )
        }

        val code = registration.ticketCode
        binding.ticketCode.isVisible = !code.isNullOrEmpty()
        binding.copyCodeButton.isVisible = !code.isNullOrEmpty()
        binding.ticketCode.text = code
        binding.groupHint.isVisible = token != null

        binding.partyValue.text = getString(R.string.dropin_confirm_party_value, registration.partySize)
        binding.levelValue.text = session?.let { getString(DropInUiText.level(it.level)) } ?: "–"
        binding.amountValue.text = MoneyFormat.vnd(registration.amount)

        val (paymentText, paymentColorAttr) = when (registration.paymentStatus) {
            PaymentStatus.PAID -> R.string.dropin_ticket_paid to androidx.appcompat.R.attr.colorPrimary
            PaymentStatus.UNPAID -> R.string.dropin_ticket_pay_at_venue to com.google.android.material.R.attr.colorOnSurface
            PaymentStatus.REFUNDED -> R.string.dropin_ticket_refunded to com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        binding.paymentValue.setText(paymentText)
        binding.paymentValue.setTextColor(MaterialColors.getColor(binding.paymentValue, paymentColorAttr))
        // "Thanh toán tại sân" kèm số tiền cần trả (AC 2.4).
        binding.amountValue.contentDescription = if (registration.paymentStatus == PaymentStatus.UNPAID) {
            getString(R.string.dropin_ticket_pay_at_venue_amount, MoneyFormat.vnd(registration.amount))
        } else {
            null
        }

        val location = session?.let {
            val courts = it.courtNames.joinToString(", ")
            if (courts.isEmpty()) it.venueAddress else getString(R.string.dropin_venue_district, courts, it.venueAddress)
        }
        binding.locationGroup.isVisible = !location.isNullOrEmpty()
        binding.locationValue.text = location

        binding.offlineHint.isVisible = token != null
    }

    private fun renderLabels(binding: FragmentDropinTicketBinding, labels: List<TicketLabel>) {
        val group = binding.labelChips
        group.removeAllViews()
        group.isVisible = labels.isNotEmpty()
        labels.forEach { label ->
            group.addView(
                Chip(requireContext()).apply {
                    setText(
                        when (label) {
                            TicketLabel.SESSION_CHANGED -> R.string.dropin_label_session_changed
                            TicketLabel.SESSION_CANCELLED -> R.string.dropin_label_session_cancelled
                            TicketLabel.VENUE_INACTIVE -> R.string.dropin_label_venue_inactive
                        },
                    )
                    isCheckable = false
                    isClickable = false
                },
            )
        }
    }

    private fun qrBitmap(token: String): Bitmap {
        qrCache?.let { (cachedToken, bitmap) -> if (cachedToken == token) return bitmap }
        // Khớp kích thước ImageView (240dp) để ảnh không bị phóng mờ.
        val sizePx = (QR_SIZE_DP * resources.displayMetrics.density).toInt()
        return QrRenderer.render(token, sizePx).also { qrCache = token to it }
    }

    private fun copyTicketCode(code: String?) {
        if (code.isNullOrEmpty()) return
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dropin_ticket_title), code))
        // Android 13+ tự hiện thông báo đã sao chép.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), R.string.dropin_ticket_code_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun raiseBrightness() {
        val window = activity?.window ?: return
        val params = window.attributes
        if (previousBrightness == null) previousBrightness = params.screenBrightness
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = params
    }

    private fun restoreBrightness() {
        val previous = previousBrightness ?: return
        val window = activity?.window ?: return
        val params = window.attributes
        params.screenBrightness = previous
        window.attributes = params
        previousBrightness = null
    }

    private companion object {
        const val QR_SIZE_DP = 240
    }
}
