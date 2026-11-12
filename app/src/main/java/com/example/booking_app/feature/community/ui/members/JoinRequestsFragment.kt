package com.example.booking_app.feature.community.ui.members

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentJoinRequestsBinding
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Yêu cầu tham gia (T039). Mở từ nút "Yêu cầu tham gia (N)" trên trang nhóm hoặc deep link
 * `smashnow://community/group/{groupId}/requests`.
 */
@AndroidEntryPoint
class JoinRequestsFragment : Fragment(R.layout.fragment_join_requests) {

    private val viewModel: JoinRequestsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentJoinRequestsBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = JoinRequestAdapter(onApprove = { viewModel.onApprove(it.userId) }, onReject = { viewModel.onReject(it.userId) })
        binding.requestList.layoutManager = LinearLayoutManager(requireContext())
        binding.requestList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, adapter, it) } }
                launch {
                    viewModel.events.collect { event ->
                        if (event is JoinRequestsEvent.ShowError) {
                            Snackbar.make(binding.root, CommunityUiText.error(requireContext(), event.error), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentJoinRequestsBinding, adapter: JoinRequestAdapter, state: JoinRequestsUiState) {
        val coreState: UiState<Unit> = when (state) {
            JoinRequestsUiState.Loading -> UiState.Loading
            is JoinRequestsUiState.Content -> UiState.Content(Unit)
            JoinRequestsUiState.Empty -> UiState.Empty
            is JoinRequestsUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.requestList,
            EmptyStateSpec(message = R.string.community_empty_join_requests, icon = R.drawable.ic_groups_24),
        )
        adapter.submitList((state as? JoinRequestsUiState.Content)?.items.orEmpty())
    }
}
