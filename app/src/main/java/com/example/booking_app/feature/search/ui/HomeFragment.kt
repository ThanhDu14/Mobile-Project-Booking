package com.example.booking_app.feature.search.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
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
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.VerifyEmailBannerEvent
import com.example.booking_app.core.ui.VerifyEmailBannerViewModel
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentHomeBinding
import com.example.booking_app.feature.search.domain.model.HomeSort
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tab Trang chủ của người chơi (spec 020 US5, FR-029): danh sách mọi cơ sở đang hoạt động. Bấm thẻ mở chi tiết sân
 * (spec 030, FR-017); nút "Đặt sân" ở chi tiết sân mở lưới đặt sân (040). Header dùng chung có avatar (spec 011).
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private val bannerViewModel: VerifyEmailBannerViewModel by viewModels()

    @Inject lateinit var avatarSource: AccountAvatarSource

    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomeBinding.bind(view)
        binding.header.tabTitle.setText(R.string.nav_tab_home)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)
        binding.verifyEmailBanner.onResendClick = bannerViewModel::onResend

        val adapter = VenueCardAdapter { venue -> openCourtDetail(venue.id) }
        val layoutManager = LinearLayoutManager(requireContext())
        binding.venueList.layoutManager = layoutManager
        binding.venueList.adapter = adapter
        binding.venueList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Còn 5 thẻ nữa là hết danh sách đang hiện thì tải thêm (FR-016).
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.onLoadMore()
                }
            }
        })
        binding.sortButton.setOnClickListener { showSortDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, adapter, it) } }
                launch { bannerViewModel.uiState.collect(binding.verifyEmailBanner::render) }
                launch {
                    bannerViewModel.events.collect { event ->
                        val message = when (event) {
                            VerifyEmailBannerEvent.Sent -> R.string.auth_verify_banner_sent
                            VerifyEmailBannerEvent.SendFailed -> R.string.auth_error_unknown
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Người dùng có thể vừa bấm link xác minh trong email rồi quay lại app (spec 010 research R9).
        bannerViewModel.onResume()
    }

    private fun render(binding: FragmentHomeBinding, adapter: VenueCardAdapter, state: HomeUiState) {
        binding.listHeader.isVisible = state is HomeUiState.Content
        if (state is HomeUiState.Content) {
            binding.allVenuesTitle.text = getString(R.string.home_all_venues_title, state.total)
            binding.sortButton.setText(HomeFormat.sortLabel(state.sort))
            adapter.submitList(state.items)
        }
        val common: UiState<Unit> = when (state) {
            HomeUiState.Loading -> UiState.Loading
            is HomeUiState.Content -> UiState.Content(Unit)
            HomeUiState.Empty -> UiState.Empty
            is HomeUiState.Error -> UiState.Error(getString(errorMessage(state.error)), viewModel::onRetry)
        }
        binding.stateViews.render(common, binding.venueList, EmptyStateSpec(R.string.home_empty, R.drawable.ic_sports_court_24))
    }

    private fun errorMessage(error: AppError): Int = when (error) {
        AppError.Network, AppError.Timeout -> R.string.search_error_network
        else -> R.string.search_error_generic
    }

    private fun showSortDialog() {
        val current = (viewModel.uiState.value as? HomeUiState.Content)?.sort ?: HomeSort.RATING
        val options = HomeSort.entries
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_sort_title)
            .setSingleChoiceItems(
                options.map { getString(HomeFormat.sortLabel(it)) }.toTypedArray(),
                options.indexOf(current),
            ) { dialog, which ->
                viewModel.onSortSelected(options[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun openCourtDetail(venueId: String) {
        val nav = findNavController()
        // Bấm hai thẻ thật nhanh: chỉ mở một chi tiết sân.
        if (nav.currentDestination?.id != R.id.homeFragment) return
        nav.navigate(R.id.action_home_to_courtDetail, bundleOf(ARG_VENUE_ID to venueId))
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 5
        const val ARG_VENUE_ID = "venueId"
    }
}
