package com.example.booking_app.feature.auth.ui.consent

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.activity.addCallback
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
import com.example.booking_app.core.session.ConsentPrefill
import com.example.booking_app.databinding.FragmentAuthTermsConsentBinding
import com.example.booking_app.feature.auth.ui.legal.LegalDocumentFragment
import com.example.booking_app.feature.auth.ui.legal.legalConsentText
import com.example.booking_app.feature.auth.ui.messageRes
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Hoàn tất tài khoản theo wireframe `buoc6_hoan-tat-tai-khoan`. Chưa xong bước này thì không vào được màn hình
 * chính: Back cũng như "Dùng tài khoản khác" (đăng xuất).
 */
@AndroidEntryPoint
class TermsConsentFragment : Fragment(R.layout.fragment_auth_terms_consent) {

    private val viewModel: TermsConsentViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthTermsConsentBinding.bind(view)

        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.termsCheckbox.setOnCheckedChangeListener { _, checked -> viewModel.onTermsChecked(checked) }
        binding.termsText.movementMethod = LinkMovementMethod.getInstance()
        binding.termsText.text = legalConsentText(requireContext(), R.string.auth_consent_terms) { doc ->
            LegalDocumentFragment.open(findNavController(), doc)
        }
        binding.submitButton.setOnClickListener { viewModel.onSubmit() }
        binding.otherAccountButton.setOnClickListener { viewModel.onUseOtherAccount() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { viewModel.onUseOtherAccount() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        val message = when (event) {
                            TermsConsentEvent.ShowNetworkError -> R.string.auth_error_network
                            is TermsConsentEvent.ShowError -> event.error.messageRes()
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
                            if (event == TermsConsentEvent.ShowNetworkError) {
                                setAction(R.string.auth_retry) { viewModel.onSubmit() }
                            }
                        }.show()
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentAuthTermsConsentBinding, state: TermsConsentUiState) {
        state.prefill?.let { prefill ->
            binding.verifiedBy.setText(
                when (prefill.provider) {
                    ConsentPrefill.Provider.GOOGLE -> R.string.auth_consent_verified_google
                    ConsentPrefill.Provider.PHONE -> R.string.auth_consent_verified_phone
                    ConsentPrefill.Provider.PASSWORD -> R.string.auth_consent_verified_email
                },
            )
            binding.verifiedContact.text = prefill.email ?: prefill.phone
            binding.nameLayout.helperText = getString(
                if (prefill.provider == ConsentPrefill.Provider.GOOGLE) {
                    R.string.auth_consent_name_helper_google
                } else {
                    R.string.auth_consent_name_helper
                },
            )
        }
        if (binding.nameInput.text?.toString() != state.name) binding.nameInput.setText(state.name)
        binding.nameLayout.error = state.nameError?.let { getString(it.messageRes()) }
        if (binding.termsCheckbox.isChecked != state.termsChecked) binding.termsCheckbox.isChecked = state.termsChecked

        binding.submitButton.isEnabled = state.canSubmit
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.auth_consent_submit)
        binding.submitProgress.isInvisible = !state.isSubmitting
        binding.checkToContinue.isVisible = !state.termsChecked
        binding.otherAccountButton.isEnabled = !state.isSubmitting
    }
}
