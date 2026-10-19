package com.example.booking_app.feature.auth.ui.otp

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
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
import com.example.booking_app.databinding.FragmentAuthOtpBinding
import com.example.booking_app.feature.auth.ui.messageRes
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Nhập mã OTP theo wireframe `buoc5_nhap-otp-sai-ma`: 6 ô một chữ số, tự nhảy ô khi gõ, dán cả mã vào một ô
 * cũng được. Đúng mã thì `MainActivity` điều hướng (số mới thì sang bước Hoàn tất tài khoản).
 */
@AndroidEntryPoint
class OtpVerifyFragment : Fragment(R.layout.fragment_auth_otp) {

    private val viewModel: OtpViewModel by viewModels()

    private var digits: List<EditText> = emptyList()

    /** Đang tự điền các ô (dán mã, xóa sau khi gửi lại) thì bỏ qua sự kiện gõ. */
    private var filling = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthOtpBinding.bind(view)
        digits = listOf(binding.digit1, binding.digit2, binding.digit3, binding.digit4, binding.digit5, binding.digit6)
        digits.forEachIndexed { index, box ->
            box.contentDescription = getString(R.string.auth_otp_digit_description, index + 1)
            box.doAfterTextChanged { onDigitChanged(index, it?.toString().orEmpty()) }
            box.setOnKeyListener { _, keyCode, event ->
                val backspaceOnEmpty = keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN &&
                    box.text.isEmpty() && index > 0
                if (backspaceOnEmpty) {
                    digits[index - 1].text = null
                    digits[index - 1].requestFocus()
                }
                backspaceOnEmpty
            }
        }

        binding.subtitle.text = getString(R.string.auth_otp_subtitle, viewModel.uiState.value.maskedPhone)
        binding.submitButton.setOnClickListener { viewModel.onSubmit() }
        binding.resendButton.setOnClickListener { viewModel.onResend(requireActivity()) }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.changeNumberButton.setOnClickListener { findNavController().navigateUp() }

        if (savedInstanceState == null) {
            digits.first().requestFocus()
            ViewCompat.getWindowInsetsController(view)?.show(WindowInsetsCompat.Type.ime())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            OtpEvent.ShowNetworkError ->
                                Snackbar.make(binding.root, R.string.auth_error_network, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.auth_retry) { viewModel.onSubmit() }
                                    .show()
                            is OtpEvent.ShowError ->
                                Snackbar.make(binding.root, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun onDigitChanged(index: Int, text: String) {
        if (filling) return
        val value = text.filter(Char::isDigit)
        when {
            // Dán cả mã: chia vào các ô từ ô này trở đi.
            value.length > 2 -> fill(index, value)
            // Gõ đè lên ô đã có số: giữ số vừa gõ.
            value.length == 2 -> fill(index, value.last().toString())
            value.length == 1 && index < digits.lastIndex -> digits[index + 1].requestFocus()
        }
        viewModel.onCodeChanged(digits.joinToString("") { it.text.toString() })
    }

    private fun fill(start: Int, value: String) {
        filling = true
        value.take(digits.size - start).forEachIndexed { offset, c -> digits[start + offset].setText(c.toString()) }
        filling = false
        digits[(start + value.length).coerceAtMost(digits.lastIndex)].requestFocus()
    }

    private fun render(binding: FragmentAuthOtpBinding, state: OtpUiState) {
        // Gửi lại mã thì ViewModel xóa mã cũ.
        if (state.code.isEmpty() && digits.any { it.text.isNotEmpty() }) {
            filling = true
            digits.forEach { it.text = null }
            filling = false
            digits.first().requestFocus()
        }

        val wrong = state.state == OtpState.WrongCode || state.state == OtpState.Locked5
        val editable = state.state == OtpState.Editing || state.state == OtpState.WrongCode
        digits.forEach { box ->
            box.background = ContextCompat.getDrawable(
                requireContext(),
                if (wrong) R.drawable.bg_otp_box_error else R.drawable.bg_otp_box,
            )
            box.isEnabled = editable
        }

        binding.errorText.isVisible = state.state in setOf(OtpState.WrongCode, OtpState.Locked5, OtpState.Expired)
        binding.errorText.text = when (state.state) {
            OtpState.WrongCode ->
                resources.getQuantityString(R.plurals.auth_otp_wrong_remaining, state.remainingAttempts, state.remainingAttempts)
            OtpState.Locked5 -> getString(R.string.auth_otp_must_resend)
            OtpState.Expired -> getString(R.string.auth_error_code_expired)
            else -> null
        }

        val verifying = state.state == OtpState.Verifying
        binding.submitButton.isEnabled = state.canSubmit
        binding.submitButton.text = if (verifying) "" else getString(R.string.auth_otp_submit)
        binding.submitProgress.isInvisible = !verifying

        val waiting = state.resendInSeconds > 0
        binding.resendButton.isEnabled = !waiting && !state.isResending && !verifying
        binding.resendButton.text = when {
            state.isResending -> ""
            waiting -> getString(R.string.auth_otp_resend_in, formatSeconds(state.resendInSeconds))
            else -> getString(R.string.auth_otp_resend)
        }
        binding.resendProgress.isInvisible = !state.isResending
    }

    private fun formatSeconds(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    override fun onDestroyView() {
        super.onDestroyView()
        digits = emptyList()
    }
}
