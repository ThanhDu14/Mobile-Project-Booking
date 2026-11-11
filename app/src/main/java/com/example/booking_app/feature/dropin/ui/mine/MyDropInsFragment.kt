package com.example.booking_app.feature.dropin.ui.mine

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentDropinMineBinding
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * "Lượt vãng lai của tôi" (US3, T047): 2 tab "Sắp tới"/"Đã qua". Bấm một lượt mở màn hình vé có sẵn
 * (`dropInTicketFragment`). Chưa có lượt nào thì hiện nút "Tìm buổi vãng lai" mở tab Vãng lai (AC 3.6).
 * Vào từ trang Tài khoản bằng deep link `smashnow://dropin/mine` (T049).
 */
@AndroidEntryPoint
class MyDropInsFragment : Fragment(R.layout.fragment_dropin_mine) {

    private val viewModel: MyDropInsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinMineBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = DropInRegistrationAdapter(viewModel::onItemClicked)
        val layoutManager = LinearLayoutManager(requireContext())
        binding.registrationList.layoutManager = layoutManager
        binding.registrationList.adapter = adapter
        binding.registrationList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (viewModel.selectedTab.value != MyDropInsTab.PAST) return
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMorePast()
                }
            }
        })

        binding.tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.selectTab(if (checkedId == R.id.tabPast) MyDropInsTab.PAST else MyDropInsTab.UPCOMING)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.uiState, viewModel.selectedTab) { state, tab -> state to tab }
                        .collect { (state, tab) -> render(binding, adapter, state, tab) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MyDropInsEvent.OpenTicket -> openTicket(event.sessionId)
                            MyDropInsEvent.FindSessions -> openDropInList()
                            is MyDropInsEvent.LoadMoreFailed -> {
                                val message = DropInUiText.error(requireContext(), event.cause)
                                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.dropin_retry) { viewModel.loadMorePast() }
                                    .show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(
        binding: FragmentDropinMineBinding,
        adapter: DropInRegistrationAdapter,
        state: MyDropInsUiState,
        tab: MyDropInsTab,
    ) {
        val checkedId = if (tab == MyDropInsTab.PAST) R.id.tabPast else R.id.tabUpcoming
        if (binding.tabGroup.checkedButtonId != checkedId) binding.tabGroup.check(checkedId)

        val coreState: UiState<Unit> = when (state) {
            MyDropInsUiState.Loading -> UiState.Loading
            is MyDropInsUiState.Content -> UiState.Content(Unit)
            MyDropInsUiState.Empty -> UiState.Empty
            is MyDropInsUiState.Error -> UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.content,
            EmptyStateSpec(
                message = R.string.dropin_mine_empty,
                icon = R.drawable.ic_groups_24,
                actionText = R.string.dropin_mine_find,
                onAction = viewModel::onFindSessionsClicked,
            ),
        )

        val content = state as? MyDropInsUiState.Content
        binding.tabUpcoming.text = if (content != null) {
            getString(R.string.dropin_mine_tab_upcoming_count, content.upcoming.size)
        } else {
            getString(R.string.dropin_mine_upcoming)
        }
        val rows = when {
            content == null -> emptyList()
            tab == MyDropInsTab.PAST -> content.past
            else -> content.upcoming
        }
        adapter.submitList(rows)
        binding.loadingMore.isVisible = tab == MyDropInsTab.PAST && content?.loadingMorePast == true

        val tabEmpty = content != null && rows.isEmpty() && !(tab == MyDropInsTab.PAST && content.canLoadMorePast)
        binding.tabEmpty.isVisible = tabEmpty
        binding.tabEmpty.setText(
            if (tab == MyDropInsTab.PAST) R.string.dropin_mine_past_empty else R.string.dropin_mine_upcoming_empty,
        )
        // Trang "Đã qua" bị lọc hết (toàn lượt sắp tới) nhưng còn trang sau: tải tiếp vì không có gì để cuộn.
        if (content != null && tab == MyDropInsTab.PAST && rows.isEmpty() && content.canLoadMorePast) {
            viewModel.loadMorePast()
        }
    }

    private fun openTicket(sessionId: String) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.myDropInsFragment) return
        nav.navigate(MyDropInsFragmentDirections.actionMyDropInsToTicket(sessionId))
    }

    /** Mở tab Vãng lai (màn gốc của `nav_dropin`), giống bấm tab ở thanh dưới. */
    private fun openDropInList() {
        findNavController().navigate(
            R.id.nav_dropin,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.homeFragment)
            },
        )
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 5
    }
}
