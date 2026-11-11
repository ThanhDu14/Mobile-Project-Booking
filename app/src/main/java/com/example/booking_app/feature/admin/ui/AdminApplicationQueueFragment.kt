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
import com.example.booking_app.databinding.FragmentAdminApplicationQueueBinding
import com.example.booking_app.feature.admin.domain.model.AdminOwnerApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminApplicationQueueFragment : Fragment(R.layout.fragment_admin_application_queue) {

    private var _binding: FragmentAdminApplicationQueueBinding? = null
    val binding get() = checkNotNull(_binding)

    val viewModel: AdminApplicationsViewModel by activityViewModels()
    private lateinit var adapter: AdminApplicationAdapter
    private var allApplications: List<AdminOwnerApplication> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminApplicationQueueBinding.bind(view)

        adapter = AdminApplicationAdapter { application ->
            viewModel.selectApplication(application)
            runCatching {
                findNavController().navigate(R.id.action_queue_to_detail)
            }
        }

        binding.recyclerApplications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApplications.adapter = adapter

        binding.buttonRetry.setOnClickListener {
            viewModel.loadQueue()
        }

        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            filterList(checkedIds.firstOrNull())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.queueState.collect(::renderState)
            }
        }
    }

    private fun renderState(state: AdminQueueState) {
        val currentBinding = _binding ?: return

        currentBinding.progressBar.isVisible = state is AdminQueueState.Loading
        currentBinding.recyclerApplications.isVisible = state is AdminQueueState.Content
        currentBinding.layoutEmpty.isVisible = state is AdminQueueState.Empty
        currentBinding.layoutError.isVisible = state is AdminQueueState.Error

        when (state) {
            is AdminQueueState.Content -> {
                allApplications = state.applications
                val pendingCount = allApplications.count { it.status == "PENDING" }
                if (pendingCount > 0) {
                    currentBinding.chipPending.text = getString(R.string.admin_filter_pending, pendingCount)
                } else {
                    currentBinding.chipPending.text = getString(R.string.admin_filter_pending_simple)
                }
                filterList(currentBinding.chipGroupStatus.checkedChipId)
            }
            is AdminQueueState.Error -> {
                currentBinding.textErrorMessage.text = state.message
                currentBinding.buttonRetry.isVisible = state.canRetry
            }
            else -> Unit
        }
    }

    private fun filterList(checkedChipId: Int?) {
        val filtered = when (checkedChipId) {
            R.id.chipApproved -> allApplications.filter { it.status == "APPROVED" }
            R.id.chipRejected -> allApplications.filter { it.status == "REJECTED" }
            else -> allApplications.filter { it.status == "PENDING" || it.status.isBlank() }
        }
        adapter.submitList(filtered)
        _binding?.layoutEmpty?.isVisible = filtered.isEmpty() && allApplications.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}