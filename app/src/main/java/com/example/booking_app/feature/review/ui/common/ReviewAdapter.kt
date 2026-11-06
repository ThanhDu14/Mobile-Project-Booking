package com.example.booking_app.feature.review.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemReviewBinding
import com.example.booking_app.feature.review.domain.model.Review
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Danh sách đánh giá (T017), dùng ở "Tất cả đánh giá" (US6) và "Đánh giá của khách" (US4).
 * Tác giả đã xóa tài khoản → "Người dùng đã xóa" + ảnh mặc định (research R9).
 *
 * @param venueName có giá trị thì hiện tên cơ sở trên mỗi đánh giá (chủ sân có nhiều cơ sở).
 * @param onReply có giá trị thì hiện nút "Phản hồi"/"Sửa phản hồi" (US4).
 */
class ReviewAdapter(
    private val onPhotoClick: (photoUrls: List<String>, index: Int) -> Unit,
    private val venueName: ((venueId: String) -> String?)? = null,
    private val onReply: ((Review) -> Unit)? = null,
) : ListAdapter<Review, ReviewAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {

        private val thumbs = listOf(binding.photo1, binding.photo2, binding.photo3)

        fun bind(review: Review) = with(binding) {
            val context = root.context
            val name = if (review.author.isDeleted || review.author.displayName.isNullOrBlank()) {
                context.getString(R.string.review_deleted_user)
            } else {
                review.author.displayName
            }
            authorName.text = name
            avatar.contentDescription = context.getString(R.string.review_avatar_description, name)
            val avatarUrl = review.author.avatarUrl.takeUnless { review.author.isDeleted }
            avatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_person_outline_24)
                error(R.drawable.ic_person_outline_24)
            }

            meta.text = listOfNotNull(
                dateFormat.format(review.createdAt.atZone(ZoneId.systemDefault())),
                context.getString(R.string.review_edited_label).takeIf { review.isEdited },
            ).joinToString(" · ")

            val score = scoreFormat.format(review.overall)
            overall.text = score
            overall.contentDescription = context.getString(R.string.review_item_overall, score)
            criteria.text = context.getString(
                R.string.review_item_criteria,
                review.ratings.court,
                review.ratings.cleanliness,
                review.ratings.service,
            )

            val venue = venueName?.invoke(review.venueId)
            venueLabel.isVisible = venue != null
            venueLabel.text = venue

            hiddenLabel.isVisible = review.isHidden
            comment.isVisible = review.comment.isNotBlank()
            comment.text = review.comment

            photos.isVisible = review.photoUrls.isNotEmpty()
            thumbs.forEachIndexed { i, thumb ->
                val url = review.photoUrls.getOrNull(i)
                thumb.isVisible = url != null
                if (url != null) {
                    thumb.load(url) {
                        crossfade(true)
                        placeholder(R.drawable.bg_venue_placeholder)
                        error(R.drawable.bg_venue_placeholder)
                    }
                    thumb.contentDescription = context.getString(R.string.review_photo_item, i + 1)
                    thumb.setOnClickListener { onPhotoClick(review.photoUrls, i) }
                } else {
                    thumb.setOnClickListener(null)
                }
            }

            val reply = review.ownerReply
            replyBox.isVisible = reply != null
            if (reply != null) {
                replyText.text = reply.text
                replyLabel.text = if (reply.isEdited) {
                    context.getString(R.string.review_owner_reply_label) + " · " +
                        context.getString(R.string.review_edited_label)
                } else {
                    context.getString(R.string.review_owner_reply_label)
                }
            }

            replyButton.isVisible = onReply != null
            replyButton.setText(if (reply == null) R.string.review_reply else R.string.review_reply_edit)
            replyButton.setOnClickListener { onReply?.invoke(review) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }

    private companion object {
        val locale: Locale = Locale.forLanguageTag("vi-VN")
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
        val scoreFormat: NumberFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }
}
