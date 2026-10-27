package com.example.booking_app.feature.booking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentBookingSlotGridBinding
import com.example.booking_app.databinding.ItemCourtColumnHeaderBinding
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.ui.adapter.DateItem
import com.example.booking_app.feature.booking.ui.adapter.DateSelectorAdapter
import com.example.booking_app.feature.booking.ui.adapter.SlotMatrixRowAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZoneId

@AndroidEntryPoint
class BookingSlotGridFragment : Fragment() {

    private var _binding: FragmentBookingSlotGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BookingSlotGridViewModel by viewModels()

    private val priceFormat = DecimalFormat("#,###")
    private val zoneVietnam = ZoneId.of("Asia/Ho_Chi_Minh")

    private lateinit var dateAdapter: DateSelectorAdapter
    private lateinit var matrixAdapter: SlotMatrixRowAdapter
    private var renderedCourts: List<Court> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingSlotGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupListeners()
        observeUiState()
        observeUiEffects()
    }

    override fun onResume() {
        super.onResume()
        // Q10: Kiểm tra và khôi phục lựa chọn nếu người dùng vừa quay lại sau khi đăng nhập
        viewModel.checkAndRestorePendingSelection()
    }

    private fun setupRecyclerViews() {
        // 1. Adapter chọn ngày (Dải ngày cuộn ngang)
        dateAdapter = DateSelectorAdapter { selectedDate ->
            if (viewModel.hasSelectedSlots()) {
                showChangeDateDialog(selectedDate)
            } else {
                viewModel.selectDate(selectedDate)
            }
        }
        binding.rvDateSelector.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = dateAdapter
        }

        // 2. Adapter lưới ma trận (Q3: Danh sách các hàng giờ 30 phút)
        matrixAdapter = SlotMatrixRowAdapter { clickedSlot ->
            viewModel.onSlotClicked(clickedSlot)
        }
        binding.rvSlotMatrixRows.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = matrixAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnEmptyRetry.setOnClickListener {
            viewModel.loadInitialData()
        }

        binding.bottomSummary.btnProceed.setOnClickListener {
            viewModel.onProceedClicked()
        }

        // Q13: Nút điều hướng sang Feature 041 "Đặt cố định theo tuần"
        binding.btnRecurringBooking.setOnClickListener {
            try {
                // Kiểm tra xem destination hoặc action của 041 có tồn tại trong nav graph không
                val recurringActionId = resources.getIdentifier("action_bookingSlotGrid_to_recurringBooking", "id", requireContext().packageName)
                if (recurringActionId != 0) {
                    findNavController().navigate(recurringActionId)
                } else {
                    Snackbar.make(
                        binding.root,
                        "Chức năng Đặt cố định theo tuần (Feature 041 - đang phát triển)",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Chức năng Đặt cố định theo tuần (Feature 041 - đang phát triển)",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: BookingSlotUiState) {
        // 1. Header (Q7: "Chọn khung giờ" + tên cơ sở + quận từ dữ liệu thật)
        binding.tvHeaderTitle.setText(R.string.booking_title)
        val venueName = state.venueName.ifEmpty { "Cơ sở ${state.venueId}" }
        val locationPart = if (state.districtName.isNotEmpty()) {
            "Quận ${state.districtName}"
        } else if (state.operatingHours.isNotEmpty()) {
            state.operatingHours
        } else {
            "TP. Hồ Chí Minh"
        }
        binding.tvVenueInfo.text = "$venueName · $locationPart"

        // 2. Loading indicator
        binding.loadingIndicator.isVisible = state.isLoading

        // 3. Dải chọn ngày (Hôm nay, Mai, T2..CN + chấm hôm nay)
        val today = LocalDate.now(zoneVietnam)
        val dateItems = state.dates.map { date ->
            DateItem(
                date = date,
                isSelected = date == state.selectedDate,
                isToday = date == today
            )
        }
        dateAdapter.submitList(dateItems)

        // 4. Cập nhật Banner Giờ Cao Điểm (Q5, 1c)
        if (!state.peakHourBannerText.isNullOrEmpty()) {
            binding.layoutPeakHourBanner.isVisible = true
            binding.tvPeakHourBannerText.text = androidx.core.text.HtmlCompat.fromHtml(
                state.peakHourBannerText,
                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } else {
            binding.layoutPeakHourBanner.isVisible = false
        }

        // 5. Banner Kín Sân
        binding.layoutFullyBookedBanner.isVisible = state.isFullyBooked
        binding.btnNextAvailableDate.setOnClickListener {
            viewModel.selectDate(state.selectedDate.plusDays(1))
        }

        // 6. Header các cột sân con (Ma trận lưới Q3)
        if (state.courts != renderedCourts) {
            renderedCourts = state.courts
            renderCourtHeaders(state.courts)
        }

        // 7. Lưới slot ma trận
        matrixAdapter.submitList(state.matrixRows)

        // 8. Trạng thái rỗng / Đóng cửa
        val showEmpty = state.isClosed || (state.courts.isEmpty() && !state.isLoading)
        binding.layoutEmptyState.isVisible = showEmpty
        binding.scrollViewMatrix.isVisible = !showEmpty
        if (state.isClosed) {
            binding.tvEmptyTitle.setText(R.string.booking_venue_closed_title)
            binding.tvEmptyMessage.text = state.errorMessage ?: getString(R.string.booking_venue_closed_message)
        } else if (state.courts.isEmpty() && !state.isLoading) {
            binding.tvEmptyTitle.setText(R.string.booking_no_courts_title)
            binding.tvEmptyMessage.setText(R.string.booking_no_courts_message)
        }

        // 9. Thanh tóm tắt đáy màn hình (Bottom Summary)
        if (state.selectedSlotsCount > 0) {
            if (state.selectionDetailSummary.isNotEmpty()) {
                binding.bottomSummary.tvSelectedSummary.text = state.selectionDetailSummary
            } else {
                val totalHours = state.selectedSlotsCount * 0.5
                val hoursString = if (totalHours % 1.0 == 0.0) {
                    "${totalHours.toInt()} giờ chơi"
                } else {
                    "$totalHours giờ chơi"
                }
                binding.bottomSummary.tvSelectedSummary.text = getString(
                    R.string.booking_summary_selected_count,
                    state.selectedSlotsCount,
                    hoursString
                )
            }
        } else {
            binding.bottomSummary.tvSelectedSummary.setText(R.string.booking_summary_placeholder)
        }

        binding.bottomSummary.tvTotalPrice.text = "${priceFormat.format(state.totalPrice)} đ"
        binding.bottomSummary.tvDepositAmount.text = "• Cọc 30%: ${priceFormat.format(state.depositAmount)} đ"

        // Nút "Giữ chỗ" (Q6, Q2: chỉ kích hoạt khi đủ >= 2 slot hợp lệ)
        binding.bottomSummary.btnProceed.isEnabled = state.canProceed && !state.isHolding
        binding.bottomSummary.btnProceed.isVisible = !state.isHolding
        binding.bottomSummary.progressProceed.isVisible = state.isHolding
    }

    private fun renderCourtHeaders(courts: List<Court>) {
        val container = binding.layoutCourtHeaders
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (court in courts) {
            val headerBinding = ItemCourtColumnHeaderBinding.inflate(inflater, container, false)
            headerBinding.tvCourtName.text = court.name
            headerBinding.tvCourtSurface.text = if (court.surfaceType.isNotEmpty()) court.surfaceType else "Thảm"
            container.addView(headerBinding.root)
        }
    }

    private fun observeUiEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is BookingSlotUiEffect.ShowConflictWarning -> {
                            Snackbar.make(binding.root, effect.message, Snackbar.LENGTH_LONG)
                                .setAction(R.string.booking_refresh_action) {
                                    viewModel.loadInitialData()
                                }
                                .show()
                        }

                        is BookingSlotUiEffect.ShowErrorSnackbar -> {
                            Snackbar.make(binding.root, effect.message, Snackbar.LENGTH_SHORT)
                                .setAnchorView(binding.bottomSummary.root)
                                .show()
                        }

                        is BookingSlotUiEffect.ShowToast -> {
                            Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
                        }

                        is BookingSlotUiEffect.NavigateToLogin -> {
                            Snackbar.make(binding.root, R.string.booking_login_required, Snackbar.LENGTH_LONG)
                                .setAction("Đăng nhập") {
                                    try {
                                        findNavController().navigate(R.id.nav_auth)
                                    } catch (e: Exception) {
                                        Snackbar.make(binding.root, "Đang mở màn hình đăng nhập...", Snackbar.LENGTH_SHORT).show()
                                    }
                                }
                                .show()
                        }

                        is BookingSlotUiEffect.NavigateToBookingSummary -> {
                            // Q4: Điều hướng sang BookingConfirmFragment thuộc 040
                            val bundle = Bundle().apply {
                                putParcelable("args", effect.args)
                            }
                            findNavController().navigate(
                                R.id.action_bookingSlotGrid_to_bookingConfirm,
                                bundle
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showChangeDateDialog(newDate: LocalDate) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.booking_change_date_dialog_title)
            .setMessage(R.string.booking_change_date_dialog_message)
            .setPositiveButton(R.string.booking_dialog_confirm) { _, _ ->
                viewModel.selectDate(newDate)
            }
            .setNegativeButton(R.string.booking_dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
