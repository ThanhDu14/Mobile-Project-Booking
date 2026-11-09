package com.example.booking_app.feature.booking_management.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentBookingDetail060Binding
import com.example.booking_app.feature.booking_management.domain.BookingBlockCalculator
import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class BookingDetailFragment : Fragment(R.layout.fragment_booking_detail_060) {

    private val viewModel: BookingDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentBookingDetail060Binding.bind(view)

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.buttonIncreaseBrightness.setOnClickListener {
            activity?.window?.let { window ->
                val layoutParams = window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                window.attributes = layoutParams
                Snackbar.make(binding.root, "Đã tăng độ sáng màn hình để quét QR", Snackbar.LENGTH_SHORT).show()
            }
        }

        var notFoundSnackbar: Snackbar? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is BookingDetailState.Loading -> {
                            binding.cardQr.isVisible = false
                            binding.cardCheckedIn.isVisible = false
                            binding.buttonCancelBooking.isVisible = false
                        }
                        is BookingDetailState.Content -> {
                            notFoundSnackbar?.dismiss()
                            notFoundSnackbar = null
                            bindDetail(binding, state.booking, state.qrBitmap)
                        }
                        is BookingDetailState.NotFound -> {
                            notFoundSnackbar?.dismiss()
                            notFoundSnackbar = Snackbar.make(binding.root, "Không tìm thấy thông tin đơn đặt sân", Snackbar.LENGTH_LONG).also { it.show() }
                        }
                        is BookingDetailState.Error -> {
                            notFoundSnackbar?.dismiss()
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun bindDetail(
        binding: FragmentBookingDetail060Binding,
        booking: BookingDetail,
        qrBitmap: android.graphics.Bitmap?
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
        binding.textBookingSubtitle.text = "#SN-$shortId"

        // QR Card (Mockup 2)
        if (qrBitmap != null && booking.checkedInAtMillis == null) {
            binding.cardQr.isVisible = true
            binding.cardCheckedIn.isVisible = false
            binding.imageBookingQr.setImageBitmap(qrBitmap)
            binding.textDetailStatus.setText(R.string.my_bookings_060_badge_confirmed)
            binding.textQrCode.text = "SN-$shortId"

            val openCheckInTime = timeFormat.format(Date(booking.startAtMillis - 30 * 60 * 1000L))
            binding.textQrInstructions.text = "Đưa mã cho chủ sân khi đến. Không quét được thì đọc mã chữ ở trên. Check-in mở từ $openCheckInTime, 30 phút trước giờ chơi."
        } else if (booking.checkedInAtMillis != null) {
            // Checked In Card (Mockup 2 Check-in)
            binding.cardQr.isVisible = false
            binding.cardCheckedIn.isVisible = true
            binding.textCheckedInTitle.text = "Đã check-in lúc ${timeFormat.format(Date(booking.checkedInAtMillis))}"
            binding.textCheckedInMessage.text = "Chúc bạn chơi vui. Đơn chuyển sang Đã hoàn thành sau ${timeFormat.format(Date(booking.endAtMillis))} và bạn đánh giá được trong 7 ngày."
        } else {
            binding.cardQr.isVisible = false
            binding.cardCheckedIn.isVisible = false
        }

        // Venue & time
        binding.textDetailVenue.text = booking.venueSnapshot.name
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
        val blockSummary = BookingBlockCalculator.calculateBlocks(booking.items)
        val timeRangesStr = if (blockSummary.blocks.isNotEmpty()) {
            blockSummary.formatTimeRanges(timeFormat)
        } else {
            val startStr = timeFormat.format(Date(booking.startAtMillis))
            val endStr = timeFormat.format(Date(booking.endAtMillis))
            "$startStr – $endStr"
        }
        binding.textDetailTime.text = "$dayPrefix • $timeRangesStr"

        val courtsStr = booking.items.map { it.courtName }.distinct().joinToString(", ").ifBlank { "Sân" }
        val durationStr = if (blockSummary.blocks.isNotEmpty()) {
            blockSummary.formatPlayingDuration()
        } else {
            val durationMinutes = ((booking.endAtMillis - booking.startAtMillis) / 60000).toInt()
            val durationHours = durationMinutes / 60
            val durationRemainMins = durationMinutes % 60
            if (durationHours > 0) {
                if (durationRemainMins > 0) "$durationHours giờ $durationRemainMins phút" else "$durationHours giờ"
            } else {
                "$durationRemainMins phút"
            }
        }
        val gapNote = blockSummary.formatGapNote()
        val courtDetailStr = if (gapNote != null) {
            "$courtsStr • Thảm • $durationStr • $gapNote"
        } else {
            "$courtsStr • Thảm • $durationStr"
        }
        binding.textDetailCourt.text = courtDetailStr
        binding.textDetailAddress.text = booking.venueSnapshot.address
        if (!booking.venueSnapshot.phone.isNullOrBlank()) {
            binding.textDetailPhone.isVisible = true
            binding.textDetailPhone.text = "Hotline: ${booking.venueSnapshot.phone}"
        } else {
            binding.textDetailPhone.isVisible = false
        }

        if (booking.recurringGroupId != null) {
            binding.textRecurringGroup.isVisible = true
            binding.textRecurringGroup.text = "Đơn đặt cố định theo tuần (Mã nhóm: ${booking.recurringGroupId})"
        } else {
            binding.textRecurringGroup.isVisible = false
        }

        // Directions button
        binding.buttonDirections.setOnClickListener {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(booking.venueSnapshot.address)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Snackbar.make(binding.root, "Không thể mở ứng dụng bản đồ", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Call venue button
        binding.buttonCallVenue.setOnClickListener {
            val phone = booking.venueSnapshot.phone
            if (!phone.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Snackbar.make(binding.root, "Không thể mở trình quay số", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Snackbar.make(binding.root, "Cơ sở chưa cung cấp số điện thoại", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Finance Breakdown (2 columns)
        val slotsCount = booking.items.size
        binding.labelSubtotal.text = "Tiền sân ($slotsCount × 30 phút)"
        binding.textSubtotal.text = currencyFormat.format(booking.finance.subtotal)
        binding.labelPaidAmount.text = if (booking.finance.depositAmount > 0) "Đã đặt cọc 30% (ví giả lập)" else "Đã thanh toán (ví giả lập)"
        binding.textPaidAmount.text = "-${currencyFormat.format(booking.finance.paidAmount)}"
        binding.textRemainingAmount.text = currencyFormat.format(booking.finance.remainingAmount)

        if (booking.finance.discount > 0) {
            binding.rowDiscount.isVisible = true
            binding.textDiscount.text = "-${currencyFormat.format(booking.finance.discount)}"
        } else {
            binding.rowDiscount.isVisible = false
        }

        // Cancellation Policy Text Box
        val deadlineTime = timeFormat.format(Date(booking.startAtMillis - 240 * 60 * 1000L))
        binding.textCancellationPolicy.text = "Chính sách hủy: hủy trước $deadlineTime hôm chơi (từ 4 giờ trở lên) được hoàn 100% tiền cọc. Hủy sau mốc đó mất tiền cọc. Đơn đã check-in không hủy được."

        // Cancel button
        val canCancel = (booking.status == RefundPolicy.Status.PENDING || booking.status == RefundPolicy.Status.CONFIRMED) &&
                booking.checkedInAtMillis == null &&
                System.currentTimeMillis() < booking.startAtMillis

        binding.buttonCancelBooking.isVisible = canCancel
        binding.buttonCancelBooking.setOnClickListener {
            findNavController().navigate(
                R.id.cancelBookingFragment,
                bundleOf("bookingId" to booking.bookingId)
            )
        }
    }
}
