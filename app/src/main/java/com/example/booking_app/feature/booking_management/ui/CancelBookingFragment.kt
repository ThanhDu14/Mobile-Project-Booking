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
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", locale)

        binding.textBookingSubtitle.text = "Mã đơn: #${booking.bookingId}"
        val startStr = timeFormat.format(Date(booking.startAtMillis))
        val endStr = timeFormat.format(Date(booking.endAtMillis))
        val dateStr = dateFormat.format(Date(booking.startAtMillis))
        binding.textCancelBookingTime.text = "$startStr – $endStr • $dateStr"
        binding.textCancelBookingSummary.text = "${booking.venueSnapshot.name} • ${booking.items.map { it.courtName }.distinct().joinToString(", ")}"

        if (quote != null) {
            binding.textRefundAmount.text = "Dự kiến hoàn: ${currencyFormat.format(quote.refundAmount)}"
            binding.textRefundExplanation.text = quote.policyExplanation
        } else {
            binding.textRefundAmount.text = "Đang ước tính..."
            binding.textRefundExplanation.text = "Số tiền hoàn thực tế do máy chủ quyết định theo mốc giờ thực tế nhận yêu cầu."
        }

        binding.textCancelPolicy.text = "Lưu ý: Mọi giao dịch hoàn tiền là giả lập phục vụ mục đích học tập. Yêu cầu hủy được xử lý nguyên tử và slot sẽ được giải phóng ngay lập tức."
    }
}
