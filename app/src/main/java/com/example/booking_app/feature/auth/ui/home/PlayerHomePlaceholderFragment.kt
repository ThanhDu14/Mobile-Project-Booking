package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.ui.VerifyEmailBannerEvent
import com.example.booking_app.core.ui.VerifyEmailBannerViewModel
import com.example.booking_app.databinding.FragmentHomePlaceholderBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Trang chủ người chơi tạm thời, có banner xác minh email. Spec sở hữu màn hình chính (020/060…) thay bằng
 * màn hình thật và giữ banner.
 */
@AndroidEntryPoint
class PlayerHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder) {

    private val viewModel: HomePlaceholderViewModel by viewModels()
    private val bannerViewModel: VerifyEmailBannerViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomePlaceholderBinding.bind(view)
        binding.title.setText(R.string.auth_home_placeholder_player)
        binding.verifyEmailBanner.onResendClick = bannerViewModel::onResend
        binding.accountButton.setOnClickListener {
            findNavController().navigate(R.id.action_playerHome_to_account)
        }

        // Nút điều hướng sang Feature 040 (Đặt sân theo giờ) để kiểm thử
        binding.btnGoToBooking.setOnClickListener {
            val bundle = Bundle().apply {
                putString("venueId", "demo-truong-chinh-909")
            }
            findNavController().navigate(R.id.action_playerHome_to_bookingSlotGrid, bundle)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isGuest.collect { isGuest ->
                        binding.guestNote.isVisible = isGuest
                        binding.loginPrompt.isVisible = isGuest
                        binding.accountButton.isVisible = !isGuest
                    }
                }
                launch { bannerViewModel.uiState.collect(binding.verifyEmailBanner::render) }
                launch {
                    bannerViewModel.events.collect { event ->
                        val message = when (event) {
                            VerifyEmailBannerEvent.Sent -> R.string.auth_verify_banner_sent
                            VerifyEmailBannerEvent.SendFailed -> R.string.auth_error_unknown
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Người dùng có thể vừa bấm link xác minh trong email rồi quay lại app (research R9).
        bannerViewModel.onResume()
    }
}
