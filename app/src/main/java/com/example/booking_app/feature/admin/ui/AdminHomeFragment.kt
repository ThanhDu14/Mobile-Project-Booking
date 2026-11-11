package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminHomeBinding
import com.example.booking_app.feature.admin.domain.model.ReportStatus
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminHomeFragment : Fragment() {

    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!

    private val toolsViewModel: AdminToolsViewModel by viewModels()
    private val appsViewModel: AdminApplicationsViewModel by activityViewModels()
    private val reportsViewModel: AdminReportsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardPendingApplications.setOnClickListener {
            findNavController().navigate(R.id.adminApplicationQueueFragment)
        }

        binding.cardOpenReports.setOnClickListener {
            findNavController().navigate(R.id.adminReportQueueFragment)
        }

        binding.buttonNotifications.setOnClickListener {
            findNavController().navigate(R.id.adminBroadcastFragment)
        }

        binding.buttonSelectDate.setOnClickListener {
            Snackbar.make(binding.root, "Chức năng chọn ngày thống kê", Snackbar.LENGTH_SHORT).show()
        }

        // Tải dữ liệu thực tế
        appsViewModel.loadQueue()
        reportsViewModel.loadReports()
        toolsViewModel.loadStats()

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    appsViewModel.queueState.collect { state ->
                        if (state is AdminQueueState.Content) {
                            val count = state.applications.count { it.status == "PENDING" || it.status == "RESUBMITTED" }
                            binding.textPendingCount.text = count.toString()
                            binding.textPendingSub.text = if (count > 0) {
                                getString(R.string.admin_home_pending_applications_sub)
                            } else {
                                "Không có hồ sơ chờ duyệt"
                            }
                        }
                    }
                }
                launch {
                    reportsViewModel.queueState.collect { state ->
                        if (state is AdminReportQueueState.Content) {
                            val openCount = state.reports.count { it.status == ReportStatus.OPEN }
                            binding.textOpenReportsCount.text = openCount.toString()
                            binding.textOpenReportsSub.text = if (openCount > 0) {
                                "Cần xử lý vi phạm"
                            } else {
                                "Không có báo cáo mở"
                            }
                        }
                    }
                }
                launch {
                    toolsViewModel.statsState.collect { state ->
                        if (state is SystemStatsUiState.Content) {
                            val stats = state.stats
                            binding.textStatNewUsers.text = stats.totalUsers.toString()
                            binding.textStatBookings.text = stats.totalBookings.toString()
                            binding.textStatActiveVenues.text = stats.totalVenues.toString()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
