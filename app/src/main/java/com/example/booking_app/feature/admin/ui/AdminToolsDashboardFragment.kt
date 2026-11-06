package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminToolsDashboardBinding
import com.example.booking_app.feature.admin.domain.model.AdminSystemStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class AdminToolsDashboardFragment : Fragment() {

    private var _binding: FragmentAdminToolsDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminToolsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminToolsDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.cardNavVouchers.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_vouchers)
        }

        binding.cardNavBroadcast.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_broadcast)
        }

        binding.cardNavUsers.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_user_mgmt)
        }

        binding.cardNavVenues.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_venue_mgmt)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.statsState.collect { state ->
                    renderStats(state)
                }
            }
        }
    }

    private fun renderStats(state: SystemStatsUiState) {
        when (state) {
            SystemStatsUiState.Loading -> {
                binding.progressStatsLoading.isVisible = true
                binding.textStatsEmpty.isVisible = false
                binding.layoutStatsContent.isVisible = false
            }
            SystemStatsUiState.Empty -> {
                binding.progressStatsLoading.isVisible = false
                binding.textStatsEmpty.isVisible = true
                binding.layoutStatsContent.isVisible = false
            }
            is SystemStatsUiState.Error -> {
                binding.progressStatsLoading.isVisible = false
                binding.textStatsEmpty.isVisible = true
                binding.textStatsEmpty.text = state.message
                binding.layoutStatsContent.isVisible = false
            }
            is SystemStatsUiState.Content -> {
                binding.progressStatsLoading.isVisible = false
                binding.textStatsEmpty.isVisible = false
                binding.layoutStatsContent.isVisible = true
                bindStats(state.stats)
            }
        }
    }

    private fun bindStats(stats: AdminSystemStats) {
        val numberFormat = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
        binding.textTotalUsers.text = numberFormat.format(stats.totalUsers)
        binding.textTotalVenues.text = numberFormat.format(stats.totalVenues)
        binding.textTotalBookings.text = numberFormat.format(stats.totalBookings)
        binding.textTotalRevenue.text = "${numberFormat.format(stats.totalRevenue)} đ"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
