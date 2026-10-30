package com.example.booking_app.feature.review.ui.favorite

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentFavoriteListBinding
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** "Sân yêu thích" (US3). Vào từ trang Tài khoản (spec 011). */
@AndroidEntryPoint
class FavoriteVenuesFragment : Fragment(R.layout.fragment_favorite_list) {

    private val viewModel: FavoriteVenuesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentFavoriteListBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = FavoriteVenueAdapter(onVenueClick = ::openVenue, onUnfavorite = viewModel::unfavorite)
        binding.favoriteList.adapter = adapter
        binding.favoriteList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 1) {
                    viewModel.loadMore()
                }
            }
        })

        // TODO(spec 020): "Tìm sân" mở màn hình tìm kiếm khi A làm xong; tạm quay về trang trước.
        val empty = EmptyStateSpec(
            message = R.string.favorite_empty,
            icon = R.drawable.ic_favorite_border_48,
            actionText = R.string.favorite_empty_action,
            onAction = { findNavController().navigateUp() },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val shown = if (state is UiState.Error) {
                            state.copy(message = getString(R.string.favorite_load_failed))
                        } else {
                            state
                        }
                        binding.stateViews.render(shown, binding.favoriteList, empty)
                        adapter.submitList((state as? UiState.Content)?.data.orEmpty())
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            FavoriteListEvent.SyncFailed ->
                                Snackbar.make(binding.root, R.string.favorite_sync_failed, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Chỉ sân đang hoạt động mới mở được. TODO(spec 030): chuyển sang chi tiết sân khi A làm xong;
     * tạm mở lưới đặt sân của spec 040.
     */
    private fun openVenue(venue: FavoriteVenue) {
        findNavController().navigate(
            R.id.action_favoriteVenues_to_bookingSlotGrid,
            Bundle().apply { putString("venueId", venue.venueId) },
        )
    }
}
