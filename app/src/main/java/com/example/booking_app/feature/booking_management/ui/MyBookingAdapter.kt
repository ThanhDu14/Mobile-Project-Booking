package com.example.booking_app.feature.booking_management.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemMyBooking060Binding
import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MyBookingAdapter(
    private val onClick: (BookingDetail) -> Unit,
    private val onContinuePayment: ((BookingDetail) -> Unit)? = null,
    private val onRebook: ((BookingDetail) -> Unit)? = null,
    private val onReview: ((BookingDetail) -> Unit)? = null
) : ListAdapter<BookingDetail, MyBookingAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMyBooking060Binding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
        onContinuePayment,
        onRebook,
        onReview
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemMyBooking060Binding,
        private val onClick: (BookingDetail) -> Unit,
        private val onContinuePayment: ((BookingDetail) -> Unit)? = null,
        private val onRebook: ((BookingDetail) -> Unit)? = null,
        private val onReview: ((BookingDetail) -> Unit)? = null
    ) : RecyclerView.ViewHolder(binding.root) {

        private fun openDirections(item: BookingDetail) {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(item.venueSnapshot.address)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            try {
                binding.root.context.startActivity(intent)
            } catch (_: Exception) {}
        }

        fun bind(item: BookingDetail) {
            val locale = Locale.forLanguageTag("vi-VN")
            val currencyFormat = NumberFormat.getCurrencyInstance(locale)
            val timeFormat = SimpleDateFormat("HH:mm", locale)
            val dateFormat = SimpleDateFormat("dd/MM", locale)

            // Short Booking Code: e.g. #SN-41F7
            val shortId = if (item.bookingId.length >= 4) {
                item.bookingId.takeLast(4).uppercase()
            } else {
                item.bookingId.uppercase()
            }
            binding.textBookingCode.text = "#SN-$shortId"

            // Venue Name
            binding.textVenue.text = item.venueSnapshot.name

            // Date & Time formatting: e.g. "T5, 08/10 • 18:00 – 19:30" or "Hôm nay • 17:00 – 19:00"
            val startCal = Calendar.getInstance().apply { timeInMillis = item.startAtMillis }
            val nowCal = Calendar.getInstance()
            val isToday = startCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    startCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

            val dayPrefix = if (isToday) {
                "Hôm nay"
            } else {
                val dow = when (startCal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "CN"
                    Calendar.MONDAY -> "T2"
                    Calendar.TUESDAY -> "T3"
                    Calendar.WEDNESDAY -> "T4"
                    Calendar.THURSDAY -> "T5"
                    Calendar.FRIDAY -> "T6"
                    Calendar.SATURDAY -> "T7"
                    else -> ""
                }
                "$dow, ${dateFormat.format(Date(item.startAtMillis))}"
            }
            val timeRange = "${timeFormat.format(Date(item.startAtMillis))} – ${timeFormat.format(Date(item.endAtMillis))}"
            binding.textBookingTime.text = "$dayPrefix • $timeRange"

            // Courts & Payment Breakdown line
            val courtsStr = item.items.map { it.courtName }.distinct().joinToString(", ").ifBlank { "Sân" }
            val paymentDetail = when {
                item.finance.depositAmount > 0 && item.finance.remainingAmount > 0 ->
                    "Đã cọc ${currencyFormat.format(item.finance.depositAmount)} • Còn ${currencyFormat.format(item.finance.remainingAmount)}"
                item.finance.paidAmount > 0 ->
                    "Đã thanh toán ${currencyFormat.format(item.finance.paidAmount)}"
                else ->
                    "Chưa thanh toán"
            }
            binding.textBookingDetails.text = "$courtsStr • $paymentDetail"

            // Status Badge and Action Buttons matching Mockup 1
            if (item.checkedInAtMillis != null) {
                // Đã check-in • Đang chơi
                binding.iconStatusBadge.setImageResource(R.drawable.ic_person_outline_24)
                binding.textStatusBadge.setText(R.string.my_bookings_060_badge_playing)

                val checkInTime = timeFormat.format(Date(item.checkedInAtMillis))
                binding.textBookingDetails.text = "$courtsStr • Check-in lúc $checkInTime • ${paymentDetail.substringBefore(" •")}"
                binding.textBookingExtra.isVisible = true
                binding.textBookingExtra.text = "Đơn chuyển sang Đã hoàn thành sau ${timeFormat.format(Date(item.endAtMillis))}."

                binding.buttonPrimaryAction.text = binding.root.context.getString(R.string.my_bookings_060_btn_view_detail)
                binding.buttonPrimaryAction.setIconResource(0)
                binding.buttonSecondaryAction.isVisible = false
                binding.buttonPrimaryAction.setOnClickListener { onClick(item) }
            } else {
                binding.iconStatusBadge.setImageResource(R.drawable.ic_check_20)
                binding.textStatusBadge.text = when (item.status) {
                    RefundPolicy.Status.CONFIRMED -> "Đã xác nhận"
                    RefundPolicy.Status.PENDING -> "Chờ duyệt"
                    RefundPolicy.Status.HOLD -> "Chờ thanh toán"
                    RefundPolicy.Status.COMPLETED -> "Hoàn thành"
                    RefundPolicy.Status.CANCELLED -> "Đã hủy"
                    RefundPolicy.Status.REJECTED -> "Đã từ chối"
                    RefundPolicy.Status.NO_SHOW -> "Vắng mặt / Chưa check-in"
                    else -> item.status.name
                }
                binding.textBookingExtra.isVisible = false

                when (item.status) {
                    RefundPolicy.Status.HOLD -> {
                        binding.buttonPrimaryAction.text = "Thanh toán tiếp"
                        binding.buttonPrimaryAction.setIconResource(0)
                        binding.buttonPrimaryAction.setOnClickListener { onContinuePayment?.invoke(item) ?: onClick(item) }
                        binding.buttonSecondaryAction.isVisible = true
                        binding.buttonSecondaryAction.text = binding.root.context.getString(R.string.my_bookings_060_directions)
                        binding.buttonSecondaryAction.setIconResource(R.drawable.ic_directions_24)
                        binding.buttonSecondaryAction.setOnClickListener { openDirections(item) }
                    }
                    RefundPolicy.Status.COMPLETED -> {
                        binding.buttonPrimaryAction.text = "Đặt lại sân này"
                        binding.buttonPrimaryAction.setIconResource(0)
                        binding.buttonPrimaryAction.setOnClickListener { onRebook?.invoke(item) ?: onClick(item) }
                        binding.buttonSecondaryAction.isVisible = true
                        binding.buttonSecondaryAction.text = "Đánh giá"
                        binding.buttonSecondaryAction.setIconResource(0)
                        binding.buttonSecondaryAction.setOnClickListener { onReview?.invoke(item) ?: onClick(item) }
                    }
                    RefundPolicy.Status.NO_SHOW -> {
                        binding.buttonPrimaryAction.text = "Đặt lại sân này"
                        binding.buttonPrimaryAction.setIconResource(0)
                        binding.buttonPrimaryAction.setOnClickListener { onRebook?.invoke(item) ?: onClick(item) }
                        binding.buttonSecondaryAction.isVisible = false
                    }
                    RefundPolicy.Status.CANCELLED, RefundPolicy.Status.REJECTED -> {
                        binding.buttonPrimaryAction.text = "Đặt lại sân này"
                        binding.buttonPrimaryAction.setIconResource(0)
                        binding.buttonPrimaryAction.setOnClickListener { onRebook?.invoke(item) ?: onClick(item) }
                        binding.buttonSecondaryAction.isVisible = false
                    }
                    else -> {
                        binding.buttonPrimaryAction.text = binding.root.context.getString(R.string.my_bookings_060_btn_checkin_code)
                        binding.buttonPrimaryAction.setIconResource(R.drawable.ic_qr_code_scanner_24)
                        binding.buttonPrimaryAction.setOnClickListener { onClick(item) }
                        binding.buttonSecondaryAction.isVisible = true
                        binding.buttonSecondaryAction.text = binding.root.context.getString(R.string.my_bookings_060_directions)
                        binding.buttonSecondaryAction.setIconResource(R.drawable.ic_directions_24)
                        binding.buttonSecondaryAction.setOnClickListener { openDirections(item) }
                    }
                }
            }
            binding.root.setOnClickListener { onClick(item) }
            binding.root.contentDescription = "${item.venueSnapshot.name}, ${binding.textStatusBadge.text}, ${binding.textBookingTime.text}"
        }
    }

    private object Diff : DiffUtil.ItemCallback<BookingDetail>() {
        override fun areItemsTheSame(oldItem: BookingDetail, newItem: BookingDetail) = oldItem.bookingId == newItem.bookingId
        override fun areContentsTheSame(oldItem: BookingDetail, newItem: BookingDetail) = oldItem == newItem
    }
}
