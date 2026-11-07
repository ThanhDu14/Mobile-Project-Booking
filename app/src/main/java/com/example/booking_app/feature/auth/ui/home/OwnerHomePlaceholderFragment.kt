package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentHomePlaceholderOwnerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tab Hôm nay tạm của `nav_owner`. Avatar ở header mở trang Tài khoản, nơi chủ sân chuyển sang chế độ người chơi.
 * Spec 112 thay phần thân bằng màn Hôm nay thật.
 */
@AndroidEntryPoint
class OwnerHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder_owner) {

    private val viewModel: OwnerHomePlaceholderViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomePlaceholderOwnerBinding.bind(view)
        binding.date.text = LocalDate.now().format(DATE_FORMAT).replaceFirstChar { it.uppercase() }
        binding.avatar.setOnClickListener { findNavController().navigate(R.id.action_ownerHome_to_account) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.initials.collect { binding.avatar.text = it }
            }
        }
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("vi"))
    }
}
