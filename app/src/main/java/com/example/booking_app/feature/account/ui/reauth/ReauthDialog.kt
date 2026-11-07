package com.example.booking_app.feature.account.ui.reauth

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.DialogAccountReauthBinding
import com.example.booking_app.feature.account.domain.model.ReauthMethod
import com.example.booking_app.feature.account.ui.AccountResults
import com.example.booking_app.feature.auth.domain.model.PhoneNumber
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Xác nhận lại danh tính (research R8, FR-017): mật khẩu, Google hoặc OTP tới số hiện tại. Trả kết quả qua
 * Fragment Result API với khóa [AccountResults.REAUTH]; `true` khi đã xác nhận, `false` khi hủy.
 */
@AndroidEntryPoint
class ReauthDialog : DialogFragment() {

    private val viewModel: ReauthViewModel by viewModels()
    private var delivered = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAccountReauthBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_reauth_title)
            .setView(binding.root)
            .setPositiveButton(R.string.account_reauth_submit, null)
            .setNegativeButton(R.string.account_reauth_cancel, null)
            .create()

        fun submit() {
            when (viewModel.uiState.value.method) {
                ReauthMethod.Password -> viewModel.onSubmitPassword(binding.passwordInput.text?.toString().orEmpty())
                is ReauthMethod.Phone -> viewModel.onSubmitCode(binding.codeInput.text?.toString().orEmpty())
                else -> Unit
            }
        }
        dialog.setOnShowListener {
            // Không tự đóng khi bấm "Xác nhận": chờ kết quả, sai thì báo lỗi ngay trong hộp thoại.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.codeInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.googleButton.setOnClickListener { viewModel.onGoogle(requireActivity()) }
        binding.sendOtpButton.setOnClickListener { viewModel.onSendOtp(requireActivity()) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val method = state.method
                        binding.message.text = when (method) {
                            ReauthMethod.Password -> getString(R.string.account_reauth_message_password)
                            ReauthMethod.Google -> getString(R.string.account_reauth_message_google)
                            is ReauthMethod.Phone ->
                                getString(R.string.account_reauth_message_phone, PhoneNumber(method.phoneE164).masked())
                            null -> getString(R.string.account_reauth_no_method)
                        }
                        val codeSent = state.otpSession != null
                        binding.passwordLayout.isVisible = method == ReauthMethod.Password
                        binding.googleButton.isVisible = method == ReauthMethod.Google
                        binding.sendOtpButton.isVisible = method is ReauthMethod.Phone
                        binding.sendOtpButton.setText(
                            if (codeSent) R.string.account_reauth_resend else R.string.account_reauth_send_otp,
                        )
                        binding.codeLayout.isVisible = method is ReauthMethod.Phone && codeSent
                        binding.error.isVisible = state.error != null
                        state.error?.let { binding.error.setText(it) }
                        binding.progress.isInvisible = !state.isSubmitting
                        binding.googleButton.isEnabled = !state.isSubmitting
                        binding.sendOtpButton.isEnabled = !state.isSubmitting
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                            isVisible = method == ReauthMethod.Password || (method is ReauthMethod.Phone && codeSent)
                            isEnabled = !state.isSubmitting
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ReauthEvent.Done -> {
                                deliver(true)
                                dismiss()
                            }
                        }
                    }
                }
            }
        }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!requireActivity().isChangingConfigurations) deliver(false)
    }

    private fun deliver(done: Boolean) {
        if (delivered) return
        delivered = true
        setFragmentResult(AccountResults.REAUTH, bundleOf(AccountResults.REAUTH_DONE to done))
    }
}
