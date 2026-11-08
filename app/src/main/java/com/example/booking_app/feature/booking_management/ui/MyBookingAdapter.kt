package com.example.booking_app.feature.booking_management.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemMyBooking060Binding
import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.PaymentStatus
import com.example.booking_app.feature.booking_management.domain.RefundPolicy
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyBookingAdapter(private val onClick: (BookingDetail) -> Unit) :
    ListAdapter<BookingDetail, MyBookingAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMyBooking060Binding.inflate(LayoutInflater.from(parent.context), parent, false), onClick
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(private val binding: ItemMyBooking060Binding, private val onClick: (BookingDetail) -> Unit) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BookingDetail) {
            val locale = Locale.forLanguageTag("vi-VN")
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", locale)
            val statusLabel = when (item.status) {
                RefundPolicy.Status.HOLD -> "CHỜ THANH TOÁN"
                RefundPolicy.Status.PENDING -> "CHỜ DUYỆT"
                RefundPolicy.Status.CONFIRMED -> "SẮP TỚI"
                RefundPolicy.Status.COMPLETED -> "HOÀN THÀNH"
                RefundPolicy.Status.CANCELLED -> "ĐÃ HỦY"
                RefundPolicy.Status.REJECTED -> "TỪ CHỐI"
                else -> item.status
            }
            val paymentLabel = when (item.finance.paymentStatus) {
                PaymentStatus.PAID -> "Đã thanh toán"
                PaymentStatus.DEPOSITED -> "Đã đặt cọc"
                PaymentStatus.UNPAID -> "Chưa thanh toán"
                PaymentStatus.REFUNDED -> "Đã hoàn tiền"
            }
            binding.textVenue.text = item.venueSnapshot.name
            binding.textBookingStatus.text = "$statusLabel • $paymentLabel"
            binding.textSlots.text = item.items.joinToString("\n") { slot ->
                "${slot.courtName} • ${date.format(Date(slot.startAtMillis))} – ${SimpleDateFormat("HH:mm", locale).format(Date(slot.endAtMillis))}"
            }.ifBlank { "Thông tin khung giờ chưa có" }
            binding.textTotal.text = NumberFormat.getCurrencyInstance(locale).format(item.finance.total)
            binding.root.setOnClickListener { onClick(item) }
            binding.root.contentDescription = "${item.venueSnapshot.name}, ${item.status}, ${binding.textSlots.text}"
        }
    }
    private object Diff : DiffUtil.ItemCallback<BookingDetail>() {
        override fun areItemsTheSame(oldItem: BookingDetail, newItem: BookingDetail) = oldItem.bookingId == newItem.bookingId
        override fun areContentsTheSame(oldItem: BookingDetail, newItem: BookingDetail) = oldItem == newItem
    }
}
