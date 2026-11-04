package com.example.booking_app.feature.courtdetail.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.booking_app.R
import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.network.AppError
import com.example.booking_app.databinding.FragmentCourtDetailBinding
import com.example.booking_app.databinding.ViewCourtDetailSectionErrorBinding
import com.example.booking_app.feature.courtdetail.domain.model.OpeningStatus
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Chi tiết sân (spec 030, contracts/ui-screens.md §2). Mở bằng deep link `smashnow://venue/{venueId}` hoặc action
 * tới `nav_court_detail`. Fragment chỉ vẽ [CourtDetailUiState] và chạy [CourtDetailEffect]; mọi quyết định ở
 * [CourtDetailViewModel].
 */
@AndroidEntryPoint
class CourtDetailFragment : Fragment() {

    private val viewModel: CourtDetailViewModel by viewModels()

    private var _binding: FragmentCourtDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var galleryAdapter: PhotoGalleryAdapter
    private var galleryRestored = false
    private var venueName: String = ""

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.onPhotoPageChanged(position)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourtDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        galleryRestored = false
        galleryAdapter = PhotoGalleryAdapter(
            describe = { index, total -> getString(R.string.court_detail_cd_photo, index + 1, total, venueName) },
            onClick = viewModel::onPhotoClicked,
        )
        binding.photoPager.adapter = galleryAdapter
        binding.photoPager.registerOnPageChangeCallback(pageCallback)

        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.fullScreenBack.setOnClickListener { findNavController().navigateUp() }
        binding.fullScreenRetry.setOnClickListener { viewModel.onRetry(SectionId.HEADER) }
        binding.amenitiesError.retryButton.setOnClickListener { viewModel.onRetry(SectionId.AMENITIES) }
        binding.courtsCard.root.setOnClickListener {
            if (viewModel.uiState.value.courts is Section.Error) viewModel.onRetry(SectionId.COURTS)
        }
        binding.bookButton.setOnClickListener { viewModel.onBookClicked() }
        // TODO(C): mở smashnow://chat/direct khi 091 có deep link (research R7); US5 nối vào ViewModel.
        binding.chatButton.setOnClickListener { showMessage(CourtDetailMessage.COMING_SOON) }

        // Chưa có US3: ẩn thẻ khoảng cách và số điện thoại.
        binding.distanceCard.root.isVisible = false
        binding.distanceCardSpace.isVisible = false
        binding.phoneCard.root.isVisible = false
        binding.phoneCardSpace.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.effects.collect(::handle) }
            }
        }
    }

    override fun onDestroyView() {
        binding.photoPager.unregisterOnPageChangeCallback(pageCallback)
        binding.photoPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    // ---------- vẽ ----------

    private fun render(state: CourtDetailUiState) {
        val header = state.header
        val firstLoad = state.availability == null && header !is Section.Error
        binding.fullScreenState.isVisible = firstLoad || header is Section.Error
        binding.fullScreenLoading.isVisible = firstLoad
        binding.fullScreenError.isVisible = header is Section.Error
        if (header is Section.Error) {
            binding.fullScreenErrorText.setText(errorText(header.error))
        }

        binding.offlineBanner.isVisible = state.isOffline
        if (header is Section.Content) renderHeader(header.data, state.photoIndex)
        renderCourts(state)
        renderAmenities(state.amenities)
        renderBottomBar(state)
    }

    private fun renderHeader(h: HeaderContent, photoIndex: Int) {
        venueName = h.name
        binding.nameText.text = h.name
        binding.addressText.text = listOfNotNull(h.address, h.districtName).joinToString(" · ")
        binding.suspendedNote.isVisible = h.suspended

        binding.ratingText.text = if (h.rating.count > 0) {
            val count = resources.getQuantityString(R.plurals.court_detail_review_count, h.rating.count, h.rating.count)
            "${CourtDetailFormat.rating(h.rating.average)} ($count)"
        } else {
            getString(R.string.court_detail_no_rating)
        }
        binding.ratingText.contentDescription = if (h.rating.count > 0) {
            getString(R.string.court_detail_cd_rating, CourtDetailFormat.rating(h.rating.average))
        } else {
            null
        }

        val opening = h.opening
        binding.openingChip.isVisible = opening != null && !h.suspended
        binding.openingChip.text = when (opening) {
            OpeningStatus.AllDay -> getString(R.string.court_detail_open_all_day)
            is OpeningStatus.Open -> getString(
                R.string.court_detail_open_now,
                h.openTime?.format().orEmpty(),
                h.closeTime?.format().orEmpty(),
            )
            is OpeningStatus.Closed -> getString(R.string.court_detail_closed_reopen, opening.reopensAt.format())
            null -> null
        }

        val items = h.photoUrls.ifEmpty { listOf(PhotoGalleryAdapter.PLACEHOLDER) }
        galleryAdapter.submitList(items) {
            val pager = _binding?.photoPager ?: return@submitList
            if (!galleryRestored && h.photoUrls.isNotEmpty()) {
                galleryRestored = true
                pager.setCurrentItem(photoIndex.coerceIn(0, items.lastIndex), false)
            }
        }
        binding.photoIndicator.isVisible = h.photoUrls.size > 1
        val shown = (photoIndex.coerceIn(0, items.lastIndex)) + 1
        binding.photoIndicator.text = getString(R.string.court_detail_photo_indicator, shown, items.size)
        binding.photoIndicator.contentDescription =
            getString(R.string.court_detail_cd_photo_indicator, shown, items.size)
    }

    private fun renderCourts(state: CourtDetailUiState) {
        val card = binding.courtsCard
        card.icon.setImageResource(R.drawable.ic_sports_court_24)
        when (val courts = state.courts) {
            Section.Loading -> {
                card.value.text = getString(R.string.court_detail_courts_label)
                card.sub.text = "…"
                card.root.contentDescription = null
            }
            is Section.Error -> {
                card.value.text = getString(R.string.court_detail_courts_label)
                card.sub.text = getString(R.string.court_detail_retry)
                card.root.contentDescription =
                    "${getString(R.string.court_detail_error_section)}. ${getString(R.string.court_detail_retry)}"
            }
            Section.Empty -> showNoCourts()
            is Section.Content -> {
                val summary = courts.data
                if (summary.activeCount == 0) {
                    showNoCourts()
                } else {
                    val surfaces = CourtDetailFormat.surfaces(requireContext(), summary)
                    card.value.text = resources.getQuantityString(
                        R.plurals.court_detail_court_count, summary.activeCount, summary.activeCount,
                    )
                    card.sub.text = surfaces
                    card.root.contentDescription =
                        getString(R.string.court_detail_courts_summary, summary.activeCount, surfaces)
                }
            }
        }
        card.root.isClickable = state.courts is Section.Error
    }

    private fun showNoCourts() {
        val card = binding.courtsCard
        card.value.text = getString(R.string.court_detail_courts_label)
        card.sub.text = getString(R.string.court_detail_courts_none)
        card.root.contentDescription = getString(R.string.court_detail_courts_none)
    }

    private fun renderAmenities(section: Section<List<Amenity>>) {
        binding.amenitiesLoading.isVisible = section is Section.Loading
        binding.amenityChips.isVisible = section is Section.Content
        binding.amenitiesEmpty.isVisible = section is Section.Empty
        showSectionError(binding.amenitiesError, section)
        if (section !is Section.Content) return
        val chips = binding.amenityChips
        val ids = section.data.map { it.id }
        if (chips.tag == ids) return
        chips.tag = ids
        chips.removeAllViews()
        section.data.forEach { amenity ->
            chips.addView(
                Chip(requireContext()).apply {
                    text = amenity.name
                    setChipIconResource(amenityIcon(amenity.iconKey))
                    isChipIconVisible = true
                    isClickable = false
                    isCheckable = false
                    contentDescription = getString(R.string.court_detail_cd_amenity, amenity.name)
                },
            )
        }
    }

    private fun renderBottomBar(state: CourtDetailUiState) {
        val bar = state.bottomBar
        binding.bottomBar.isVisible = bar != BottomBarState.Hidden
        val book = bar as? BottomBarState.Book
        binding.bookButton.isVisible = book != null
        binding.chatButton.isVisible = book != null
        binding.bottomSuspendedText.isVisible = bar == BottomBarState.Suspended
        binding.bottomPriceText.isVisible = book != null
        binding.bottomWindowText.isVisible = false
        when (val price = book?.price) {
            null, BottomPrice.Loading -> binding.bottomPriceText.text = null
            is BottomPrice.ForWindow -> {
                binding.bottomPriceText.text =
                    getString(R.string.court_detail_price_per_hour, CourtDetailFormat.vnd(price.perHour))
                binding.bottomWindowText.text = CourtDetailFormat.window(requireContext(), price.window)
                binding.bottomWindowText.isVisible = true
            }
            is BottomPrice.From -> binding.bottomPriceText.text =
                getString(R.string.court_detail_price_from, CourtDetailFormat.vnd(price.perHour))
            BottomPrice.Contact -> binding.bottomPriceText.setText(R.string.court_detail_contact)
        }
    }

    private fun showSectionError(view: ViewCourtDetailSectionErrorBinding, section: Section<*>) {
        view.root.isVisible = section is Section.Error
        if (section is Section.Error) view.errorText.setText(R.string.court_detail_error_section)
    }

    private fun errorText(error: AppError): Int = when (error) {
        AppError.Network, AppError.Timeout -> R.string.court_detail_error_network
        else -> R.string.court_detail_error_generic
    }

    // ---------- sự kiện ----------

    private fun handle(effect: CourtDetailEffect) {
        when (effect) {
            is CourtDetailEffect.OpenBookingGrid -> {
                // TODO(B): truyền date/startTime/endTime khi 040 có tham số (research R1).
                findNavController().navigate(
                    R.id.action_courtDetail_to_bookingSlotGrid,
                    bundleOf(CourtDetailViewModel.ARG_VENUE_ID to effect.venueId),
                )
            }
            is CourtDetailEffect.OpenPhotoViewer -> findNavController().navigate(
                R.id.action_global_photoViewer,
                bundleOf("photoUrls" to effect.urls.toTypedArray(), "startIndex" to effect.index),
            )
            is CourtDetailEffect.Snackbar -> showMessage(effect.message)
            else -> Unit // Các sự kiện của US3–US5 nối vào khi làm các story đó.
        }
    }

    private fun showMessage(message: CourtDetailMessage) {
        val text = when (message) {
            CourtDetailMessage.OWN_VENUE -> R.string.court_detail_own_venue
            CourtDetailMessage.SUSPENDED -> R.string.court_detail_suspended
            CourtDetailMessage.PHONE_COPIED -> R.string.court_detail_phone_copied
            CourtDetailMessage.FAVORITE_ADDED -> R.string.court_detail_favorite_added
            CourtDetailMessage.FAVORITE_REMOVED -> R.string.court_detail_favorite_removed
            CourtDetailMessage.FAVORITE_FAILED -> R.string.court_detail_favorite_failed
            CourtDetailMessage.COMING_SOON -> R.string.court_detail_coming_soon
        }
        Snackbar.make(binding.courtDetailRoot, text, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.bottomBar.takeIf { it.isVisible })
            .show()
    }

    private companion object {
        /** `iconKey` của `amenities` (spec 020) → drawable; khóa lạ dùng biểu tượng chung. */
        @DrawableRes
        fun amenityIcon(iconKey: String): Int = when (iconKey.replace('-', '_')) {
            "parking" -> R.drawable.ic_amenity_parking
            "drinks" -> R.drawable.ic_amenity_drinks
            "shower" -> R.drawable.ic_amenity_shower
            "racket_rental" -> R.drawable.ic_amenity_racket_rental
            else -> R.drawable.ic_amenity_default
        }
    }
}
