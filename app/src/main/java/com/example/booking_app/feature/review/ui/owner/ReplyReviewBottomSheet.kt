package com.example.booking_app.feature.review.ui.owner

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.BottomSheetReviewReplyBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Ô nhập phản hồi (US4, T061). Dùng chung [OwnerReviewsViewModel] với [OwnerReviewsFragment] (mở bằng
 * `childFragmentManager`); kết quả gửi do Fragment cha nhận qua `events` rồi gọi [showError] hoặc đóng sheet.
 */
class ReplyReviewBottomSheet : BottomSheetDialogFragment(R.layout.bottom_sheet_review_reply) {

    private val viewModel: OwnerReviewsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private var binding: BottomSheetReviewReplyBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = BottomSheetReviewReplyBinding.bind(view).also { binding = it }
        val args = requireArguments()
        val reviewId = requireNotNull(args.getString(ARG_REVIEW_ID))
        val existing = args.getString(ARG_EXISTING_REPLY)

        binding.title.setText(if (existing == null) R.string.review_reply_title else R.string.review_reply_edit)
        binding.reviewExcerpt.text = args.getString(ARG_EXCERPT)
        binding.reviewExcerpt.isVisible = !args.getString(ARG_EXCERPT).isNullOrBlank()
        if (savedInstanceState == null && existing != null) binding.replyInput.setText(existing)
        binding.replyInput.doAfterTextChanged { binding.replyLayout.error = null }
        binding.sendButton.setOnClickListener {
            viewModel.submitReply(reviewId, binding.replyInput.text?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.sendButton.isEnabled = !state.isReplying
                    binding.sendButton.text = if (state.isReplying) "" else getString(R.string.review_reply_send)
                    binding.sendProgress.isVisible = state.isReplying
                }
            }
        }
    }

    fun showError(message: String) {
        binding?.replyLayout?.error = message
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ReplyReviewBottomSheet"
        private const val ARG_REVIEW_ID = "reviewId"
        private const val ARG_EXISTING_REPLY = "existingReply"
        private const val ARG_EXCERPT = "excerpt"

        fun newInstance(reviewId: String, existingReply: String?, excerpt: String) = ReplyReviewBottomSheet().apply {
            arguments = bundleOf(
                ARG_REVIEW_ID to reviewId,
                ARG_EXISTING_REPLY to existingReply,
                ARG_EXCERPT to excerpt,
            )
        }
    }
}
