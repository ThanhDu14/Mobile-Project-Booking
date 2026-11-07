package com.example.booking_app.feature.review.ui.write

import android.os.Bundle
import android.view.View
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentReviewWriteBinding
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.ui.common.messageRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Viết đánh giá cho một đơn đã hoàn thành (US1). Vào từ chi tiết đơn (spec 060) hoặc deep link
 * `smashnow://review/write/{bookingId}`. Đơn đã có đánh giá thì hiện lại nội dung đã gửi, chỉ xem.
 */
@AndroidEntryPoint
class WriteReviewFragment : Fragment(R.layout.fragment_review_write) {

    private val viewModel: WriteReviewViewModel by viewModels()

    private val locale = Locale.forLanguageTag("vi-VN")
    private val zone = ZoneId.systemDefault()
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, dd/MM", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val deadlineFormat = DateTimeFormatter.ofPattern("dd/MM", locale)
    private val scoreFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReviewWriteBinding.bind(view)

        // Bấm quay lại khi đang có thay đổi: hỏi "Bỏ đánh giá đang viết?" (T036).
        val discardCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = confirmDiscard()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, discardCallback)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        val ratingBars = mapOf(
            ReviewCriterion.COURT to binding.ratingCourt,
            ReviewCriterion.CLEANLINESS to binding.ratingCleanliness,
            ReviewCriterion.SERVICE to binding.ratingService,
        )
        ratingBars.forEach { (criterion, bar) ->
            bar.setOnRatingBarChangeListener { _, rating, fromUser ->
                if (fromUser) viewModel.onRatingChanged(criterion, rating.toInt().coerceAtLeast(1))
            }
        }
        binding.commentInput.doAfterTextChanged { viewModel.onCommentChanged(it?.toString().orEmpty()) }
        binding.submitButton.setOnClickListener { viewModel.submit() }

        // Màn hình này không có trạng thái rỗng; spec chỉ để thỏa chữ ký của render().
        val empty = EmptyStateSpec(message = R.string.review_load_failed)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        discardCallback.isEnabled = state.hasUnsavedChanges && !state.isSubmitting
                        val screenState: UiState<Unit> = when {
                            state.isLoading -> UiState.Loading
                            state.loadError != null -> UiState.Error(
                                getString(R.string.review_load_failed) + "\n" + getString(state.loadError.messageRes()),
                                retry = viewModel::retryLoad,
                            )
                            else -> UiState.Content(Unit)
                        }
                        binding.stateViews.render(screenState, binding.content, empty)
                        if (screenState is UiState.Content) bind(binding, state, ratingBars)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is WriteReviewEvent.Submitted -> {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.review_submitted, scoreFormat.format(event.overall)),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                findNavController().navigateUp()
                            }
                            is WriteReviewEvent.SubmitFailed -> {
                                val snackbar = Snackbar.make(
                                    binding.root,
                                    event.error.messageRes(),
                                    if (event.canRetry) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG,
                                )
                                if (event.canRetry) snackbar.setAction(R.string.review_retry) { viewModel.submit() }
                                snackbar.anchorView = binding.bottomBar
                                snackbar.show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bind(
        binding: FragmentReviewWriteBinding,
        state: WriteReviewUiState,
        ratingBars: Map<ReviewCriterion, RatingBar>,
    ) {
        state.target?.let { bindTarget(binding, it) }

        val draft = state.draft
        val values = mapOf(
            ReviewCriterion.COURT to draft.court,
            ReviewCriterion.CLEANLINESS to draft.cleanliness,
            ReviewCriterion.SERVICE to draft.service,
        )
        ratingBars.forEach { (criterion, bar) ->
            val stars = values[criterion]
            if (bar.rating.toInt() != (stars ?: 0)) bar.rating = (stars ?: 0).toFloat()
            bar.setIsIndicator(state.isReadOnly)
            bar.contentDescription = if (stars == null) {
                getString(R.string.review_rating_none, getString(criterion.labelRes()))
            } else {
                getString(R.string.review_rating_description, getString(criterion.labelRes()), stars)
            }
        }

        binding.missingCriteria.isVisible = state.missingCriteria.isNotEmpty()
        if (state.missingCriteria.isNotEmpty()) {
            val names = state.missingCriteria.joinToString(", ") { "\"${getString(it.labelRes())}\"" }
            binding.missingCriteria.text = getString(R.string.review_missing_named, names)
        }

        if (binding.commentInput.text?.toString() != draft.comment) binding.commentInput.setText(draft.comment)
        binding.commentInput.isEnabled = !state.isReadOnly
        binding.commentLayout.error = if (state.commentTooLong) getString(R.string.review_comment_too_long) else null

        val banner = when (state.eligibility) {
            ReviewEligibility.CAN_VIEW -> getString(R.string.review_already_submitted)
            ReviewEligibility.EXPIRED -> getString(R.string.review_window_closed)
            ReviewEligibility.NOT_ELIGIBLE -> getString(R.string.review_error_booking_not_completed)
            ReviewEligibility.CAN_WRITE, null -> null
        }
        binding.statusBanner.isVisible = banner != null
        binding.statusBanner.text = banner

        binding.publicNotice.isVisible = state.canEditUntil != null && state.eligibility != ReviewEligibility.NOT_ELIGIBLE
        state.canEditUntil?.let {
            binding.publicNotice.text = getString(R.string.review_public_notice, deadlineFormat.format(it.atZone(zone)))
        }

        binding.overallValue.text = state.overall
            ?.let { getString(R.string.review_overall_value, scoreFormat.format(it)) }
            ?: getString(R.string.review_overall_empty)

        binding.bottomBar.isVisible = state.eligibility == ReviewEligibility.CAN_WRITE
        binding.submitButton.isEnabled = state.canSubmit
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.review_submit)
        binding.submitProgress.isVisible = state.isSubmitting
    }

    private fun bindTarget(binding: FragmentReviewWriteBinding, target: ReviewTarget) {
        binding.venueName.text = target.venueName
        val start = target.startAt?.atZone(zone)
        val end = target.endAt?.atZone(zone)
        binding.bookingTime.text = if (start != null && end != null) {
            listOfNotNull(
                getString(
                    R.string.review_booking_time,
                    dayFormat.format(start),
                    timeFormat.format(start),
                    timeFormat.format(end),
                ),
                target.courtNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · ")
        } else {
            target.courtNames.joinToString(", ")
        }
        binding.venuePhoto.load(target.venuePhotoUrl)
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.review_discard_title)
            .setMessage(R.string.review_discard_message)
            .setNegativeButton(R.string.review_discard_keep, null)
            .setPositiveButton(R.string.review_discard_confirm) { _, _ -> findNavController().navigateUp() }
            .show()
    }

    private fun ReviewCriterion.labelRes(): Int = when (this) {
        ReviewCriterion.COURT -> R.string.review_criterion_court
        ReviewCriterion.CLEANLINESS -> R.string.review_criterion_cleanliness
        ReviewCriterion.SERVICE -> R.string.review_criterion_service
    }
}
