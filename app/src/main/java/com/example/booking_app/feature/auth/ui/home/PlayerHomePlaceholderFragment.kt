package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentHomePlaceholderBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Trang chủ người chơi tạm thời. Spec sở hữu màn hình chính (020/060…) thay bằng màn hình thật. */
@AndroidEntryPoint
class PlayerHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder) {

    private val viewModel: HomePlaceholderViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomePlaceholderBinding.bind(view)
        binding.title.setText(R.string.auth_home_placeholder_player)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isGuest.collect { isGuest ->
                    binding.guestNote.isVisible = isGuest
                    binding.loginPrompt.isVisible = isGuest
                }
            }
        }
    }
}
