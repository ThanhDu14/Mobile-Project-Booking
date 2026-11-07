package com.example.booking_app.feature.owner.ui.vouchers

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerVoucherFormBinding
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherDraft
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherType
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerVoucherFormFragment : Fragment(R.layout.fragment_owner_voucher_form) {
    private val viewModel: OwnerVoucherViewModel by viewModels()
    private var _binding: FragmentOwnerVoucherFormBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val venueId get() = requireArguments().getString(ARG_VENUE_ID).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerVoucherFormBinding.bind(view)
        binding.type.adapter = ArrayAdapter.createFromResource(requireContext(), R.array.owner_voucher_types, android.R.layout.simple_spinner_dropdown_item)
        binding.save.setOnClickListener { submitForm() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.loading.isVisible = state.isSaving
                    binding.save.isEnabled = !state.isSaving
                    binding.validationText.isVisible = state.validationErrors.isNotEmpty()
                    binding.validationText.text = state.validationErrors.joinToString("\n")
                    binding.errorText.isVisible = state.error != null
                    binding.errorText.text = state.error?.let { error ->
                        if (error.contains("Contract CRUD", true)) getString(R.string.owner_voucher_contract_error) else error
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun submitForm() {
        val from = runCatching { LocalDate.parse(binding.validFrom.text?.toString()?.trim().orEmpty()) }.getOrNull()
        val to = runCatching { LocalDate.parse(binding.validTo.text?.toString()?.trim().orEmpty()) }.getOrNull()
        if (from == null || to == null) {
            binding.validationText.text = getString(R.string.owner_voucher_date_format_error)
            binding.validationText.isVisible = true
            return
        }
        val draft = OwnerVoucherDraft(
            code = binding.code.text?.toString().orEmpty().trim(),
            type = if (binding.type.selectedItemPosition == 0) OwnerVoucherType.PERCENT else OwnerVoucherType.FIXED,
            value = binding.value.text?.toString()?.toLongOrNull() ?: 0L,
            maxDiscountVnd = binding.maxDiscount.text?.toString()?.toLongOrNull() ?: 0L,
            minOrderTotalVnd = binding.minOrder.text?.toString()?.toLongOrNull() ?: 0L,
            validFrom = from,
            validTo = to,
            usageLimit = binding.usageLimit.text?.toString()?.toIntOrNull(),
            perUserLimit = binding.perUserLimit.text?.toString()?.toIntOrNull(),
            isActive = binding.isActive.isChecked,
        )
        viewModel.create(venueId, draft)
    }

    private companion object { const val ARG_VENUE_ID = "venueId" }
}
