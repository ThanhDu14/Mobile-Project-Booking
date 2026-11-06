package com.example.booking_app.feature.owner.ui.vouchers

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
import com.example.booking_app.databinding.FragmentOwnerVouchersBinding
import com.example.booking_app.databinding.ItemOwnerVoucherBinding
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucher
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerVouchersFragment : Fragment(R.layout.fragment_owner_vouchers) {
    private val viewModel: OwnerVoucherViewModel by viewModels()
    private var _binding: FragmentOwnerVouchersBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var selectedVenueId: String? = null
    private var venueCount = -1
    private var settingSpinner = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerVouchersBinding.bind(view)
        binding.retry.setOnClickListener { viewModel.refresh() }
        binding.createVoucher.setOnClickListener {
            selectedVenueId?.let { id ->
                findNavController().navigate(R.id.action_ownerVouchers_to_form, Bundle().apply { putString(ARG_VENUE_ID, id) })
            } ?: Snackbar.make(binding.root, R.string.owner_voucher_select_venue_first, Snackbar.LENGTH_SHORT).show()
        }
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

    private fun render(state: OwnerVoucherUiState) {
        binding.loading.isVisible = state.isLoading
        binding.errorText.isVisible = !state.isLoading && state.error != null
        binding.errorText.text = state.error?.let { error ->
            if (error.contains("Contract CRUD", true)) getString(R.string.owner_voucher_contract_error) else error
        }
        binding.emptyText.isVisible = !state.isLoading && state.error == null && state.isEmpty
        binding.voucherScroll.isVisible = !state.isLoading && state.error == null && state.vouchers.isNotEmpty()
        renderVenues(state.venues, state.selectedVenueId)
        if (binding.voucherScroll.isVisible) renderVouchers(state.vouchers)
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

    private fun renderVouchers(vouchers: List<OwnerVoucher>) {
        binding.voucherList.removeAllViews()
        vouchers.forEach { voucher ->
            val row = ItemOwnerVoucherBinding.inflate(layoutInflater, binding.voucherList, false)
            row.code.text = voucher.code
            row.range.text = getString(R.string.owner_voucher_range, voucher.validFrom.format(DATE_FORMAT), voucher.validTo.format(DATE_FORMAT))
            row.usage.text = getString(R.string.owner_voucher_usage, voucher.usedCount, voucher.usageLimit?.toString() ?: getString(R.string.owner_voucher_unlimited), if (voucher.isActive) getString(R.string.owner_voucher_active) else getString(R.string.owner_voucher_inactive))
            row.isActive.setOnCheckedChangeListener(null)
            row.isActive.isChecked = voucher.isActive
            row.isActive.setOnCheckedChangeListener { _, active ->
                selectedVenueId?.let { venue -> viewModel.setActive(venue, voucher.code, active) }
            }
            binding.voucherList.addView(row.root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private companion object {
        const val ARG_VENUE_ID = "venueId"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
