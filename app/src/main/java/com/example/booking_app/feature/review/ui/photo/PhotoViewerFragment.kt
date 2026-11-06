package com.example.booking_app.feature.review.ui.photo

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentReviewPhotoViewerBinding

/** Xem ảnh của một đánh giá toàn màn hình (US2, T046). Mở từ lưới ảnh trong `item_review`. */
class PhotoViewerFragment : Fragment(R.layout.fragment_review_photo_viewer) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReviewPhotoViewerBinding.bind(view)
        val urls = requireArguments().getStringArray(ARG_PHOTO_URLS).orEmpty().toList()
        val start = requireArguments().getInt(ARG_START_INDEX).coerceIn(0, (urls.size - 1).coerceAtLeast(0))

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.pager.adapter = PhotoPagerAdapter(urls)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.toolbar.title = getString(R.string.review_photo_viewer_title, position + 1, urls.size)
            }
        })
        if (savedInstanceState == null) binding.pager.setCurrentItem(start, false)
    }

    private class PhotoPagerAdapter(private val urls: List<String>) :
        RecyclerView.Adapter<PhotoPagerAdapter.Holder>() {

        class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
        )

        override fun getItemCount() = urls.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.image.contentDescription =
                holder.image.context.getString(R.string.review_photo_item, position + 1)
            holder.image.load(urls[position]) {
                crossfade(true)
                error(R.drawable.bg_venue_placeholder)
            }
        }
    }

    companion object {
        /** Tên argument khớp `nav_review.xml` (contracts/ui-screens.md §1). */
        const val ARG_PHOTO_URLS = "photoUrls"
        const val ARG_START_INDEX = "startIndex"

        fun args(photoUrls: List<String>, startIndex: Int): Bundle =
            bundleOf(ARG_PHOTO_URLS to photoUrls.toTypedArray(), ARG_START_INDEX to startIndex)
    }
}
