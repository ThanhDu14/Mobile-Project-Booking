package com.example.booking_app.feature.auth.ui.forgot

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAuthForgotPasswordBinding
import com.example.booking_app.feature.auth.ui.messageRes
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Quên mật khẩu theo wireframe `buoc7_quen-mat-khau`. Đặt mật khẩu mới trên trang của Firebase (FR-019). */
@AndroidEntryPoint
class ForgotPasswordFragment : Fragment(R.layout.fragment_auth_forgot_password) {

    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthForgotPasswordBinding.bind(view)

        if (binding.emailInput.text.isNullOrEmpty()) binding.emailInput.setText(viewModel.uiState.value.email)
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.emailInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_SEND).also { if (it) viewModel.onSubmit() }
        }
        binding.submitButton.setOnClickListener { viewModel.onSubmit() }
        binding.resendButton.setOnClickListener { viewModel.onSubmit() }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.backToLoginButton.setOnClickListener { backToLogin() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ForgotPasswordEvent.ShowNetworkError ->
                                Snackbar.make(binding.root, R.string.auth_error_network, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.auth_retry) { viewModel.onSubmit() }
                                    .show()
                            is ForgotPasswordEvent.ShowError ->
                                Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentAuthForgotPasswordBinding, state: ForgotPasswordUiState) {
        binding.emailLayout.error = state.emailError?.let { getString(it.messageRes()) }

        binding.submitContainer.isVisible = !state.sent
        binding.submitButton.isEnabled = !state.isSubmitting && state.resendInSeconds == 0
        binding.submitButton.text = when {
            state.isSubmitting -> ""
            state.resendInSeconds > 0 -> getString(R.string.auth_forgot_resend_in, formatSeconds(state.resendInSeconds))
            else -> getString(R.string.auth_forgot_submit)
        }
        binding.submitProgress.isInvisible = !state.isSubmitting || state.sent

        binding.sentGroup.isVisible = state.sent
        binding.backToLoginButton.isVisible = state.sent
        binding.resendButton.isEnabled = !state.isSubmitting && state.resendInSeconds == 0
        binding.resendButton.text = when {
            state.isSubmitting -> ""
            state.resendInSeconds > 0 -> getString(R.string.auth_forgot_resend_in, formatSeconds(state.resendInSeconds))
            else -> getString(R.string.auth_forgot_resend)
        }
        binding.resendProgress.isInvisible = !state.isSubmitting || !state.sent
    }

    /** Đi từ màn hình đăng nhập thì quay lại đó; đi từ lỗi "Email đã được sử dụng" thì mở màn hình đăng nhập. */
    private fun backToLogin() {
        val navController = findNavController()
        if (navController.previousBackStackEntry?.destination?.id == R.id.loginFragment) {
            navController.popBackStack()
        } else {
            navController.navigate(R.id.action_forgot_to_login, bundleOf(ARG_EMAIL to viewModel.uiState.value.email))
        }
    }

    private fun formatSeconds(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    private companion object {
        const val ARG_EMAIL = "email"
    }
}
