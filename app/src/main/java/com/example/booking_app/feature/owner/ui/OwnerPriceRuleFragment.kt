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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

@AndroidEntryPoint
class OwnerPriceRuleFragment : Fragment(R.layout.fragment_owner_price_rule) {
    private var _binding: FragmentOwnerPriceRuleBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerPriceRuleViewModel by viewModels()

    private var editingRuleId: String? = null
    private var gapDialog: AlertDialog? = null
    private var selectedLabel = "NORMAL"

    private val adapter = OwnerPriceRuleAdapter(
        onEdit = { rule ->
            editingRuleId = rule.id
            binding.formTitle.setText(R.string.edit)
            binding.daysInput.setText(rule.daysOfWeek.sorted().joinToString(","))
            binding.startTimeInput.setText(rule.startTime)
            binding.endTimeInput.setText(rule.endTime)
            binding.priceInput.setText(rule.pricePerHour.toString())
            selectedLabel=rule.label
            binding.labelInput.setText(priceLabels()[listOf("NORMAL","PEAK","WEEKEND").indexOf(rule.label).coerceAtLeast(0)])
        },
        onDelete = { rule ->
            viewModel.requestDelete(rule)
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerPriceRuleBinding.bind(view)
        binding.rules.adapter = adapter
        binding.retry.setOnClickListener { viewModel.load() }
        binding.labelInput.showSoftInputOnFocus=false
        editingRuleId = savedInstanceState?.getString("editingRuleId") ?: editingRuleId
        selectedLabel = savedInstanceState?.getString("selectedLabel") ?: selectedLabel
        binding.labelInput.setText(priceLabels()[listOf("NORMAL","PEAK","WEEKEND").indexOf(selectedLabel).coerceAtLeast(0)])
        binding.labelInput.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.owner_price_kind)
                .setItems(priceLabels()) { _,index -> selectedLabel=listOf("NORMAL","PEAK","WEEKEND")[index]; binding.labelInput.setText(priceLabels()[index]) }.show()
        }
        listOf(binding.startTimeInput,binding.endTimeInput).forEach { field ->
            field.showSoftInputOnFocus=false
            field.setOnClickListener {
                val raw=field.text.toString().padEnd(4,'0')
                val picker=MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(raw.take(2).toIntOrNull() ?: 8).setMinute(raw.takeLast(2).toIntOrNull() ?: 0).build()
                picker.addOnPositiveButtonClickListener { field.setText("%02d%02d".format(picker.hour,picker.minute)) }
                picker.show(parentFragmentManager,"owner-price-time")
            }
        }

        binding.saveRule.setOnClickListener {
            val days = binding.daysInput.text.toString().split(",")
                .mapNotNull { it.trim().toIntOrNull() }
            val startTime = binding.startTimeInput.text.toString().trim()
            val endTime = binding.endTimeInput.text.toString().trim()
            val price = binding.priceInput.text.toString().trim().toLongOrNull() ?: 0L
            val label = selectedLabel

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
        binding.saveRule.isEnabled = state.isFresh && !state.isSubmitting && !state.isLoading
        listOf(binding.daysInput,binding.startTimeInput,binding.endTimeInput,binding.priceInput,binding.labelInput)
            .forEach { it.isEnabled = binding.saveRule.isEnabled }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("editingRuleId", editingRuleId)
        outState.putString("selectedLabel", selectedLabel)
    }

    private fun clearForm() {
        editingRuleId = null
        binding.formTitle.setText(R.string.owner_price_rule_add)
        binding.daysInput.text.clear()
        binding.startTimeInput.text.clear()
        binding.endTimeInput.text.clear()
        binding.priceInput.text.clear()
        selectedLabel="NORMAL"
        binding.labelInput.setText(priceLabels()[0])
    }

    private fun priceLabels()=arrayOf(getString(R.string.owner_price_normal_label),getString(R.string.owner_price_peak_label),getString(R.string.owner_price_weekend_label))

    override fun onDestroyView() {
        gapDialog?.dismiss()
        gapDialog = null
        _binding = null
        super.onDestroyView()
    }
}
