package com.example.booking_app.feature.booking.ui

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
import com.example.booking_app.R
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.databinding.FragmentBookingConfirmBinding
import com.example.booking_app.databinding.ItemConfirmCourtSlotsBinding
import com.example.booking_app.feature.booking.domain.usecase.ReleaseHoldUseCase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BookingConfirmFragment : Fragment() {

    private var _binding: FragmentBookingConfirmBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var releaseHoldUseCase: ReleaseHoldUseCase

    private var countDownTimer: CountDownTimer? = null
    private var holdArgs: HoldToPaymentArgs? = null
    private val currencyFormatter = DecimalFormat("#,###", java.text.DecimalFormatSymbols(Locale.GERMANY))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        holdArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("args", HoldToPaymentArgs::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("args")
        }
        val args = holdArgs ?: run {
            Toast.makeText(requireContext(), "Thiếu thông tin giữ chỗ", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setupUI(args)
        startCountdown(args.holdExpiresAtMillis)

        // Bắt sự kiện nút back hệ thống
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleCancelAndRelease()
                }
            }
        )
    }

    private fun setupUI(args: HoldToPaymentArgs) {
        binding.tvConfirmVenueName.text = args.venueName.ifEmpty { "Cơ sở ${args.venueId}" }
        binding.tvConfirmVenueAddress.text = args.venueAddress.ifEmpty {
            if (args.districtName.isNotEmpty()) "Quận ${args.districtName}, TP. Hồ Chí Minh" else "TP. Hồ Chí Minh"
        }

        binding.tvConfirmTotalPrice.text = "${currencyFormatter.format(args.subtotalAmount)} đ"
        val depositAmount = (args.subtotalAmount * 0.3).toLong()
        binding.tvConfirmDepositAmount.text = "${currencyFormatter.format(depositAmount)} đ"

        // Hiển thị danh sách các slot đã chọn gom theo sân
        displaySelectedSlots(args.slotIds)

        binding.btnConfirmBack.setOnClickListener {
            handleCancelAndRelease()
        }

        binding.btnConfirmCancel.setOnClickListener {
            handleCancelAndRelease()
        }

        binding.btnConfirmPay.setOnClickListener {
            handleProceedToPayment(args)
        }
    }

    private fun displaySelectedSlots(slotIds: Array<String>) {
        val container = binding.layoutSelectedSlotsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        // Phân tích slotId: {courtId}_{yyyyMMdd}_{startTime}
        // Gom nhóm theo courtId
        val courtGroups = slotIds.groupBy { slotId ->
            val parts = slotId.split("_")
            if (parts.isNotEmpty()) parts[0] else "court"
        }

        var courtIndex = 1
        for ((courtId, courtSlotIds) in courtGroups) {
            val itemBinding = ItemConfirmCourtSlotsBinding.inflate(inflater, container, false)
            itemBinding.tvCourtBadge.text = "S$courtIndex"
            itemBinding.tvCourtName.text = "Sân $courtIndex"

            val times = courtSlotIds.mapNotNull { id ->
                val parts = id.split("_")
                if (parts.size >= 3) parts[2] else null
            }.sorted()

            val timeRangeText = if (times.isNotEmpty()) {
                val start = formatHhmm(times.first())
                val endSlotStart = times.last()
                val end = add30Minutes(endSlotStart)
                val hoursCount = times.size * 0.5
                "$start - $end (${hoursCount} giờ chơi)"
            } else {
                "${courtSlotIds.size} khung giờ"
            }

            itemBinding.tvSlotDetails.text = timeRangeText
            val courtSubtotal = if (slotIds.isNotEmpty()) {
                (holdArgs?.subtotalAmount ?: 0L) * courtSlotIds.size / slotIds.size
            } else 0L
            itemBinding.tvCourtSubtotal.text = "${currencyFormatter.format(courtSubtotal)} đ"

            container.addView(itemBinding.root)
            courtIndex++
        }
    }

    private fun startCountdown(holdExpiresAtMillis: Long) {
        countDownTimer?.cancel()
        val remainingMillis = holdExpiresAtMillis - System.currentTimeMillis()

        if (remainingMillis <= 0) {
            onCountdownTimeout()
            return
        }

        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val formatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                if (_binding != null) {
                    binding.tvCountdownText.text = getString(R.string.booking_confirm_countdown_banner, formatted)
                }
            }

            override fun onFinish() {
                if (_binding != null) {
                    onCountdownTimeout()
                }
            }
        }.start()
    }

    private fun onCountdownTimeout() {
        countDownTimer?.cancel()
        if (!isAdded) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.booking_confirm_timeout_title)
            .setMessage(R.string.booking_confirm_timeout_message)
            .setPositiveButton(R.string.booking_confirm_timeout_ok) { dialog, _ ->
                dialog.dismiss()
                releaseHoldSilently()
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleCancelAndRelease() {
        countDownTimer?.cancel()
        val args = holdArgs
        val userId = sessionManager.currentUid() ?: ""
        if (args != null && userId.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                releaseHoldUseCase(
                    venueId = args.venueId,
                    slotIds = args.slotIds.toList(),
                    userId = userId
                )
            }
        }
        findNavController().popBackStack()
    }

    private fun releaseHoldSilently() {
        val args = holdArgs ?: return
        val userId = sessionManager.currentUid() ?: ""
        if (userId.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                releaseHoldUseCase(
                    venueId = args.venueId,
                    slotIds = args.slotIds.toList(),
                    userId = userId
                )
            }
        }
    }

    private fun handleProceedToPayment(args: HoldToPaymentArgs) {
        // Chuyển sang màn hình thanh toán (Feature 050)
        // Nếu destination 050 chưa có trong nav graph nhánh này thì thông báo rõ ràng theo Q4
        try {
            val paymentActionId = resources.getIdentifier("action_bookingConfirm_to_payment", "id", requireContext().packageName)
            if (paymentActionId != 0) {
                findNavController().navigate(paymentActionId)
            } else {
                Snackbar.make(
                    binding.root,
                    "Chuyển sang bước thanh toán (Feature 050). Đang hoàn thiện tích hợp.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                "Chuyển sang bước thanh toán (Feature 050): ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun formatHhmm(hhmm: String): String {
        if (hhmm.length < 4) return hhmm
        return "${hhmm.substring(0, 2)}:${hhmm.substring(2, 4)}"
    }

    private fun add30Minutes(hhmm: String): String {
        if (hhmm.length < 4) return hhmm
        val h = hhmm.substring(0, 2).toIntOrNull() ?: return hhmm
        val m = hhmm.substring(2, 4).toIntOrNull() ?: return hhmm
        val totalM = h * 60 + m + 30
        val newH = (totalM / 60) % 24
        val newM = totalM % 60
        return String.format(Locale.US, "%02d:%02d", newH, newM)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        _binding = null
    }
}
