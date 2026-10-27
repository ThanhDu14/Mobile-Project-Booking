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
import java.time.DayOfWeek
import java.time.LocalDate
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

    // Trạng thái chọn phương thức thanh toán display-only
    private enum class PaymentOption { PAY_AT_VENUE, DEPOSIT, WALLET }
    private var selectedPaymentOption = PaymentOption.PAY_AT_VENUE

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

        // Bắt sự kiện nút back hệ thống (nhả hold theo US3)
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
        // 1. Thẻ cơ sở
        binding.tvConfirmVenueName.text = args.venueName.ifEmpty { "Cơ sở ${args.venueId}" }
        binding.tvConfirmVenueAddress.text = args.venueAddress.ifEmpty {
            if (args.districtName.isNotEmpty()) "Quận ${args.districtName}, TP. Hồ Chí Minh" else "TP. Hồ Chí Minh"
        }

        // 2. Hiển thị danh sách các slot đã chọn gom theo sân (2c: định dạng ngày chuẩn T4, 28/10, bỏ hậu tố giờ chơi)
        displaySelectedSlots(args)

        // Link "Đổi khung giờ" (2d: bấm về lại lưới, không nhả hold)
        binding.btnChangeSlots.setOnClickListener {
            findNavController().popBackStack()
        }

        // 3. Phương thức thanh toán (3b: 3 radio options)
        val depositAmount = (args.subtotalAmount * 0.3).toLong()
        binding.tvDepositDescription.text = getString(
            R.string.booking_payment_deposit_desc,
            currencyFormatter.format(depositAmount)
        )
        binding.tvWalletDescription.text = getString(
            R.string.booking_payment_wallet_desc,
            currencyFormatter.format(args.subtotalAmount)
        )

        updatePaymentRadioSelection(PaymentOption.PAY_AT_VENUE)

        binding.rowPayAtVenue.setOnClickListener {
            updatePaymentRadioSelection(PaymentOption.PAY_AT_VENUE)
        }
        binding.rowDeposit.setOnClickListener {
            updatePaymentRadioSelection(PaymentOption.DEPOSIT)
        }
        binding.rowWallet.setOnClickListener {
            updatePaymentRadioSelection(PaymentOption.WALLET)
        }

        // 4. Thanh số dư ví (3c: placeholder 0 đ kèm TODO(050))
        binding.tvWalletBalance.text = getString(R.string.booking_wallet_balance_format, "0")
        binding.btnTopUpWallet.setOnClickListener {
            // TODO(050): Tích hợp màn hình nạp ví giả lập khi feature 050 hoàn thiện
            Snackbar.make(binding.root, "Nạp ví giả lập sẽ được tích hợp ở feature 050.", Snackbar.LENGTH_SHORT).show()
        }

        // 5. Hàng mã giảm giá (3d: display-only kèm TODO(050))
        binding.layoutVoucherRow.setOnClickListener {
            // TODO(050): Tích hợp bottom sheet áp dụng mã giảm giá khi feature 050 hoàn thiện
            Snackbar.make(binding.root, "Áp dụng mã giảm giá sẽ được tích hợp ở feature 050.", Snackbar.LENGTH_SHORT).show()
        }

        // 6. Thẻ breakdown (3e: Tạm tính, Giảm giá 0đ, Tổng cộng)
        binding.tvBreakdownSlotsLabel.text = getString(R.string.booking_breakdown_subtotal_format, args.slotIds.size)
        binding.tvBreakdownSubtotal.text = "${currencyFormatter.format(args.subtotalAmount)} đ"
        binding.tvBreakdownDiscount.text = "0 đ" // TODO(050): Trừ tiền voucher khi feature 050 hoàn thiện
        binding.tvBreakdownTotal.text = "${currencyFormatter.format(args.subtotalAmount)} đ"

        // 7. Bottom bar (3g)
        binding.tvBottomTotalPrice.text = "${currencyFormatter.format(args.subtotalAmount)} đ"
        binding.btnConfirmBooking.setOnClickListener {
            // TODO(050): Tạo đơn và gọi API thanh toán thật khi feature 050 hoàn thiện
            Snackbar.make(
                binding.root,
                getString(R.string.booking_confirm_display_only_notice),
                Snackbar.LENGTH_LONG
            ).show()
        }

        // Mũi tên back trên top bar (US3: nhả slot khi rời màn hình)
        binding.btnConfirmBack.setOnClickListener {
            handleCancelAndRelease()
        }
    }

    private fun updatePaymentRadioSelection(option: PaymentOption) {
        selectedPaymentOption = option
        binding.rbPayAtVenue.isChecked = (option == PaymentOption.PAY_AT_VENUE)
        binding.rbDeposit.isChecked = (option == PaymentOption.DEPOSIT)
        binding.rbWallet.isChecked = (option == PaymentOption.WALLET)
    }

    private fun displaySelectedSlots(args: HoldToPaymentArgs) {
        val container = binding.layoutSelectedSlotsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        if (args.courtDetails.isNotEmpty()) {
            for (detail in args.courtDetails) {
                // Format: "$courtBadge|$courtName|$surfaceType|$dateText|$timeRangeText|$subtotal"
                val parts = detail.split("|")
                val itemBinding = ItemConfirmCourtSlotsBinding.inflate(inflater, container, false)
                val badge = if (parts.isNotEmpty()) parts[0] else "S1"
                val name = if (parts.size > 1) parts[1] else "Sân"
                val surface = if (parts.size > 2) parts[2] else "Thảm"
                val dateText = if (parts.size > 3) parts[3] else ""
                val timeRange = if (parts.size > 4) parts[4] else ""
                val subtotal = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L

                itemBinding.tvCourtBadge.text = badge
                itemBinding.tvCourtName.text = "$name · $surface"
                // 2. Format: "T4, 28/10 · 06:30 – 07:00", không có hậu tố giờ chơi
                itemBinding.tvSlotDetails.text = if (dateText.isNotEmpty()) {
                    "$dateText · $timeRange"
                } else {
                    timeRange
                }
                itemBinding.tvCourtSubtotal.text = "${currencyFormatter.format(subtotal)} đ"
                container.addView(itemBinding.root)
            }
        } else {
            // Fallback: Phân tích slotId: {courtId}_{yyyyMMdd hoặc yyyy-MM-dd}_{startTime}
            val courtGroups = args.slotIds.groupBy { slotId ->
                val parts = slotId.split("_")
                if (parts.isNotEmpty()) parts[0] else "court"
            }

            var courtIndex = 1
            for ((_, courtSlotIds) in courtGroups) {
                val itemBinding = ItemConfirmCourtSlotsBinding.inflate(inflater, container, false)
                itemBinding.tvCourtBadge.text = "S$courtIndex"
                itemBinding.tvCourtName.text = "Sân $courtIndex · Thảm"

                var dateText = ""
                val times = courtSlotIds.mapNotNull { id ->
                    val parts = id.split("_")
                    if (parts.size >= 3) {
                        if (dateText.isEmpty()) {
                            dateText = formatDateString(parts[1])
                        }
                        parts[2]
                    } else null
                }.sorted()

                val timeRangeText = if (times.isNotEmpty()) {
                    val start = formatHhmm(times.first())
                    val endSlotStart = times.last()
                    val end = add30Minutes(endSlotStart)
                    if (dateText.isNotEmpty()) "$dateText · $start – $end" else "$start – $end"
                } else {
                    "${courtSlotIds.size} khung giờ"
                }

                itemBinding.tvSlotDetails.text = timeRangeText
                val courtSubtotal = if (args.slotIds.isNotEmpty()) {
                    args.subtotalAmount * courtSlotIds.size / args.slotIds.size
                } else 0L
                itemBinding.tvCourtSubtotal.text = "${currencyFormatter.format(courtSubtotal)} đ"

                container.addView(itemBinding.root)
                courtIndex++
            }
        }
    }

    private fun formatDateString(dateStr: String): String {
        return try {
            val localDate = if (dateStr.length == 8 && dateStr.all { it.isDigit() }) {
                val y = dateStr.substring(0, 4).toInt()
                val m = dateStr.substring(4, 6).toInt()
                val d = dateStr.substring(6, 8).toInt()
                LocalDate.of(y, m, d)
            } else {
                LocalDate.parse(dateStr)
            }
            val dow = when (localDate.dayOfWeek) {
                DayOfWeek.MONDAY -> "T2"
                DayOfWeek.TUESDAY -> "T3"
                DayOfWeek.WEDNESDAY -> "T4"
                DayOfWeek.THURSDAY -> "T5"
                DayOfWeek.FRIDAY -> "T6"
                DayOfWeek.SATURDAY -> "T7"
                DayOfWeek.SUNDAY -> "CN"
                else -> ""
            }
            val dayMonth = String.format(Locale.US, "%02d/%02d", localDate.dayOfMonth, localDate.monthValue)
            "$dow, $dayMonth"
        } catch (e: Exception) {
            dateStr
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
                    binding.tvCountdownText.text = "Đang giữ chỗ cho bạn. Hoàn tất trước khi hết giờ."
                    binding.tvCountdownDigits.text = formatted
                    binding.tvBottomCountdown.text = formatted
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
