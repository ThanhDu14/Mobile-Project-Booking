package com.example.booking_app.feature.account.ui.district

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.SheetAccountDistrictPickerBinding
import com.example.booking_app.feature.account.domain.validation.DistrictSelectionRule
import com.example.booking_app.feature.account.ui.AccountResults
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Chọn tối đa 5 khu vực; trả kết quả cho `EditProfileFragment` bằng Fragment Result API. */
@AndroidEntryPoint
class DistrictPickerBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: DistrictPickerViewModel by viewModels()
    private var binding: SheetAccountDistrictPickerBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetAccountDistrictPickerBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.doneButton.setOnClickListener {
            setFragmentResult(
                AccountResults.DISTRICTS,
                bundleOf(AccountResults.DISTRICT_IDS to viewModel.selectedIds().toTypedArray()),
            )
            dismiss()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: DistrictPickerUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is DistrictPickerUiState.Loading
        binding.empty.isVisible = state is DistrictPickerUiState.Empty
        binding.error.isVisible = state is DistrictPickerUiState.Error
        binding.listScroll.isVisible = state is DistrictPickerUiState.Content
        binding.doneButton.isEnabled = state is DistrictPickerUiState.Content
        val content = state as? DistrictPickerUiState.Content
        binding.counter.isVisible = content != null
        content ?: return
        binding.counter.text =
            getString(R.string.account_districts_counter, content.selectedIds.size, DistrictSelectionRule.MAX)

        binding.list.removeAllViews()
        content.districts.forEach { district ->
            val checked = district.id in content.selectedIds
            val box = MaterialCheckBox(requireContext()).apply {
                text = district.name
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_min)
                isChecked = checked
                // Đủ 5 thì khu vực chưa chọn bị tắt (FR-002).
                isEnabled = checked || content.canSelectMore
                setOnClickListener { viewModel.toggle(district.id) }
            }
            binding.list.addView(box)
        }
    }
}
