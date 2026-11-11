package com.example.booking_app.feature.dropin.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.booking_app.R
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.databinding.BottomSheetDropinFilterBinding
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.FilterError
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet bộ lọc (US1, T027). Sửa trên bản nháp; "Áp dụng" trả [DropInFilterArgs] về danh sách qua
 * `SavedStateHandle` của back stack entry trước đó (khóa [RESULT_KEY]).
 */
@AndroidEntryPoint
class DropInFilterBottomSheet : BottomSheetDialogFragment() {

    private val args: DropInFilterBottomSheetArgs by navArgs()
    private val districtsViewModel: DropInDistrictsViewModel by viewModels()

    private var _binding: BottomSheetDropinFilterBinding? = null
    private val binding get() = _binding!!

    private lateinit var draft: DropInFilter

    /** Đang dựng lại chip từ [draft]: bỏ qua listener để không ghi ngược. */
    private var binding_ = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDropinFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val saved = savedInstanceState?.let { BundleCompat.getParcelable(it, KEY_DRAFT, DropInFilterArgs::class.java) }
        draft = (saved ?: args.current).toFilter()

        binding.districtHint.text = getString(R.string.dropin_filter_district_hint, DropInFilter.MAX_DISTRICTS)
        binding.districtRetry.setOnClickListener { districtsViewModel.load() }
        binding.dateButton.setOnClickListener { pickDate() }
        binding.dateClear.setOnClickListener {
            draft = draft.copy(date = null)
            renderDate()
        }
        SLOT_CHIPS.forEach { (id, slot) ->
            binding.slotGroup.findViewById<Chip>(id).setOnCheckedChangeListener { _, checked ->
                if (binding_) return@setOnCheckedChangeListener
                draft = draft.copy(timeSlot = if (checked) slot else draft.timeSlot.takeIf { it != slot })
            }
        }
        LEVEL_CHIPS.forEach { (id, level) ->
            binding.levelGroup.findViewById<Chip>(id).setOnCheckedChangeListener { _, checked ->
                if (binding_) return@setOnCheckedChangeListener
                draft = draft.copy(level = if (checked) level else draft.level.takeIf { it != level })
            }
        }
        binding.maxPriceInput.doAfterTextChanged { text ->
            if (binding_) return@doAfterTextChanged
            draft = draft.copy(maxPrice = text?.toString()?.trim()?.toLongOrNull()?.takeIf { it > 0 })
        }
        binding.clearAllButton.setOnClickListener {
            draft = DropInFilter()
            renderAll()
        }
        binding.applyButton.setOnClickListener { apply() }

        renderAll()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                districtsViewModel.state.collect(::renderDistricts)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::draft.isInitialized) outState.putParcelable(KEY_DRAFT, DropInFilterArgs.from(draft))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun renderAll() {
        binding_ = true
        SLOT_CHIPS.forEach { (id, slot) -> binding.slotGroup.findViewById<Chip>(id).isChecked = draft.timeSlot == slot }
        LEVEL_CHIPS.forEach { (id, level) -> binding.levelGroup.findViewById<Chip>(id).isChecked = draft.level == level }
        binding.maxPriceInput.setText(draft.maxPrice?.toString().orEmpty())
        binding_ = false
        renderDate()
        renderDistricts(districtsViewModel.state.value)
    }

    private fun renderDate() {
        val date = draft.date
        binding.dateButton.text = date?.let {
            getString(
                R.string.dropin_relative_date,
                getString(DropInUiText.dayOfWeekShort(it.dayOfWeek)),
                it.format(DATE_FORMAT),
            )
        } ?: getString(R.string.dropin_filter_date_any)
        binding.dateClear.isVisible = date != null
    }

    private fun renderDistricts(state: DropInDistrictsViewModel.State) {
        val b = _binding ?: return
        b.districtLoading.isVisible = state is DropInDistrictsViewModel.State.Loading
        b.districtError.isVisible = state is DropInDistrictsViewModel.State.Failed
        val districts = (state as? DropInDistrictsViewModel.State.Loaded)?.districts.orEmpty()
            .filter { it.isActive || it.id in draft.districtIds }
        binding_ = true
        b.districtGroup.removeAllViews()
        districts.forEach { district -> b.districtGroup.addView(districtChip(district)) }
        binding_ = false
        renderDistrictLimit()
    }

    private fun districtChip(district: District): Chip =
        (layoutInflater.inflate(R.layout.item_dropin_filter_chip, binding.districtGroup, false) as Chip).apply {
            text = district.name
            isChecked = district.id in draft.districtIds
            setOnCheckedChangeListener { chip, checked ->
                if (binding_) return@setOnCheckedChangeListener
                val ids = draft.districtIds
                if (checked && ids.size >= DropInFilter.MAX_DISTRICTS) {
                    // Giới hạn `in` của Firestore (research R6): không cho chọn quá 15 quận.
                    chip.isChecked = false
                    renderDistrictLimit(exceeded = true)
                    return@setOnCheckedChangeListener
                }
                draft = draft.copy(districtIds = if (checked) ids + district.id else ids - district.id)
                renderDistrictLimit()
            }
        }

    private fun renderDistrictLimit(exceeded: Boolean = false) {
        val b = _binding ?: return
        val error = exceeded || draft.validate() is FilterError.TooManyDistricts
        b.districtHint.text = getString(
            if (error) R.string.dropin_filter_district_error else R.string.dropin_filter_district_hint,
            DropInFilter.MAX_DISTRICTS,
        )
        b.districtHint.setTextColor(
            com.google.android.material.color.MaterialColors.getColor(
                b.districtHint,
                if (error) androidx.appcompat.R.attr.colorError else com.google.android.material.R.attr.colorOnSurfaceVariant,
            ),
        )
    }

    private fun pickDate() {
        val today = LocalDate.now(TimeFormat.ZONE)
        val start = today.toUtcMillis()
        val end = today.plusDays(BOOKING_WINDOW_DAYS).toUtcMillis()
        val constraints = CalendarConstraints.Builder()
            .setStart(start)
            .setEnd(end)
            .setValidator(
                CompositeDateValidator.allOf(
                    listOf(DateValidatorPointForward.from(start), DateValidatorPointBackward.before(end + DAY_MS)),
                ),
            )
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.dropin_filter_date_pick)
            .setCalendarConstraints(constraints)
            .setSelection(draft.date?.toUtcMillis() ?: start)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            draft = draft.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            if (_binding != null) renderDate()
        }
        picker.show(childFragmentManager, DATE_PICKER_TAG)
    }

    private fun apply() {
        if (draft.validate() != null) {
            renderDistrictLimit(exceeded = true)
            return
        }
        findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_KEY, DropInFilterArgs.from(draft))
        dismiss()
    }

    private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    companion object {
        const val RESULT_KEY = "dropin_filter_result"
        private const val KEY_DRAFT = "dropin_filter_draft"
        private const val DATE_PICKER_TAG = "dropin_date_picker"

        /** Cùng phạm vi đặt trước với spec 040 (FR-003). */
        private const val BOOKING_WINDOW_DAYS = 30L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

        private val SLOT_CHIPS = listOf(
            R.id.slotMorning to TimeSlot.MORNING,
            R.id.slotNoon to TimeSlot.NOON,
            R.id.slotAfternoon to TimeSlot.AFTERNOON,
            R.id.slotEvening to TimeSlot.EVENING,
        )
        private val LEVEL_CHIPS = listOf(
            R.id.levelBeginner to Level.BEGINNER,
            R.id.levelIntermediate to Level.INTERMEDIATE,
            R.id.levelAdvanced to Level.ADVANCED,
        )
    }
}
