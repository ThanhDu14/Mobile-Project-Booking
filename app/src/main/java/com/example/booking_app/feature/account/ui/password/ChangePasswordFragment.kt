package com.example.booking_app.feature.account.ui.password

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAccountChangePasswordBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Đổi mật khẩu (US5, FR-014). Chỉ mở được khi tài khoản có provider `password` (FR-015, `accountEntries`). */
@AndroidEntryPoint
class ChangePasswordFragment : Fragment(R.layout.fragment_account_change_password) {

    private val viewModel: ChangePasswordViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAccountChangePasswordBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.currentInput.doAfterTextChanged { viewModel.onCurrentChanged(it?.toString().orEmpty()) }
        binding.newInput.doAfterTextChanged { viewModel.onNewChanged(it?.toString().orEmpty()) }
        binding.confirmInput.doAfterTextChanged { viewModel.onConfirmChanged(it?.toString().orEmpty()) }
        binding.confirmInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) viewModel.onSubmit() }
        }
        binding.submitButton.setOnClickListener { viewModel.onSubmit() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentAccountChangePasswordBinding, state: ChangePasswordUiState) {
        // Ô bị xóa sau lỗi mạng (FR-023): đồng bộ lại nội dung ô với state.
        binding.currentInput.syncText(state.current)
        binding.newInput.syncText(state.new)
        binding.confirmInput.syncText(state.confirm)
        binding.currentLayout.error = state.currentError?.let(::getString)
        binding.newLayout.error = state.newError?.let(::getString)
        binding.confirmLayout.error = state.confirmError?.let(::getString)
        binding.checkLen8.renderCheck(state.checks.len8)
        binding.checkLetterDigit.renderCheck(state.checks.letterDigit)
        binding.submitButton.isEnabled = state.canSubmit
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.account_password_submit)
        binding.progress.isVisible = state.isSubmitting
    }

    private fun handle(binding: FragmentAccountChangePasswordBinding, event: ChangePasswordEvent) {
        when (event) {
            ChangePasswordEvent.Changed -> {
                // Gắn vào view của Activity để Snackbar còn hiện sau khi quay lại trang Tài khoản.
                Snackbar.make(requireActivity().findViewById(android.R.id.content), R.string.account_password_changed, Snackbar.LENGTH_SHORT)
                    .show()
                findNavController().popBackStack()
            }
            is ChangePasswordEvent.ShowError -> {
                val snackbar = Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                if (event.retryable) snackbar.setAction(R.string.account_retry) { binding.currentInput.requestFocus() }
                snackbar.show()
            }
        }
    }

    private fun TextView.syncText(value: String) {
        if (text?.toString() != value) setText(value)
    }

    /** Dấu tích màu chính khi đạt, màu chữ phụ khi chưa (giống màn hình đăng ký của 010). */
    private fun TextView.renderCheck(passed: Boolean) {
        val color = MaterialColors.getColor(
            this,
            if (passed) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
        setTextColor(color)
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
    }
}
