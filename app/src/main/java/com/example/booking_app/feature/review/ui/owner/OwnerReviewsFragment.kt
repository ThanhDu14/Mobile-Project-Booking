package com.example.booking_app.feature.review.ui.owner

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
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentReviewOwnerListBinding
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.VenueOption
import com.example.booking_app.feature.review.ui.common.ReviewAdapter
import com.example.booking_app.feature.review.ui.common.errorMessage
import com.example.booking_app.feature.review.ui.common.messageRes
import com.example.booking_app.feature.review.ui.photo.PhotoViewerFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * "Đánh giá của khách" (US4). Menu chế độ quản lý sân (spec 111/112) mở bằng `R.id.ownerReviewsFragment`
 * hoặc deep link `smashnow://review/owner`.
 */
@AndroidEntryPoint
class OwnerReviewsFragment : Fragment(R.layout.fragment_review_owner_list) {

    private val viewModel: OwnerReviewsViewModel by viewModels()

    /** Danh sách cơ sở đã dựng chip, tránh dựng lại mỗi lần state đổi. */
    private var renderedVenues: List<VenueOption>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReviewOwnerListBinding.bind(view)
        renderedVenues = null
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = ReviewAdapter(
            onPhotoClick = { urls, index ->
                findNavController().navigate(R.id.ownerReviewPhotoViewerFragment, PhotoViewerFragment.args(urls, index))
            },
            venueName = { venueId ->
                viewModel.uiState.value.takeIf { it.venues.size > 1 }?.venueName(venueId)
            },
            onReply = ::openReply,
        )
        binding.reviewList.adapter = adapter
        binding.reviewList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 1) {
                    viewModel.loadMore()
                }
            }
        })

        binding.chipUnreplied.setOnCheckedChangeListener { _, checked -> viewModel.setUnrepliedOnly(checked) }
        binding.venueChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
                ?: return@setOnCheckedStateChangeListener
            viewModel.selectVenue(chip.tag as? String)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.venues != renderedVenues) {
                            renderVenueChips(binding, state.venues, state.selectedVenueId)
                            renderedVenues = state.venues
                        }
                        binding.chipUnreplied.isChecked = state.unrepliedOnly
                        binding.filters.isVisible = state.venues.isNotEmpty()

                        val empty = EmptyStateSpec(
                            message = if (state.venues.isEmpty()) {
                                R.string.review_owner_no_venues
                            } else {
                                R.string.review_owner_empty
                            },
                        )
                        val shown = (state.reviews as? UiState.Error)
                            ?.let { it.copy(message = getString(R.string.review_list_load_failed) + "\n" + errorMessage(it)) }
                            ?: state.reviews
                        binding.stateViews.render(shown, binding.reviewList, empty)
                        adapter.submitList((state.reviews as? UiState.Content)?.data.orEmpty())
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        val sheet = childFragmentManager.findFragmentByTag(ReplyReviewBottomSheet.TAG)
                            as? ReplyReviewBottomSheet
                        when (event) {
                            is OwnerReviewsEvent.ReplyInvalid -> sheet?.showError(
                                getString(if (event.tooLong) R.string.review_reply_too_long else R.string.review_reply_empty),
                            )
                            OwnerReviewsEvent.ReplySent -> {
                                sheet?.dismiss()
                                Snackbar.make(binding.root, R.string.review_reply_sent, Snackbar.LENGTH_SHORT).show()
                            }
                            is OwnerReviewsEvent.ReplyFailed -> {
                                val message = getString(event.error.messageRes())
                                if (sheet != null) sheet.showError(message)
                                else Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderVenueChips(binding: FragmentReviewOwnerListBinding, venues: List<VenueOption>, selected: String?) {
        binding.venueChips.removeAllViews()
        if (venues.size < 2) return // Chỉ một cơ sở thì không cần lọc.
        val options = listOf<VenueOption?>(null) + venues
        options.forEach { venue ->
            val chip = layoutInflater.inflate(R.layout.view_review_filter_chip, binding.venueChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = venue?.name ?: getString(R.string.review_owner_all_venues)
            chip.tag = venue?.venueId
            chip.isChecked = venue?.venueId == selected
            binding.venueChips.addView(chip)
        }
    }

    private fun openReply(review: Review) {
        if (childFragmentManager.findFragmentByTag(ReplyReviewBottomSheet.TAG) != null) return
        ReplyReviewBottomSheet.newInstance(review.id, review.ownerReply?.text, review.comment)
            .show(childFragmentManager, ReplyReviewBottomSheet.TAG)
    }
}
