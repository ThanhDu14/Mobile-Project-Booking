package com.example.booking_app.feature.owner.ui.calendar

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerCalendarBinding
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerSlot
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerCalendarFragment : Fragment(R.layout.fragment_owner_calendar) {
    private val viewModel: OwnerCalendarViewModel by viewModels()
    private var _binding: FragmentOwnerCalendarBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var spinnerVenueId: String? = null
    private var renderedVenues: List<OwnerVenue> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerCalendarBinding.bind(view)
        binding.dayMode.isCheckable = true
        binding.weekMode.isCheckable = true
        binding.dayMode.setOnClickListener { viewModel.setMode(OwnerCalendarMode.DAY) }
        binding.weekMode.setOnClickListener { viewModel.setMode(OwnerCalendarMode.WEEK) }
        binding.previousPeriod.setOnClickListener { viewModel.movePeriod(-1) }
        binding.nextPeriod.setOnClickListener { viewModel.movePeriod(1) }
        binding.refresh.setOnClickListener { viewModel.refresh() }
        binding.venueSpinner.onItemSelectedListener = VenueSelectionListener { position ->
            renderedVenues.getOrNull(position)?.id?.let(viewModel::selectVenue)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) viewModel.refresh()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: OwnerCalendarUiState) {
        binding.loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.errorText.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.errorText.text = state.error
        binding.emptyText.visibility = if (!state.isLoading && state.error == null && state.isEmpty) View.VISIBLE else View.GONE
        binding.calendarScroll.visibility = if (!state.isLoading && state.error == null && !state.isEmpty) View.VISIBLE else View.GONE
        binding.dayMode.isChecked = state.mode == OwnerCalendarMode.DAY
        binding.weekMode.isChecked = state.mode == OwnerCalendarMode.WEEK
        binding.periodLabel.text = state.periodLabel()
        renderVenues(state.venues, state.selectedVenueId)
        if (!state.isLoading && state.error == null && !state.isEmpty) {
            binding.calendarContent.removeAllViews()
            if (state.mode == OwnerCalendarMode.DAY) renderDayGrid(state) else renderWeekSummary(state)
        }
    }

    private fun renderVenues(venues: List<OwnerVenue>, selectedId: String?) {
        val context = context ?: return
        if (spinnerVenueId == selectedId && (binding.venueSpinner.adapter?.count ?: -1) == venues.size) return
        spinnerVenueId = selectedId
        renderedVenues = venues
        binding.venueSpinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            venues.map(OwnerVenue::name),
        )
        val selectedIndex = venues.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0) binding.venueSpinner.setSelection(selectedIndex, false)
    }

    private fun renderDayGrid(state: OwnerCalendarUiState) {
        val context = context ?: return
        val slots = state.slotsByDate[state.selectedDate].orEmpty()
        val courts = state.courts
        val slotTimes = slots.mapNotNull { slot -> slot.startAt?.toDate()?.toInstant()?.atZone(VIETNAM_ZONE)?.toLocalTime() }
            .distinct().sorted()
        addGridRow(
            cells = listOf("Giờ") + courts.map(OwnerCourt::name),
            heading = true,
        )
        slotTimes.forEach { time ->
            val cells = mutableListOf("${time.format(TIME_FORMAT)}")
            courts.forEach { court ->
                val slot = slots.firstOrNull { candidate ->
                    candidate.courtId == court.id && candidate.startAt?.toDate()?.toInstant()
                        ?.atZone(VIETNAM_ZONE)?.toLocalTime() == time
                }
                cells += slot?.let(::slotLabel).orEmpty()
            }
            addGridRow(cells, heading = false, slots = courts.map { court ->
                slots.firstOrNull { candidate ->
                    candidate.courtId == court.id && candidate.startAt?.toDate()?.toInstant()
                        ?.atZone(VIETNAM_ZONE)?.toLocalTime() == time
                }
            })
        }
        if (slotTimes.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.emptyText.setText(R.string.owner_calendar_empty)
        }
    }

    private fun renderWeekSummary(state: OwnerCalendarUiState) {
        val monday = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0L..6L).forEach { offset ->
            val date = monday.plusDays(offset)
            val slots = state.slotsByDate[date].orEmpty()
            val booked = slots.count { it.status == "BOOKED" }
            val dropIns = slots.count { it.status == "BLOCKED" && it.blockReason == "DROP_IN" }
            val held = slots.count { it.status == "HOLD" || it.status == "HELD" }
            val summary = listOfNotNull(
                booked.takeIf { it > 0 }?.let { getString(R.string.owner_calendar_booked_count, it) },
                dropIns.takeIf { it > 0 }?.let { getString(R.string.owner_calendar_drop_in_count, it) },
                held.takeIf { it > 0 }?.let { getString(R.string.owner_calendar_held_count, it) },
            ).ifEmpty { listOf(getString(R.string.owner_calendar_no_busy_slots)) }
                .joinToString(getString(R.string.owner_calendar_summary_separator))
            val card = MaterialCardView(requireContext()).apply {
                radius = resources.getDimension(R.dimen.radius_md)
                strokeWidth = dp(1)
                strokeColor = ContextCompat.getColor(context, R.color.sn_line)
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                contentDescription = getString(
                    R.string.owner_calendar_week_day_accessibility,
                    date.format(DATE_FORMAT),
                    summary,
                )
                setOnClickListener {
                    viewModel.selectDate(date)
                    viewModel.setMode(OwnerCalendarMode.DAY)
                }
            }
            val text = TextView(context).apply {
                text = getString(R.string.owner_calendar_week_day_summary, date.format(DATE_FORMAT), summary)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.sn_ink))
                setPadding(dp(16), dp(16), dp(16), dp(16))
                minHeight = dp(72)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            card.addView(text)
            binding.calendarContent.addView(card, matchWidth().apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun addGridRow(cells: List<String>, heading: Boolean, slots: List<OwnerSlot?> = emptyList()) {
        val context = context ?: return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        cells.forEachIndexed { index, value ->
            val cell = TextView(context).apply {
                text = value
                textSize = if (heading) 14f else 12f
                typeface = if (heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(ContextCompat.getColor(context, R.color.sn_ink))
                gravity = android.view.Gravity.CENTER
                minHeight = dp(if (heading) 48 else 64)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = cellBackground(if (heading) null else slots.getOrNull(index - 1))
                contentDescription = if (index == 0 || heading) value else {
                    val slot = slots.getOrNull(index - 1)
                    "${cells.first()} ${value.ifBlank { "Không có lịch" }}"
                }
            }
            val params = LinearLayout.LayoutParams(if (index == 0) dp(72) else dp(108), ViewGroup.LayoutParams.WRAP_CONTENT)
            row.addView(cell, params)
        }
        binding.calendarContent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun cellBackground(slot: OwnerSlot?): GradientDrawable {
        val color = when {
            slot == null -> R.color.sn_surface_raised
            slot.status == "BOOKED" -> R.color.sn_court
            slot.status == "BLOCKED" && slot.blockReason == "DROP_IN" -> R.color.sn_court_container
            slot.status in setOf("HOLD", "HELD") -> R.color.sn_lamp_container
            slot.status == "BLOCKED" -> R.color.sn_surface_sunken
            else -> R.color.sn_surface_raised
        }
        return GradientDrawable().apply {
            setColor(ContextCompat.getColor(requireContext(), color))
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), ContextCompat.getColor(requireContext(), R.color.sn_line))
        }
    }

    private fun slotLabel(slot: OwnerSlot): String = when {
        slot.status == "BOOKED" -> "Đã đặt"
        slot.status == "BLOCKED" && slot.blockReason == "DROP_IN" -> "Vãng lai"
        slot.status in setOf("HOLD", "HELD") -> "Đang giữ"
        slot.status == "BLOCKED" -> "Đã khóa"
        slot.status == "AVAILABLE" -> "Trống"
        else -> slot.status
    }

    private fun OwnerCalendarUiState.periodLabel(): String = if (mode == OwnerCalendarMode.DAY) {
        selectedDate.format(LONG_DATE_FORMAT).replaceFirstChar { it.uppercase() }
    } else {
        val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        "${monday.format(DATE_FORMAT)} – ${sunday.format(DATE_FORMAT)}"
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class VenueSelectionListener(
        private val onSelected: (Int) -> Unit,
    ) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (parent?.adapter is ArrayAdapter<*>) onSelected(position)
        }
    }

    private companion object {
        val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi"))
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("vi"))
        val LONG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi"))
    }
}
