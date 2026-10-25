package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentHomePlaceholderBinding

/** Màn hình tạm của `nav_owner`. D thay bằng màn hình thật (spec 111/112). */
class OwnerHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomePlaceholderBinding.bind(view)
        binding.title.setText(R.string.auth_home_placeholder_owner)
        binding.accountButton.isVisible = true
        binding.accountButton.setOnClickListener { findNavController().navigate(R.id.action_ownerHome_to_account) }
    }
}
