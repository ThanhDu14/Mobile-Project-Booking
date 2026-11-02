package com.example.booking_app.feature.auth.ui.login

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
import com.example.booking_app.databinding.FragmentAuthLoginBinding
import com.example.booking_app.feature.auth.ui.link.LinkAccountDialog
import com.example.booking_app.feature.auth.ui.messageRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Đăng nhập theo wireframe `buoc2_dang-nhap`. Đăng nhập xong `MainActivity` điều hướng theo `SessionState`
 * (đóng `nav_auth` hoặc vào graph của vai trò, rồi mở `returnTo` nếu có).
 */
@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_auth_login) {

    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthLoginBinding.bind(view)

        if (binding.emailInput.text.isNullOrEmpty()) binding.emailInput.setText(viewModel.uiState.value.email)
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) viewModel.onSubmit() }
        }
        binding.loginButton.setOnClickListener { viewModel.onSubmit() }

        binding.googleButton.setOnClickListener { viewModel.onGoogle(requireActivity()) }

        binding.registerButton.setOnClickListener { openRegister() }

        binding.forgotButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgot, bundleOf(ARG_EMAIL to viewModel.uiState.value.email))
        }

        binding.phoneButton.setOnClickListener { findNavController().navigate(R.id.action_login_to_phone) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentAuthLoginBinding, state: LoginUiState) {
        binding.emailLayout.error = state.emailError?.let { getString(it.messageRes()) }
        binding.passwordLayout.error = state.passwordError?.let { getString(it.messageRes()) }
        binding.formError.isVisible = state.formError != null
        state.formError?.let { binding.formError.setText(it.messageRes()) }

        val busy = state.isSubmitting || state.isGoogleSubmitting
        binding.loginButton.isEnabled = !busy
        binding.loginButton.text = if (state.isSubmitting) "" else getString(R.string.auth_login_submit)
        binding.loginProgress.isInvisible = !state.isSubmitting
        binding.googleButton.isEnabled = !busy
        binding.googleButton.text = if (state.isGoogleSubmitting) "" else getString(R.string.auth_continue_google)
        binding.googleProgress.isInvisible = !state.isGoogleSubmitting
    }

    private fun handle(binding: FragmentAuthLoginBinding, event: LoginEvent) {
        when (event) {
            LoginEvent.ShowLocked -> showLockedDialog()
            LoginEvent.ShowSessionRevoked ->
                Snackbar.make(binding.root, R.string.auth_session_revoked, Snackbar.LENGTH_LONG).show()
            LoginEvent.ClearPassword -> binding.passwordInput.text = null
            LoginEvent.ShowNetworkError ->
                Snackbar.make(binding.root, R.string.auth_error_network, Snackbar.LENGTH_LONG)
                    .setAction(R.string.auth_retry) { binding.passwordInput.requestFocus() }
                    .show()
            is LoginEvent.ShowError ->
                Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
            is LoginEvent.OpenLinkDialog ->
                if (childFragmentManager.findFragmentByTag(LinkAccountDialog.TAG) == null) {
                    LinkAccountDialog.newInstance(event.email).show(childFragmentManager, LinkAccountDialog.TAG)
                }
            LoginEvent.LinkDone -> {
                (childFragmentManager.findFragmentByTag(LinkAccountDialog.TAG) as? LinkAccountDialog)?.dismiss()
                Snackbar.make(binding.root, R.string.auth_link_done, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /** Sang màn hình đăng ký; đi từ màn hình đăng ký thì quay lại đó. Giữ `returnTo` (FR-026). */
    private fun openRegister() {
        val navController = findNavController()
        if (navController.previousBackStackEntry?.destination?.id == R.id.registerFragment) {
            navController.popBackStack()
            return
        }
        navController.navigate(R.id.action_login_to_register, bundleOf(ARG_RETURN_TO to arguments?.getString(ARG_RETURN_TO)))
    }

    private fun showLockedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_locked_title)
            .setMessage(getString(R.string.auth_locked_message, getString(R.string.support_email)))
            .setPositiveButton(R.string.auth_close, null)
            .show()
    }

    private companion object {
        const val ARG_RETURN_TO = "returnTo"
        const val ARG_EMAIL = "email"
    }
}
