package com.example.booking_app.feature.booking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentBookingSlotGridBinding
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.ui.adapter.CourtTabAdapter
import com.example.booking_app.feature.booking.ui.adapter.CourtTabItem
import com.example.booking_app.feature.booking.ui.adapter.DateItem
import com.example.booking_app.feature.booking.ui.adapter.DateSelectorAdapter
import com.example.booking_app.feature.booking.ui.adapter.SlotGridAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

@AndroidEntryPoint
class BookingSlotGridFragment : Fragment() {

    private var _binding: FragmentBookingSlotGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BookingSlotGridViewModel by viewModels()

    private val priceFormat = DecimalFormat("#,###", DecimalFormatSymbols(Locale.GERMANY))

    private lateinit var dateAdapter: DateSelectorAdapter
    private lateinit var courtAdapter: CourtTabAdapter
    private lateinit var slotAdapter: SlotGridAdapter

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

    private fun setupRecyclerViews() {
        // 1. Adapter chọn ngày
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

        // 2. Adapter chọn sân con
        courtAdapter = CourtTabAdapter { selectedCourt ->
            if (viewModel.hasSelectedSlots()) {
                showChangeCourtDialog(selectedCourt)
            } else {
                viewModel.selectCourt(selectedCourt)
            }
        }
        binding.rvCourtTabs.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = courtAdapter
        }

        // 3. Adapter lưới khung giờ 30 phút (3 cột)
        slotAdapter = SlotGridAdapter { clickedSlot ->
            viewModel.onSlotClicked(clickedSlot)
        }
        binding.rvSlotGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = slotAdapter
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
        val context = requireContext()

        // 1. Header & Giờ hoạt động
        val venueDisplay = state.venueName.ifEmpty { "Cơ sở ${state.venueId}" }
        val hoursDisplay = state.operatingHours.ifEmpty { "06:00 - 22:00" }
        binding.tvVenueInfo.text = "$venueDisplay • $hoursDisplay"

        // 2. Loading
        binding.loadingIndicator.isVisible = state.isLoading

        // 3. Danh sách ngày
        val today = LocalDate.now()
        val dateItems = state.dates.map { date ->
            DateItem(
                date = date,
                isSelected = date == state.selectedDate,
                isToday = date == today
            )
        }
        dateAdapter.submitList(dateItems)

        // 4. Danh sách sân con
        val courtItems = state.courts.map { court ->
            CourtTabItem(
                court = court,
                isSelected = court.id == state.selectedCourtId
            )
        }
        courtAdapter.submitList(courtItems)

        // 5. Lưới slot
        slotAdapter.submitList(state.slots)

        // 6. Trạng thái rỗng / Đóng cửa
        val showEmpty = state.isClosed || (state.courts.isEmpty() && !state.isLoading)
        binding.layoutEmptyState.isVisible = showEmpty
        binding.rvSlotGrid.isVisible = !showEmpty
        if (state.isClosed) {
            binding.tvEmptyTitle.setText(R.string.booking_venue_closed_title)
            binding.tvEmptyMessage.setText(R.string.booking_venue_closed_message)
        } else if (state.courts.isEmpty() && !state.isLoading) {
            binding.tvEmptyTitle.setText(R.string.booking_no_courts_title)
            binding.tvEmptyMessage.setText(R.string.booking_no_courts_message)
        }

        // 7. Thanh tóm tắt đáy màn hình
        if (state.selectedSlotsCount > 0) {
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
        } else {
            binding.bottomSummary.tvSelectedSummary.setText(R.string.booking_summary_placeholder)
        }

        binding.bottomSummary.tvTotalPrice.text = "${priceFormat.format(state.totalPrice)}đ"
        binding.bottomSummary.tvDepositAmount.text = "• Cọc 30%: ${priceFormat.format(state.depositAmount)}đ"

        binding.bottomSummary.btnProceed.isEnabled = state.canProceed && !state.isHolding
        binding.bottomSummary.btnProceed.isVisible = !state.isHolding
        binding.bottomSummary.progressProceed.isVisible = state.isHolding
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
                                    // Điều hướng tới Login (Spec 010)
                                    findNavController().navigate(R.id.nav_auth)
                                }
                                .show()
                        }

                        is BookingSlotUiEffect.NavigateToBookingSummary -> {
                            Toast.makeText(
                                requireContext(),
                                "Giữ chỗ thành công 5 phút! Mã đơn: ${effect.args.bookingId}",
                                Toast.LENGTH_LONG
                            ).show()
                            // Điều hướng sang màn hình thanh toán (Spec 050) sẽ được kết nối ở Phase 5 (T035)
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

    private fun showChangeCourtDialog(newCourt: Court) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.booking_change_court_dialog_title)
            .setMessage(R.string.booking_change_court_dialog_message)
            .setPositiveButton(R.string.booking_dialog_confirm) { _, _ ->
                viewModel.selectCourt(newCourt)
            }
            .setNegativeButton(R.string.booking_dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
