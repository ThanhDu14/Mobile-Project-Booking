package com.example.booking_app.feature.auth.ui.register

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
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
import com.example.booking_app.databinding.FragmentAuthRegisterBinding
import com.example.booking_app.feature.auth.ui.legal.LegalDocumentFragment
import com.example.booking_app.feature.auth.ui.legal.legalConsentText
import com.example.booking_app.feature.auth.ui.messageRes
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Đăng ký email theo wireframe `buoc3_dang-ky-email`. Đăng ký xong `MainActivity` điều hướng theo `SessionState`
 * (đóng `nav_auth` hoặc vào trang chủ người chơi).
 */
@AndroidEntryPoint
class RegisterFragment : Fragment(R.layout.fragment_auth_register) {

    private val viewModel: RegisterViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthRegisterBinding.bind(view)

        val state = viewModel.uiState.value
        if (binding.nameInput.text.isNullOrEmpty()) binding.nameInput.setText(state.name)
        if (binding.emailInput.text.isNullOrEmpty()) binding.emailInput.setText(state.email)
        binding.termsCheckbox.isChecked = state.termsChecked

        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.confirmInput.doAfterTextChanged { viewModel.onConfirmPasswordChanged(it?.toString().orEmpty()) }
        mapOf(
            binding.nameInput to RegisterField.NAME,
            binding.emailInput to RegisterField.EMAIL,
            binding.passwordInput to RegisterField.PASSWORD,
            binding.confirmInput to RegisterField.CONFIRM_PASSWORD,
        ).forEach { (input, field) ->
            input.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.onFieldFocusLost(field) }
        }
        binding.termsCheckbox.setOnCheckedChangeListener { _, checked -> viewModel.onTermsChecked(checked) }
        binding.termsText.movementMethod = LinkMovementMethod.getInstance()
        binding.termsText.text = legalConsentText(requireContext(), R.string.auth_register_terms) { doc ->
            LegalDocumentFragment.open(findNavController(), doc)
        }
        binding.emailInUseText.movementMethod = LinkMovementMethod.getInstance()
        binding.emailInUseText.text = emailInUseText()

        binding.submitButton.setOnClickListener { viewModel.onSubmit() }
        binding.googleButton.setOnClickListener { viewModel.onGoogle(requireActivity()) }
        binding.loginButton.setOnClickListener { openLogin() }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        // TODO(US5 T072): nối màn hình số điện thoại.
        binding.phoneButton.setOnClickListener { comingSoon(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    private fun render(binding: FragmentAuthRegisterBinding, state: RegisterUiState) {
        binding.nameLayout.error = state.nameError?.let { getString(it.messageRes()) }
        binding.emailLayout.error = state.emailError?.let { getString(it.messageRes()) }
        binding.emailInUseText.isVisible = state.emailInUse
        binding.passwordLayout.error = state.passwordError?.let { getString(it.messageRes()) }
        binding.confirmLayout.error = state.confirmError?.let { getString(it.messageRes()) }
        binding.checkLen8.renderCheck(state.passwordChecks.len8)
        binding.checkLetterDigit.renderCheck(state.passwordChecks.letterDigit)
        if (binding.termsCheckbox.isChecked != state.termsChecked) binding.termsCheckbox.isChecked = state.termsChecked

        val busy = state.isSubmitting || state.isGoogleSubmitting
        binding.submitButton.isEnabled = state.canSubmit
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.auth_register_submit)
        binding.submitProgress.isInvisible = !state.isSubmitting
        binding.googleButton.isEnabled = !busy
        binding.googleButton.text = if (state.isGoogleSubmitting) "" else getString(R.string.auth_register_google)
        binding.googleProgress.isInvisible = !state.isGoogleSubmitting
    }

    private fun handle(binding: FragmentAuthRegisterBinding, event: RegisterEvent) {
        when (event) {
            RegisterEvent.ShowNetworkError ->
                Snackbar.make(binding.root, R.string.auth_error_network, Snackbar.LENGTH_LONG)
                    .setAction(R.string.auth_retry) { viewModel.onSubmit() }
                    .show()
            is RegisterEvent.ShowError ->
                Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
            RegisterEvent.OpenLogin -> openLogin()
        }
    }

    /** Dấu tích màu court khi đạt, màu chữ phụ khi chưa. */
    private fun TextView.renderCheck(passed: Boolean) {
        val color = MaterialColors.getColor(
            this,
            if (passed) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
        setTextColor(color)
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
    }

    private fun emailInUseText(): CharSequence {
        val login = getString(R.string.auth_register_email_in_use_login)
        val forgot = getString(R.string.auth_register_email_in_use_forgot)
        val text = SpannableString(getString(R.string.auth_register_email_in_use, login, forgot))
        listOf<Pair<String, (View) -> Unit>>(login to { openLogin() }, forgot to ::comingSoon).forEach { (label, onClick) ->
            val start = text.indexOf(label)
            text.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) = onClick(widget)
                },
                start,
                start + label.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return text
    }

    /**
     * Sang màn hình đăng nhập, điền sẵn email. Đi từ màn hình đăng nhập thì quay lại đó để back stack không
     * dài thêm; giữ `returnTo` để đăng nhập xong vẫn mở đúng chỗ (FR-026).
     */
    private fun openLogin() {
        val navController = findNavController()
        if (navController.previousBackStackEntry?.destination?.id == R.id.loginFragment) {
            navController.popBackStack()
            return
        }
        navController.navigate(
            R.id.action_register_to_login,
            bundleOf(ARG_EMAIL to viewModel.uiState.value.email, ARG_RETURN_TO to arguments?.getString(ARG_RETURN_TO)),
        )
    }

    private fun comingSoon(view: View) {
        Snackbar.make(view, R.string.auth_welcome_coming_soon, Snackbar.LENGTH_SHORT).show()
    }

    private companion object {
        const val ARG_EMAIL = "email"
        const val ARG_RETURN_TO = "returnTo"
    }
}
