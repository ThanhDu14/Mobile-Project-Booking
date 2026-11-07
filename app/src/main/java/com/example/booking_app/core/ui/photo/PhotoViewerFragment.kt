package com.example.booking_app.core.ui.photo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentPhotoViewerBinding

/**
 * Xem ảnh toàn màn hình (spec 030 FR-004, research R8): `ViewPager2` + [ZoomableImageView], chỉ báo "2/5".
 * Mở bằng action toàn cục `action_global_photoViewer` của `nav_player` với `photoUrls`, `startIndex`.
 * Dùng chung với ảnh đánh giá của spec 070.
 */
class PhotoViewerFragment : Fragment() {

    private val args: PhotoViewerFragmentArgs by navArgs()
    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updateIndicator(position)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val urls = args.photoUrls.toList()
        binding.photoPager.adapter = PhotoPagerAdapter(urls) { index ->
            getString(R.string.court_detail_cd_photo_indicator, index + 1, urls.size)
        }
        binding.photoPager.registerOnPageChangeCallback(pageCallback)
        if (savedInstanceState == null) {
            binding.photoPager.setCurrentItem(args.startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0)), false)
        }
        updateIndicator(binding.photoPager.currentItem)
        binding.closeButton.setOnClickListener { findNavController().navigateUp() }
    }

    private fun updateIndicator(position: Int) {
        val total = args.photoUrls.size
        binding.photoIndicator.text = getString(R.string.court_detail_photo_indicator, position + 1, total)
        binding.photoIndicator.contentDescription =
            getString(R.string.court_detail_cd_photo_indicator, position + 1, total)
    }

    override fun onDestroyView() {
        binding.photoPager.unregisterOnPageChangeCallback(pageCallback)
        binding.photoPager.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
