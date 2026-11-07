package com.example.booking_app.feature.courtdetail.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.location.DistanceFormatter
import com.example.booking_app.core.location.GeoDistance
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.courtdetail.domain.OpeningHours
import com.example.booking_app.feature.courtdetail.domain.OpeningTicker
import com.example.booking_app.feature.courtdetail.domain.SearchWindowArgs
import com.example.booking_app.feature.courtdetail.domain.model.CourtSummary
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.PhoneDisplay
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import com.example.booking_app.feature.courtdetail.domain.model.SearchWindow
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueDetail
import com.example.booking_app.feature.courtdetail.domain.usecase.Bookable
import com.example.booking_app.feature.courtdetail.domain.usecase.CheckVenueBookableUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.LoadCourtSummaryUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.LoadLatestReviewsUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.LoadPriceTableUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.ObserveVenueDetailUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.VenueView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Chi tiết sân (contracts/ui-screens.md §4). Một `StateFlow` cho cả màn hình, sự kiện qua `Channel`.
 *
 * - Listener `venues/{id}` chỉ chạy khi có người thu thập [uiState] (`WhileSubscribed(5s)`), nên được gỡ khi
 *   Fragment `STOPPED` (research R2).
 * - `courts`, `priceRules` đọc một lần, song song với venue; khách thì chờ venue trước để không đọc khi cơ sở
 *   đang tạm ngừng (R10).
 * - Khung giờ từ tìm kiếm hết hạn khi qua giờ bắt đầu (ticker mỗi phút).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CourtDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeVenueDetail: ObserveVenueDetailUseCase,
    private val loadCourtSummary: LoadCourtSummaryUseCase,
    private val loadPriceTable: LoadPriceTableUseCase,
    private val checkVenueBookable: CheckVenueBookableUseCase,
    private val loadLatestReviews: LoadLatestReviewsUseCase,
    private val sessionManager: SessionManager,
    private val locationProvider: LocationProvider,
    private val clock: AppClock,
) : ViewModel() {

    private val venueId: String = checkNotNull(savedStateHandle[ARG_VENUE_ID]) { "Thiếu venueId" }

    private val window = MutableStateFlow(
        SearchWindowArgs.parse(
            savedStateHandle[ARG_DATE],
            savedStateHandle[ARG_START],
            savedStateHandle[ARG_END],
            now(),
        ),
    )

    /** Các mục đọc một lần và trạng thái phụ, gộp vào [uiState]. */
    private data class Sections(
        val courts: Section<CourtSummary> = Section.Loading,
        /** Dòng giá thô: bảng giá dựng lại từ đây khi khung giờ đổi hoặc hết hạn. */
        val priceRules: Section<List<PriceRule>> = Section.Loading,
        val availabilityOverride: VenueAvailability? = null,
        val photoIndex: Int = 0,
        val distanceText: String? = null,
        /** 3 đánh giá mới nhất; điểm và tiêu chí lấy từ venue khi dựng [ReviewsContent] (research R3). */
        val latestReviews: Section<List<ReviewPreview>> = Section.Loading,
    )

    private val sections = MutableStateFlow(Sections(photoIndex = savedStateHandle[KEY_PHOTO_INDEX] ?: 0))
    private val venueReload = MutableStateFlow(0)
    private var latestVenue: VenueDetail? = null
    private var latestView: VenueView? = null
    private var loadsStarted = false
    private var bookingInFlight = false
    private var distanceRequested = false
    private var reviewsRequested = false

    private val effectChannel = Channel<CourtDetailEffect>(Channel.BUFFERED)
    val effects: Flow<CourtDetailEffect> = effectChannel.receiveAsFlow()

    private val venueFlow: Flow<VenueView?> = venueReload
        .flatMapLatest { observeVenueDetail(venueId).onStart<VenueView?> { emit(null) } }
        .onEach(::onVenue)

    private val ticker: Flow<ZonedDateTime> = OpeningTicker.ticks(clock)
        .onEach { now -> window.update { w -> w?.takeIf { SearchWindowArgs.isUpcoming(it, now) } } }

    val uiState: StateFlow<CourtDetailUiState> =
        combine(venueFlow, sections, ticker, window) { venue, s, now, w -> buildState(venue, s, now, w) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                CourtDetailUiState(window = window.value, photoIndex = sections.value.photoIndex),
            )

    init {
        if (!sessionManager.isGuest.value) startLoads()
    }

    // ---------- thao tác ----------

    fun onRetry(section: SectionId) {
        when (section) {
            SectionId.HEADER, SectionId.AMENITIES -> venueReload.update { it + 1 }
            SectionId.COURTS -> loadCourts()
            SectionId.PRICES -> loadPrices()
            SectionId.REVIEWS -> loadReviews()
        }
    }

    fun onBookClicked() {
        viewModelScope.launch { book() }
    }

    fun onPhotoClicked(index: Int) {
        val urls = latestVenue?.photoUrls.orEmpty()
        if (urls.isEmpty()) return
        send(CourtDetailEffect.OpenPhotoViewer(urls, index.coerceIn(0, urls.lastIndex)))
    }

    fun onPhotoPageChanged(index: Int) {
        savedStateHandle[KEY_PHOTO_INDEX] = index
        sections.update { it.copy(photoIndex = index) }
    }

    /** Số hợp lệ → mở trình gọi; số không hợp lệ thì bấm không làm gì (chỉ nhấn giữ để sao chép). */
    fun onPhoneClicked() {
        val phone = PhoneDisplay.of(latestVenue?.phone) as? PhoneDisplay.Dialable ?: return
        send(CourtDetailEffect.Dial(phone.e164))
    }

    fun onPhoneLongClicked() {
        val phone = PhoneDisplay.of(latestVenue?.phone) ?: return
        send(CourtDetailEffect.CopyPhone(phone.text))
    }

    /** "Xem tất cả (N)" mở danh sách đánh giá của 070 (FR-015). */
    fun onSeeAllReviewsClicked() {
        val venue = latestVenue ?: return
        send(CourtDetailEffect.OpenReviews(venueId, venue.name))
    }

    fun onReviewPhotoClicked(reviewId: String, index: Int) {
        val reviews = (sections.value.latestReviews as? Section.Content)?.data ?: return
        val urls = reviews.firstOrNull { it.id == reviewId }?.photoUrls.orEmpty()
        if (urls.isEmpty()) return
        send(CourtDetailEffect.OpenPhotoViewer(urls, index.coerceIn(0, urls.lastIndex)))
    }

    fun onDirectionsClicked() {
        val venue = latestVenue ?: return
        val location = venue.location ?: return
        send(CourtDetailEffect.OpenDirections(location.lat, location.lng, venue.name))
    }

    // ---------- nội bộ ----------

    /** Khoảng cách chỉ khi đã có quyền vị trí từ trước (020 xin quyền); 030 không xin quyền (research R7). */
    private fun loadDistance(venue: VenueDetail) {
        if (distanceRequested) return
        val target = venue.location ?: return
        distanceRequested = true
        if (!locationProvider.hasPermission()) return
        viewModelScope.launch {
            val me = try {
                locationProvider.current()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch
            val meters = GeoDistance.meters(me, com.google.firebase.firestore.GeoPoint(target.lat, target.lng))
            sections.update { it.copy(distanceText = DistanceFormatter.format(meters)) }
        }
    }

    private suspend fun book() {
        val venue = latestVenue ?: return
        if (bookingInFlight) return
        if (sessionManager.isGuest.value) {
            send(CourtDetailEffect.ShowLoginPrompt(PendingAction.BOOK))
            return
        }
        if (venue.ownerId == sessionManager.currentUid()) {
            send(CourtDetailEffect.Snackbar(CourtDetailMessage.OWN_VENUE))
            return
        }
        val availability = currentAvailability()
        if (availability != VenueAvailability.ACTIVE) {
            send(CourtDetailEffect.Snackbar(CourtDetailMessage.SUSPENDED))
            return
        }
        bookingInFlight = true
        try {
            when (val result = checkVenueBookable(venueId, availability)) {
                Bookable.Yes -> send(CourtDetailEffect.OpenBookingGrid(venueId, currentWindow()))
                is Bookable.No -> {
                    sections.update { it.copy(availabilityOverride = result.availability) }
                    send(CourtDetailEffect.Snackbar(CourtDetailMessage.SUSPENDED))
                }
            }
        } finally {
            bookingInFlight = false
        }
    }

    private fun currentAvailability(): VenueAvailability? =
        sections.value.availabilityOverride ?: latestVenue?.availability

    /** Khung giờ còn hiệu lực tại thời điểm bấm (ticker chỉ chạy mỗi phút). */
    private fun currentWindow(): SearchWindow? = window.value?.takeIf { SearchWindowArgs.isUpcoming(it, now()) }

    private fun onVenue(view: VenueView?) {
        latestView = view
        if (view is VenueView.Found) {
            val venue = view.result.venue
            // Listener báo lại ACTIVE thì bỏ trạng thái ghi đè từ lần kiểm tra server trước.
            if (venue.availability != latestVenue?.availability) sections.update { it.copy(availabilityOverride = null) }
            latestVenue = venue
            loadDistance(venue)
            // Đánh giá tải sau phần đầu (FR-024); chưa có đánh giá thì không đọc `reviews` (R3).
            if (!reviewsRequested && venue.rating.count > 0) loadReviews()
            if (!loadsStarted) startLoads()
        }
    }

    private fun startLoads() {
        loadsStarted = true
        loadCourts()
        loadPrices()
    }

    private fun loadPrices() {
        sections.update { it.copy(priceRules = Section.Loading) }
        viewModelScope.launch {
            val section = try {
                loadPriceTable.rules(venueId).let { if (it.isEmpty()) Section.Empty else Section.Content(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Section.Error(e.toAppError())
            }
            sections.update { it.copy(priceRules = section) }
        }
    }

    /** Mục bảng giá và giá ở thanh dưới, dựng từ dòng giá thô và khung giờ còn hiệu lực. */
    private fun prices(rules: Section<List<PriceRule>>, w: SearchWindow?): Pair<Section<PriceContent>, BottomPrice> =
        when (rules) {
            Section.Loading -> Section.Loading to BottomPrice.Loading
            Section.Empty -> Section.Empty to BottomPrice.Contact
            is Section.Error -> rules to BottomPrice.Loading
            is Section.Content -> {
                val table = loadPriceTable.table(rules.data, w)
                val section = if (table.groups.isEmpty()) Section.Empty else Section.Content(PriceContent(table.groups, table.selected))
                val selected = table.selected
                val bottom = when {
                    selected != null -> selected.perHour?.let { BottomPrice.ForWindow(it, selected.window) } ?: BottomPrice.Contact
                    table.minPerHour != null -> BottomPrice.From(table.minPerHour)
                    else -> BottomPrice.Contact
                }
                section to bottom
            }
        }

    private fun loadReviews() {
        reviewsRequested = true
        sections.update { it.copy(latestReviews = Section.Loading) }
        viewModelScope.launch {
            val section = try {
                Section.Content(loadLatestReviews(venueId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Section.Error(e.toAppError())
            }
            sections.update { it.copy(latestReviews = section) }
        }
    }

    private fun reviews(venue: VenueDetail, latest: Section<List<ReviewPreview>>): Section<ReviewsContent> =
        if (venue.rating.count == 0) {
            Section.Empty
        } else {
            when (latest) {
                Section.Loading, Section.Empty -> Section.Loading
                is Section.Error -> latest
                is Section.Content -> Section.Content(ReviewsContent(venue.rating, latest.data))
            }
        }

    private fun loadCourts() {
        sections.update { it.copy(courts = Section.Loading) }
        viewModelScope.launch {
            val section = try {
                Section.Content(loadCourtSummary(venueId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Section.Error(e.toAppError())
            }
            sections.update { it.copy(courts = section) }
        }
    }

    private fun buildState(view: VenueView?, s: Sections, now: ZonedDateTime, w: SearchWindow?): CourtDetailUiState {
        val base = CourtDetailUiState(window = w, photoIndex = s.photoIndex)
        return when (view) {
            null -> base
            VenueView.Gone -> base.copy(availability = VenueAvailability.GONE)
            VenueView.SuspendedGuest -> base.copy(availability = VenueAvailability.SUSPENDED_GUEST)
            is VenueView.Failed -> base.copy(header = Section.Error(view.error))
            is VenueView.Found -> {
                val venue = view.result.venue
                val availability = s.availabilityOverride ?: venue.availability
                val suspended = availability != VenueAvailability.ACTIVE
                val (prices, bottomPrice) = prices(s.priceRules, w)
                base.copy(
                    availability = availability,
                    header = Section.Content(
                        HeaderContent(
                            name = venue.name,
                            address = venue.address,
                            districtName = venue.districtName,
                            rating = venue.rating,
                            opening = OpeningHours.status(venue.openTime, venue.closeTime, now),
                            openTime = TimeOfDay.parse(venue.openTime),
                            closeTime = TimeOfDay.parseEnd(venue.closeTime),
                            photoUrls = venue.photoUrls,
                            suspended = suspended,
                        ),
                    ),
                    courts = s.courts,
                    prices = prices,
                    amenities = when (val list = venue.amenities) {
                        null -> Section.Error(AppError.Unknown(null))
                        else -> if (list.isEmpty()) Section.Empty else Section.Content(list)
                    },
                    isOffline = venue.isFromCache,
                    map = venue.location?.let { Section.Content(it) } ?: Section.Empty,
                    phone = PhoneDisplay.of(venue.phone),
                    distanceText = s.distanceText,
                    reviews = reviews(venue, s.latestReviews),
                    bottomBar = if (suspended) BottomBarState.Suspended else BottomBarState.Book(bottomPrice),
                )
            }
        }
    }

    private fun now(): ZonedDateTime = Instant.ofEpochMilli(clock.now()).atZone(BookingWindow.ZONE)

    private fun send(effect: CourtDetailEffect) {
        effectChannel.trySend(effect)
    }

    companion object {
        const val ARG_VENUE_ID = "venueId"
        const val ARG_DATE = "date"
        const val ARG_START = "startTime"
        const val ARG_END = "endTime"
        const val KEY_PHOTO_INDEX = "photoIndex"
        const val KEY_PENDING_ACTION = "pendingAction"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
