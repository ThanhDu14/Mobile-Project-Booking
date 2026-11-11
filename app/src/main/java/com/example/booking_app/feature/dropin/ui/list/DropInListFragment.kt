package com.example.booking_app.feature.dropin.ui.list

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.core.ui.render
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.databinding.FragmentDropinListBinding
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.FilterChip
import com.example.booking_app.feature.dropin.ui.common.DropInUiText
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Danh sách buổi vãng lai (US1, T026). Chip bộ lọc bỏ được từng cái; bấm chip hoặc "Bộ lọc" mở bottom sheet.
 * Cuộn gần cuối thì tải trang tiếp. Kết quả của bottom sheet trả về qua `SavedStateHandle` của back stack entry này.
 */
@AndroidEntryPoint
class DropInListFragment : Fragment(R.layout.fragment_dropin_list) {

    private val viewModel: DropInListViewModel by viewModels()
    private val districtsViewModel: DropInDistrictsViewModel by viewModels()

    @Inject lateinit var avatarSource: AccountAvatarSource

    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDropinListBinding.bind(view)
        binding.header.tabTitle.setText(R.string.dropin_title)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)

        val adapter = DropInSessionAdapter { viewModel.onSessionClicked(it.id) }
        val layoutManager = LinearLayoutManager(requireContext())
        binding.sessionList.layoutManager = layoutManager
        binding.sessionList.adapter = adapter
        binding.sessionList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })
        binding.openFilterChip.setOnClickListener { openFilter() }

        observeFilterResult()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, adapter, it) } }
                launch {
                    combine(viewModel.filter, districtsViewModel.state) { filter, _ -> filter }
                        .collect { renderChips(binding, it) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is DropInListEvent.OpenDetail -> findNavController().navigate(
                                DropInListFragmentDirections.actionDropInListToDetail(event.sessionId),
                            )
                            is DropInListEvent.LoadMoreFailed -> {
                                val message = DropInUiText.error(requireContext(), event.cause)
                                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.dropin_retry) { viewModel.loadMore() }
                                    .show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(binding: FragmentDropinListBinding, adapter: DropInSessionAdapter, state: DropInListUiState) {
        val coreState: UiState<Unit> = when (state) {
            DropInListUiState.Loading -> UiState.Loading
            is DropInListUiState.Content -> UiState.Content(Unit)
            is DropInListUiState.Empty -> UiState.Empty
            is DropInListUiState.Error -> UiState.Error(DropInUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        val empty = if ((state as? DropInListUiState.Empty)?.hasFilter == true) {
            EmptyStateSpec(
                message = R.string.dropin_list_empty_filtered,
                icon = R.drawable.ic_search_24,
                actionText = R.string.dropin_list_reduce_filter,
                onAction = { openFilter() },
            )
        } else {
            EmptyStateSpec(message = R.string.dropin_list_empty, icon = R.drawable.ic_groups_24)
        }
        binding.stateViews.render(coreState, binding.sessionList, empty)

        val items = (state as? DropInListUiState.Content)?.items.orEmpty()
        adapter.submitList(items)
        binding.resultCount.isVisible = state is DropInListUiState.Content
        if (state is DropInListUiState.Content) {
            // Số buổi đang hiện; còn trang sau thì là số đã tải.
            binding.resultCount.text = resources.getQuantityString(R.plurals.dropin_list_count, items.size, items.size)
        }
    }

    private fun renderChips(binding: FragmentDropinListBinding, filter: DropInFilter) {
        val group = binding.filterChips
        // Giữ chip "Bộ lọc" ở đầu, dựng lại các chip điều kiện.
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.id != R.id.openFilterChip) group.removeViewAt(i)
        }
        val names = districtsViewModel.namesById()
        filter.chips().forEach { chip ->
            val label = chipLabel(requireContext(), chip, names)
            group.addView(
                Chip(requireContext()).apply {
                    text = label
                    isCheckable = false
                    isCloseIconVisible = true
                    closeIconContentDescription = getString(R.string.dropin_chip_remove, label)
                    setOnClickListener { openFilter() }
                    setOnCloseIconClickListener { viewModel.onChipRemoved(chip) }
                },
            )
        }
    }

    private fun openFilter() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.dropInListFragment) return
        nav.navigate(DropInListFragmentDirections.actionDropInListToFilter(DropInFilterArgs.from(viewModel.filter.value)))
    }

    private fun observeFilterResult() {
        val entry = findNavController().getBackStackEntry(R.id.dropInListFragment)
        entry.savedStateHandle.getLiveData<DropInFilterArgs>(DropInFilterBottomSheet.RESULT_KEY)
            .observe(viewLifecycleOwner) { args ->
                if (args != null) {
                    // Đặt null thay vì remove(): remove() gỡ luôn LiveData này khỏi SavedStateHandle nên các lần
                    // "Áp dụng" sau không tới được observer (lỗi chỉ lọc được lần đầu).
                    entry.savedStateHandle[DropInFilterBottomSheet.RESULT_KEY] = null
                    viewModel.applyFilter(args)
                }
            }
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 5
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

        fun chipLabel(context: Context, chip: FilterChip, districtNames: Map<String, String>): String = when (chip) {
            is FilterChip.District -> districtNames[chip.districtId] ?: chip.districtId
            is FilterChip.Date -> context.getString(
                R.string.dropin_relative_date,
                context.getString(DropInUiText.dayOfWeekShort(chip.date.dayOfWeek)),
                chip.date.format(DATE_FORMAT),
            )
            is FilterChip.Slot -> context.getString(DropInUiText.timeSlot(chip.timeSlot))
            is FilterChip.Lvl -> context.getString(DropInUiText.level(chip.level))
            is FilterChip.MaxPrice -> context.getString(R.string.dropin_chip_max_price, MoneyFormat.vnd(chip.maxPrice))
        }
    }
}
