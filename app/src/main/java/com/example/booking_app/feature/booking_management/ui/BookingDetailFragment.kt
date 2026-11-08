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
                            bindDetail(binding, state.booking, state.qrBitmap)
                        }
                        is BookingDetailState.NotFound -> {
                            Snackbar.make(binding.root, "Không tìm thấy thông tin đơn đặt sân", Snackbar.LENGTH_LONG).show()
                        }
                        is BookingDetailState.Error -> {
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
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", locale)
        val fullDateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)

        binding.textBookingSubtitle.text = "Mã đơn: #${booking.bookingId}"

        // QR Card
        if (qrBitmap != null) {
            binding.cardQr.isVisible = true
            binding.imageBookingQr.setImageBitmap(qrBitmap)
            binding.textDetailStatus.text = "SẮP TỚI • ĐÃ XÁC NHẬN"
            binding.textQrCode.text = booking.bookingId
            binding.textQrInstructions.text = "Đưa mã này cho nhân viên lễ tân khi đến sân"
        } else {
            binding.cardQr.isVisible = false
        }

        // Checked In Card
        if (booking.checkedInAtMillis != null) {
            binding.cardCheckedIn.isVisible = true
            binding.textCheckedInTitle.text = "ĐÃ CHECK-IN TẠI SÂN"
            binding.textCheckedInMessage.text = "Đã xác nhận lúc ${fullDateTimeFormat.format(Date(booking.checkedInAtMillis))}"
        } else {
            binding.cardCheckedIn.isVisible = false
        }

        // Venue & time
        binding.textDetailVenue.text = booking.venueSnapshot.name
        val startStr = timeFormat.format(Date(booking.startAtMillis))
        val endStr = timeFormat.format(Date(booking.endAtMillis))
        val dateStr = dateFormat.format(Date(booking.startAtMillis))
        binding.textDetailTime.text = "$startStr – $endStr • $dateStr"

        val courtsStr = booking.items.map { it.courtName }.distinct().joinToString(", ")
        binding.textDetailCourt.text = "Sân: $courtsStr"
        binding.textDetailAddress.text = "Địa chỉ: ${booking.venueSnapshot.address}"
        binding.textDetailPhone.text = "Hotline: ${booking.venueSnapshot.phone ?: "Chưa cập nhật"}"

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

        // Slots summary
        binding.textBookingSlots.text = booking.items.joinToString("\n") { item ->
            "• ${item.courtName}: ${timeFormat.format(Date(item.startAtMillis))} - ${timeFormat.format(Date(item.endAtMillis))} (${currencyFormat.format(item.price)})"
        }

        // Finance
        binding.textSubtotal.text = "Tiền gốc: ${currencyFormat.format(booking.finance.subtotal)}"
        binding.textDiscount.text = "Giảm giá voucher: -${currencyFormat.format(booking.finance.discount)}"
        binding.textPaidAmount.text = "Đã thanh toán: ${currencyFormat.format(booking.finance.paidAmount)}"
        binding.textRemainingAmount.text = "Còn lại tại sân: ${currencyFormat.format(booking.finance.remainingAmount)}"
        binding.textPaymentMethod.text = "Hình thức: ${booking.finance.paymentMethod} (${booking.finance.paymentStatus})"
        binding.textCreatedAt.text = "Thời gian tạo: ${fullDateTimeFormat.format(Date(booking.createdAtMillis))}"

        // Cancellation Policy
        binding.textCancellationPolicy.text = "Chính sách hủy: Hủy trước 4 tiếng được hoàn 100% tiền vào ví giả lập. Hủy trong vòng 4 tiếng áp dụng phạt hủy trễ theo quy định."

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
