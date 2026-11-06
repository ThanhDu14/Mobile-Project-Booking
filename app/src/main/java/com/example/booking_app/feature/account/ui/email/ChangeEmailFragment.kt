package com.example.booking_app.feature.account.ui.email

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAccountChangeEmailBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Đổi email đăng nhập (US7, FR-016). Chỉ mở được khi tài khoản có provider `password` (`accountEntries`). */
@AndroidEntryPoint
class ChangeEmailFragment : Fragment(R.layout.fragment_account_change_email) {

    private val viewModel: ChangeEmailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAccountChangeEmailBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.currentEmail.text = viewModel.currentEmail.orEmpty()
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) viewModel.onSubmit() }
        }
        binding.submitButton.setOnClickListener { viewModel.onSubmit() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.errors.collect { error ->
                        val snackbar = Snackbar.make(binding.root, error.message, Snackbar.LENGTH_LONG)
                        if (error.retryable) snackbar.setAction(R.string.account_retry) { viewModel.onSubmit() }
                        snackbar.show()
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentAccountChangeEmailBinding, state: ChangeEmailUiState) {
        binding.emailInput.syncText(state.email)
        binding.passwordInput.syncText(state.password)
        binding.emailLayout.error = state.emailError?.let(::getString)
        binding.passwordLayout.error = state.passwordError?.let(::getString)
        binding.sentCard.isVisible = state.sent
        binding.submitButton.isEnabled = state.canSubmit
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.account_email_submit)
        binding.progress.isVisible = state.isSubmitting
    }

    private fun TextView.syncText(value: String) {
        if (text?.toString() != value) setText(value)
    }
}
