package com.example.booking_app.feature.review.ui.list

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
import com.example.booking_app.databinding.FragmentReviewListBinding
import com.example.booking_app.databinding.ViewReviewCriterionBarBinding
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import com.example.booking_app.feature.review.ui.common.ReviewAdapter
import com.example.booking_app.feature.review.ui.common.errorMessage
import com.example.booking_app.feature.review.ui.photo.PhotoViewerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * "Tất cả đánh giá" của một sân (US6). Vào từ nút "Xem tất cả" ở chi tiết sân (spec 030) bằng
 * `R.id.venueReviewsFragment` với `venueId`, `venueName`, hoặc deep link `smashnow://review/venue/{venueId}`.
 */
@AndroidEntryPoint
class VenueReviewsFragment : Fragment(R.layout.fragment_review_list) {

    private val viewModel: VenueReviewsViewModel by viewModels()

    private val scoreFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReviewListBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = ReviewAdapter(onPhotoClick = ::openPhotos)
        binding.reviewList.adapter = adapter
        binding.reviewList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 1) {
                    viewModel.loadMore()
                }
            }
        })

        val starChips = mapOf(
            binding.chip5 to 5,
            binding.chip4 to 4,
            binding.chip3 to 3,
            binding.chip2 to 2,
            binding.chip1 to 1,
        )
        starChips.forEach { (chip, stars) -> chip.text = getString(R.string.review_filter_stars, stars) }
        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val filter = when (id) {
                binding.chipPhotos.id -> ReviewFilter.WithPhotos
                binding.chipAll.id -> ReviewFilter.All
                else -> starChips.entries.firstOrNull { it.key.id == id }?.let { ReviewFilter.Stars(it.value) }
                    ?: ReviewFilter.All
            }
            viewModel.setFilter(filter)
        }

        val bars = listOf(
            Triple(binding.barCourt, R.string.review_criterion_court, VenueRatingSummary::courtAvg),
            Triple(binding.barCleanliness, R.string.review_criterion_cleanliness, VenueRatingSummary::cleanlinessAvg),
            Triple(binding.barService, R.string.review_criterion_service, VenueRatingSummary::serviceAvg),
        )
        bars.forEach { (bar, label, _) -> bar.label.setText(label) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.toolbar.subtitle = state.venueName.ifBlank { null }

                    val summary = state.summary
                    binding.summary.isVisible = summary != null && summary.count > 0
                    if (summary != null) {
                        binding.summaryAverage.text = scoreFormat.format(summary.average)
                        binding.summaryCount.text = getString(R.string.review_list_count, summary.count)
                        bars.forEach { (bar, label, value) -> bindBar(bar, getString(label), value(summary)) }
                    }

                    // Sân chưa có đánh giá: ẩn luôn chip lọc; có đánh giá mà lọc ra rỗng thì báo theo bộ lọc.
                    val noReviewsAtAll = state.filter == ReviewFilter.All && state.reviews is UiState.Empty
                    binding.filterChips.isVisible = !noReviewsAtAll
                    val empty = EmptyStateSpec(
                        message = if (noReviewsAtAll) R.string.review_empty_venue else R.string.review_empty_filter,
                    )
                    val shown = (state.reviews as? UiState.Error)?.let {
                        it.copy(message = getString(R.string.review_list_load_failed) + "\n" + errorMessage(it))
                    } ?: state.reviews
                    binding.stateViews.render(shown, binding.reviewList, empty)
                    adapter.submitList((state.reviews as? UiState.Content)?.data.orEmpty())
                }
            }
        }
    }

    private fun bindBar(bar: ViewReviewCriterionBarBinding, label: String, value: Double) {
        val text = scoreFormat.format(value)
        bar.value.text = text
        bar.bar.progress = (value * 10).toInt()
        bar.root.contentDescription = "$label: $text / 5"
    }

    private fun openPhotos(urls: List<String>, index: Int) {
        findNavController().navigate(R.id.reviewPhotoViewerFragment, PhotoViewerFragment.args(urls, index))
    }
}
