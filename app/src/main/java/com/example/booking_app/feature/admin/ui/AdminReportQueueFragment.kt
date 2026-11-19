package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminReportQueueBinding
import com.example.booking_app.feature.admin.domain.model.AdminReport
import com.example.booking_app.feature.admin.domain.model.ReportStatus
import com.example.booking_app.feature.admin.domain.model.ReportTargetType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminReportQueueFragment : Fragment(R.layout.fragment_admin_report_queue) {

    private var _binding: FragmentAdminReportQueueBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminReportsViewModel by activityViewModels()
    private lateinit var adapter: AdminReportAdapter
    private var allReports: List<AdminReport> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminReportQueueBinding.bind(view)
        binding.toggleGroupStatus.check(if (viewModel.closedTab) R.id.buttonTabClosed else R.id.buttonTabOpen)

        adapter = AdminReportAdapter { report ->
            viewModel.selectReport(report)
            runCatching {
                findNavController().navigate(R.id.action_report_queue_to_detail)
            }
        }

        binding.recyclerReports.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReports.adapter = adapter
        binding.recyclerReports.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !rv.canScrollVertically(1)) viewModel.loadMore()
            }
        })
        viewModel.loadReports()

        binding.buttonRetry.setOnClickListener {
            viewModel.loadReports()
        }

        binding.toggleGroupStatus.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.selectStatus(checkedId == R.id.buttonTabClosed)
            }
        }

        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, _ ->
            filterReports()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.queueState.collect(::renderState)
            }
        }
    }

    private fun renderState(state: AdminReportQueueState) {
        val currentBinding = _binding ?: return

        currentBinding.progressBar.isVisible = state is AdminReportQueueState.Loading
        currentBinding.recyclerReports.isVisible = state is AdminReportQueueState.Content
        currentBinding.layoutEmpty.isVisible = state is AdminReportQueueState.Empty
        currentBinding.layoutError.isVisible = state is AdminReportQueueState.Error

        when (state) {
            is AdminReportQueueState.Content -> {
                allReports = state.reports
                adapter.updateTargetCounts(allReports)
                filterReports()
            }
            is AdminReportQueueState.Error -> {
                currentBinding.textErrorMessage.text = state.message
                currentBinding.buttonRetry.isVisible = state.canRetry
            }
            AdminReportQueueState.Empty -> { allReports = emptyList(); adapter.submitList(emptyList()) }
            else -> Unit
        }
    }

    private fun filterReports() {
        val currentBinding = _binding ?: return

        val isOpenTab = currentBinding.toggleGroupStatus.checkedButtonId != R.id.buttonTabClosed
        val targetType = when (currentBinding.chipGroupCategory.checkedChipId) {
            R.id.chipFilterPost -> ReportTargetType.POST
            R.id.chipFilterMessage -> ReportTargetType.MESSAGE
            R.id.chipFilterReview -> ReportTargetType.REVIEW
            R.id.chipFilterVenue -> ReportTargetType.VENUE
            R.id.chipFilterUser -> ReportTargetType.USER
            else -> null
        }

        val filtered = allReports.filter { report ->
            val matchStatus = if (isOpenTab) report.status == ReportStatus.OPEN else report.status != ReportStatus.OPEN
            val matchType = targetType == null || report.targetType == targetType
            matchStatus && matchType
        }

        adapter.submitList(filtered)
        currentBinding.layoutEmpty.isVisible = filtered.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopQueue()
        _binding = null
    }
}
