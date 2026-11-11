package com.example.booking_app.feature.courtdetail.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.example.booking_app.R
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.databinding.ItemCourtDetailReviewBinding
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import java.time.format.DateTimeFormatter

/**
 * 3 đánh giá mới nhất ở chi tiết sân (FR-014, ui-screens §2 mục 7). Người viết đã xóa tài khoản (`authorName == null`)
 * hiện "Người dùng đã xóa" và ảnh mặc định. Chạm ảnh thu nhỏ → [onPhotoClick] với id đánh giá và vị trí ảnh.
 */
class ReviewPreviewAdapter(
    private val onPhotoClick: (reviewId: String, index: Int) -> Unit,
) : ListAdapter<ReviewPreview, ReviewPreviewAdapter.Holder>(Diff) {

    class Holder(val binding: ItemCourtDetailReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCourtDetailReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val review = getItem(position)
        val b = holder.binding
        val context = b.root.context
        val name = review.authorName ?: context.getString(R.string.court_detail_review_deleted_user)
        val date = review.createdAt?.let { DATE.format(it) }.orEmpty()

        b.authorName.text = name
        b.date.text = date
        b.stars.rating = review.overall.toFloat()
        b.headerRow.contentDescription = context.getString(R.string.court_detail_cd_review, name, review.overall, date)

        val avatar = review.authorAvatarUrl
        b.avatarInitial.text = review.authorName?.trim()?.firstOrNull()?.uppercase().orEmpty()
        if (review.authorName == null) {
            b.avatarInitial.isVisible = false
            b.avatarImage.isVisible = true
            b.avatarImage.setImageResource(R.drawable.ic_person_outline_24)
        } else if (avatar != null) {
            b.avatarImage.isVisible = true
            b.avatarImage.load(avatar) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.bg_account_avatar_initials)
                error(R.drawable.ic_person_outline_24)
            }
        } else {
            b.avatarInitial.isVisible = true
            b.avatarImage.isVisible = false
        }

        b.comment.isVisible = !review.comment.isNullOrBlank()
        b.comment.text = review.comment

        b.replyBlock.isVisible = review.reply != null
        b.replyText.text = review.reply

        bindPhotos(b, review)
    }

    private fun bindPhotos(b: ItemCourtDetailReviewBinding, review: ReviewPreview) {
        val row = b.photoRow
        row.removeAllViews()
        b.photoScroll.isVisible = review.photoUrls.isNotEmpty()
        val res = row.resources
        val size = res.getDimensionPixelSize(R.dimen.court_detail_review_photo_size)
        val gap = res.getDimensionPixelSize(R.dimen.space_2)
        review.photoUrls.forEachIndexed { index, url ->
            row.addView(
                ImageView(row.context).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { if (index > 0) marginStart = gap }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = res.getString(R.string.court_detail_cd_review_photo, index + 1)
                    load(url) {
                        placeholder(R.drawable.img_venue_placeholder)
                        error(R.drawable.img_venue_placeholder)
                    }
                    setOnClickListener { onPhotoClick(review.id, index) }
                },
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<ReviewPreview>() {
        override fun areItemsTheSame(oldItem: ReviewPreview, newItem: ReviewPreview): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ReviewPreview, newItem: ReviewPreview): Boolean = oldItem == newItem
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM").withZone(BookingWindow.ZONE)
    }
}
