package com.example.booking_app.feature.community.ui.composer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemDraftPhotoBinding
import com.example.booking_app.feature.community.domain.model.DraftPhoto

/** Một mục ảnh trong lưới soạn bài kèm nguồn hiển thị (Uri nội bộ hoặc signed URL khi sửa bài). */
data class DraftPhotoItem(
    val photo: DraftPhoto,
    val displaySource: String?,
)

/** Lưới ảnh đang đính kèm (T049): trạng thái từng ảnh, bấm ảnh lỗi để thử lại, nút gỡ ảnh ≥ 48dp. */
class DraftPhotoAdapter(
    private val onRetry: (String) -> Unit,
    private val onRemove: (String) -> Unit,
) : ListAdapter<DraftPhotoItem, DraftPhotoAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDraftPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), position)

    inner class ViewHolder(private val binding: ItemDraftPhotoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DraftPhotoItem, position: Int) {
            val context = binding.root.context
            val number = position + 1
            binding.photoImage.contentDescription = context.getString(R.string.community_composer_photo_preview_cd, number)
            binding.photoImage.load(item.displaySource) {
                placeholder(R.drawable.ic_photo_library_24)
                error(R.drawable.ic_photo_library_24)
            }

            val isUploading = item.photo.state == DraftPhoto.State.UPLOADING || item.photo.state == DraftPhoto.State.PENDING
            val isFailed = item.photo.state == DraftPhoto.State.FAILED

            binding.uploadingOverlay.isVisible = isUploading
            binding.failedOverlay.isVisible = isFailed
            binding.failedOverlay.contentDescription = context.getString(R.string.community_composer_retry_photo_cd, number)
            binding.failedOverlay.setOnClickListener { onRetry(item.photo.localUri) }

            binding.removeButton.contentDescription = context.getString(R.string.community_composer_remove_photo_cd, number)
            binding.removeButton.setOnClickListener { onRemove(item.photo.localUri) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DraftPhotoItem>() {
        override fun areItemsTheSame(oldItem: DraftPhotoItem, newItem: DraftPhotoItem) =
            oldItem.photo.localUri == newItem.photo.localUri

        override fun areContentsTheSame(oldItem: DraftPhotoItem, newItem: DraftPhotoItem) = oldItem == newItem
    }
}
