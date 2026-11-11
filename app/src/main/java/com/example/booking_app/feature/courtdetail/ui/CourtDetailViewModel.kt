package com.example.booking_app.feature.courtdetail.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.location.DistanceFormatter
import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.core.location.GeoDistance
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.courtdetail.domain.FavoriteGateway
import com.example.booking_app.feature.courtdetail.domain.OpeningHours
import com.example.booking_app.feature.courtdetail.domain.OpeningTicker
import com.example.booking_app.feature.courtdetail.domain.PendingActionResolver
import com.example.booking_app.feature.courtdetail.domain.SearchWindowArgs
import com.example.booking_app.feature.courtdetail.domain.model.CourtSummary
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.example.booking_app.feature.courtdetail.domain.model.PendingDecision
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
 * - Khách bấm "Đặt sân", trái tim, chat → mời đăng nhập; thao tác ghi vào `SavedStateHandle` và chạy đúng một lần
 *   khi màn hình hiện lại sau khi đăng nhập (research R11, [PendingActionResolver]).
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
    private val favoriteGateway: FavoriteGateway,
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

    /** `onResume` → `true`, `onPause` → `false`: khi `nav_auth` nằm trên thì không hủy nhầm thao tác chờ. */
    private var screenVisible = false
    private var latestIsFavorite: Boolean? = null

    /** Trạng thái lạc quan khi vừa bấm trái tim; xóa khi ghi xong để listener quyết định (lỗi → về trạng thái cũ). */
    private val favoriteOverride = MutableStateFlow<Boolean?>(null)

    private val effectChannel = Channel<CourtDetailEffect>(Channel.BUFFERED)
    val effects: Flow<CourtDetailEffect> = effectChannel.receiveAsFlow()

    private val venueFlow: Flow<VenueView?> = venueReload
        .flatMapLatest { observeVenueDetail(venueId).onStart<VenueView?> { emit(null) } }
        .onEach(::onVenue)

    /** `null` khi là khách. Listener `favorites` chỉ chạy khi đã đăng nhập và có người thu thập [uiState]. */
    private val favoriteFlow: Flow<Boolean?> = sessionManager.sessionState
        .map { (it as? SessionState.SignedIn)?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                favoriteGateway.observeIsFavorite(venueId)
                    .map<Boolean, Boolean?> { it }
                    // Mất quyền đọc (vừa đăng xuất): giữ trạng thái đang hiện như 070.
                    .catch { }
            }
        }
        .let { observed -> combine(observed, favoriteOverride) { o, override -> o?.let { override ?: it } } }
        .onEach { latestIsFavorite = it }

    private val ticker: Flow<ZonedDateTime> = OpeningTicker.ticks(clock)
        .onEach { now -> window.update { w -> w?.takeIf { SearchWindowArgs.isUpcoming(it, now) } } }

    val uiState: StateFlow<CourtDetailUiState> =
        combine(venueFlow, sections, ticker, window, favoriteFlow) { venue, s, now, w, favorite ->
            buildState(venue, s, now, w).copy(isFavorite = favorite)
        }
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

    /** Gọi từ `onResume` (contracts/ui-screens.md §4). */
    fun onScreenVisible() {
        screenVisible = true
        // Khách vừa đăng nhập từ màn hình "Sân tạm ngừng hoạt động": đọc lại cơ sở với quyền mới (R10).
        if (latestView == VenueView.SuspendedGuest && !sessionManager.isGuest.value) venueReload.update { it + 1 }
        resolvePending()
    }

    /** "Đăng nhập" ở màn hình rút gọn của khách (không có thao tác chờ). */
    fun onLoginClicked() {
        send(CourtDetailEffect.OpenLogin)
    }

    /** "Bỏ khỏi yêu thích" ở màn hình "Sân không còn tồn tại" (FR-022). */
    fun onRemoveFavoriteFromGone() {
        if (latestIsFavorite != true) return
        viewModelScope.launch { applyFavorite(false) }
    }

    /** Gọi từ `onPause`. */
    fun onScreenHidden() {
        screenVisible = false
    }

    /** "Đăng nhập" trong hộp mời: nhớ thao tác rồi mở `nav_auth`. */
    fun onLoginChosen(action: PendingAction) {
        savedStateHandle[KEY_PENDING_ACTION] = action.name
        send(CourtDetailEffect.OpenLogin)
    }

    fun onFavoriteClicked() {
        if (sessionManager.isGuest.value) {
            send(CourtDetailEffect.ShowLoginPrompt(PendingAction.FAVORITE))
            return
        }
        if (latestVenue == null) return
        viewModelScope.launch { applyFavorite(latestIsFavorite != true) }
    }

    fun onChatClicked() {
        if (sessionManager.isGuest.value) {
            send(CourtDetailEffect.ShowLoginPrompt(PendingAction.CHAT))
            return
        }
        chat()
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

    private fun resolvePending() {
        val pending = savedStateHandle.get<String>(KEY_PENDING_ACTION)
            ?.let { name -> PendingAction.entries.firstOrNull { it.name == name } }
        val venue = latestVenue
        val decision = PendingActionResolver.resolve(
            state = sessionManager.sessionState.value,
            pending = pending,
            ownerId = venue?.ownerId,
            currentUid = sessionManager.currentUid(),
            screenVisible = screenVisible,
        )
        when (decision) {
            PendingDecision.Wait -> Unit
            PendingDecision.Cancel -> clearPending()
            PendingDecision.RejectOwnVenue -> {
                clearPending()
                send(CourtDetailEffect.Snackbar(CourtDetailMessage.OWN_VENUE))
            }
            is PendingDecision.Run -> {
                // Chưa có venue (tạo lại sau process death): chờ [onVenue] gọi lại để còn kiểm tra chủ cơ sở.
                if (venue == null) return
                clearPending()
                when (decision.action) {
                    PendingAction.BOOK -> viewModelScope.launch { book() }
                    PendingAction.FAVORITE -> viewModelScope.launch { applyFavorite(true) }
                    PendingAction.CHAT -> chat()
                }
            }
        }
    }

    private fun clearPending() {
        savedStateHandle.remove<String>(KEY_PENDING_ACTION)
    }

    private fun chat() {
        val venue = latestVenue ?: return
        if (venue.ownerId == sessionManager.currentUid()) {
            send(CourtDetailEffect.Snackbar(CourtDetailMessage.OWN_VENUE))
            return
        }
        if (currentAvailability() != VenueAvailability.ACTIVE) return
        send(CourtDetailEffect.OpenChat(venueId))
    }

    /** Cơ sở tạm ngừng thì chỉ được bỏ yêu thích, không thêm mới (FR-021, US6-2). */
    private fun canFavorite(target: Boolean): Boolean =
        !target || currentAvailability() == VenueAvailability.ACTIVE

    /** Lạc quan (FR-021 của 070): trái tim đổi ngay, ghi lỗi thì trả về trạng thái cũ và báo snackbar. */
    private suspend fun applyFavorite(target: Boolean) {
        if (!canFavorite(target)) return
        favoriteOverride.value = target
        send(CourtDetailEffect.Snackbar(if (target) CourtDetailMessage.FAVORITE_ADDED else CourtDetailMessage.FAVORITE_REMOVED))
        val result = favoriteGateway.setFavorite(venueSummary(), target)
        favoriteOverride.value = null
        if (result.isFailure) send(CourtDetailEffect.Snackbar(CourtDetailMessage.FAVORITE_FAILED))
    }

    /** Ảnh chụp nhanh lưu kèm yêu thích (070 R12); cơ sở đã xóa thì chỉ còn `venueId` để bỏ yêu thích. */
    private fun venueSummary(): VenueSummary = latestVenue
        ?.let { VenueSummary(venueId = it.id, name = it.name, photoPath = it.coverPhotoPath, address = it.address) }
        ?: VenueSummary(venueId = venueId, name = "", photoPath = null, address = "")

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
            if (screenVisible) resolvePending()
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
