package com.example.booking_app.feature.owner.ui.statistics

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerStatisticsBinding
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsPeriod
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsResult
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerStatisticsFragment : Fragment(R.layout.fragment_owner_statistics) {
    private val viewModel: OwnerStatisticsViewModel by viewModels()
    private var _binding: FragmentOwnerStatisticsBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var selectedVenueId: String? = null
    private var venueCount = -1
    private var settingSpinner = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerStatisticsBinding.bind(view)
        binding.periodGroup.isSingleSelection = true
        binding.day.setOnClickListener { viewModel.setPeriod(OwnerStatisticsPeriod.DAY) }
        binding.week.setOnClickListener { viewModel.setPeriod(OwnerStatisticsPeriod.WEEK) }
        binding.custom.setOnClickListener { applyCustomRange() }
        binding.previous.setOnClickListener { viewModel.movePeriod(-1) }
        binding.next.setOnClickListener { viewModel.movePeriod(1) }
        binding.refresh.setOnClickListener { viewModel.refresh() }
        binding.venueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!settingSpinner) viewModel.uiState.value.venues.getOrNull(position)?.id?.let(viewModel::selectVenue)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: OwnerStatisticsUiState) {
        binding.loading.isVisible = state.isLoading
        binding.errorText.isVisible = !state.isLoading && state.error != null
        binding.errorText.text = state.error?.let { error ->
            if (error.contains("projection", true) || error.contains("not available", true)) getString(R.string.owner_statistics_contract_error) else error
        }
        binding.emptyText.isVisible = !state.isLoading && state.error == null && state.isEmpty
        binding.reportScroll.isVisible = !state.isLoading && state.error == null && state.result != null
        binding.day.isChecked = state.period == OwnerStatisticsPeriod.DAY
        binding.week.isChecked = state.period == OwnerStatisticsPeriod.WEEK
        binding.custom.isChecked = state.period == OwnerStatisticsPeriod.CUSTOM
        binding.navigationRow.isVisible = state.period != OwnerStatisticsPeriod.CUSTOM
        binding.customRange.isVisible = state.period == OwnerStatisticsPeriod.CUSTOM
        binding.periodLabel.text = when (state.period) {
            OwnerStatisticsPeriod.DAY -> state.selectedDate.format(DATE_FORMAT)
            OwnerStatisticsPeriod.WEEK -> {
                val monday = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                "${monday.format(DATE_FORMAT)} – ${monday.plusDays(6).format(DATE_FORMAT)}"
            }
            OwnerStatisticsPeriod.CUSTOM -> "${state.customStartDate?.format(DATE_FORMAT).orEmpty()} – ${state.customEndDate?.format(DATE_FORMAT).orEmpty()}"
        }
        renderVenues(state.venues, state.selectedVenueId)
        state.result?.let(::renderResult)
    }

    private fun applyCustomRange() {
        val start = runCatching { LocalDate.parse(binding.fromDate.text?.toString()?.trim().orEmpty()) }.getOrNull()
        val end = runCatching { LocalDate.parse(binding.toDate.text?.toString()?.trim().orEmpty()) }.getOrNull()
        if (start == null || end == null) Snackbar.make(binding.root, R.string.owner_statistics_date_format_error, Snackbar.LENGTH_SHORT).show()
        else viewModel.setCustomRange(start, end)
    }

    private fun renderVenues(venues: List<OwnerVenue>, selected: String?) {
        if (selectedVenueId == selected && venueCount == venues.size) return
        selectedVenueId = selected
        venueCount = venues.size
        settingSpinner = true
        binding.venueSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, venues.map { it.name })
        val index = venues.indexOfFirst { it.id == selected }
        if (index >= 0) binding.venueSpinner.setSelection(index, false)
        settingSpinner = false
    }

    private fun renderResult(result: OwnerStatisticsResult) {
        binding.revenue.text = getString(
            R.string.owner_statistics_revenue,
            NUMBER_FORMAT.format(result.revenue.depositedVnd),
            NUMBER_FORMAT.format(result.revenue.paidVnd),
            result.revenue.refundedBookingCount,
            NUMBER_FORMAT.format(result.revenue.refundedVnd),
        )
        binding.occupancy.text = getString(R.string.owner_statistics_occupancy, result.occupancyPercent)
        val freshness = result.computedAt.atZone(LOCAL_ZONE).format(DATE_TIME_FORMAT)
        binding.freshness.text = getString(if (result.isPartialPeriod) R.string.owner_statistics_partial_freshness else R.string.owner_statistics_freshness, freshness)
        binding.peakChart.submit(result.peakHours)
    }

    private companion object {
        val LOCAL_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi"))
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))
        val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"))
    }
}
