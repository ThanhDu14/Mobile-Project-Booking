package com.example.booking_app.feature.owner.ui.bookings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.feature.owner.ui.setOwnerDialogInput
import com.example.booking_app.databinding.FragmentOwnerBookingsBinding
import com.example.booking_app.databinding.ItemOwnerBookingBinding
import com.example.booking_app.feature.owner.domain.booking.OwnerBooking
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingCommand
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingFilter
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingStatus
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingDetail
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.time.ZoneId
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerBookingsFragment : Fragment(R.layout.fragment_owner_bookings) {
    private val viewModel: OwnerBookingsViewModel by viewModels()
    private var _binding: FragmentOwnerBookingsBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var renderedVenueId: String? = null
    private var renderedVenueCount = -1
    private var renderingSpinner = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerBookingsBinding.bind(view)
        binding.refresh.setOnClickListener { viewModel.refresh() }
        binding.filterGroup.isSingleSelection = true
        binding.filterPending.setOnClickListener { viewModel.selectFilter(OwnerBookingFilter.PENDING) }
        binding.filterUpcoming.setOnClickListener { viewModel.selectFilter(OwnerBookingFilter.UPCOMING) }
        binding.filterPast.setOnClickListener { viewModel.selectFilter(OwnerBookingFilter.PAST) }
        binding.venueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!renderingSpinner) viewModel.uiState.value.venues.getOrNull(position)?.id?.let(viewModel::selectVenue)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startCountdownTicker()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) viewModel.refresh()
    }

    override fun onStop() {
        viewModel.stopCountdownTicker()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: OwnerBookingsUiState) {
        binding.loading.isVisible = state.isLoading
        binding.errorText.isVisible = !state.isLoading && state.error != null
        binding.errorText.text = state.error?.let { message ->
            if (message.contains("Hợp đồng đọc", ignoreCase = true)) getString(R.string.owner_bookings_contract_error) else message
        }
        binding.emptyText.isVisible = !state.isLoading && state.error == null && state.isEmpty
        binding.bookingScroll.isVisible = !state.isLoading && state.error == null && state.bookings.isNotEmpty()
        binding.filterPending.isChecked = state.filter == OwnerBookingFilter.PENDING
        binding.filterUpcoming.isChecked = state.filter == OwnerBookingFilter.UPCOMING
        binding.filterPast.isChecked = state.filter == OwnerBookingFilter.PAST
        renderVenues(state.venues, state.selectedVenueId)
        if (binding.bookingScroll.isVisible) renderBookings(state.bookings, state.now, state.actionInProgress,
            state.venues.firstOrNull { it.id == state.selectedVenueId }?.status == "ACTIVE")
        state.actionMessage?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
        state.selectedDetail?.let { detail ->
            showDetails(detail)
            viewModel.clearDetails()
        }
    }

    private fun renderVenues(venues: List<com.example.booking_app.feature.owner.domain.model.OwnerVenue>, selectedId: String?) {
        if (renderedVenueId == selectedId && renderedVenueCount == venues.size) return
        val context = context ?: return
        renderingSpinner = true
        renderedVenueId = selectedId
        renderedVenueCount = venues.size
        binding.venueSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, venues.map { it.name })
        val selectedIndex = venues.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0) binding.venueSpinner.setSelection(selectedIndex, false)
        renderingSpinner = false
    }

    private fun renderBookings(bookings: List<OwnerBooking>, now: java.time.Instant, busy: Boolean, canOperate: Boolean) {
        val context = context ?: return
        // Keep attached rows during countdown ticks so an in-flight touch is not discarded.
        val contentKey = Triple(bookings, busy, canOperate)
        val rebuild = binding.bookingList.tag != contentKey
        if (rebuild) {
            binding.bookingList.removeAllViews()
            binding.bookingList.tag = contentKey
        }
        bookings.forEachIndexed { index, booking ->
            val row = if (rebuild) ItemOwnerBookingBinding.inflate(layoutInflater, binding.bookingList, false)
                else ItemOwnerBookingBinding.bind(binding.bookingList.getChildAt(index))
            row.customer.text = booking.customerDisplayName?.takeIf(String::isNotBlank)
                ?: getString(R.string.owner_bookings_customer_unknown)
            val start = booking.startAt.atZone(VIETNAM_ZONE)
            val end = booking.endAt.atZone(VIETNAM_ZONE)
            row.time.text = getString(
                R.string.owner_bookings_date_time,
                start.format(DATE_FORMAT),
                start.format(TIME_FORMAT),
                end.format(TIME_FORMAT),
            )
            row.courts.text = booking.courtNames.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                ?: getString(R.string.owner_bookings_court_unknown)
            row.amount.text = booking.displayAmountVnd?.let { getString(R.string.owner_bookings_amount_vnd, NUMBER_FORMAT.format(it)) }
                ?: getString(R.string.owner_bookings_amount_unknown)
            row.status.text = when (booking.status) {
                OwnerBookingStatus.PENDING -> getString(R.string.owner_bookings_status_pending)
                OwnerBookingStatus.CONFIRMED -> getString(R.string.owner_bookings_status_confirmed)
                else -> getString(R.string.owner_bookings_status_other, booking.status.name)
            }
            row.deadline.isVisible = booking.status == OwnerBookingStatus.PENDING
            row.deadline.text = booking.approvalDeadline?.let { deadline ->
                val remainingSeconds = Duration.between(now, deadline).seconds.coerceAtLeast(0)
                val remainingText = if (remainingSeconds == 0L) getString(R.string.owner_bookings_deadline_expired) else
                    getString(R.string.owner_bookings_deadline_remaining, remainingSeconds / 60, remainingSeconds % 60)
                "${getString(R.string.owner_bookings_deadline, deadline.atZone(VIETNAM_ZONE).format(DATE_TIME_FORMAT))} · $remainingText"
            } ?: getString(R.string.owner_bookings_no_deadline)
            row.pendingActions.isVisible = booking.status == OwnerBookingStatus.PENDING
            row.checkIn.isVisible = booking.status == OwnerBookingStatus.CONFIRMED
            row.checkIn.setOnClickListener {
                findNavController().navigate(R.id.action_ownerBookings_to_checkin)
            }
            val canDecide = !busy && canOperate && booking.status == OwnerBookingStatus.PENDING &&
                booking.approvalDeadline?.isAfter(now) == true
            row.approve.isEnabled = canDecide
            row.reject.isEnabled = canDecide
            row.approve.setOnClickListener { viewModel.submit(OwnerBookingCommand.Approve(booking.venueId, booking.bookingId)) }
            row.reject.setOnClickListener { requestReason(R.string.owner_bookings_reject) { reason ->
                viewModel.submit(OwnerBookingCommand.Reject(booking.venueId, booking.bookingId, reason))
            } }
            row.cancelBooking.isVisible = booking.status == OwnerBookingStatus.CONFIRMED &&
                booking.startAt.isAfter(now) && booking.checkedInAt == null
            row.cancelBooking.isEnabled = !busy && canOperate
            row.cancelBooking.setOnClickListener { requestReason(R.string.owner_cancel_booking) { reason ->
                viewModel.submit(OwnerBookingCommand.CancelConfirmed(booking.venueId, booking.bookingId, reason))
            } }
            row.checkIn.isEnabled = !busy && canOperate && booking.checkedInAt == null &&
                !now.isBefore(booking.startAt.minusSeconds(1800)) && now.isBefore(booking.endAt)
            row.details.isEnabled = !busy
            row.details.setOnClickListener { viewModel.showDetails(booking.bookingId) }
            if (rebuild) binding.bookingList.addView(row.root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showDetails(detail: OwnerBookingDetail) {
        val booking = detail.booking
        val message = buildList {
            add(getString(R.string.owner_bookings_details_body, booking.bookingId, booking.status.name, detail.slotCount))
            booking.displayAmountVnd?.let { add(getString(R.string.owner_bookings_amount_vnd, NUMBER_FORMAT.format(it))) }
            booking.depositAmountVnd?.let { add(getString(R.string.owner_booking_deposit, NUMBER_FORMAT.format(it))) }
            detail.refundVnd?.let { add(getString(R.string.owner_booking_refund, NUMBER_FORMAT.format(it))) }
            detail.cancelReason?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("\n\n")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.owner_bookings_details_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun requestReason(title: Int, onConfirm: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.owner_decision_reason)
            minLines = 2
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setOwnerDialogInput(input)
            .setNegativeButton(R.string.common_cancel, null).setPositiveButton(R.string.confirm, null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val reason = input.text.toString().trim()
            if (reason.isBlank()) input.error = getString(R.string.owner_decision_reason_required)
            else { dialog.dismiss(); onConfirm(reason) }
        } }
        dialog.show()
    }

    private companion object {
        val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("vi"))
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi"))
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))
        val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"))
    }
}
