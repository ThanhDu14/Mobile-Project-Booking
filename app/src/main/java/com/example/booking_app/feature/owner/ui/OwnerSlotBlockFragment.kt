package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerSlotBlockBinding
import com.example.booking_app.feature.owner.domain.SlotBlockInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerSlotBlockFragment : Fragment(R.layout.fragment_owner_slot_block) {
    private var _binding: FragmentOwnerSlotBlockBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerSlotBlockViewModel by viewModels()
    private lateinit var gridAdapter: OwnerSlotGridAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerSlotBlockBinding.bind(view)

        val reasons = resources.getStringArray(R.array.owner_slot_block_reasons)
        val reasonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, reasons)
        binding.reason.setAdapter(reasonAdapter)

        gridAdapter = OwnerSlotGridAdapter { item ->
            viewModel.toggleSlotSelection(item.slotId)
        }
        binding.slotGrid.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = gridAdapter
        }

        binding.date.doAfterTextChanged { text ->
            val dateStr = text?.toString().orEmpty().trim()
            if (dateStr.length == 8) {
                viewModel.selectDate(dateStr)
            }
        }

        binding.reason.setOnItemClickListener { _, _, position, _ ->
            val selected = reasons.getOrNull(position) ?: "MAINTENANCE"
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
        courtId = requireArguments().getString("courtId").orEmpty(),
        date = binding.date.text.toString().trim(),
        startTime = binding.startTime.text.toString().trim(),
        endTime = binding.endTime.text.toString().trim(),
        reason = binding.reason.text.toString().trim()
    )

    private fun render(state: OwnerSlotBlockUiState) {
        binding.progress.visibility = if (state.isSubmitting || state.isLoadingGrid) View.VISIBLE else View.GONE
        binding.stale.visibility = if (state.isStale) View.VISIBLE else View.GONE
        binding.offline.visibility = if (state.isOffline) View.VISIBLE else View.GONE
        binding.message.text = state.error ?: state.message.orEmpty()
        binding.slotIds.text = state.slotIds.joinToString("\n")
        binding.retry.visibility = if (state.isStale) View.VISIBLE else View.GONE

        val canOperate = !state.isOffline && !state.isSubmitting && !state.isStale
        binding.submit.isEnabled = canOperate
        binding.lockSelected.isEnabled = canOperate
        binding.unlockSelected.isEnabled = canOperate

        if (binding.date.text.isNullOrEmpty() && state.selectedDate.isNotEmpty()) {
            binding.date.setText(state.selectedDate)
        }
        if (binding.reason.text.isNullOrEmpty() && state.selectedReason.isNotEmpty()) {
            binding.reason.setText(state.selectedReason, false)
        }

        gridAdapter.submitList(state.gridSlots)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
