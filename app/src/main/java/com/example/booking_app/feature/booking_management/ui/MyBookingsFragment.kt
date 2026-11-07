package com.example.booking_app.feature.booking_management.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.databinding.FragmentMyBookings060Binding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyBookingsFragment : Fragment(R.layout.fragment_my_bookings_060) {
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var authNavigator: AuthNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentMyBookings060Binding.bind(view)
        for (index in 0 until binding.bookingFilterGroup.childCount) {
            binding.bookingFilterGroup.getChildAt(index).isEnabled = false
        }
        binding.listBookings.isVisible = false
        binding.textBookingsEmpty.setText(R.string.my_bookings_060_in_development)
        binding.textBookingsEmpty.isVisible = true

        if (sessionManager.sessionState.value is SessionState.SignedOut) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.my_bookings_060_login_required)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.my_bookings_060_login) { _, _ ->
                    authNavigator.openLogin(
                        findNavController(),
                        Uri.parse("smashnow://bookings/mine"),
                    )
                }
                .show()
        }
    }
}
