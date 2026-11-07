package com.example.booking_app.feature.booking.ui

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.databinding.FragmentBookingRecurringConfirmBinding
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking.domain.model.RecurringSession
import com.example.booking_app.feature.booking.domain.model.RecurringSessionStatus
import com.example.booking_app.feature.booking.domain.usecase.ReleaseRecurringHoldUseCase
import com.example.booking_app.feature.booking.ui.adapter.RecurringSessionSummaryAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BookingRecurringConfirmFragment : Fragment() {

    private var _binding: FragmentBookingRecurringConfirmBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var releaseRecurringHoldUseCase: ReleaseRecurringHoldUseCase

    @Inject
    lateinit var serverTimeOffsetProvider: ServerTimeOffsetProvider

    private var countDownTimer: CountDownTimer? = null
    private var holdArgs: HoldToPaymentArgs? = null
    private lateinit var summaryAdapter: RecurringSessionSummaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingRecurringConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        holdArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("args", HoldToPaymentArgs::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("args")
        }

        val args = holdArgs
        if (args == null) {
            Toast.makeText(requireContext(), "Thiếu thông tin chu kỳ đặt sân", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setupBackPressHandler()
        setupUI(args)
        startCountdown(args)
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmCancelAndRelease()
                }
            }
        )

        binding.toolbar.setNavigationOnClickListener {
            confirmCancelAndRelease()
        }

        binding.btnCancelHold.setOnClickListener {
            confirmCancelAndRelease()
        }

        binding.btnContinueToPayment.setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.booking_confirm_display_only_notice,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupUI(args: HoldToPaymentArgs) {
        binding.tvVenueName.text = args.venueName.ifEmpty { "Cơ sở ${args.venueId}" }
        binding.tvRecurringScheduleOverview.text = "${args.totalSessions} buổi đặt cố định"

        summaryAdapter = RecurringSessionSummaryAdapter()
        binding.rvConfirmSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvConfirmSessions.adapter = summaryAdapter

        val sessions = reconstructSessionsFromSlotIds(args)
        summaryAdapter.submitList(sessions)

        binding.tvSessionsListHeader.text = getString(R.string.recurring_sessions_list_title, args.totalSessions)
        binding.tvConfirmTotalSessions.text = "${args.totalSessions} buổi"

        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
        binding.tvConfirmSubtotal.text = "${formatter.format(args.subtotalAmount)} đ"

        val deposit = if (args.depositAmount > 0L) {
            args.depositAmount
        } else {
            val raw = (args.subtotalAmount * 30L) / 100L
            ((raw + 500L) / 1000L) * 1000L
        }
        binding.tvConfirmDeposit.text = "${formatter.format(deposit)} đ"
    }

    private fun reconstructSessionsFromSlotIds(args: HoldToPaymentArgs): List<RecurringSession> {
        val groupedSlots = args.slotIds.groupBy { slotId ->
            val parts = slotId.split("_")
            val court = if (parts.isNotEmpty()) parts[0] else ""
            val date = if (parts.size >= 2) parts[1] else ""
            "$court|$date"
        }

        val pricePerSession = if (args.totalSessions > 0) args.subtotalAmount / args.totalSessions else 0L

        return groupedSlots.mapNotNull { (key, slotList) ->
            val parts = key.split("|")
            val courtId = parts[0]
            val dateStr = parts[1]

            val date = runCatching {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
            }.getOrNull() ?: return@mapNotNull null

            val sortedSlots = slotList.sorted()
            val startHhmm = sortedSlots.firstOrNull()?.split("_")?.getOrNull(2) ?: "0000"
            val lastSlotHhmm = sortedSlots.lastOrNull()?.split("_")?.getOrNull(2) ?: "0000"
            val endHhmm = calculateSlotEndTime(lastSlotHhmm)

            RecurringSession(
                date = date,
                dayOfWeek = date.dayOfWeek.value,
                startTime = startHhmm,
                endTime = endHhmm,
                courtId = courtId,
                courtName = courtId,
                surfaceType = "",
                slotIds = slotList,
                price = pricePerSession,
                status = RecurringSessionStatus.HELD
            )
        }.sortedBy { it.date }
    }

    private fun calculateSlotEndTime(startTime: String): String {
        return try {
            val hour = startTime.substring(0, 2).toInt()
            val minute = startTime.substring(2, 4).toInt()
            val totalMinutes = hour * 60 + minute + 30
            val endHour = (totalMinutes / 60) % 24
            val endMinute = totalMinutes % 60
            String.format(Locale.US, "%02d%02d", endHour, endMinute)
        } catch (e: Exception) {
            startTime
        }
    }

    private fun startCountdown(args: HoldToPaymentArgs) {
        val millisRemaining = (args.holdExpiresAtMillis - serverTimeOffsetProvider.now()).coerceAtLeast(0L)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(millisRemaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (_binding == null) return
                val totalSec = millisUntilFinished / 1000L
                val minutes = totalSec / 60L
                val seconds = totalSec % 60L
                val formatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                binding.tvCountdownTimer.text = getString(R.string.recurring_timer_banner, formatted)
            }

            override fun onFinish() {
                if (_binding == null) return
                binding.tvCountdownTimer.text = getString(R.string.recurring_timer_banner, "00:00")
                Toast.makeText(
                    requireContext(),
                    "Hết thời gian giữ chỗ (5 phút)",
                    Toast.LENGTH_LONG
                ).show()
                executeReleaseHold()
                findNavController().popBackStack()
            }
        }.start()
    }

    private fun confirmCancelAndRelease() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recurring_cancel_dialog_title)
            .setMessage(R.string.recurring_cancel_dialog_message)
            .setPositiveButton(R.string.booking_dialog_confirm) { _, _ ->
                executeReleaseHold()
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.booking_dialog_cancel, null)
            .show()
    }

    private fun executeReleaseHold() {
        countDownTimer?.cancel()
        countDownTimer = null

        val args = holdArgs ?: return
        val userId = sessionManager.currentUid() ?: ""
        if (userId.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                releaseRecurringHoldUseCase(
                    recurringGroupId = args.bookingId,
                    slotIds = args.slotIds.toList(),
                    userId = userId
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        _binding = null
    }
}
