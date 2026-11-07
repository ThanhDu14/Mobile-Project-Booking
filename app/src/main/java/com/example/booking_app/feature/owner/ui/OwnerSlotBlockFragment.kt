package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerSlotBlockBinding.bind(view)
        binding.reason.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.owner_slot_block_reasons)))
        binding.preview.setOnClickListener { viewModel.updateForm(readForm()); viewModel.preview() }
        binding.submit.setOnClickListener { viewModel.updateForm(readForm()); viewModel.submit() }
        binding.retry.setOnClickListener { viewModel.retryAfterRefresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    private fun readForm() = SlotBlockInput(
        courtId = requireArguments().getString("courtId").orEmpty(),
        date = binding.date.text.toString(), startTime = binding.startTime.text.toString(),
        endTime = binding.endTime.text.toString(), reason = binding.reason.text.toString()
    )

    private fun render(state: OwnerSlotBlockUiState) {
        binding.progress.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
        binding.stale.visibility = if (state.isStale) View.VISIBLE else View.GONE
        binding.offline.visibility = if (state.isOffline) View.VISIBLE else View.GONE
        binding.message.text = state.error ?: state.message.orEmpty()
        binding.slotIds.text = state.slotIds.joinToString("\n")
        binding.retry.visibility = if (state.isStale) View.VISIBLE else View.GONE
        binding.submit.isEnabled = !state.isOffline && !state.isSubmitting && !state.isStale
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
