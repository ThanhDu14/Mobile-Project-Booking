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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminReportQueueFragment : Fragment(R.layout.fragment_admin_report_queue) {

    private var _binding: FragmentAdminReportQueueBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminReportsViewModel by activityViewModels()
    private lateinit var adapter: AdminReportAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminReportQueueBinding.bind(view)

        adapter = AdminReportAdapter { report ->
            viewModel.selectReport(report)
            runCatching {
                findNavController().navigate(R.id.action_report_queue_to_detail)
            }
        }

        binding.recyclerReports.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReports.adapter = adapter

        binding.buttonRetry.setOnClickListener {
            viewModel.loadReports()
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
                adapter.submitList(state.reports)
            }
            is AdminReportQueueState.Error -> {
                currentBinding.textErrorMessage.text = state.message
                currentBinding.buttonRetry.isVisible = state.canRetry
            }
            else -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
