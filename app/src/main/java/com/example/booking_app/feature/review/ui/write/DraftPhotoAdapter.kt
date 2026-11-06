package com.example.booking_app.feature.review.ui.write

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemReviewDraftPhotoBinding
import com.example.booking_app.feature.review.domain.model.DraftPhoto

/**
 * Lưới ảnh đính kèm khi viết/sửa đánh giá (US2, T045): ảnh đang tải có vòng xoay, ảnh lỗi có badge và
 * chạm để thử lại (FR-006); nút X bỏ ảnh. [editable] = false (chỉ xem) thì ẩn nút X.
 */
class DraftPhotoAdapter(
    private val onRetry: (DraftPhoto) -> Unit,
    private val onRemove: (DraftPhoto) -> Unit,
) : ListAdapter<DraftPhoto, DraftPhotoAdapter.Holder>(Diff) {

    var editable: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemReviewDraftPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), position)

    inner class Holder(private val binding: ItemReviewDraftPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: DraftPhoto, position: Int) {
            val context = binding.root.context
            binding.photo.load(photo.localUri)
            val uploading = photo.state == DraftPhoto.State.UPLOADING || photo.state == DraftPhoto.State.PENDING
            val failed = photo.state == DraftPhoto.State.FAILED
            binding.scrim.isVisible = uploading || failed
            binding.progress.isVisible = uploading
            binding.errorBadge.isVisible = failed

            binding.root.contentDescription = when {
                failed -> context.getString(R.string.review_photo_failed)
                uploading -> context.getString(R.string.review_photo_uploading)
                else -> context.getString(R.string.review_photo_item, position + 1)
            }
            binding.root.isClickable = failed
            binding.root.setOnClickListener(if (failed) ({ onRetry(photo) }) else null)

            binding.removeButton.isVisible = editable
            binding.removeButton.contentDescription = context.getString(R.string.review_photo_remove, position + 1)
            binding.removeButton.setOnClickListener { onRemove(photo) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DraftPhoto>() {
        override fun areItemsTheSame(oldItem: DraftPhoto, newItem: DraftPhoto) = oldItem.localUri == newItem.localUri
        override fun areContentsTheSame(oldItem: DraftPhoto, newItem: DraftPhoto) = oldItem == newItem
    }
}
