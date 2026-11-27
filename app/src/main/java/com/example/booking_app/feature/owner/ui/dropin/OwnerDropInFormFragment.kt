package com.example.booking_app.feature.owner.ui.dropin

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
import com.example.booking_app.databinding.FragmentOwnerDropinFormBinding
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInDraft
import com.example.booking_app.feature.owner.domain.model.OwnerCourt
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.google.android.material.chip.Chip

@AndroidEntryPoint
class OwnerDropInFormFragment : Fragment(R.layout.fragment_owner_dropin_form) {
    private val viewModel: OwnerDropInViewModel by viewModels()
    private var _binding: FragmentOwnerDropinFormBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var venues: List<OwnerVenue> = emptyList()
    private var courts: List<OwnerCourt> = emptyList()
    private var venueId: String? = null
    private var courtId: String? = null
    private val selectedCourtIds = linkedSetOf<String>()
    private var settingSpinners = false
    private var populated = false
    private var navigatedAfterSave = false
    private var retainedDraft: Bundle? = null
    private val sessionId get() = arguments?.getString("sessionId")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerDropinFormBinding.bind(view)
        venues = emptyList()
        courts = emptyList()
        binding.venueSpinner.onItemSelectedListener = SpinnerListener { index ->
            if (!settingSpinners) venues.getOrNull(index)?.id?.let(viewModel::selectVenue)
        }
        binding.courtSpinner.onItemSelectedListener = SpinnerListener { index -> courts.getOrNull(index)?.id?.let { courtId = it } }
        binding.save.setOnClickListener { submitForm() }
        binding.levelSpinner.adapter = ArrayAdapter.createFromResource(requireContext(), R.array.owner_dropin_levels, android.R.layout.simple_spinner_dropdown_item)
        (savedInstanceState?.getBundle("draft") ?: retainedDraft)?.let(::restoreDraft)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) viewModel.refresh()
    }

    override fun onDestroyView() {
        _binding?.let { retainedDraft = captureDraft() }
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("draft", if (_binding != null) captureDraft() else retainedDraft)
    }

    private fun captureDraft() = Bundle().apply {
        putStringArrayList("values", arrayListOf(binding.date.text.toString(), binding.startTime.text.toString(),
            binding.endTime.text.toString(), binding.capacity.text.toString(), binding.price.text.toString(), binding.note.text.toString()))
        putStringArrayList("courts", ArrayList(selectedCourtIds))
        putInt("level", binding.levelSpinner.selectedItemPosition)
        putBoolean("populated", populated)
    }

    private fun restoreDraft(draft: Bundle) {
        draft.getStringArrayList("values")?.let { values ->
            listOf(binding.date,binding.startTime,binding.endTime,binding.capacity,binding.price,binding.note)
                .forEachIndexed { index, field -> values.getOrNull(index)?.let(field::setText) }
        }
        selectedCourtIds.clear()
        selectedCourtIds.addAll(draft.getStringArrayList("courts").orEmpty())
        binding.levelSpinner.setSelection(draft.getInt("level"))
        populated = draft.getBoolean("populated")
    }

    private fun render(state: OwnerDropInUiState) {
        if (state.completedMessage == "Đã tạo buổi" || state.completedMessage == "Đã cập nhật buổi") {
            if (!navigatedAfterSave) {
                navigatedAfterSave = true
                findNavController().popBackStack()
            }
            return
        }
        if (venues != state.venues || venueId != state.selectedVenueId) {
            venues = state.venues
            venueId = state.selectedVenueId
            settingSpinners = true
            binding.venueSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, venues.map { it.name })
            val index = venues.indexOfFirst { it.id == venueId }
            if (index >= 0) binding.venueSpinner.setSelection(index, false)
            settingSpinners = false
        }
        if (courts != state.courts) {
            courts = state.courts
            binding.courtSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, courts.map { it.name })
            courtId = courts.firstOrNull()?.id
            selectedCourtIds.retainAll(courts.map { it.id }.toSet())
            if (selectedCourtIds.isEmpty()) courtId?.let(selectedCourtIds::add)
            renderCourtChoices()
        }
        if (!populated && sessionId != null && courts.isNotEmpty()) {
            state.sessions.firstOrNull { it.sessionId == sessionId }?.let { session ->
                val start=session.startAt.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                binding.date.setText(start.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                binding.startTime.setText(start.format(DateTimeFormatter.ofPattern("HH:mm")))
                binding.endTime.setText(session.endAt.atZone(start.zone).format(DateTimeFormatter.ofPattern("HH:mm")))
                binding.capacity.setText(session.capacity.toString()); binding.price.setText(session.pricePerPersonVnd.toString())
                binding.note.setText(session.note)
                binding.levelSpinner.setSelection(listOf("BEGINNER","INTERMEDIATE","ADVANCED").indexOf(session.level).coerceAtLeast(0))
                courts.indexOfFirst { it.id in session.courtIds }.takeIf { it>=0 }?.let { binding.courtSpinner.setSelection(it); courtId=courts[it].id }
                selectedCourtIds.clear(); selectedCourtIds.addAll(session.courtIds); renderCourtChoices()
                binding.save.setText(R.string.save)
                populated=true
            }
        }
        val ready = !state.isLoading && courts.isNotEmpty() && (sessionId == null || populated)
        binding.loading.isVisible = state.isSaving || state.isLoading
        binding.save.isEnabled = ready && !state.isSaving
        listOf(binding.date,binding.startTime,binding.endTime,binding.capacity,binding.price,binding.note,
            binding.levelSpinner,binding.courtChoices).forEach { it.isEnabled = ready && !state.isSaving }
        binding.venueSpinner.isEnabled = sessionId == null && !state.isSaving && state.venues.isNotEmpty()
        for (index in 0 until binding.courtChoices.childCount) binding.courtChoices.getChildAt(index).isEnabled = ready && !state.isSaving
        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error?.let { error ->
            if (error.contains("Hợp đồng", true)) getString(R.string.owner_dropin_contract_error) else error
        }
        binding.validationText.isVisible = state.validationErrors.isNotEmpty()
        binding.validationText.text = state.validationErrors.joinToString("\n")
    }

    private fun submitForm() {
        val input = OwnerDropInDraft(
            venueId = venueId.orEmpty(),
            courtIds = selectedCourtIds.toList(),
            date = binding.date.text?.toString().orEmpty().trim(),
            startTime = binding.startTime.text?.toString().orEmpty().trim(),
            endTime = binding.endTime.text?.toString().orEmpty().trim(),
            level = when (binding.levelSpinner.selectedItemPosition) {
                0 -> "BEGINNER"
                1 -> "INTERMEDIATE"
                else -> "ADVANCED"
            },
            capacity = binding.capacity.text?.toString()?.toIntOrNull() ?: 0,
            pricePerPersonVnd = binding.price.text?.toString()?.toLongOrNull() ?: -1L,
            note = binding.note.text?.toString().orEmpty().trim(),
        )
        sessionId?.let { viewModel.update(it,input) } ?: viewModel.create(input)
    }

    private fun renderCourtChoices() {
        binding.courtSpinner.isVisible = false
        binding.courtChoices.removeAllViews()
        courts.forEach { court -> binding.courtChoices.addView(Chip(requireContext()).apply {
            text=court.name;isCheckable=true;isChecked=court.id in selectedCourtIds
            setOnCheckedChangeListener { _,checked ->
                if(checked) selectedCourtIds.add(court.id) else selectedCourtIds.remove(court.id)
                renderCourtCount()
            }
        }) }
        renderCourtCount()
    }

    private fun renderCourtCount() {
        binding.courtCountNote.text = getString(R.string.owner_dropin_create_price_note, selectedCourtIds.size)
    }

    private class SpinnerListener(private val selected: (Int) -> Unit) : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = selected(position)
    }
}
