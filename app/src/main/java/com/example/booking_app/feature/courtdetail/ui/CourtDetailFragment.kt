package com.example.booking_app.feature.courtdetail.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
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
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.databinding.FragmentCourtDetailBinding
import com.example.booking_app.databinding.ViewCourtDetailCriteriaBinding
import com.example.booking_app.databinding.ViewCourtDetailMapBinding
import com.example.booking_app.databinding.ViewCourtDetailSectionErrorBinding
import com.example.booking_app.feature.courtdetail.domain.model.LatLng
import com.example.booking_app.feature.courtdetail.domain.model.OpeningStatus
import com.example.booking_app.feature.courtdetail.domain.model.PhoneDisplay
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Chi tiết sân (spec 030, contracts/ui-screens.md §2). Mở bằng deep link `smashnow://venue/{venueId}` hoặc action
 * tới `nav_court_detail`. Fragment chỉ vẽ [CourtDetailUiState] và chạy [CourtDetailEffect]; mọi quyết định ở
 * [CourtDetailViewModel].
 */
@AndroidEntryPoint
class CourtDetailFragment : Fragment() {

    @Inject lateinit var authNavigator: AuthNavigator

    private val viewModel: CourtDetailViewModel by viewModels()

    private var _binding: FragmentCourtDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var galleryAdapter: PhotoGalleryAdapter
    private val priceAdapter = PriceTableAdapter()
    private val reviewAdapter = ReviewPreviewAdapter(onPhotoClick = { id, index -> viewModel.onReviewPhotoClicked(id, index) })
    private var galleryRestored = false
    private var venueName: String = ""

    /** Bản đồ chỉ tạo khi mục "Vị trí" gần vùng nhìn thấy (R7); lỗi tile thì ẩn đến hết vòng đời view. */
    private var mapController: VenueMapController? = null
    private var mapPoint: LatLng? = null
    private var mapFailed = false

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
        binding.priceList.adapter = priceAdapter
        binding.pricesError.retryButton.setOnClickListener { viewModel.onRetry(SectionId.PRICES) }
        binding.reviewList.adapter = reviewAdapter
        binding.reviewsError.retryButton.setOnClickListener { viewModel.onRetry(SectionId.REVIEWS) }
        binding.seeAllReviewsButton.setOnClickListener { viewModel.onSeeAllReviewsClicked() }
        binding.criteriaCourt.criteriaLabel.setText(R.string.court_detail_criteria_court)
        binding.criteriaCleanliness.criteriaLabel.setText(R.string.court_detail_criteria_cleanliness)
        binding.criteriaService.criteriaLabel.setText(R.string.court_detail_criteria_service)
        binding.amenitiesError.retryButton.setOnClickListener { viewModel.onRetry(SectionId.AMENITIES) }
        binding.courtsCard.root.setOnClickListener {
            if (viewModel.uiState.value.courts is Section.Error) viewModel.onRetry(SectionId.COURTS)
        }
        binding.bookButton.setOnClickListener { viewModel.onBookClicked() }
        // TODO(C): mở smashnow://chat/direct khi 091 có deep link (research R7); US5 nối vào ViewModel.
        binding.chatButton.setOnClickListener { showMessage(CourtDetailMessage.COMING_SOON) }

        mapController = null
        mapPoint = null
        mapFailed = false
        binding.directionsButton.setOnClickListener { viewModel.onDirectionsClicked() }
        binding.distanceCard.root.setOnClickListener { viewModel.onDirectionsClicked() }
        binding.distanceCard.icon.setImageResource(R.drawable.ic_directions_24)
        binding.phoneCard.icon.setImageResource(R.drawable.ic_call_24)
        listOf(binding.phoneCard.root, binding.pricesEmpty).forEach { view ->
            view.setOnClickListener { viewModel.onPhoneClicked() }
            view.setOnLongClickListener {
                viewModel.onPhoneLongClicked()
                true
            }
        }
        binding.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> maybeShowMap() })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.effects.collect(::handle) }
            }
        }
    }

    override fun onDestroyView() {
        // MapView.onDetach đã chạy ở ON_DESTROY của viewLifecycleOwner (VenueMapController).
        mapController = null
        binding.scrollView.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        binding.photoPager.unregisterOnPageChangeCallback(pageCallback)
        binding.photoPager.adapter = null
        binding.priceList.adapter = null
        binding.reviewList.adapter = null
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
        renderDistanceCard(state)
        renderPhoneCard(state.phone)
        renderAmenities(state.amenities)
        renderPrices(state.prices, state.phone)
        renderLocation(state)
        renderReviews(state)
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

    private fun renderPrices(section: Section<PriceContent>, phone: PhoneDisplay?) {
        binding.pricesLoading.isVisible = section is Section.Loading
        binding.priceList.isVisible = section is Section.Content
        // Rỗng → "Liên hệ" + số (bấm gọi như thẻ số điện thoại, R9); không có số → "Liên hệ chủ sân".
        binding.pricesEmpty.isVisible = section is Section.Empty
        binding.pricesEmpty.text = phone?.let { getString(R.string.court_detail_contact_phone, it.text) }
            ?: getString(R.string.court_detail_contact_owner)
        binding.pricesEmpty.isClickable = phone is PhoneDisplay.Dialable
        binding.pricesEmpty.isLongClickable = phone != null
        showSectionError(binding.pricesError, section)
        val content = (section as? Section.Content)?.data
        priceAdapter.submitGroups(content?.groups.orEmpty())
        val selected = content?.selected
        binding.selectedWindowCard.isVisible = selected != null
        if (selected != null) {
            binding.selectedWindowText.text = CourtDetailFormat.window(requireContext(), selected.window)
            binding.selectedWindowPrice.text = selected.perHour
                ?.let { getString(R.string.court_detail_price_per_hour, CourtDetailFormat.vnd(it)) }
                ?: getString(R.string.court_detail_contact)
        }
    }

    /** Mục 7 (FR-014, FR-015): rỗng → "Sân chưa có đánh giá", ẩn "Xem tất cả"; lỗi chỉ ở mục này → "Thử lại". */
    private fun renderReviews(state: CourtDetailUiState) {
        val section = state.reviews
        binding.reviewsSection.isVisible = state.header is Section.Content
        binding.reviewsLoading.isVisible = section is Section.Loading
        binding.reviewsEmpty.isVisible = section is Section.Empty
        showSectionError(binding.reviewsError, section)
        val content = (section as? Section.Content)?.data
        binding.reviewsSummary.isVisible = content != null
        binding.reviewList.isVisible = content != null && content.reviews.isNotEmpty()
        binding.seeAllReviewsButton.isVisible = content != null
        reviewAdapter.submitList(content?.reviews.orEmpty())
        if (content == null) return

        val summary = content.summary
        val score = CourtDetailFormat.rating(summary.average)
        val count = resources.getQuantityString(R.plurals.court_detail_review_count, summary.count, summary.count)
        binding.reviewsScoreText.text = score
        binding.reviewsStars.rating = summary.average.toFloat()
        binding.reviewsCountText.text = count
        binding.reviewsScoreBlock.contentDescription = "${getString(R.string.court_detail_cd_rating, score)}, $count"
        renderCriteria(binding.criteriaCourt, R.string.court_detail_criteria_court, summary.courtAvg)
        renderCriteria(binding.criteriaCleanliness, R.string.court_detail_criteria_cleanliness, summary.cleanlinessAvg)
        renderCriteria(binding.criteriaService, R.string.court_detail_criteria_service, summary.serviceAvg)
        binding.seeAllReviewsButton.text = getString(R.string.court_detail_reviews_see_all, summary.count)
    }

    private fun renderCriteria(view: ViewCourtDetailCriteriaBinding, label: Int, value: Double?) {
        view.root.isVisible = value != null
        if (value == null) return
        val text = CourtDetailFormat.rating(value)
        view.criteriaValue.text = text
        view.criteriaBar.setProgressCompat((value * 10).toInt().coerceIn(0, view.criteriaBar.max), false)
        view.root.contentDescription = getString(R.string.court_detail_cd_criteria, getString(label), text)
    }

    /** Thẻ (b): "1,2 km / Từ vị trí của bạn" khi có quyền vị trí, ngược lại "Chỉ đường"; ẩn khi không có tọa độ. */
    private fun renderDistanceCard(state: CourtDetailUiState) {
        val visible = state.map is Section.Content
        binding.distanceCard.root.isVisible = visible
        binding.distanceCardSpace.isVisible = visible
        if (!visible) return
        val card = binding.distanceCard
        val distance = state.distanceText
        val directions = getString(R.string.court_detail_directions)
        if (distance != null) {
            val from = getString(R.string.court_detail_distance_from_you)
            card.value.text = distance
            card.sub.text = from
            card.sub.isVisible = true
            card.root.contentDescription = "$distance $from. $directions"
        } else {
            card.value.text = directions
            card.sub.isVisible = false
            card.root.contentDescription = directions
        }
    }

    /** Thẻ (c): ẩn khi không có số; `DisplayOnly` bỏ "Bấm để gọi" và chỉ nhấn giữ để sao chép (R9). */
    private fun renderPhoneCard(phone: PhoneDisplay?) {
        binding.phoneCard.root.isVisible = phone != null
        binding.phoneCardSpace.isVisible = phone != null
        if (phone == null) return
        val card = binding.phoneCard
        val dialable = phone is PhoneDisplay.Dialable
        card.value.text = phone.text
        card.sub.isVisible = dialable
        card.sub.setText(R.string.court_detail_tap_to_call)
        card.root.isClickable = dialable
        card.root.isLongClickable = true
        card.root.contentDescription = if (dialable) {
            "${phone.text}. ${getString(R.string.court_detail_tap_to_call)}"
        } else {
            phone.text
        }
    }

    /** Mục 6: không có tọa độ → ẩn bản đồ và "Chỉ đường", vẫn hiện địa chỉ (Edge case R7). */
    private fun renderLocation(state: CourtDetailUiState) {
        val header = (state.header as? Section.Content)?.data
        binding.locationSection.isVisible = header != null
        if (header == null) return
        binding.locationAddressText.text = listOfNotNull(header.address, header.districtName).joinToString(", ")
        val point = (state.map as? Section.Content)?.data
        binding.directionsButton.isVisible = point != null
        mapPoint = point
        val map = mapController
        when {
            point == null || mapFailed -> mapCard()?.isVisible = false
            map != null -> {
                mapCard()?.isVisible = true
                map.show(point, header.name)
            }
            // Lần vẽ đầu chưa có kích thước: đợi layout xong rồi xét lại.
            else -> binding.scrollView.post { maybeShowMap() }
        }
    }

    private fun mapCard(): View? = _binding?.root?.findViewById(R.id.mapCard)

    /** Inflate bản đồ khi mục "Vị trí" cách cạnh dưới vùng nhìn thấy dưới một chiều cao màn hình (FR-024). */
    private fun maybeShowMap() {
        val b = _binding ?: return
        val point = mapPoint ?: return
        if (mapController != null || mapFailed || !b.locationSection.isVisible) return
        val viewport = b.scrollView.height
        if (viewport == 0) return
        val visibleBottom = b.scrollView.scrollY + viewport
        if (b.locationSection.top - visibleBottom >= viewport) return
        val mapBinding = ViewCourtDetailMapBinding.bind(b.mapStub.inflate())
        mapController = VenueMapController(mapBinding, viewLifecycleOwner) {
            mapFailed = true
            mapCard()?.isVisible = false
        }.also { it.show(point, venueName) }
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
            is CourtDetailEffect.ShowLoginPrompt -> MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.court_detail_booking_login_required)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.court_detail_booking_login) { _, _ ->
                    authNavigator.openLogin(findNavController())
                }
                .show()
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
            is CourtDetailEffect.Dial -> dial(effect.e164)
            is CourtDetailEffect.CopyPhone -> copyPhone(effect.text)
            is CourtDetailEffect.OpenDirections -> openDirections(effect)
            is CourtDetailEffect.OpenReviews -> openReviews(effect.venueId)
            else -> Unit // Các sự kiện của US4–US5 nối vào khi làm các story đó.
        }
    }

    /** Danh sách đánh giá đầy đủ của 070 qua deep link (research R3). */
    private fun openReviews(venueId: String) {
        try {
            findNavController().navigate("smashnow://review/venue/${Uri.encode(venueId)}".toUri())
        } catch (_: IllegalArgumentException) {
            // TODO(C): bỏ nhánh này khi 070 khai báo deep link smashnow://review/venue/{venueId} trong nav graph.
            showMessage(CourtDetailMessage.COMING_SOON)
        }
    }

    /** `ACTION_DIAL` không cần quyền; máy không nghe gọi được thì sao chép số (R9). */
    private fun dial(e164: String) {
        val fallback = viewModel.uiState.value.phone?.text ?: e164
        if (!requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            copyPhone(fallback)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_DIAL, "tel:$e164".toUri()))
        } catch (_: ActivityNotFoundException) {
            copyPhone(fallback)
        }
    }

    private fun copyPhone(text: String) {
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.court_detail_phone_copied), text))
        showMessage(CourtDetailMessage.PHONE_COPIED)
    }

    /** Ứng dụng bản đồ qua `geo:`, không có thì trình duyệt. Dùng try/catch nên không khai báo `<queries>` (R7). */
    private fun openDirections(effect: CourtDetailEffect.OpenDirections) {
        val lat = effect.lat
        val lng = effect.lng
        val geo = "geo:$lat,$lng?q=$lat,$lng(${Uri.encode(effect.name)})".toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (_: ActivityNotFoundException) {
            val web = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng".toUri()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, web))
            } catch (_: ActivityNotFoundException) {
                // Máy không có cả ứng dụng bản đồ lẫn trình duyệt: không còn cách mở, bỏ qua.
            }
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
