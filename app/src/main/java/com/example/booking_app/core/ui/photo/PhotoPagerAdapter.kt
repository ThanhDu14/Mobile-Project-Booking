package com.example.booking_app.core.ui.photo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemPhotoViewerBinding

/** Trang ảnh của [PhotoViewerFragment]: ảnh lỗi thì hiện ảnh mặc định, không làm hỏng các trang khác. */
class PhotoPagerAdapter(
    private val urls: List<String>,
    private val describe: (index: Int) -> String,
) : RecyclerView.Adapter<PhotoPagerAdapter.Holder>() {

    class Holder(val binding: ItemPhotoViewerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPhotoViewerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = urls.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.zoomableImage.apply {
            contentDescription = describe(position)
            load(urls[position]) {
                placeholder(R.drawable.img_venue_placeholder)
                error(R.drawable.img_venue_placeholder)
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.binding.zoomableImage.resetZoom()
    }
}
