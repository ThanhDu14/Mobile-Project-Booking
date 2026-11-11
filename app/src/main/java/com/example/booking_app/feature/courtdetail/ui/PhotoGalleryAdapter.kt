package com.example.booking_app.feature.courtdetail.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemCourtDetailPhotoBinding

/**
 * Thư viện ảnh 16:9 ở đầu chi tiết sân (FR-003). Ảnh lỗi hoặc cơ sở chưa có ảnh thì hiện `img_venue_placeholder`
 * (danh sách rỗng được Fragment đổi thành một phần tử [PLACEHOLDER]).
 */
class PhotoGalleryAdapter(
    private val describe: (index: Int, total: Int) -> String,
    private val onClick: (index: Int) -> Unit,
) : ListAdapter<String, PhotoGalleryAdapter.Holder>(Diff) {

    class Holder(val binding: ItemCourtDetailPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCourtDetailPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val url = getItem(position)
        holder.binding.photo.apply {
            if (url == PLACEHOLDER) {
                setImageResource(R.drawable.img_venue_placeholder)
                contentDescription = null
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnClickListener(null)
                isClickable = false
            } else {
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = describe(position, itemCount)
                load(url) {
                    placeholder(R.drawable.img_venue_placeholder)
                    error(R.drawable.img_venue_placeholder)
                }
                setOnClickListener { onClick(holder.bindingAdapterPosition) }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }

    companion object {
        /** Phần tử duy nhất khi cơ sở chưa có ảnh. */
        const val PLACEHOLDER = ""
    }
}
