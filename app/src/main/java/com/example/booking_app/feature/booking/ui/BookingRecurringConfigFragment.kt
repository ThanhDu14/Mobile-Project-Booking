package com.example.booking_app.feature.booking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentBookingRecurringConfigBinding
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.ui.adapter.ConflictResolutionAdapter
import com.example.booking_app.feature.booking.ui.adapter.DayOfWeekChipAdapter
import com.example.booking_app.feature.booking.ui.adapter.RecurringSessionSummaryAdapter
import com.example.booking_app.feature.booking.ui.state.BookingRecurringUiEffect
import com.example.booking_app.feature.booking.ui.state.BookingRecurringUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class BookingRecurringConfigFragment : Fragment() {

    private var _binding: FragmentBookingRecurringConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BookingRecurringViewModel by viewModels()

    private lateinit var dayChipAdapter: DayOfWeekChipAdapter
    private lateinit var conflictAdapter: ConflictResolutionAdapter
    private lateinit var sessionSummaryAdapter: RecurringSessionSummaryAdapter

    private var loadedCourts: List<Court> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingRecurringConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupAdapters()
        setupDropdowns()
        setupChipGroups()
        setupActions()
        observeState()
        observeEffects()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupAdapters() {
        dayChipAdapter = DayOfWeekChipAdapter { selectedDays ->
            viewModel.onDaysSelected(selectedDays)
        }
        binding.rvDayChips.adapter = dayChipAdapter

        conflictAdapter = ConflictResolutionAdapter { session, action ->
            viewModel.onConflictActionSelected(session, action)
        }
        binding.rvConflicts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvConflicts.adapter = conflictAdapter

        sessionSummaryAdapter = RecurringSessionSummaryAdapter()
        binding.rvSessionSummary.adapter = sessionSummaryAdapter
    }

    private fun setupDropdowns() {
        val times = generateTimeSlots()
        val timeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, times)

        binding.actvStartTime.setAdapter(timeAdapter)
        binding.actvStartTime.setText("18:00", false)

        binding.actvEndTime.setAdapter(timeAdapter)
        binding.actvEndTime.setText("20:00", false)

        binding.actvStartTime.setOnItemClickListener { _, _, position, _ ->
            val selectedStart = times[position].replace(":", "")
            val selectedEnd = binding.actvEndTime.text.toString().replace(":", "")
            viewModel.onTimeRangeSelected(selectedStart, selectedEnd)
        }

        binding.actvEndTime.setOnItemClickListener { _, _, position, _ ->
            val selectedStart = binding.actvStartTime.text.toString().replace(":", "")
            val selectedEnd = times[position].replace(":", "")
            viewModel.onTimeRangeSelected(selectedStart, selectedEnd)
        }

        val durationOptions = listOf("4 tuần", "8 tuần", "12 tuần")
        val durationAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, durationOptions)
        binding.actvDurationWeeks.setAdapter(durationAdapter)
        binding.actvDurationWeeks.setText("8 tuần", false)

        binding.actvDurationWeeks.setOnItemClickListener { _, _, position, _ ->
            val weeks = when (position) {
                0 -> 4
                1 -> 8
                2 -> 12
                else -> 8
            }
            viewModel.onDurationWeeksSelected(weeks)
        }
    }

    private fun setupChipGroups() {
        binding.cgStartWeek.setOnCheckedStateChangeListener { _, checkedIds ->
            val startNextWeek = checkedIds.contains(binding.chipNextWeek.id)
            viewModel.onStartNextWeekSelected(startNextWeek)
        }
    }

    private fun setupActions() {
        binding.btnCheckAvailability.setOnClickListener {
            viewModel.checkAvailability()
        }

        binding.btnProceed.setOnClickListener {
            viewModel.proceedToHold()
        }

        binding.btnBackToGrid.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: BookingRecurringUiState) {
        // Update courts dropdown if changed
        if (state.availableCourts != loadedCourts && state.availableCourts.isNotEmpty()) {
            loadedCourts = state.availableCourts
            val courtNames = loadedCourts.map { "${it.name} (${it.surfaceType})" }
            val courtAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, courtNames)
            binding.actvPreferredCourt.setAdapter(courtAdapter)
            binding.actvPreferredCourt.setText(courtNames.first(), false)
            viewModel.onPreferredCourtSelected(loadedCourts.first().id)

            binding.actvPreferredCourt.setOnItemClickListener { _, _, position, _ ->
                viewModel.onPreferredCourtSelected(loadedCourts[position].id)
            }
        }

        // Loading and checking state
        binding.progressBarChecking.visibility = if (state.isCheckingAvailability) View.VISIBLE else View.GONE
        binding.btnCheckAvailability.isEnabled = !state.isCheckingAvailability && !state.isLoading

        // Render check results & bottom bar
        if (state.sessions.isNotEmpty()) {
            binding.layoutCheckResults.visibility = View.VISIBLE
            binding.layoutBottomBar.visibility = View.VISIBLE

            val validCount = state.validSessions.size
            val daysText = formatDaysText(state.config?.daysOfWeek ?: emptyList())
            val timeRangeText = state.config?.let {
                val start = formatTime(it.startTime)
                val end = formatTime(it.endTime)
                "$start – $end"
            } ?: ""
            binding.tvSummaryHeader.text = "$validCount buổi · $daysText · $timeRangeText"

            val conflictCount = state.conflictingSessions.size
            if (conflictCount > 0) {
                binding.tvConflictBadgePill.visibility = View.VISIBLE
                binding.tvConflictBadgePill.text = "$conflictCount trùng lịch"
                binding.rvConflicts.visibility = View.VISIBLE
                conflictAdapter.submitList(state.conflictingSessions)
            } else {
                binding.tvConflictBadgePill.visibility = View.GONE
                binding.rvConflicts.visibility = View.GONE
                conflictAdapter.submitList(emptyList())
            }

            sessionSummaryAdapter.submitList(state.sessions.take(4))
            val remainingSessions = state.sessions.size - 4
            if (remainingSessions > 0) {
                binding.tvMoreSessions.visibility = View.VISIBLE
                binding.tvMoreSessions.text = "… và $remainingSessions buổi nữa"
            } else {
                binding.tvMoreSessions.visibility = View.GONE
            }

            // Financial summary in bottom bar
            val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
            val subtotal = state.summary?.subtotalAmount ?: 0L
            binding.tvBottomTotalPrice.text = "${formatter.format(subtotal)} đ"
            binding.tvBottomDepositNote.text = "$validCount buổi · cọc 30% hoặc trả trước toàn bộ"

            if (state.isHolding) {
                binding.btnProceed.isEnabled = false
                binding.btnProceed.text = getString(R.string.recurring_btn_holding)
            } else {
                binding.btnProceed.isEnabled = state.canProceed
                binding.btnProceed.text = getString(R.string.recurring_btn_hold_chain)
            }

            // Warning if valid sessions < 4
            if (validCount < 4) {
                binding.cardMinSessionsWarning.visibility = View.VISIBLE
                binding.tvMinSessionsWarning.text = getString(R.string.recurring_min_sessions_warning, validCount)
            } else {
                binding.cardMinSessionsWarning.visibility = View.GONE
            }
        } else {
            binding.layoutCheckResults.visibility = View.GONE
            binding.layoutBottomBar.visibility = View.GONE
        }
    }

    private fun formatDaysText(days: List<Int>): String {
        val dayNames = days.sorted().map { day ->
            when (day) {
                1 -> "T2"
                2 -> "T3"
                3 -> "T4"
                4 -> "T5"
                5 -> "T6"
                6 -> "T7"
                else -> "CN"
            }
        }
        return when (dayNames.size) {
            0 -> ""
            1 -> dayNames.first()
            2 -> "${dayNames[0]} và ${dayNames[1]}"
            else -> dayNames.joinToString(", ")
        }
    }

    private fun formatTime(time: String): String {
        return if (time.length >= 4) {
            "${time.substring(0, 2)}:${time.substring(2, 4)}"
        } else {
            time
        }
    }

    private fun observeEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is BookingRecurringUiEffect.ShowToast -> {
                            Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
                        }
                        is BookingRecurringUiEffect.ShowErrorDialog -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(effect.title)
                                .setMessage(effect.message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                        is BookingRecurringUiEffect.NavigateToConfirm -> {
                            val state = viewModel.uiState.value
                            val validSessions = state.validSessions
                            val args = HoldToPaymentArgs(
                                bookingId = effect.recurringGroupId,
                                venueId = state.venueId,
                                venueName = state.venueName.ifEmpty { "Cơ sở cầu lông" },
                                isRecurring = true,
                                totalSessions = validSessions.size,
                                subtotalAmount = state.summary?.subtotalAmount ?: 0L,
                                slotIds = validSessions.flatMap { it.slotIds }.toTypedArray(),
                                holdExpiresAtMillis = System.currentTimeMillis() + state.holdCountdownSeconds * 1000L,
                                depositAmount = state.summary?.depositAmount ?: 0L
                            )
                            val bundle = Bundle().apply {
                                putParcelable("args", args)
                            }
                            findNavController().navigate(
                                R.id.action_recurringConfig_to_recurringConfirm,
                                bundle
                            )
                        }
                        is BookingRecurringUiEffect.NavigateBackToGrid -> {
                            findNavController().navigateUp()
                        }
                        is BookingRecurringUiEffect.NavigateToLogin -> {
                            Toast.makeText(
                                requireContext(),
                                "Vui lòng đăng nhập để tiến hành giữ chỗ",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun generateTimeSlots(): List<String> {
        val result = mutableListOf<String>()
        for (h in 5..23) {
            result.add(String.format(Locale.US, "%02d:00", h))
            result.add(String.format(Locale.US, "%02d:30", h))
        }
        result.add("24:00")
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
