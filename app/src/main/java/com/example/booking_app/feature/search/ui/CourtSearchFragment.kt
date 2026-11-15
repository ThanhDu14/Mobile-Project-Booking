package com.example.booking_app.feature.search.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.databinding.DialogCourtSearchPriceBinding
import com.example.booking_app.databinding.DialogCourtSearchWindowBinding
import com.example.booking_app.databinding.BottomSheetCourtSearchFilterBinding
import com.example.booking_app.databinding.FragmentCourtSearchBinding
import com.example.booking_app.databinding.ItemSearchRecommendationBinding
import com.example.booking_app.databinding.BottomSheetSearchSourcesBinding
import com.example.booking_app.databinding.DialogSearchExplanationBinding
import com.example.booking_app.databinding.ItemSearchClaimBinding
import com.example.booking_app.databinding.ItemSearchSourceBinding
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import com.example.booking_app.databinding.ViewCourtSearchControlsBinding
import com.example.booking_app.feature.search.domain.CourtSearchSort
import com.example.booking_app.feature.search.domain.ReviewPreference
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class CourtSearchFragment : Fragment(R.layout.fragment_court_search) {
    private val viewModel: CourtSearchViewModel by viewModels()
    private var aiExpanded = false
    @Inject lateinit var avatarSource: AccountAvatarSource
    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator
    @Inject lateinit var authNavigator: AuthNavigator

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) viewModel.sort(CourtSearchSort.NEAREST)
        else view?.let { Snackbar.make(it, R.string.search_location_unavailable, Snackbar.LENGTH_SHORT).show() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCourtSearchBinding.bind(view)
        val manager = LinearLayoutManager(requireContext())
        binding.results.layoutManager = manager
        val controls = ViewCourtSearchControlsBinding.inflate(layoutInflater, binding.results, false)
        binding.header.tabTitle.setText(R.string.nav_tab_search)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)

        val venues = VenueCardAdapter(::openVenue)
        venues.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.results.adapter = ConcatAdapter(ControlsAdapter(controls.root), venues)
        binding.results.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && manager.findLastVisibleItemPosition() >= venues.itemCount - 4) viewModel.loadMore()
            }
        })

        val initial = viewModel.state.value
        controls.keyword.setText(initial.criteria.keyword)
        controls.aiInput.setText(initial.aiInput)
        controls.keyword.doAfterTextChanged { viewModel.keyword(it.toString()) }
        controls.aiInput.doAfterTextChanged { viewModel.input(it.toString()) }
        controls.filtersKeptClear.setOnClickListener { viewModel.clearFilters() }
        controls.keyword.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            hideKeyboard(controls.keyword)
            viewModel.retry()
            true
        }
        controls.aiInput.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEND || !controls.aiSubmit.isEnabled) return@setOnEditorActionListener false
            hideKeyboard(controls.aiInput)
            viewModel.submitAi()
            true
        }
        controls.aiSubmit.setOnClickListener {
            if (viewModel.state.value.guest) authNavigator.openLogin(findNavController())
            else {
                hideKeyboard(controls.aiInput)
                viewModel.submitAi()
            }
        }
        controls.district.setOnClickListener { chooseDistrict() }
        controls.allFilters.setOnClickListener { showFilters() }
        controls.price.setOnClickListener { choosePrice() }
        controls.window.setOnClickListener { chooseWindow() }
        controls.amenities.setOnClickListener { chooseAmenities() }
        controls.sort.setOnClickListener { chooseSort() }
        controls.retry.setOnClickListener { viewModel.retry() }
        controls.aiToggle.setOnClickListener {
            if (viewModel.state.value.guest) authNavigator.openLogin(findNavController())
            else {
                aiExpanded = !aiExpanded
                controls.aiBody.isVisible = aiExpanded
                if (aiExpanded) controls.aiInput.requestFocus()
            }
        }

        var previous: CourtSearchUiState? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        render(controls, venues, previous, state)
                        if (state.notice != previous?.notice && state.notice != null && manager.findFirstVisibleItemPosition() > 0) {
                            Snackbar.make(binding.root, noticeText(state.notice), Snackbar.LENGTH_LONG).show()
                        }
                        previous = state
                    }
                }
                launch { viewModel.effects.collect(::handleEffect) }
                launch {
                    viewModel.refreshUsage()
                    var day = viewModel.today()
                    var wasLocked = viewModel.lockSeconds(viewModel.state.value.usage) > 0
                    while (true) {
                        delay(1_000)
                        val locked = viewModel.lockSeconds(viewModel.state.value.usage) > 0
                        if (viewModel.today() != day || (wasLocked && !locked)) {
                            day = viewModel.today()
                            viewModel.refreshUsage()
                        }
                        wasLocked = locked
                        renderUsage(controls, viewModel.state.value)
                    }
                }
            }
        }
    }

    private fun render(
        binding: ViewCourtSearchControlsBinding,
        venues: VenueCardAdapter,
        previous: CourtSearchUiState?,
        state: CourtSearchUiState,
    ) {
        if (binding.keyword.text.toString() != state.criteria.keyword) binding.keyword.setText(state.criteria.keyword)
        if (binding.aiInput.text.toString() != state.aiInput) binding.aiInput.setText(state.aiInput)
        binding.aiField.isVisible = !state.guest
        binding.filtersKeptRow.isVisible = state.filtersKept && state.criteria.hasFilters()
        renderHistory(binding, previous, state)
        if (state.clarification != null) aiExpanded = true
        binding.aiBody.isVisible = aiExpanded && !state.guest
        renderUsage(binding, state)
        // Lỗi ngày hiện trong khung AI, ngay trên câu hỏi chọn ngày; các thông báo khác giữ vị trí cũ.
        val dateNotice = state.notice == SearchNotice.INVALID_DATE
        binding.aiDateNotice.isVisible = dateNotice
        if (dateNotice) binding.aiDateNoticeText.setText(noticeText(SearchNotice.INVALID_DATE))
        binding.notice.isVisible = state.notice != null && !dateNotice
        state.notice?.takeIf { !dateNotice }?.let { binding.notice.setText(noticeText(it)) }
        binding.clarify.isVisible = state.clarification != null
        binding.clarifyChoices.isVisible = state.clarification != null

        if (previous?.clarification != state.clarification) {
            binding.clarifyChoices.removeAllViews()
            state.clarification?.let { pending ->
                viewModel.clarificationDates(pending).forEach { date ->
                    binding.clarifyChoices.addChoice(dateLabel(date)) { viewModel.clarify(date) }
                }
                binding.clarifyChoices.addChoice(getString(R.string.search_ai_manual), viewModel::useManualClarification)
            }
        }
        if (previous?.criteria != state.criteria || previous?.preference != state.preference ||
            previous?.districts != state.districts || previous?.amenities != state.amenities) {
            renderChips(binding.filters, state)
        }
        if (previous?.recommendations != state.recommendations || previous?.reviewBusy != state.reviewBusy ||
            previous?.usage != state.usage) renderRecommendations(binding, state)

        binding.loading.isVisible = state.loading
        binding.sort.isVisible = true
        binding.sort.setText(sortLabel(state.criteria.sort))
        binding.resultsTitle.text = if (!state.criteria.hasFilters()) {
            if (state.complete && !state.capped) getString(R.string.search_all_count, state.venues.size)
            else getString(R.string.search_all_title)
        } else if (state.complete && state.venues.isNotEmpty() && !state.capped) {
            getString(R.string.search_result_count, state.venues.size)
        } else getString(R.string.search_result_title)
        val message = when {
            state.failed -> R.string.search_error_generic
            state.loading -> null
            !state.criteria.hasFilters() && state.venues.isEmpty() && state.complete -> R.string.search_all_empty
            state.capped -> R.string.search_result_capped
            state.venues.isEmpty() && state.complete -> R.string.search_result_empty
            else -> null
        }
        binding.resultsMessage.isVisible = message != null
        message?.let(binding.resultsMessage::setText)
        binding.retry.isVisible = state.failed
        if (previous?.venues != state.venues || previous?.shownCount != state.shownCount) {
            venues.submitList(state.venues.take(state.shownCount))
        }
    }

    /** Lịch sử câu hỏi AI: chỉ hiện khi ô AI đang trống. Chạm để điền lại, ✕ để xóa khỏi lịch sử. */
    private fun renderHistory(binding: ViewCourtSearchControlsBinding, previous: CourtSearchUiState?, state: CourtSearchUiState) {
        val visible = !state.guest && state.aiInput.isBlank() && state.aiHistory.isNotEmpty()
        binding.aiHistoryTitle.isVisible = visible
        binding.aiHistory.isVisible = visible
        if (previous != null && previous.aiHistory == state.aiHistory) return
        binding.aiHistory.removeAllViews()
        for (query in state.aiHistory) {
            binding.aiHistory.addView(Chip(requireContext()).apply {
                text = query
                isCloseIconVisible = true
                closeIconContentDescription = getString(R.string.search_ai_history_remove, query)
                setOnClickListener {
                    binding.aiInput.setText(query)
                    binding.aiInput.setSelection(binding.aiInput.length())
                }
                setOnCloseIconClickListener { viewModel.removeHistory(query) }
            })
        }
    }

    private fun renderUsage(binding: ViewCourtSearchControlsBinding, state: CourtSearchUiState) {
        val locked = viewModel.lockSeconds(state.usage)
        binding.aiToggle.text = state.usage?.let { getString(R.string.search_ai_compact_usage, it.remaining, it.limit) }
            ?: getString(R.string.search_ai_compact)
        binding.usage.isVisible = !state.guest && (state.usage != null || locked > 0)
        binding.usage.text = when {
            locked > 0 -> getString(R.string.search_ai_lock_remaining, locked / 60, locked % 60)
            state.usage != null -> getString(R.string.search_ai_usage, state.usage.remaining, state.usage.limit)
            else -> ""
        }
        val input = state.aiInput.trim()
        binding.aiSubmit.isEnabled = state.guest || (!state.aiBusy && locked == 0L && state.usage?.remaining != 0 &&
            input.isNotEmpty() && input.codePointCount(0, input.length) <= 200)
        binding.aiSubmit.setText(when {
            state.guest -> R.string.search_ai_login
            state.aiBusy -> R.string.search_ai_loading
            else -> R.string.search_ai_submit
        })
    }

    private fun renderRecommendations(binding: ViewCourtSearchControlsBinding, state: CourtSearchUiState) {
        binding.recommendations.isVisible = state.recommendations.isNotEmpty()
        binding.recommendationRows.removeAllViews()
        for (recommendation in state.recommendations) {
            val venue = state.venues.firstOrNull { it.id == recommendation.venueId } ?: continue
            val row = ItemSearchRecommendationBinding.inflate(layoutInflater, binding.recommendationRows, false)
            row.venueName.text = venue.name
            row.venueName.setOnClickListener { openVenue(venue) }
            row.rating.isVisible = venue.ratingCount > 0
            row.rating.text = "${String.format(Locale.ROOT, "%.1f", venue.ratingAvg).replace('.', ',')} (${venue.ratingCount})"
            val quote = recommendation.quotes.firstOrNull { it.supports }?.text
            row.quote.isVisible = quote != null
            quote?.let { row.quote.text = getString(R.string.search_rag_quote, it) }
            row.source.isEnabled = !state.reviewBusy && recommendation.sources.isNotEmpty()
            row.explain.isEnabled = !state.reviewBusy && recommendation.sources.isNotEmpty()
            row.source.setOnClickListener { viewModel.sources(venue.id) }
            row.explain.setOnClickListener { viewModel.explain(venue.id) }
            binding.recommendationRows.addView(row.root)
        }
    }

    private fun renderChips(group: ChipGroup, state: CourtSearchUiState) {
        group.removeAllViews()
        val criteria = state.criteria
        criteria.districtId?.let { id ->
            group.addRemovable(state.districts.firstOrNull { it.id == id }?.name ?: id) { viewModel.district(null) }
        }
        criteria.window?.let { window ->
            group.addRemovable(getString(R.string.search_window_label, dateLabel(window.date), window.start.format(), window.end.format())) {
                viewModel.window(null)
            }
        }
        val min = criteria.priceMin?.let(HomeFormat::vnd)
        val max = criteria.priceMax?.let(HomeFormat::vnd)
        val price = when {
            min != null && max != null -> getString(R.string.search_price_range_chip, min, max)
            min != null -> getString(R.string.search_price_min_chip, min)
            max != null -> getString(R.string.search_price_max_chip, max)
            else -> null
        }
        price?.let { group.addRemovable(it) { viewModel.price(null, null) } }
        criteria.amenityIds.forEach { id ->
            group.addRemovable(state.amenities.firstOrNull { it.id == id }?.name ?: id) {
                viewModel.amenities(viewModel.state.value.criteria.amenityIds - id)
            }
        }
        state.preference?.let {
            group.addRemovable(getString(R.string.search_rag_chip, getString(preferenceLabel(it))), viewModel::removePreference)
        }
        if (group.childCount > 1) group.addChoice(getString(R.string.search_filter_clear), viewModel::clearFilters)
    }

    private fun chooseDistrict() {
        val state = viewModel.state.value
        if (state.districts.isEmpty()) return missingCatalog()
        val names = listOf(getString(R.string.search_filter_all_districts)) + state.districts.map { it.name }
        val checked = state.districts.indexOfFirst { it.id == state.criteria.districtId } + 1
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_district)
            .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                viewModel.district(state.districts.getOrNull(which - 1)?.id)
                dialog.dismiss()
            }.show()
    }

    private fun showFilters() {
        val current = viewModel.state.value
        if (!current.catalogReady) viewModel.loadCatalog()
        val sheet = BottomSheetCourtSearchFilterBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheet.root)
        val original = current.criteria
        var district = original.districtId
        var selectedDay = original.window?.date
        var start = original.window?.start ?: TimeOfDay(18 * 60)
        var end = original.window?.end ?: TimeOfDay(20 * 60)
        val selectedAmenities = original.amenityIds.toMutableSet()
        var priceChanged = false
        val minValue = ((original.priceMin ?: 0).coerceIn(0, 200_000) / 10_000 * 10_000).toFloat()
        val maxValue = ((original.priceMax ?: 200_000).coerceIn(0, 200_000) / 10_000 * 10_000).toFloat()
        sheet.priceRange.setValues(minOf(minValue, maxValue), maxOf(minValue, maxValue))

        fun renderDistrict() {
            sheet.district.text = viewModel.state.value.districts.firstOrNull { it.id == district }?.name
                ?: getString(R.string.search_filter_all_districts)
        }
        fun renderPrice() {
            val low = if (priceChanged) sheet.priceRange.values[0].toLong() else original.priceMin ?: 0L
            val high = if (priceChanged) sheet.priceRange.values[1].toLong().takeIf { it < 200_000 }
                else original.priceMax
            sheet.priceLabel.text = if (high == null) {
                getString(R.string.search_price_sheet_unlimited, HomeFormat.vnd(low))
            } else getString(R.string.search_price_sheet_range, HomeFormat.vnd(low), HomeFormat.vnd(high))
        }
        fun renderTime() {
            sheet.start.text = getString(R.string.search_choose_start, start.format())
            sheet.end.text = getString(R.string.search_choose_end, end.format())
            sheet.windowError.isVisible = false
        }
        fun renderDays() {
            sheet.days.removeAllViews()
            val today = viewModel.today()
            val noDay = Chip(requireContext()).apply {
                text = getString(R.string.search_filter_any_day)
                isCheckable = true
                isChecked = selectedDay == null
                setOnClickListener { selectedDay = null; renderDays() }
            }
            sheet.days.addView(noDay)
            for (offset in 0..BookingWindow.ADVANCE_DAYS) {
                val date = today.plusDays(offset.toLong())
                val label = when (offset) {
                    0 -> getString(R.string.search_today, dateLabel(date))
                    1 -> getString(R.string.search_tomorrow, dateLabel(date))
                    else -> date.format(DateTimeFormatter.ofPattern("EEE dd/MM", java.util.Locale.forLanguageTag("vi-VN")))
                }
                sheet.days.addView(Chip(requireContext()).apply {
                    text = label
                    isCheckable = true
                    isChecked = date == selectedDay
                    setOnClickListener { selectedDay = date; renderDays() }
                })
            }
        }
        renderDistrict()
        renderPrice()
        renderTime()
        renderDays()
        sheet.priceRange.addOnChangeListener { _, _, fromUser ->
            if (fromUser) priceChanged = true
            renderPrice()
        }
        sheet.district.setOnClickListener {
            val districts = viewModel.state.value.districts
            if (districts.isEmpty()) {
                missingCatalog()
            } else {
                val names = listOf(getString(R.string.search_filter_all_districts)) + districts.map { it.name }
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_district_full)
                    .setSingleChoiceItems(names.toTypedArray(), districts.indexOfFirst { it.id == district } + 1) { picker, which ->
                        district = districts.getOrNull(which - 1)?.id
                        sheet.district.text = districts.getOrNull(which - 1)?.name ?: names.first()
                        picker.dismiss()
                    }.show()
            }
        }
        val catalogJob = viewLifecycleOwner.lifecycleScope.launch {
            var rendered = emptyList<com.example.booking_app.core.catalog.Amenity>()
            viewModel.state.collect { state ->
                renderDistrict()
                if (rendered != state.amenities) {
                    rendered = state.amenities
                    sheet.amenities.removeAllViews()
                    for (amenity in rendered) {
                        sheet.amenities.addView(Chip(requireContext()).apply {
                            text = amenity.name
                            isCheckable = true
                            isChecked = amenity.id in selectedAmenities
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedAmenities.add(amenity.id) else selectedAmenities.remove(amenity.id)
                            }
                        })
                    }
                }
            }
        }
        sheet.start.setOnClickListener { chooseTime(start, false) { start = it; if (selectedDay == null) selectedDay = viewModel.today().plusDays(1); renderTime(); renderDays() } }
        sheet.end.setOnClickListener { chooseTime(end, true) { end = it; if (selectedDay == null) selectedDay = viewModel.today().plusDays(1); renderTime(); renderDays() } }
        sheet.close.setOnClickListener { dialog.dismiss() }
        sheet.clear.setOnClickListener {
            selectedDay = null
            selectedAmenities.clear()
            for (index in 0 until sheet.amenities.childCount) (sheet.amenities.getChildAt(index) as? Chip)?.isChecked = false
            sheet.priceRange.setValues(0f, 200_000f)
            priceChanged = true
            renderDistrict()
            renderPrice()
            renderDays()
        }
        sheet.apply.setOnClickListener {
            val values = sheet.priceRange.values
            val min = if (priceChanged) values[0].toLong().takeIf { it > 0 } else original.priceMin
            val max = if (priceChanged) values[1].toLong().takeIf { it < 200_000 } else original.priceMax
            val window = selectedDay?.let { BookingSearchWindow(it, start, end) }
            if (viewModel.applyFilters(district, min, max, selectedAmenities.toSet(), window)) dialog.dismiss()
            else sheet.windowError.isVisible = true
        }
        dialog.setOnShowListener {
            sheet.root.layoutParams = sheet.root.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            BottomSheetBehavior.from(sheet.root.parent as View).state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.setOnDismissListener { catalogJob.cancel() }
        dialog.show()
    }

    private fun chooseAmenities() {
        val state = viewModel.state.value
        if (state.amenities.isEmpty()) return missingCatalog()
        val selected = state.criteria.amenityIds.toMutableSet()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_amenities)
            .setMultiChoiceItems(state.amenities.map { it.name }.toTypedArray(),
                state.amenities.map { it.id in selected }.toBooleanArray()) { _, index, checked ->
                val id = state.amenities[index].id
                if (checked) selected.add(id) else selected.remove(id)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.search_filter_apply) { _, _ -> viewModel.amenities(selected.toSet()) }
            .show()
    }

    private fun choosePrice() {
        val price = DialogCourtSearchPriceBinding.inflate(layoutInflater)
        val criteria = viewModel.state.value.criteria
        price.min.setText(criteria.priceMin?.toString().orEmpty())
        price.max.setText(criteria.priceMax?.toString().orEmpty())
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_price)
            .setView(price.root).setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.search_filter_apply, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val min = price.min.text.toString().toLongOrNull()
                val max = price.max.text.toString().toLongOrNull()
                if ((min != null && min <= 0) || (max != null && max <= 0) ||
                    (min != null && max != null && min > max)) {
                    price.maxField.error = getString(R.string.search_price_invalid)
                } else {
                    viewModel.price(min, max)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun chooseWindow(suggestedDate: LocalDate? = null) {
        val view = DialogCourtSearchWindowBinding.inflate(layoutInflater)
        val current = viewModel.state.value.criteria.window
        val today = viewModel.today()
        var day = suggestedDate?.takeIf { BookingWindow.isDateAllowed(it, today) } ?: current?.date ?: today.plusDays(1)
        var start = current?.start ?: TimeOfDay(18 * 60)
        var end = current?.end ?: TimeOfDay(20 * 60)
        fun render() {
            view.dayValue.text = dayWithWeekday(day)
            view.startValue.text = start.format()
            view.endValue.text = end.format()
            view.error.isVisible = false
        }
        render()
        view.day.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, selectedDay ->
                day = LocalDate.of(year, month + 1, selectedDay)
                render()
            }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
                val zone = ZoneId.systemDefault()
                datePicker.minDate = today.atStartOfDay(zone).toInstant().toEpochMilli()
                datePicker.maxDate = today.plusDays(BookingWindow.ADVANCE_DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            }.show()
        }
        view.start.setOnClickListener { chooseTime(start, false) { start = it; render() } }
        view.end.setOnClickListener { chooseTime(end, true) { end = it; render() } }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_window)
            .setView(view.root).setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.search_filter_apply, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (viewModel.window(BookingSearchWindow(day, start, end))) dialog.dismiss()
                else view.error.isVisible = true
            }
        }
        dialog.show()
    }

    private fun chooseTime(current: TimeOfDay, end: Boolean, selected: (TimeOfDay) -> Unit) {
        val times = (0..(if (end) 48 else 47)).map { TimeOfDay(it * 30) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (end) getString(R.string.search_choose_end, current.format()) else getString(R.string.search_choose_start, current.format()))
            .setSingleChoiceItems(times.map(TimeOfDay::format).toTypedArray(), times.indexOf(current)) { dialog, which ->
                selected(times[which])
                dialog.dismiss()
            }.show()
    }

    private fun chooseSort() {
        val sorts = CourtSearchSort.entries
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_sort_title)
            .setSingleChoiceItems(sorts.map { getString(sortLabel(it)) }.toTypedArray(),
                sorts.indexOf(viewModel.state.value.criteria.sort)) { dialog, which ->
                viewModel.sort(sorts[which])
                dialog.dismiss()
            }.show()
    }

    private fun missingCatalog() {
        viewModel.loadCatalog()
        view?.let { Snackbar.make(it, R.string.search_filter_no_catalog, Snackbar.LENGTH_SHORT).show() }
    }

    private fun handleEffect(effect: CourtSearchEffect) {
        when (effect) {
            is CourtSearchEffect.ChooseWindow -> chooseWindow(effect.date)
            is CourtSearchEffect.DropIn -> findNavController().navigate(effect.link)
            is CourtSearchEffect.Policy -> showPolicy(effect.topic)
            is CourtSearchEffect.Sources -> showSources(effect)
            is CourtSearchEffect.Explanation -> showExplanation(effect)
            CourtSearchEffect.RequestLocation -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.search_location_title).setMessage(R.string.search_location_rationale)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }.show()
        }
    }

    /** "Vì sao phù hợp?": Điểm cộng (màu chính) và Điểm trừ (màu tertiary); chạm một dòng để mở nguồn. */
    private fun showExplanation(effect: CourtSearchEffect.Explanation) {
        val view = DialogSearchExplanationBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_rag_summary_label)
            .setView(view.root).setPositiveButton(android.R.string.ok, null).create()
        for (claim in effect.claims) {
            val row = ItemSearchClaimBinding.inflate(layoutInflater, view.claims, false)
            val color = MaterialColors.getColor(row.root,
                if (claim.supports) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorTertiary)
            row.dot.backgroundTintList = ColorStateList.valueOf(color)
            row.label.setText(if (claim.supports) R.string.search_rag_plus else R.string.search_rag_minus)
            row.label.setTextColor(color)
            row.text.text = claim.excerpt.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi-VN")) }
            row.root.setOnClickListener {
                dialog.dismiss()
                viewModel.source(effect.venueId, claim.source, claim.supports)
            }
            view.claims.addView(row.root)
        }
        dialog.show()
    }

    /** Đánh giá gốc trong bottom sheet: mỗi đánh giá một thẻ, có nhãn Ủng hộ/Trái chiều và vị trí "1/2". */
    private fun showSources(effect: CourtSearchEffect.Sources) {
        val sheet = BottomSheetSearchSourcesBinding.inflate(layoutInflater)
        val venueName = viewModel.state.value.venues.firstOrNull { it.id == effect.venueId }?.name
        sheet.venue.isVisible = venueName != null
        sheet.venue.text = venueName
        effect.items.forEachIndexed { index, item ->
            val card = ItemSearchSourceBinding.inflate(layoutInflater, sheet.items, false)
            card.comment.text = getString(R.string.search_rag_quote, item.comment.trim())
            card.counter.isVisible = effect.items.size > 1
            card.counter.text = getString(R.string.search_rag_position, index + 1, effect.items.size)
            card.stance.isVisible = item.supports != null
            item.supports?.let { supports ->
                card.stance.setText(if (supports) R.string.search_rag_stance_support else R.string.search_rag_stance_counter)
                card.stance.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(card.stance,
                    if (supports) com.google.android.material.R.attr.colorPrimaryContainer
                    else com.google.android.material.R.attr.colorTertiaryContainer))
                card.stance.setTextColor(MaterialColors.getColor(card.stance,
                    if (supports) com.google.android.material.R.attr.colorOnPrimaryContainer
                    else com.google.android.material.R.attr.colorOnTertiaryContainer))
            }
            sheet.items.addView(card.root)
        }
        BottomSheetDialog(requireContext()).apply {
            setContentView(sheet.root)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }.show()
    }

    private fun showPolicy(topic: String?) {
        val policies = listOf(
            "CANCELLATION" to (R.string.search_ai_policy_cancellation to R.string.search_ai_cancellation_body),
            "DEPOSIT" to (R.string.search_ai_policy_deposit to R.string.search_ai_deposit_body),
            "VOUCHER" to (R.string.search_ai_policy_voucher to R.string.search_ai_voucher_body),
            "PAYMENT" to (R.string.search_ai_policy_payment to R.string.search_ai_payment_body),
        )
        val policy = policies.firstOrNull { it.first == topic }?.second
        val dialog = MaterialAlertDialogBuilder(requireContext())
        if (policy == null) dialog.setTitle(R.string.search_ai_policy)
            .setItems(policies.map { getString(it.second.first) }.toTypedArray()) { _, which -> showPolicy(policies[which].first) }
        else dialog.setTitle(policy.first).setMessage(policy.second).setPositiveButton(android.R.string.ok, null)
        dialog.show()
    }

    private fun openVenue(venue: HomeVenue) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.courtSearchFragment) return
        val window = venue.window
        nav.navigate(R.id.action_search_to_courtDetail, bundleOf(
            "venueId" to venue.id, "date" to window?.dateKey(),
            "startTime" to window?.start?.toHhmm(), "endTime" to window?.end?.toHhmm(),
        ))
    }

    private fun ChipGroup.addRemovable(label: String, action: () -> Unit) {
        addView(Chip(context).apply {
            text = label
            setChipBackgroundColorResource(R.color.sn_court_container)
            isCloseIconVisible = true
            setOnCloseIconClickListener { action() }
        })
    }

    private fun ChipGroup.addChoice(label: String, action: () -> Unit) {
        addView(Chip(context).apply { text = label; setOnClickListener { action() } })
    }

    private fun hideKeyboard(view: View) {
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun dateLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("dd/MM"))

    /** "Thứ hai, 16/11": thứ giúp người dùng thấy ngày đã chọn đúng chưa. */
    private fun dayWithWeekday(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("vi-VN")))
            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi-VN")) }

    private fun sortLabel(sort: CourtSearchSort): Int = when (sort) {
        CourtSearchSort.RATING -> R.string.search_sort_rating
        CourtSearchSort.PRICE_ASC -> R.string.search_sort_price
        CourtSearchSort.NEAREST -> R.string.search_sort_nearest
    }

    private fun preferenceLabel(preference: ReviewPreference): Int = when (preference) {
        ReviewPreference.GRIP -> R.string.search_rag_grip
        ReviewPreference.HIGH_CEILING -> R.string.search_rag_ceiling
        ReviewPreference.QUIET -> R.string.search_rag_quiet
        ReviewPreference.CLEAN -> R.string.search_rag_clean
        ReviewPreference.GOOD_LIGHT -> R.string.search_rag_light
        ReviewPreference.VENTILATED -> R.string.search_rag_air
        ReviewPreference.GOOD_SERVICE -> R.string.search_rag_service
    }

    private fun noticeText(notice: SearchNotice): Int = when (notice) {
        SearchNotice.AI_ERROR -> R.string.search_ai_unavailable
        SearchNotice.QUOTA -> R.string.search_ai_quota
        SearchNotice.LOCKED -> R.string.search_ai_locked
        SearchNotice.SOURCE_UNAVAILABLE -> R.string.search_rag_source_unavailable
        SearchNotice.INVALID_WINDOW -> R.string.search_invalid_window
        SearchNotice.INVALID_DATE -> R.string.search_ai_invalid_date
        SearchNotice.DROPPED_FILTERS -> R.string.search_ai_dropped
        SearchNotice.OFF_TOPIC -> R.string.search_ai_off_topic
        SearchNotice.LOCATION_UNAVAILABLE -> R.string.search_location_unavailable
    }

    private class ControlsAdapter(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(view) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }
}
