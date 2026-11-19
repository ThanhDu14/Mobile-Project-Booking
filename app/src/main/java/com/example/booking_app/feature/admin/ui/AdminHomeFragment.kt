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
            val current = java.time.LocalDate.parse(toolsViewModel.statsDate, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                toolsViewModel.loadStats(java.time.LocalDate.of(year, month + 1, day).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
        }

        // Tải dữ liệu thực tế
        appsViewModel.selectStatus("PENDING")
        reportsViewModel.selectStatus(false)
        toolsViewModel.loadStats()

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    appsViewModel.queueState.collect { state ->
                        binding.textPendingCount.text = if (state is AdminQueueState.Empty) "0" else "—"
                        if (state is AdminQueueState.Content) {
                            val count = state.applications.count { it.status == "PENDING" || it.status == "RESUBMITTED" }
                            binding.textPendingCount.text = if (count >= 30) "≥$count" else count.toString()
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
                        binding.textOpenReportsCount.text = if (state is AdminReportQueueState.Empty) "0" else "—"
                        if (state is AdminReportQueueState.Content) {
                            val openCount = state.reports.count { it.status == ReportStatus.OPEN }
                            binding.textOpenReportsCount.text = if (openCount >= 30) "≥$openCount" else openCount.toString()
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
                        val stats = (state as? SystemStatsUiState.Content)?.stats
                        binding.textStatNewUsers.text = stats?.metrics?.get("totalUsers")?.toString() ?: "—"
                        binding.textStatBookings.text = stats?.metrics?.get("totalBookings")?.toString() ?: "—"
                        binding.textStatActiveVenues.text = stats?.metrics?.get("totalVenues")?.toString() ?: "—"
                        binding.textStatDropins.text = stats?.metrics?.get("totalRevenue")?.toString() ?: "—"
                        binding.textDailyStatsHeader.text = when (state) {
                            SystemStatsUiState.Loading -> getString(R.string.admin_stats_loading)
                            SystemStatsUiState.Empty -> "Chưa có thống kê ngày "+toolsViewModel.statsDate
                            is SystemStatsUiState.Error -> state.message+" · Chạm để thử lại"
                            is SystemStatsUiState.Content -> stats?.let { "Số liệu ngày " + java.time.LocalDate.parse(it.date, java.time.format.DateTimeFormatter.BASIC_ISO_DATE).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
                        }
                        val updated = stats?.updatedAt?.let { value ->
                            runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")) }.getOrNull()
                        }
                        binding.textStatsSource.text = if (stats == null) "Chưa có nguồn số liệu" else "Nguồn: thống kê hệ thống · Cập nhật: ${updated ?: "Chưa có thời điểm"}"
                        binding.textDailyStatsHeader.setOnClickListener { toolsViewModel.loadStats() }
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
