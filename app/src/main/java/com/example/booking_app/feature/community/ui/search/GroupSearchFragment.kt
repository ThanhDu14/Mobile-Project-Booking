package com.example.booking_app.feature.community.ui.search

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentGroupSearchBinding
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.example.booking_app.feature.community.ui.home.CommunityHomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Tab "Khám phá" (T037), nhúng trong `CommunityHomeFragment` như child fragment. Mở trang nhóm và "Tạo nhóm của bạn"
 * đi qua `CommunityHomeViewModel` của màn cha (cùng luồng điều hướng và kiểm tra đăng nhập với tab "Nhóm của tôi").
 */
@AndroidEntryPoint
class GroupSearchFragment : Fragment(R.layout.fragment_group_search) {

    private val viewModel: GroupSearchViewModel by viewModels()
    private val homeViewModel: CommunityHomeViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentGroupSearchBinding.bind(view)

        val adapter = GroupAdapter { homeViewModel.onGroupClicked(it.group.id) }
        val layoutManager = LinearLayoutManager(requireContext())
        binding.groupList.layoutManager = layoutManager
        binding.groupList.adapter = adapter
        binding.groupList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) viewModel.loadMore()
            }
        })

        // Ô tìm: giữ chữ của ViewModel (xoay màn hình/khôi phục); chỉ đẩy lên khi người dùng gõ.
        binding.searchInput.setText(viewModel.query.value)
        binding.searchInput.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) v.clearFocus()
            false
        }
        binding.districtChip.setOnClickListener { showDistrictPicker() }
        binding.clearFiltersButton.setOnClickListener {
            binding.searchInput.setText("")
            viewModel.clearFilters()
        }
        binding.createGroupButton.setOnClickListener { homeViewModel.onCreateClicked() }
        binding.loadMoreRetry.setOnClickListener { viewModel.loadMore() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, adapter, it) } }
                launch {
                    combine(viewModel.districtIds, viewModel.districts) { ids, districts -> ids to districts }
                        .collect { (ids, districts) -> renderDistrictChip(binding, ids, districts.associate { it.id to it.name }) }
                }
                launch {
                    // Bỏ bớt bộ lọc đổi query từ ViewModel: cập nhật ô nhập nếu khác chữ đang hiện.
                    viewModel.query.collect { q -> if (binding.searchInput.text?.toString() != q) binding.searchInput.setText(q) }
                }
            }
        }
    }

    private fun render(binding: FragmentGroupSearchBinding, adapter: GroupAdapter, state: GroupSearchUiState) {
        val content = state as? GroupSearchUiState.Content
        binding.emptyView.isVisible = state is GroupSearchUiState.Empty
        binding.loadMoreProgress.isVisible = content?.loadingMore == true
        binding.loadMoreRetry.isVisible = content?.loadMoreFailed == true

        if (state is GroupSearchUiState.Empty) {
            binding.stateViews.stateLoading.isVisible = false
            binding.stateViews.stateMessage.isVisible = false
            binding.groupList.isVisible = false
            // AC 2.6: có bộ lọc thì gợi ý "Bỏ bớt bộ lọc", luôn có "Tạo nhóm của bạn".
            binding.emptyText.setText(if (state.hasFilter) R.string.community_search_empty_filtered else R.string.community_empty_search)
            binding.clearFiltersButton.isVisible = state.hasFilter
            adapter.submitList(emptyList())
            return
        }
        val coreState: UiState<Unit> = when (state) {
            GroupSearchUiState.Loading -> UiState.Loading
            is GroupSearchUiState.Content -> UiState.Content(Unit)
            is GroupSearchUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), state.cause), viewModel::retry)
            is GroupSearchUiState.Empty -> UiState.Empty
        }
        binding.stateViews.render(
            coreState,
            binding.groupList,
            EmptyStateSpec(message = R.string.community_empty_search, icon = R.drawable.ic_groups_24),
        )
        adapter.submitList(content?.cards.orEmpty())
    }

    private fun renderDistrictChip(binding: FragmentGroupSearchBinding, ids: List<String>, names: Map<String, String>) {
        val selected = ids.firstOrNull()
        val label = selected?.let { names[it] }
        binding.districtChip.text = if (label != null) {
            getString(R.string.community_search_area_chip, label)
        } else {
            getString(R.string.community_search_area_all)
        }
        binding.districtChip.contentDescription =
            getString(R.string.community_search_area_cd, label ?: getString(R.string.community_search_area_all))
    }

    /** Hộp chọn một khu vực; "Mọi khu vực" ở đầu danh sách. */
    private fun showDistrictPicker() {
        val districts = viewModel.districts.value
        val names = listOf(getString(R.string.community_search_area_all)) + districts.map { it.name }
        val selected = districts.indexOfFirst { it.id == viewModel.districtIds.value.firstOrNull() } + 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.community_search_pick_area)
            .setSingleChoiceItems(names.toTypedArray(), selected) { dialog, which ->
                viewModel.onDistrictSelected(districts.getOrNull(which - 1)?.id)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.community_action_cancel, null)
            .show()
    }

    private companion object {
        /** Tải trang tiếp khi còn chừng này thẻ nữa là tới cuối. */
        const val LOAD_MORE_THRESHOLD = 4
    }
}
