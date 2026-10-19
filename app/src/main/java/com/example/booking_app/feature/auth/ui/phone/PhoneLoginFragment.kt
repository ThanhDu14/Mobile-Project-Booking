package com.example.booking_app.feature.auth.ui.phone

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAuthPhoneBinding
import com.example.booking_app.feature.auth.ui.messageRes
import com.example.booking_app.feature.auth.ui.otp.OtpViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Nhập số điện thoại theo wireframe `buoc4_dang-nhap-so-dien-thoai`. */
@AndroidEntryPoint
class PhoneLoginFragment : Fragment(R.layout.fragment_auth_phone) {

    private val viewModel: PhoneLoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthPhoneBinding.bind(view)

        if (binding.phoneInput.text.isNullOrEmpty()) binding.phoneInput.setText(viewModel.uiState.value.phone)
        binding.phoneInput.doAfterTextChanged { viewModel.onPhoneChanged(it?.toString().orEmpty()) }
        binding.phoneInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_SEND).also { if (it) viewModel.onSubmit(requireActivity()) }
        }
        binding.submitButton.setOnClickListener { viewModel.onSubmit(requireActivity()) }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.emailButton.setOnClickListener { openEmailLogin() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PhoneLoginEvent.OpenOtp -> findNavController().navigate(
                                R.id.action_phone_to_otp,
                                bundleOf(
                                    OtpViewModel.ARG_PHONE to event.session.phoneE164,
                                    OtpViewModel.ARG_VERIFICATION_ID to event.session.verificationId,
                                    OtpViewModel.ARG_SENT_AT to event.session.sentAtElapsed,
                                    OtpViewModel.ARG_RESEND_TOKEN to event.session.resendToken,
                                ),
                            )
                            PhoneLoginEvent.ShowNetworkError ->
                                Snackbar.make(binding.root, R.string.auth_error_network, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.auth_retry) { viewModel.onSubmit(requireActivity()) }
                                    .show()
                            is PhoneLoginEvent.ShowError ->
                                Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentAuthPhoneBinding, state: PhoneLoginUiState) {
        binding.phoneLayout.error = state.phoneError?.let { getString(it.messageRes()) }
            ?: state.sendError?.let { getString(it.messageRes()) }
        binding.submitButton.isEnabled = !state.isSending
        binding.submitButton.text = if (state.isSending) "" else getString(R.string.auth_phone_submit)
        binding.submitProgress.isInvisible = !state.isSending
    }

    /** "Đăng nhập bằng email": đi từ màn hình đăng nhập thì quay lại đó. */
    private fun openEmailLogin() {
        val navController = findNavController()
        if (navController.previousBackStackEntry?.destination?.id == R.id.loginFragment) {
            navController.popBackStack()
        } else {
            navController.navigate(R.id.action_phone_to_login)
        }
    }
}
