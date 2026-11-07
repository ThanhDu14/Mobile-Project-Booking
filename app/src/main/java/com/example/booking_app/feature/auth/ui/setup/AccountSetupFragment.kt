package com.example.booking_app.feature.auth.ui.setup

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.core.network.AppError
import com.example.booking_app.databinding.FragmentAuthAccountSetupBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** "Đang chuẩn bị tài khoản…". Chưa xong thì không vào được màn hình chính (FR-006). */
@AndroidEntryPoint
class AccountSetupFragment : Fragment(R.layout.fragment_auth_account_setup) {

    private val viewModel: AccountSetupViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthAccountSetupBinding.bind(view)
        binding.retryButton.setOnClickListener { viewModel.onRetry() }
        binding.signOutButton.setOnClickListener { viewModel.onSignOut() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val failed = state as? AccountSetupUiState.Failed
                    binding.progress.isVisible = failed == null
                    binding.errorActions.isVisible = failed != null
                    binding.message.setText(
                        when (failed?.error) {
                            null -> R.string.auth_setup_loading
                            AppError.RateLimited -> R.string.auth_error_too_many_attempts
                            else -> R.string.auth_setup_failed
                        },
                    )
                }
            }
        }
    }
}
