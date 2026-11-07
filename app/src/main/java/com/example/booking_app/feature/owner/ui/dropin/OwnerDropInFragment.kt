package com.example.booking_app.feature.owner.ui.dropin

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerDropinBinding
import com.example.booking_app.databinding.ItemOwnerDropinSessionBinding
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInSession
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerDropInFragment : Fragment(R.layout.fragment_owner_dropin) {
    private val viewModel: OwnerDropInViewModel by viewModels()
    private var _binding: FragmentOwnerDropinBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var venueId: String? = null
    private var venueCount = -1
    private var settingVenue = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerDropinBinding.bind(view)
        binding.refresh.setOnClickListener { viewModel.refresh() }
        binding.createSession.setOnClickListener { findNavController().navigate(R.id.action_ownerDropIn_to_form) }
        binding.venueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!settingVenue) viewModel.uiState.value.venues.getOrNull(position)?.id?.let(viewModel::selectVenue)
            }
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

    private fun render(state: OwnerDropInUiState) {
        binding.loading.isVisible = state.isLoading
        binding.errorText.isVisible = !state.isLoading && state.error != null
        binding.errorText.text = state.error?.let { error ->
            if (error.contains("Hợp đồng đọc", true)) getString(R.string.owner_dropin_contract_error) else error
        }
        binding.emptyText.isVisible = !state.isLoading && state.error == null && state.isEmpty
        binding.sessionScroll.isVisible = !state.isLoading && state.error == null && state.sessions.isNotEmpty()
        renderVenues(state)
        if (binding.sessionScroll.isVisible) renderSessions(state.sessions)
        state.completedMessage?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }
    }

    private fun renderVenues(state: OwnerDropInUiState) {
        if (venueId == state.selectedVenueId && venueCount == state.venues.size) return
        venueId = state.selectedVenueId
        venueCount = state.venues.size
        settingVenue = true
        binding.venueSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, state.venues.map { it.name })
        val index = state.venues.indexOfFirst { it.id == state.selectedVenueId }
        if (index >= 0) binding.venueSpinner.setSelection(index, false)
        settingVenue = false
    }

    private fun renderSessions(sessions: List<OwnerDropInSession>) {
        binding.sessionList.removeAllViews()
        sessions.sortedBy { it.startAt }.forEach { session ->
            val row = ItemOwnerDropinSessionBinding.inflate(layoutInflater, binding.sessionList, false)
            val start = session.startAt.atZone(VIETNAM_ZONE)
            val end = session.endAt.atZone(VIETNAM_ZONE)
            row.time.text = getString(R.string.owner_dropin_session_time, start.format(DATE_FORMAT), start.format(TIME_FORMAT), end.format(TIME_FORMAT))
            row.status.text = getString(R.string.owner_dropin_session_status, session.status)
            row.capacity.text = getString(R.string.owner_dropin_capacity_status, session.registeredCount, session.capacity, NUMBER_FORMAT.format(session.pricePerPersonVnd))
            row.roster.setOnClickListener {
                findNavController().navigate(
                    R.id.action_ownerDropIn_to_roster,
                    Bundle().apply { putString("venueId", session.venueId); putString("sessionId", session.sessionId) },
                )
            }
            binding.sessionList.addView(row.root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private companion object {
        val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("vi"))
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi"))
        val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"))
    }
}
