package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerPriceRuleBinding
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import com.example.booking_app.feature.owner.domain.PriceRuleInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerPriceRuleFragment : Fragment(R.layout.fragment_owner_price_rule) {
    private var _binding: FragmentOwnerPriceRuleBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerPriceRuleViewModel by viewModels()

    private var editingRuleId: String? = null
    private var gapDialog: AlertDialog? = null

    private val adapter = OwnerPriceRuleAdapter(
        onEdit = { rule ->
            editingRuleId = rule.id
            binding.formTitle.setText(R.string.edit)
            binding.daysInput.setText(rule.daysOfWeek.sorted().joinToString(","))
            binding.startTimeInput.setText(rule.startTime)
            binding.endTimeInput.setText(rule.endTime)
            binding.priceInput.setText(rule.pricePerHour.toString())
            binding.labelInput.setText(rule.label)
        },
        onDelete = { rule ->
            viewModel.requestDelete(rule)
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerPriceRuleBinding.bind(view)
        binding.rules.adapter = adapter
        binding.retry.setOnClickListener { viewModel.load() }

        binding.saveRule.setOnClickListener {
            val days = binding.daysInput.text.toString().split(",")
                .mapNotNull { it.trim().toIntOrNull() }
            val startTime = binding.startTimeInput.text.toString().trim()
            val endTime = binding.endTimeInput.text.toString().trim()
            val price = binding.priceInput.text.toString().trim().toLongOrNull() ?: 0L
            val label = binding.labelInput.text.toString().trim().uppercase()

            val input = PriceRuleInput(days, startTime, endTime, price, label)
            viewModel.save(editingRuleId, input)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: OwnerPriceRuleUiState) {
        binding.progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.empty.visibility = if (state.isEmpty) View.VISIBLE else View.GONE

        binding.message.text = when (state.errorKind) {
            OwnerErrorKind.PERMISSION -> getString(R.string.owner_permission_error)
            OwnerErrorKind.NETWORK -> getString(R.string.owner_network_error)
            else -> state.error ?: state.message.orEmpty()
        }

        binding.freshness.text = if (state.isFresh) {
            getString(R.string.owner_data_fresh)
        } else if (state.error != null) {
            getString(R.string.owner_data_not_fresh)
        } else ""

        adapter.submitList(state.rules)

        if (state.message == "Đã lưu bảng giá" && !state.isSubmitting) {
            clearForm()
        }

        val ruleToWarn = state.gapWarningRule
        if (ruleToWarn != null && gapDialog == null) {
            gapDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.owner_price_delete)
                .setMessage(R.string.owner_price_delete_gap_warning)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.confirmDelete(ruleToWarn.id)
                    gapDialog = null
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    viewModel.dismissGapWarning()
                    gapDialog = null
                }
                .setOnDismissListener {
                    gapDialog = null
                }
                .show()
        } else if (ruleToWarn == null) {
            gapDialog?.dismiss()
            gapDialog = null
        }
    }

    private fun clearForm() {
        editingRuleId = null
        binding.formTitle.setText(R.string.owner_price_rule_add)
        binding.daysInput.text.clear()
        binding.startTimeInput.text.clear()
        binding.endTimeInput.text.clear()
        binding.priceInput.text.clear()
        binding.labelInput.text.clear()
    }

    override fun onDestroyView() {
        gapDialog?.dismiss()
        gapDialog = null
        _binding = null
        super.onDestroyView()
    }
}
