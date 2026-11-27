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
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import androidx.core.widget.doAfterTextChanged
import android.widget.AdapterView
import android.widget.TextView

@AndroidEntryPoint
class OwnerVoucherFormFragment : Fragment(R.layout.fragment_owner_voucher_form) {
    private val viewModel: OwnerVoucherViewModel by viewModels()
    private var _binding: FragmentOwnerVoucherFormBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val venueId get() = requireArguments().getString(ARG_VENUE_ID).orEmpty()
    private val editingCode get() = arguments?.getString("code")
    private var populated = false
    private var selectedVenue = false
    private var navigatedAfterSave = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerVoucherFormBinding.bind(view)
        populated = savedInstanceState?.getBoolean("populated") ?: populated
        binding.code.isEnabled = editingCode == null
        binding.type.adapter = ArrayAdapter.createFromResource(requireContext(), R.array.owner_voucher_types, android.R.layout.simple_spinner_dropdown_item)
        listOf(binding.code, binding.value, binding.maxDiscount, binding.minOrder).forEach { input ->
            input.doAfterTextChanged { renderPreview() }
        }
        binding.type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = renderPreview()
        }
        renderPreview()
        binding.save.setOnClickListener { submitForm() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!selectedVenue && state.venues.any { it.id == venueId }) {
                        selectedVenue=true
                        viewModel.selectVenue(venueId)
                    }
                    if (!populated && editingCode != null) state.vouchers.firstOrNull { it.code == editingCode }?.let { voucher ->
                        binding.code.setText(voucher.code);binding.code.isEnabled=false
                        binding.type.setSelection(if(voucher.type==OwnerVoucherType.PERCENT)0 else 1)
                        binding.value.setText(voucher.value.toString());binding.maxDiscount.setText(voucher.maxDiscountVnd.toString())
                        binding.minOrder.setText(voucher.minOrderTotalVnd.toString());binding.validFrom.setText(voucher.validFrom.toString())
                        binding.validTo.setText(voucher.validTo.toString());binding.usageLimit.setText(voucher.usageLimit?.toString().orEmpty())
                        binding.perUserLimit.setText(voucher.perUserLimit?.toString().orEmpty());binding.isActive.isChecked=voucher.isActive
                        binding.save.setText(R.string.save);populated=true
                    }
                    if (state.saved) {
                        if (!navigatedAfterSave) {
                            navigatedAfterSave = true
                            Snackbar.make(binding.root, R.string.owner_voucher_saved, Snackbar.LENGTH_LONG).show()
                            findNavController().popBackStack()
                        }
                        return@collect
                    }
                    binding.loading.isVisible = state.isSaving || state.isLoading
                    binding.save.isEnabled = !state.isSaving && !state.isLoading && (editingCode == null || populated)
                    binding.validationText.isVisible = state.validationErrors.isNotEmpty()
                    binding.validationText.text = state.validationErrors.joinToString("\n")
                    binding.errorText.isVisible = state.error != null
                    binding.errorText.text = state.error?.let { error ->
                        if (error.contains("Contract CRUD", true)) getString(R.string.owner_voucher_contract_error) else error
                    }
                    binding.previewFooter.text = getString(R.string.owner_voucher_create_preview_footer,
                        state.venues.firstOrNull { it.id == venueId }?.name.orEmpty())
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("populated", populated)
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
        editingCode?.let { viewModel.update(venueId,it,draft) } ?: viewModel.create(venueId, draft)
    }

    private fun renderPreview() {
        binding.previewCode.text = binding.code.text?.toString().orEmpty()
        val amount = binding.value.text?.toString().orEmpty()
        val discount = if (binding.type.selectedItemPosition == 0) "$amount%" else "$amount đ"
        binding.previewSummary.text = getString(R.string.owner_voucher_preview_live, discount,
            binding.maxDiscount.text?.toString().orEmpty(), binding.minOrder.text?.toString().orEmpty())
    }

    private companion object { const val ARG_VENUE_ID = "venueId" }
}
