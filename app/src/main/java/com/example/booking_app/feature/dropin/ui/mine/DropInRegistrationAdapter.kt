package com.example.booking_app.feature.dropin.ui.mine

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.databinding.ItemDropinRegistrationBinding
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.ui.common.DropInUiText

/**
 * Thẻ một lượt trong "Lượt vãng lai của tôi" (T047): ngày giờ, cơ sở, số người, trạng thái thanh toán, nhãn kết quả,
 * nhãn "Buổi đã thay đổi". Bấm thẻ hoặc "Xem vé" mở vé của buổi.
 */
class DropInRegistrationAdapter(
    private val onClick: (sessionId: String) -> Unit,
) : ListAdapter<MyDropInRow, DropInRegistrationAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDropinRegistrationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDropinRegistrationBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MyDropInRow) {
            val context = binding.root.context
            val item = row.item
            val registration = item.registration

            val status = statusText(context, row)
            binding.statusChip.text = status
            binding.statusChip.setChipIconResource(statusIcon(row))
            binding.statusChip.isChipIconVisible = row.outcome == null
            binding.changedChip.isVisible = row.showSessionChanged

            val code = registration.ticketCode
            binding.ticketCode.isVisible = code != null && row.outcome == null
            binding.ticketCode.text = code

            val venue = item.session?.venueName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.dropin_mine_unknown_session)
            binding.venueName.text = venue

            val start = item.startAt
            val end = item.endAt
            val dateTime = if (start != null && end != null) {
                DropInUiText.dateTimeLine(context, start, end)
            } else {
                context.getString(R.string.dropin_mine_unknown_time)
            }
            binding.dateTime.text = dateTime

            val detail = context.getString(
                R.string.dropin_mine_detail_line,
                context.getString(R.string.dropin_mine_party, registration.partySize),
                paymentText(context, registration.paymentStatus, registration.amount, registration.refundAmount),
            )
            binding.detailLine.text = detail

            // "Xem vé" chỉ cho lượt còn hiệu lực ở "Sắp tới" (đã đăng ký, chưa bị hủy).
            val canViewTicket = row.outcome == null && registration.status == RegistrationStatus.REGISTERED
            binding.viewTicketButton.isVisible = canViewTicket
            binding.viewTicketButton.setOnClickListener { onClick(item.sessionId) }

            val labels = buildList {
                add(status)
                if (row.showSessionChanged) add(context.getString(R.string.dropin_label_session_changed))
            }.joinToString(", ")
            binding.root.contentDescription =
                context.getString(R.string.dropin_mine_item_description, venue, dateTime, detail, labels)
            binding.root.setOnClickListener { onClick(item.sessionId) }
        }

        private fun statusText(context: Context, row: MyDropInRow): String {
            val outcome = row.outcome
            if (outcome != null) return context.getString(outcomeLabel(outcome))
            return when (row.item.registration.status) {
                RegistrationStatus.WAITLISTED -> context.getString(R.string.dropin_mine_status_waitlisted)
                else -> context.getString(R.string.dropin_mine_status_registered, row.item.registration.partySize)
            }
        }

        private fun statusIcon(row: MyDropInRow): Int =
            if (row.item.registration.status == RegistrationStatus.WAITLISTED) R.drawable.ic_hourglass_24 else R.drawable.ic_check_20

        private fun paymentText(context: Context, status: PaymentStatus, amount: Long, refund: Long?): String =
            when (status) {
                PaymentStatus.PAID -> context.getString(R.string.dropin_mine_paid_amount, MoneyFormat.vnd(amount))
                PaymentStatus.UNPAID -> context.getString(R.string.dropin_mine_pay_at_venue_amount, MoneyFormat.vnd(amount))
                PaymentStatus.REFUNDED ->
                    context.getString(R.string.dropin_mine_refunded_amount, MoneyFormat.vnd(refund ?: amount))
            }
    }

    private object Diff : DiffUtil.ItemCallback<MyDropInRow>() {
        override fun areItemsTheSame(oldItem: MyDropInRow, newItem: MyDropInRow) =
            oldItem.item.sessionId == newItem.item.sessionId

        override fun areContentsTheSame(oldItem: MyDropInRow, newItem: MyDropInRow) = oldItem == newItem
    }

    companion object {
        fun outcomeLabel(outcome: RegistrationOutcome): Int = when (outcome) {
            RegistrationOutcome.ATTENDED -> R.string.dropin_label_attended
            RegistrationOutcome.CANCELLED_BY_ME -> R.string.dropin_label_cancelled_by_me
            RegistrationOutcome.SESSION_CANCELLED -> R.string.dropin_label_session_cancelled_short
            RegistrationOutcome.NO_SHOW -> R.string.dropin_label_no_show
            RegistrationOutcome.UPCOMING -> R.string.dropin_mine_upcoming
        }
    }
}
