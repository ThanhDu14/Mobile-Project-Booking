package com.example.booking_app.feature.dropin.ui.ticket

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import com.example.booking_app.databinding.BottomSheetDropinAddPartyBinding
import com.example.booking_app.databinding.FragmentDropinTicketBinding
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Vé vãng lai (US2 T040, US3 T048; wireframe `08_drop-in_buoc4`). QR vẽ bằng [QrRenderer] cạnh 240dp trên nền trắng,
 * mã vé chọn để sao chép được, trạng thái thanh toán (AC 2.3, 2.4). Độ sáng màn hình tăng tối đa khi vé hiện và trả
 * lại như cũ khi rời màn hình (contracts/ui-screens.md mục 1). Ngoại tuyến hiện vé đã lưu kèm dải thông báo (FR-015);
 * nút "Chỉ đường" (AC 3.2) và "Thêm người" (FR-008a, bottom sheet, không phải destination của nav graph).
 */
@AndroidEntryPoint
class DropInTicketFragment : Fragment(R.layout.fragment_dropin_ticket) {

    private val viewModel: DropInTicketViewModel by viewModels()

    /** QR đã vẽ, giữ lại để không vẽ lại mỗi lần realtime đẩy cập nhật. */
    private var qrCache: Pair<String, Bitmap>? = null

    /** Độ sáng của cửa sổ trước khi mở vé; `null` khi chưa ghi đè. */
    private var previousBrightness: Float? = null

    private var addPartyDialog: BottomSheetDialog? = null
    private var addPartyBinding: BottomSheetDropinAddPartyBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinTicketBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.successBanner.isVisible = viewModel.justRegistered
        binding.copyCodeButton.setOnClickListener { copyTicketCode(binding.ticketCode.text?.toString()) }
        binding.directionsButton.setOnClickListener { viewModel.openDirections() }
        binding.addPartyButton.setOnClickListener { viewModel.startAddParty() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.addPartyForm.collect { renderAddParty(it) } }
                launch { viewModel.events.collect { onEvent(binding, it) } }
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
        // dismiss() không gọi OnCancelListener nên form trong ViewModel còn nguyên, xoay màn hình mở lại được.
        addPartyDialog?.dismiss()
        addPartyDialog = null
        addPartyBinding = null
        super.onDestroyView()
    }

    private fun render(binding: FragmentDropinTicketBinding, state: DropInTicketUiState) {
        val coreState: UiState<Unit> = when (state) {
            DropInTicketUiState.Loading -> UiState.Loading
            DropInTicketUiState.NotFound -> UiState.Empty
            is DropInTicketUiState.Error ->
                UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retry)
            is DropInTicketUiState.Content, is DropInTicketUiState.Offline -> UiState.Content(Unit)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(message = R.string.dropin_ticket_not_found, icon = R.drawable.ic_confirmation_number_20),
        )
        when (state) {
            is DropInTicketUiState.Content -> bindContent(binding, state, offline = false)
            is DropInTicketUiState.Offline -> bindContent(binding, state.cached, offline = true)
            else -> Unit
        }
    }

    private fun bindContent(binding: FragmentDropinTicketBinding, state: DropInTicketUiState.Content, offline: Boolean) {
        val registration = state.registration
        val session = state.session

        binding.offlineBanner.isVisible = offline
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

        binding.offlineHint.isVisible = token != null && !offline

        // Chỉ đường khi biết cơ sở; thêm người chỉ khi đang có mạng và đủ điều kiện (FR-008a).
        binding.directionsButton.isVisible = session != null
        binding.addPartyButton.isVisible = !offline && state.addParty != null
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

    // ---------- Thêm người (FR-008a) ----------

    private fun renderAddParty(form: AddPartyForm?) {
        if (form == null) {
            addPartyDialog?.dismiss()
            addPartyDialog = null
            addPartyBinding = null
            return
        }
        val sheet = addPartyBinding ?: createAddPartyDialog()
        addPartyDialog?.setCancelable(!form.submitting)

        sheet.addPartyHint.text = getString(R.string.dropin_add_party_hint, form.currentPartySize, form.maxAdditional)
        sheet.addPartyValue.text = form.additional.toString()
        sheet.addPartyDecrease.isEnabled = !form.submitting && form.additional > 1
        sheet.addPartyIncrease.isEnabled = !form.submitting && form.additional < form.maxAdditional
        sheet.addPartyAmount.text = MoneyFormat.vnd(form.extraAmount)
        sheet.addPartyBreakdown.text =
            getString(R.string.dropin_confirm_price_times, MoneyFormat.vnd(form.pricePerPerson), form.additional)
        sheet.addPartyPaymentNote.setText(
            when (form.paymentMethod) {
                PaymentMethod.MOCK_PAY -> R.string.dropin_add_party_pay_mock
                PaymentMethod.AT_VENUE -> R.string.dropin_add_party_pay_at_venue
            },
        )

        val error = form.error
        sheet.addPartyError.isVisible = error != null
        sheet.addPartyError.text = error?.let { addPartyErrorText(it) }

        sheet.addPartySubmit.isEnabled = !form.submitting
        sheet.addPartySubmit.text = when {
            form.submitting -> getString(R.string.dropin_add_party_submitting)
            form.canRetry -> getString(R.string.dropin_retry)
            else -> getString(R.string.dropin_add_party_submit, form.additional)
        }
    }

    private fun createAddPartyDialog(): BottomSheetDropinAddPartyBinding {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = BottomSheetDropinAddPartyBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        sheet.addPartyDecrease.setOnClickListener { viewModel.decreaseAdditional() }
        sheet.addPartyIncrease.setOnClickListener { viewModel.increaseAdditional() }
        sheet.addPartySubmit.setOnClickListener { viewModel.submitAddParty() }
        // Chỉ người dùng tự đóng (vuốt, bấm ngoài, nút Back) mới báo ViewModel.
        dialog.setOnCancelListener { viewModel.dismissAddParty() }
        dialog.show()
        addPartyDialog = dialog
        addPartyBinding = sheet
        return sheet
    }

    private fun addPartyErrorText(error: Throwable): String = when (error) {
        is DropInError.PartyLimit -> if (error.maxAdditional > 0) {
            getString(R.string.dropin_add_party_error_limit, error.maxAdditional)
        } else {
            getString(R.string.dropin_add_party_error_limit_none)
        }
        is DropInError.SessionFull -> if (error.remaining > 0) {
            getString(R.string.dropin_add_party_error_full, error.remaining)
        } else {
            getString(R.string.dropin_confirm_no_seat)
        }
        is DropInError.NotRegistered -> getString(R.string.dropin_add_party_error_not_registered)
        is DropInError.Network -> getString(R.string.dropin_add_party_error_network)
        is DropInError.Busy -> getString(R.string.dropin_add_party_error_busy)
        else -> DropInUiText.error(requireContext(), error)
    }

    private fun onEvent(binding: FragmentDropinTicketBinding, event: DropInTicketEvent) {
        when (event) {
            is DropInTicketEvent.OpenDirections -> openDirections(binding, event)
            is DropInTicketEvent.PartyAdded -> Snackbar.make(
                binding.root,
                getString(R.string.dropin_add_party_success, event.partySize),
                Snackbar.LENGTH_SHORT,
            ).show()
            is DropInTicketEvent.AddPartyFailed ->
                Snackbar.make(binding.root, addPartyErrorText(event.cause), Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------- Chỉ đường (AC 3.2) ----------

    /**
     * Ưu tiên Google Maps theo tọa độ cơ sở; không có Google Maps thì ứng dụng bản đồ bất kỳ qua `geo:`, sau cùng là
     * trình duyệt. Chưa có tọa độ thì tìm theo địa chỉ. Dùng try/catch nên không cần khai báo `<queries>`.
     */
    private fun openDirections(binding: FragmentDropinTicketBinding, event: DropInTicketEvent.OpenDirections) {
        val location = event.location
        val geo: Uri
        val web: Uri
        if (location != null) {
            val latLng = "${location.latitude},${location.longitude}"
            geo = Uri.parse("geo:$latLng?q=$latLng(${Uri.encode(event.label)})")
            web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latLng")
        } else {
            val query = event.address ?: event.label
            if (query.isBlank()) return
            geo = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            web = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        }
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, geo).setPackage(GOOGLE_MAPS_PACKAGE),
            Intent(Intent.ACTION_VIEW, geo),
            Intent(Intent.ACTION_VIEW, web),
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Thử cách tiếp theo.
            }
        }
        Snackbar.make(binding.root, R.string.dropin_detail_no_map_app, Snackbar.LENGTH_SHORT).show()
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
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
