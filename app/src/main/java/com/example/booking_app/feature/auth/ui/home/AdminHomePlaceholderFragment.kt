package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentHomePlaceholderBinding

/** Màn hình tạm của `nav_admin`. D thay bằng màn hình thật (spec 120). */
class AdminHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentHomePlaceholderBinding.bind(view).title.setText(R.string.auth_home_placeholder_admin)
    }
}
