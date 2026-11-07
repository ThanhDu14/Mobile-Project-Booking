package com.example.booking_app.feature.auth.ui.link

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.DialogAuthLinkAccountBinding
import com.example.booking_app.feature.auth.ui.login.LoginViewModel
import com.example.booking_app.feature.auth.ui.messageRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Liên kết Google vào tài khoản mật khẩu cùng email (FR-012, research R7). Mở từ `LoginFragment` bằng
 * `childFragmentManager` và dùng chung `LoginViewModel`, nơi giữ credential Google đang chờ.
 */
class LinkAccountDialog : DialogFragment() {

    private val viewModel: LoginViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAuthLinkAccountBinding.inflate(layoutInflater)
        binding.message.text = getString(R.string.auth_link_message, requireArguments().getString(ARG_EMAIL))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_link_title)
            .setView(binding.root)
            .setPositiveButton(R.string.auth_link_submit, null)
            .setNegativeButton(R.string.auth_link_cancel, null)
            .create()
        dialog.setOnShowListener {
            // Không tự đóng khi bấm "Liên kết": chờ kết quả, sai mật khẩu thì báo lỗi ngay trong hộp thoại.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.onLinkSubmit(binding.passwordInput.text?.toString().orEmpty())
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.passwordLayout.error = state.linkError?.let { getString(it.messageRes()) }
                    binding.progress.isInvisible = !state.isLinking
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !state.isLinking
                }
            }
        }
        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.onLinkCancelled()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!viewModel.uiState.value.isLinking) viewModel.onLinkCancelled()
    }

    companion object {
        const val TAG = "LinkAccountDialog"
        private const val ARG_EMAIL = "email"

        fun newInstance(email: String) = LinkAccountDialog().apply { arguments = bundleOf(ARG_EMAIL to email) }
    }
}
