package com.example.booking_app.feature.booking_management.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentCancelBooking060Binding
import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.RefundPolicyQuote
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CancelBookingFragment : Fragment(R.layout.fragment_cancel_booking_060) {

    private val viewModel: CancelBookingViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCancelBooking060Binding.bind(view)

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.buttonKeepBooking.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.groupCancelReasons.setOnCheckedChangeListener { _, checkedId ->
            val reasonKey = when (checkedId) {
                R.id.reasonBusy -> getString(R.string.my_bookings_060_reason_busy)
                R.id.reasonWeather -> getString(R.string.my_bookings_060_reason_weather)
                R.id.reasonPlayers -> getString(R.string.my_bookings_060_reason_players)
                R.id.reasonWrongBooking -> getString(R.string.my_bookings_060_reason_wrong)
                R.id.reasonOther -> "OTHER"
                else -> ""
            }
            binding.inputOtherReason.isVisible = (checkedId == R.id.reasonOther)
            viewModel.onReasonSelected(reasonKey)
        }

        binding.inputOtherReason.doAfterTextChanged { text ->
            viewModel.onOtherReasonTextChanged(text?.toString() ?: "")
        }

        binding.buttonConfirmCancel.setOnClickListener {
            viewModel.confirmCancel()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.buttonConfirmCancel.isEnabled = state.isConfirmEnabled && !state.isLoading

                    if (state.booking != null) {
                        bindBooking(binding, state.booking, state.quote)
                    }

                    if (state.errorMessage != null) {
                        Snackbar.make(binding.root, state.errorMessage, Snackbar.LENGTH_LONG).show()
                    }

                    if (state.isSuccess) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Hủy đơn thành công")
                            .setMessage(state.resultMessage ?: "Đơn đặt sân đã được hủy.")
                            .setPositiveButton("Đóng") { _, _ ->
                                findNavController().popBackStack(R.id.myBookingsFragment, false)
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }
    }

    private fun bindBooking(
        binding: FragmentCancelBooking060Binding,
        booking: BookingDetail,
        quote: RefundPolicyQuote?
    ) {
        val locale = Locale.forLanguageTag("vi-VN")
        val currencyFormat = NumberFormat.getCurrencyInstance(locale)
        val timeFormat = SimpleDateFormat("HH:mm", locale)
        val dateFormat = SimpleDateFormat("dd/MM", locale)

        val shortId = if (booking.bookingId.length >= 4) {
            booking.bookingId.takeLast(4).uppercase()
        } else {
            booking.bookingId.uppercase()
        }
        binding.textBookingSubtitle.text = "#SN-$shortId • ${booking.venueSnapshot.name}"

        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = booking.startAtMillis }
        val nowCal = java.util.Calendar.getInstance()
        val isToday = startCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                startCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)

        val dayPrefix = if (isToday) {
            "Hôm nay"
        } else {
            val dow = when (startCal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.SUNDAY -> "CN"
                java.util.Calendar.MONDAY -> "T2"
                java.util.Calendar.TUESDAY -> "T3"
                java.util.Calendar.WEDNESDAY -> "T4"
                java.util.Calendar.THURSDAY -> "T5"
                java.util.Calendar.FRIDAY -> "T6"
                java.util.Calendar.SATURDAY -> "T7"
                else -> ""
            }
            "$dow, ${dateFormat.format(Date(booking.startAtMillis))}"
        }
        val startStr = timeFormat.format(Date(booking.startAtMillis))
        val endStr = timeFormat.format(Date(booking.endAtMillis))
        binding.textCancelBookingTime.text = "$dayPrefix • $startStr – $endStr"

        val courtsStr = booking.items.map { it.courtName }.distinct().joinToString(", ").ifBlank { "Sân" }
        val paymentMethodStr = if (booking.finance.depositAmount > 0) "Đặt cọc 30% bằng ví giả lập" else "Thanh toán bằng ví giả lập"
        binding.textCancelBookingSummary.text = "$courtsStr • $paymentMethodStr"

        if (quote != null) {
            binding.textRefundAmount.text = "Bạn được hoàn  ${currencyFormat.format(quote.refundAmount)}"
            val hoursLeft = (booking.startAtMillis - System.currentTimeMillis()) / 3600_000L
            val daysLeft = (hoursLeft / 24).toInt()
            val timeRemainingStr = if (daysLeft > 0) "Còn $daysLeft ngày tới giờ chơi" else "Còn $hoursLeft giờ tới giờ chơi"
            binding.textRefundExplanation.text = if (quote.isEarlyCancel) {
                "$timeRemainingStr, hủy từ 4 giờ trở lên được hoàn 100% tiền cọc vào ví giả lập. Khung giờ được mở lại cho người khác ngay."
            } else {
                "Hủy trong vòng 4 tiếng áp dụng phạt 30% phí hủy trễ theo quy định và hoàn 70% vào ví giả lập."
            }
        } else {
            binding.textRefundAmount.text = "Đang ước tính..."
            binding.textRefundExplanation.text = "Số tiền hoàn thực tế do máy chủ quyết định theo mốc giờ thực tế nhận yêu cầu."
        }
    }
}
