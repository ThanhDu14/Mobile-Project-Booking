package com.example.booking_app.feature.owner.ui

import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.LocalDate
import java.time.Instant
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.navigation.fragment.findNavController
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.ui.keepFormAboveKeyboard
import com.example.booking_app.databinding.FragmentOwnerSlotBlockBinding
import com.example.booking_app.feature.owner.domain.SlotBlockInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.google.android.material.chip.Chip

@AndroidEntryPoint
class OwnerSlotBlockFragment : Fragment(R.layout.fragment_owner_slot_block) {
    private var _binding: FragmentOwnerSlotBlockBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerSlotBlockViewModel by viewModels()
    private lateinit var gridAdapter: OwnerSlotGridAdapter
    private var renderedCourts: List<com.example.booking_app.feature.owner.domain.model.OwnerCourt> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.keepFormAboveKeyboard()
        _binding = FragmentOwnerSlotBlockBinding.bind(view)

        val reasons = arrayOf(getString(R.string.owner_reason_maintenance),getString(R.string.owner_reason_event),getString(R.string.owner_reason_walk_in))
        val reasonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, reasons)
        binding.reason.setAdapter(reasonAdapter)

        gridAdapter = OwnerSlotGridAdapter { item ->
            viewModel.toggleSlotSelection(item.slotId)
        }
        binding.slotGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = gridAdapter
        }

        binding.back.setOnClickListener { findNavController().navigateUp() }
        // Ngày hiện "dd/MM/yyyy", ViewModel vẫn nhận "yyyyMMdd".
        binding.date.setOnClickListener {
            val current = parseRawDate(viewModel.uiState.value.selectedDate) ?: LocalDate.now(VIETNAM_ZONE)
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.owner_slot_date)
                .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                binding.date.setText(picked.format(DISPLAY_DATE))
                viewModel.selectDate(picked.format(RAW_DATE))
            }
            picker.show(parentFragmentManager, "owner-slot-date")
        }
        listOf(binding.startTime, binding.endTime).forEach { field ->
            field.setOnClickListener {
                val raw = field.text?.toString().orEmpty().replace(":", "").padEnd(4, '0')
                val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(raw.take(2).toIntOrNull() ?: 8).setMinute(raw.takeLast(2).toIntOrNull() ?: 0).build()
                picker.addOnPositiveButtonClickListener { field.setText("%02d:%02d".format(picker.hour, picker.minute)) }
                picker.show(parentFragmentManager, "owner-slot-time")
            }
        }

        binding.reason.setOnItemClickListener { _, _, position, _ ->
            val selected = listOf("MAINTENANCE","EVENT","WALK_IN").getOrElse(position) { "MAINTENANCE" }
            viewModel.setReason(selected)
        }

        binding.preview.setOnClickListener {
            viewModel.updateForm(readForm())
            viewModel.preview()
        }
        binding.submit.setOnClickListener {
            viewModel.updateForm(readForm())
            viewModel.submit()
        }
        binding.lockSelected.setOnClickListener {
            viewModel.lockSelectedSlots()
        }
        binding.unlockSelected.setOnClickListener {
            viewModel.unlockSelectedSlots()
        }
        binding.retry.setOnClickListener {
            viewModel.retryAfterRefresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun readForm() = SlotBlockInput(
        courtId = viewModel.uiState.value.courtId,
        date = viewModel.uiState.value.selectedDate,
        startTime = binding.startTime.text.toString().trim().replace(":", ""),
        endTime = binding.endTime.text.toString().trim().replace(":", ""),
        reason = viewModel.uiState.value.selectedReason
    )

    private fun render(state: OwnerSlotBlockUiState) {
        if (renderedCourts != state.courts) {
            renderedCourts=state.courts
            binding.courtChoices.removeAllViews()
            state.courts.forEach { court -> binding.courtChoices.addView(Chip(requireContext()).apply {
                text=court.name;isCheckable=true;isChecked=court.id==state.courtId
                setOnClickListener { viewModel.selectCourt(court.id) }
            }) }
        }
        binding.progress.visibility = if (state.isSubmitting || state.isLoadingGrid) View.VISIBLE else View.GONE
        binding.stale.visibility = if (state.isStale) View.VISIBLE else View.GONE
        binding.offline.visibility = if (state.isOffline) View.VISIBLE else View.GONE
        binding.message.text = state.error ?: state.message.orEmpty()
        binding.slotIds.text = if (state.slotIds.isEmpty()) "" else getString(R.string.owner_slot_preview_count,state.slotIds.size)
        binding.retry.visibility = if (state.isStale) View.VISIBLE else View.GONE

        val canOperate = !state.isOffline && !state.isSubmitting && !state.isStale
        binding.submit.isEnabled = canOperate
        binding.lockSelected.isEnabled = canOperate
        binding.unlockSelected.isEnabled = canOperate

        if (binding.date.text.isNullOrEmpty() && state.selectedDate.isNotEmpty()) {
            binding.date.setText(parseRawDate(state.selectedDate)?.format(DISPLAY_DATE) ?: state.selectedDate)
        }
        if (binding.reason.text.isNullOrEmpty() && state.selectedReason.isNotEmpty()) {
            binding.reason.setText(getString(when(state.selectedReason) {"EVENT"->R.string.owner_reason_event;"WALK_IN"->R.string.owner_reason_walk_in;else->R.string.owner_reason_maintenance}), false)
        }

        gridAdapter.submitList(state.gridSlots)
    }

    private fun parseRawDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw, RAW_DATE) }.getOrNull()

    private companion object {
        val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val RAW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
