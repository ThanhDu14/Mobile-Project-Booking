package com.example.booking_app.feature.courtdetail.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.booking_app.databinding.FragmentCourtDetailBinding
import dagger.hilt.android.AndroidEntryPoint

/** Chi tiết sân (spec 030). Khung ban đầu (T024); nội dung thêm theo từng user story. */
@AndroidEntryPoint
class CourtDetailFragment : Fragment() {

    private var _binding: FragmentCourtDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourtDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
