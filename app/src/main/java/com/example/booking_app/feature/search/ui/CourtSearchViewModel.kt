package com.example.booking_app.feature.search.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.catalog.Amenity
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.parseFunctionErrorCode
import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.search.domain.AiCourtFilter
import com.example.booking_app.feature.search.domain.AiCourtParse
import com.example.booking_app.feature.search.domain.AiQueryHistory
import com.example.booking_app.feature.search.domain.AiSearchUsage
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.CourtSearchSort
import com.example.booking_app.feature.search.domain.ReviewClaim
import com.example.booking_app.feature.search.domain.ReviewPreference
import com.example.booking_app.feature.search.domain.ReviewRecommendation
import com.example.booking_app.feature.search.domain.ReviewSource
import com.example.booking_app.feature.search.domain.Relaxation
import com.example.booking_app.feature.search.domain.RelaxationPlanner
import com.example.booking_app.feature.search.domain.SearchHistory
import com.example.booking_app.feature.search.domain.SearchHistoryEntry
import com.example.booking_app.feature.search.domain.SearchHistoryRepository
import com.example.booking_app.feature.search.domain.VoiceError
import com.example.booking_app.feature.search.domain.VoiceEvent
import com.example.booking_app.feature.search.domain.VoiceInput
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.domain.model.LatLng
import com.example.booking_app.feature.search.domain.map.CourtMap
import com.example.booking_app.feature.search.domain.map.MapViewport
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Giới hạn ký tự của câu hỏi AI (khớp `counterMaxLength` của ô nhập). */
private const val AI_INPUT_MAX = 200

enum class SearchNotice { MAP_DISTRICT_DROPPED, AI_ERROR, QUOTA, LOCKED, SOURCE_UNAVAILABLE, INVALID_WINDOW, INVALID_DATE, DROPPED_FILTERS, OFF_TOPIC, LOCATION_UNAVAILABLE, HISTORY_DATE_PAST, VOICE_NO_MATCH, VOICE_NETWORK }

data class CourtSearchUiState(
    val criteria: CourtSearchCriteria = CourtSearchCriteria(),
    val districts: List<District> = emptyList(),
    val amenities: List<Amenity> = emptyList(),
    val catalogReady: Boolean = false,
    val venues: List<HomeVenue> = emptyList(),
    val shownCount: Int = 20,
    val loading: Boolean = false,
    val complete: Boolean = true,
    val failed: Boolean = false,
    val capped: Boolean = false,
    val guest: Boolean = true,
    val aiInput: String = "",
    /** Ô AI vừa được xóa chữ trong khi bộ lọc vẫn đang áp dụng: hiện gợi ý, không tự xóa bộ lọc. */
    val filtersKept: Boolean = false,
    val aiHistory: List<String> = emptyList(),
    /** Lịch sử tìm kiếm trên Firestore (spec 020 US4), mới nhất trước; khách luôn rỗng. */
    val history: List<SearchHistoryEntry> = emptyList(),
    /** Micro ở ô AI (spec 022 US4): ẩn khi máy không có dịch vụ nhận dạng. */
    val voiceAvailable: Boolean = false,
    val listening: Boolean = false,
    /** Người dùng đã từ chối quyền micro: micro mờ, bấm thì gợi ý mở cài đặt. */
    val voiceDenied: Boolean = false,
    val aiBusy: Boolean = false,
    val reviewBusy: Boolean = false,
    val usage: AiSearchUsage? = null,
    val clarification: AiCourtParse? = null,
    val preference: ReviewPreference? = null,
    val recommendations: List<ReviewRecommendation> = emptyList(),
    /** Gợi ý nới điều kiện khi lần tìm có lọc ra 0 sân (spec 022 US6), tối đa 3, mỗi gợi ý có ≥ 1 sân. */
    val relaxations: List<Relaxation> = emptyList(),
    val notice: SearchNotice? = null,
)

/** Một đánh giá gốc: nguyên văn và chiều nhận định (`null` khi không rõ). */
data class ReviewSourceItem(val comment: String, val supports: Boolean?)

/** Lý do xin quyền vị trí: sắp xếp "Gần nhất" (020), "Vị trí của tôi" trên bản đồ, "Sân gần bạn" (021). */
enum class LocationReason { SORT, MAP, NEARBY }

/** Lệnh đưa bản đồ tới [center] một lần; [id] tăng mỗi lần để lệnh giống nhau vẫn được thực hiện lại. */
data class MapFocus(val center: LatLng, val zoom: Double, val id: Int)

/**
 * Trạng thái chế độ bản đồ (spec 021), tách khỏi [CourtSearchUiState] để kéo bản đồ không vẽ lại cả màn hình.
 * Giữ trong ViewModel nên mở chi tiết sân rồi quay lại vẫn đúng vùng, mức phóng và sân đang chọn (FR-009).
 */
data class CourtMapState(
    val visible: Boolean = false,
    val viewport: MapViewport? = null,
    val selectedId: String? = null,
    val myLocation: LatLng? = null,
    /** Người dùng đã kéo/phóng sau lần tải gần nhất: hiện nút "Tìm ở khu vực này" khi có ích (FR-005). */
    val moved: Boolean = false,
    val focus: MapFocus? = null,
)

sealed interface CourtSearchEffect {
    data class Policy(val topic: String?) : CourtSearchEffect
    data class DropIn(val link: Uri) : CourtSearchEffect
    data class ChooseWindow(val date: LocalDate?) : CourtSearchEffect
    /** Xin quyền vị trí; [reason] quyết định làm gì khi có kết quả ([CourtSearchViewModel.locationResult]). */
    data class RequestLocation(val reason: LocationReason = LocationReason.SORT) : CourtSearchEffect
    /** Đánh giá gốc đã qua SOURCE (kiểm tra phiên bản), hiển thị trong bottom sheet. */
    data class Sources(val venueId: String, val items: List<ReviewSourceItem>) : CourtSearchEffect
    data class Explanation(val venueId: String, val claims: List<ReviewClaim>) : CourtSearchEffect
}

@HiltViewModel
class CourtSearchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: CourtSearchRepository,
    private val ai: CourtAiApi,
    private val catalog: CatalogRepository,
    private val location: LocationProvider,
    private val session: SessionManager,
    private val clock: AppClock,
    private val history: AiQueryHistory,
    private val searchHistory: SearchHistoryRepository,
    private val voice: VoiceInput,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CourtSearchUiState(criteria = restoredCriteria(), aiInput = savedState["aiInput"] ?: ""),
    )
    val state = _state.asStateFlow()
    private val _effects = Channel<CourtSearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var searchJob: Job? = null
    private var recommendJob: Job? = null
    private var parseJob: Job? = null
    private var reviewJob: Job? = null
    private var usageJob: Job? = null
    private var historyJob: Job? = null
    private var voiceJob: Job? = null
    private var relaxJob: Job? = null
    private val _voiceLevel = MutableStateFlow(0f)

    /** Âm lượng micro 0..1 khi đang nghe; tách khỏi [state] vì cập nhật vài chục lần mỗi giây. */
    val voiceLevel = _voiceLevel.asStateFlow()
    private var origin: GeoPoint? = null
    private var focusId = 0

    private val _map = MutableStateFlow(CourtMapState())
    val map: StateFlow<CourtMapState> = _map.asStateFlow()
    private var searchGeneration = 0

    init {
        _state.update { it.copy(voiceAvailable = voice.isAvailable()) }
        loadCatalog()
        viewModelScope.launch {
            session.sessionState.filter { it is SessionState.SignedIn || it is SessionState.SignedOut }
                .map { (it as? SessionState.SignedIn)?.uid }.distinctUntilChanged().collect { uid ->
                parseJob?.cancel()
                stopVoice()
                recommendJob?.cancel()
                reviewJob?.cancel()
                _state.update { it.copy(
                    guest = uid == null, usage = null, aiBusy = false, reviewBusy = false,
                    preference = null, recommendations = emptyList(), clarification = null,
                    aiInput = if (uid == null) "" else it.aiInput, notice = null,
                    aiHistory = uid?.let(history::read).orEmpty(),
                ) }
                if (uid == null) savedState["aiInput"] = ""
                observeHistory(uid)
                refreshUsage()
            }
        }
        search()
    }

    fun today(): LocalDate = now().toLocalDate()

    fun loadCatalog() = viewModelScope.launch {
        try {
            val districts = catalog.districts().filter { it.isActive }
            val amenities = catalog.amenities().filter { it.isActive }
            _state.update { it.copy(districts = districts, amenities = amenities, catalogReady = true) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The next filter interaction can retry loading the catalog.
        }
    }

    fun keyword(value: String) {
        if (value == state.value.criteria.keyword) return
        change(state.value.criteria.copy(keyword = value), preference = null, debounce = true)
    }

    fun district(id: String?) {
        change(state.value.criteria.copy(districtId = id))
        recordSearch()
    }

    fun amenities(ids: Set<String>) {
        change(state.value.criteria.copy(amenityIds = ids))
        recordSearch()
    }

    fun price(min: Long?, max: Long?) {
        change(state.value.criteria.copy(priceMin = min, priceMax = max))
        recordSearch()
    }

    fun applyFilters(districtId: String?, min: Long?, max: Long?, amenities: Set<String>, window: BookingSearchWindow?): Boolean {
        if (window != null && !window.isBookableAt(now())) return false
        change(state.value.criteria.copy(districtId = districtId, priceMin = min, priceMax = max,
            amenityIds = amenities, window = window))
        recordSearch()
        return true
    }

    fun window(window: BookingSearchWindow?): Boolean {
        if (window != null && !window.isBookableAt(now())) {
            _state.update { it.copy(notice = SearchNotice.INVALID_WINDOW) }
            return false
        }
        change(state.value.criteria.copy(window = window))
        recordSearch()
        return true
    }

    fun lockSeconds(usage: AiSearchUsage?): Long = usage?.lockedUntil?.let { value ->
        runCatching { ((Instant.parse(value).toEpochMilli() - clock.now() + 999) / 1000).coerceAtLeast(0) }
            .getOrDefault(0)
    } ?: 0

    fun sort(sort: CourtSearchSort) {
        if (sort != CourtSearchSort.NEAREST) {
            change(state.value.criteria.copy(sort = sort))
            recordSearch()
            return
        }
        if (!location.hasPermission()) {
            _effects.trySend(CourtSearchEffect.RequestLocation(LocationReason.SORT))
            return
        }
        viewModelScope.launch {
            val point = location.current()
            if (point == null) _state.update { it.copy(notice = SearchNotice.LOCATION_UNAVAILABLE) }
            else {
                origin = point
                change(state.value.criteria.copy(sort = sort))
                recordSearch()
            }
        }
    }

    fun clearFilters() {
        _state.update { it.copy(filtersKept = false) }
        val current = state.value.criteria
        change(CourtSearchCriteria(keyword = current.keyword, districtId = current.districtId, sort = current.sort), null)
        recordSearch()
    }

    fun removePreference() {
        recommendJob?.cancel()
        reviewJob?.cancel()
        _state.update { it.copy(preference = null, recommendations = emptyList(), reviewBusy = false) }
    }

    fun retry() = search()

    /** Bấm tìm trên bàn phím của ô tên sân: một "lần tìm" theo FR-021. */
    fun submitKeyword() {
        search()
        recordSearch()
    }

    /**
     * Lưu bộ điều kiện hiện tại vào lịch sử (FR-021): chỉ khi đã đăng nhập và có từ khóa hợp lệ
     * hoặc ít nhất một bộ lọc. Lỗi ghi (mất mạng, Rules) không ảnh hưởng việc tìm.
     */
    fun recordSearch() {
        val uid = session.currentUid() ?: return
        val criteria = state.value.criteria
        if (!SearchHistory.worthSaving(criteria)) return
        val current = state.value.history
        val prune = current.size >= SearchHistory.LIMIT && current.none { it.id == SearchHistory.idOf(criteria) }
        viewModelScope.launch {
            try {
                searchHistory.record(uid, criteria, prune)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // History is best effort; the search itself already ran.
            }
        }
    }

    /**
     * Chạy lại một mục lịch sử (FR-023, US4-3): khung giờ đã qua bị bỏ kèm thông báo "Ngày đã qua";
     * quận/tiện ích không còn trong danh mục bị bỏ kèm thông báo chung. Mục được đưa lên đầu.
     */
    fun applyHistory(entry: SearchHistoryEntry) {
        val current = state.value
        var criteria = entry.criteria
        var notice: SearchNotice? = null
        val window = criteria.window
        if (window != null && !window.isBookableAt(now())) {
            criteria = criteria.copy(window = null)
            notice = SearchNotice.HISTORY_DATE_PAST
        }
        if (current.catalogReady) {
            val district = criteria.districtId?.takeIf { id -> current.districts.any { it.id == id } }
            val amenities = criteria.amenityIds.filter { id -> current.amenities.any { it.id == id } }.toSet()
            if (district != criteria.districtId || amenities != criteria.amenityIds) {
                criteria = criteria.copy(districtId = district, amenityIds = amenities)
                notice = notice ?: SearchNotice.DROPPED_FILTERS
            }
        }
        // Vị trí không được lưu (FR-014): thiếu vị trí hiện tại thì sắp xếp theo đánh giá.
        if (criteria.sort == CourtSearchSort.NEAREST && origin == null) {
            criteria = criteria.copy(sort = CourtSearchSort.RATING)
        }
        change(criteria, preference = null)
        notice?.let { value -> _state.update { it.copy(notice = value) } }
        recordSearch()
    }

    fun deleteHistory(id: String) {
        val uid = session.currentUid() ?: return
        _state.update { it.copy(history = it.history.filterNot { entry -> entry.id == id }) }
        viewModelScope.launch {
            try {
                searchHistory.delete(uid, id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The listener restores the entry if the delete did not reach the server.
            }
        }
    }

    fun clearHistory() {
        val uid = session.currentUid() ?: return
        val ids = state.value.history.map { it.id }
        _state.update { it.copy(history = emptyList()) }
        viewModelScope.launch {
            try {
                searchHistory.clear(uid, ids)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The listener restores the entries if the delete did not reach the server.
            }
        }
    }

    private fun observeHistory(uid: String?) {
        historyJob?.cancel()
        _state.update { it.copy(history = emptyList()) }
        if (uid == null) return
        historyJob = viewModelScope.launch {
            try {
                searchHistory.observe(uid).collect { entries ->
                    if (session.currentUid() == uid) _state.update { it.copy(history = entries) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Without history the search screen keeps working.
            }
        }
    }

    fun loadMore() {
        _state.update { if (it.shownCount >= it.venues.size) it else it.copy(shownCount = it.shownCount + 20) }
    }

    fun input(value: String) {
        if (session.currentUid() == null || value == state.value.aiInput) return
        savedState["aiInput"] = value
        _state.update {
            it.copy(
                aiInput = value, clarification = null, notice = null,
                filtersKept = value.isBlank() && it.criteria.hasFilters(),
            )
        }
    }

    /** Quay lại màn hình (có thể từ Cài đặt): quyền đã bật thì bỏ trạng thái micro mờ. */
    fun voicePermissionChecked(granted: Boolean) {
        if (granted && state.value.voiceDenied) _state.update { it.copy(voiceDenied = false) }
    }

    /** Kết quả xin quyền micro; `true` thì bắt đầu nghe luôn. */
    fun voicePermission(granted: Boolean) {
        _state.update { it.copy(voiceDenied = !granted) }
        if (granted) startVoice()
    }

    /**
     * Nghe một câu và điền vào ô AI (spec 022 US4). Không tự gửi nên không tốn lượt AI.
     * Bấm micro lần nữa thì dừng. Lỗi nhận dạng giữ lại chữ cũ và hiện gợi ý gõ tay.
     */
    fun toggleVoice() {
        if (state.value.listening) stopVoice() else startVoice()
    }

    fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
        _voiceLevel.value = 0f
        _state.update { it.copy(listening = false) }
    }

    private fun startVoice() {
        if (session.currentUid() == null || !state.value.voiceAvailable || state.value.aiBusy) return
        voiceJob?.cancel()
        val before = state.value.aiInput
        _state.update { it.copy(listening = true, notice = null, clarification = null) }
        voiceJob = viewModelScope.launch {
            try {
                voice.listen().collect { event ->
                    when (event) {
                        VoiceEvent.Ready -> Unit
                        is VoiceEvent.Level -> _voiceLevel.value = event.value
                        is VoiceEvent.Partial -> input(event.text.take(AI_INPUT_MAX))
                        is VoiceEvent.Final -> input(event.text.take(AI_INPUT_MAX))
                        is VoiceEvent.Error -> {
                            input(before)
                            _state.update { it.copy(notice = if (event.kind == VoiceError.NETWORK) {
                                SearchNotice.VOICE_NETWORK
                            } else SearchNotice.VOICE_NO_MATCH) }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                input(before)
                _state.update { it.copy(notice = SearchNotice.VOICE_NO_MATCH) }
            } finally {
                _voiceLevel.value = 0f
                _state.update { it.copy(listening = false) }
            }
        }
    }

    /** Xóa một câu khỏi lịch sử câu hỏi AI trên máy. */
    fun removeHistory(query: String) {
        val uid = session.currentUid() ?: return
        history.remove(uid, query)
        _state.update { it.copy(aiHistory = history.read(uid)) }
    }

    fun submitAi() {
        val uid = session.currentUid() ?: return
        val current = state.value
        val query = current.aiInput.trim()
        if (current.aiBusy || current.usage?.remaining == 0 || lockSeconds(current.usage) > 0 ||
            query.isEmpty() || query.codePointCount(0, query.length) > AI_INPUT_MAX) return
        val generation = searchGeneration
        _state.update { it.copy(aiBusy = true, clarification = null, notice = null, filtersKept = false) }
        parseJob = viewModelScope.launch {
            try {
                val response = ai.parse(query)
                if (session.currentUid() != uid) return@launch
                usageJob?.cancel()
                if (response.result.intent != "off_topic") history.add(uid, query)
                _state.update { it.copy(usage = response.usage, aiHistory = history.read(uid)) }
                if (generation != searchGeneration) return@launch
                when (response.result.intent) {
                    "faq" -> _effects.send(CourtSearchEffect.Policy(response.result.faqTopic))
                    "off_topic" -> _state.update { it.copy(notice = SearchNotice.OFF_TOPIC) }
                    "unclear" -> {
                        if ("WINDOW_TOO_SHORT" in response.result.dropped && response.result.filter?.type != "DROP_IN") {
                            applyAi(response.result)
                            _state.update { it.copy(notice = SearchNotice.INVALID_WINDOW) }
                            _effects.send(CourtSearchEffect.ChooseWindow(BookingSearchWindow.parseDate(response.result.filter?.date)))
                        } else _state.update { it.copy(
                            clarification = response.result,
                            notice = if ("INVALID_DATE" in response.result.dropped) SearchNotice.INVALID_DATE else null,
                        ) }
                    }
                    else -> applyAi(response.result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session.currentUid() == uid) {
                    if (generation == searchGeneration) _state.update { it.copy(notice = aiNotice(error)) }
                    refreshUsage()
                }
            } finally {
                if (session.currentUid() == uid) _state.update { it.copy(aiBusy = false) }
            }
        }
    }

    fun clarificationDates(result: AiCourtParse): List<LocalDate> {
        val filter = result.filter
        val choices = filter?.dateChoices.orEmpty().mapNotNull(BookingSearchWindow::parseDate)
        if (choices.isNotEmpty()) return choices
        BookingSearchWindow.parseDate(filter?.date)?.let { return listOf(it) }
        val today = today()
        val saturday = today.plusDays(((6 - today.dayOfWeek.value + 7) % 7).toLong())
        return (listOf(today, today.plusDays(1), saturday)).distinct().filter { it != today || eveningWindow(it) != null }
    }

    fun clarify(date: LocalDate) {
        val result = state.value.clarification ?: return
        _state.update { it.copy(notice = null) }
        val filter = result.filter ?: AiCourtFilter()
        val dateKey = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val requested = BookingSearchWindow.parse(dateKey, filter.start, filter.end)?.takeIf { it.isBookableAt(now()) }
        val window = requested ?: eveningWindow(date)
        if (window == null && filter.type != "DROP_IN") {
            useManualClarification()
            return
        }
        applyAi(result.copy(filter = filter.copy(
            date = dateKey, dateChoices = emptyList(), start = window?.start?.toHhmm(), end = window?.end?.toHhmm(),
            partOfDay = filter.partOfDay ?: "EVENING",
        )))
    }

    fun useManualClarification() {
        val result = state.value.clarification ?: return
        _state.update { it.copy(notice = null) }
        if (result.filter?.type == "DROP_IN") {
            applyAi(result)
            return
        }
        applyAi(result.copy(filter = result.filter?.copy(date = null, start = null, end = null)))
        _effects.trySend(CourtSearchEffect.ChooseWindow(BookingSearchWindow.parseDate(result.filter?.date)))
    }

    fun explain(venueId: String) {
        val current = state.value
        val preference = current.preference ?: return
        val recommendation = current.recommendations.firstOrNull { it.venueId == venueId } ?: return
        runReviewAction(venueId) {
            val explanation = ai.explain(venueId, preference, recommendation.sources)
            CourtSearchEffect.Explanation(venueId, explanation.claims) to explanation.usage
        }
    }

    /** Mở một nguồn, ví dụ từ một dòng trong "Vì sao phù hợp?". */
    fun source(venueId: String, source: ReviewSource, supports: Boolean? = null) {
        runReviewAction(venueId) {
            CourtSearchEffect.Sources(venueId, listOf(ReviewSourceItem(ai.source(venueId, source), supports))) to null
        }
    }

    /** Mở mọi nguồn của một đề xuất (tối đa 3). Nguồn nào hết hiệu lực thì cả đề xuất bị bỏ như trước. */
    fun sources(venueId: String) {
        val recommendation = state.value.recommendations.firstOrNull { it.venueId == venueId } ?: return
        runReviewAction(venueId) {
            val items = recommendation.sources.map { source ->
                ReviewSourceItem(
                    ai.source(venueId, source),
                    recommendation.quotes.firstOrNull { it.reviewId == source.reviewId }?.supports,
                )
            }
            CourtSearchEffect.Sources(venueId, items) to null
        }
    }

    fun refreshUsage() {
        usageJob?.cancel()
        val uid = session.currentUid() ?: return
        usageJob = viewModelScope.launch {
            try {
                val usage = ai.usage()
                if (session.currentUid() == uid) _state.update { it.copy(usage = usage) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A failed usage read leaves the manual search available.
            }
        }
    }

    private fun runReviewAction(
        venueId: String,
        request: suspend () -> Pair<CourtSearchEffect, AiSearchUsage?>,
    ) {
        val uid = session.currentUid() ?: return
        if (state.value.reviewBusy || state.value.recommendations.none { it.venueId == venueId }) return
        val generation = searchGeneration
        _state.update { it.copy(reviewBusy = true, notice = null) }
        reviewJob = viewModelScope.launch {
            try {
                val (effect, usage) = request()
                if (session.currentUid() != uid || generation != searchGeneration || state.value.preference == null) return@launch
                if (usage != null) {
                    usageJob?.cancel()
                    _state.update { it.copy(usage = usage) }
                }
                _effects.send(effect)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == searchGeneration && session.currentUid() == uid) {
                    val notice = aiNotice(error)
                    _state.update { it.copy(notice = notice, recommendations =
                        if (notice == SearchNotice.SOURCE_UNAVAILABLE) it.recommendations.filterNot { item -> item.venueId == venueId }
                        else it.recommendations) }
                    refreshUsage()
                }
            } finally {
                if (generation == searchGeneration && session.currentUid() == uid) _state.update { it.copy(reviewBusy = false) }
            }
        }
    }

    private fun applyAi(result: AiCourtParse) {
        val filter = result.filter ?: AiCourtFilter()
        if (filter.type == "DROP_IN") {
            _state.update { it.copy(clarification = null, notice = if (result.dropped.isEmpty()) null else SearchNotice.DROPPED_FILTERS) }
            _effects.trySend(CourtSearchEffect.DropIn(dropInLink(filter)))
            return
        }
        val requestedSort = CourtSearchSort.entries.firstOrNull { it.name == filter.sort } ?: CourtSearchSort.RATING
        val current = state.value
        val district = filter.districtId?.takeIf { id -> !current.catalogReady || current.districts.any { it.id == id } }
        val amenities = filter.amenityIds.filter { id ->
            !current.catalogReady || current.amenities.any { it.id == id }
        }.toSet()
        val requestedWindow = BookingSearchWindow.parse(filter.date, filter.start, filter.end)
        val window = requestedWindow?.takeIf { it.isBookableAt(now()) }
        val dropped = result.dropped.isNotEmpty() || district != filter.districtId ||
            amenities.size != filter.amenityIds.size || (requestedWindow != null && window == null)
        val criteria = CourtSearchCriteria(
            keyword = filter.keyword.orEmpty(),
            districtId = district,
            priceMin = filter.priceMin,
            priceMax = filter.priceMax,
            amenityIds = amenities,
            window = window,
            sort = if (requestedSort == CourtSearchSort.NEAREST && origin == null) CourtSearchSort.RATING else requestedSort,
        )
        change(criteria, ReviewPreference.entries.firstOrNull { it.name == result.preference })
        // AI-filled filters are saved like a manual search (spec 020 US4), not the question itself.
        recordSearch()
        if (dropped) _state.update { it.copy(notice = if ("INVALID_DATE" in result.dropped) SearchNotice.INVALID_DATE else SearchNotice.DROPPED_FILTERS) }
        if (requestedSort == CourtSearchSort.NEAREST && origin == null) sort(requestedSort)
    }

    private fun change(
        criteria: CourtSearchCriteria,
        preference: ReviewPreference? = state.value.preference,
        debounce: Boolean = false,
    ) {
        save(criteria)
        _state.update { it.copy(criteria = criteria, preference = preference, clarification = null, notice = null) }
        _map.update { it.copy(moved = false) }
        search(debounce)
    }

    private fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        recommendJob?.cancel()
        reviewJob?.cancel()
        relaxJob?.cancel()
        val generation = ++searchGeneration
        val criteria = state.value.criteria
        val preference = state.value.preference
        _state.update { it.copy(
            venues = emptyList(), shownCount = 20, loading = true, complete = false,
            failed = false, capped = false, recommendations = emptyList(), reviewBusy = false,
            relaxations = emptyList(),
        ) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            var requestedRecommendations = false
            try {
                repository.search(criteria, origin).collect { page ->
                    if (generation != searchGeneration) return@collect
                    _state.update { it.copy(
                        venues = page.venues, loading = page.venues.isEmpty() && !page.complete,
                        complete = page.complete, capped = page.capped,
                    ) }
                    if (!requestedRecommendations && preference != null && state.value.preference == preference &&
                        page.venues.isNotEmpty() && (page.venues.size >= 30 || page.complete)) {
                        requestedRecommendations = true
                        recommend(preference, page.venues.take(30).map { it.id }, generation)
                    }
                }
                val done = state.value
                if (generation == searchGeneration && done.complete && done.venues.isEmpty() && criteria.hasFilters()) {
                    suggestRelaxations(criteria, generation)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == searchGeneration) _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** Bấm một gợi ý nới điều kiện: áp dụng như đổi bộ lọc bằng tay (chip đổi theo, sửa tiếp được). */
    /** Chuyển Danh sách / Bản đồ (FR-001). Hai chế độ dùng chung tiêu chí và kết quả nên không mất bộ lọc. */
    fun showMap(visible: Boolean) {
        _map.update { it.copy(visible = visible, selectedId = if (visible) it.selectedId else null) }
    }

    /** Bản đồ báo vùng đang xem; [byUser] khi người dùng kéo/phóng (không tự tải lại, FR-004). */
    fun mapMoved(viewport: MapViewport, byUser: Boolean) {
        _map.update { it.copy(viewport = viewport, moved = it.moved || byUser) }
    }

    /** Chọn marker hoặc vuốt thẻ (FR-007, FR-008); `null` khi chạm vùng trống (FR-010). */
    fun selectVenue(id: String?) {
        _map.update { it.copy(selectedId = id) }
    }

    /**
     * "Tìm ở khu vực này" (FR-005): kết quả đã gồm mọi sân khớp bộ lọc nên chỉ cần bỏ lọc quận (kèm thông báo) để
     * bản đồ hiện đủ sân trong vùng đang xem.
     */
    fun searchThisArea() {
        val criteria = state.value.criteria
        if (criteria.districtId == null) {
            _map.update { it.copy(moved = false) }
            return
        }
        change(criteria.copy(districtId = null))
        _state.update { it.copy(notice = SearchNotice.MAP_DISTRICT_DROPPED) }
        recordSearch()
    }

    /** "Vị trí của tôi" (FR-011, FR-012): chỉ xin quyền khi người dùng bấm. */
    fun locateOnMap() {
        if (!location.hasPermission()) {
            _effects.trySend(CourtSearchEffect.RequestLocation(LocationReason.MAP))
            return
        }
        viewModelScope.launch {
            val point = location.current()
            if (point == null) {
                _state.update { it.copy(notice = SearchNotice.LOCATION_UNAVAILABLE) }
                return@launch
            }
            val here = LatLng(point.latitude, point.longitude)
            _map.update { it.copy(myLocation = here, focus = MapFocus(here, CourtMap.FOCUS_ZOOM, ++focusId)) }
            // Vị trí chỉ dùng trên máy để tính khoảng cách (FR-014, FR-015), không lưu vào tiêu chí hay lịch sử.
            if (origin != point) {
                origin = point
                search()
            }
        }
    }

    /** Kết quả hộp thoại xin quyền vị trí, theo lý do đã xin. */
    fun locationResult(reason: LocationReason, granted: Boolean) {
        when (reason) {
            LocationReason.SORT -> if (granted) sort(CourtSearchSort.NEAREST)
                else _state.update { it.copy(notice = SearchNotice.LOCATION_UNAVAILABLE) }
            LocationReason.MAP -> if (granted) locateOnMap()
                else _state.update { it.copy(notice = SearchNotice.LOCATION_UNAVAILABLE) }
            LocationReason.NEARBY -> Unit
        }
    }

    fun applyRelaxation(relaxation: Relaxation) {
        change(relaxation.criteria)
        recordSearch()
    }

    /**
     * Tính gợi ý nới điều kiện trong app (US6-2: không gọi AI, không trừ lượt). Lỗi đọc dữ liệu thì
     * không hiện gợi ý, màn hình giữ trạng thái rỗng thường (US6-4).
     */
    private fun suggestRelaxations(criteria: CourtSearchCriteria, generation: Int) {
        val probes = RelaxationPlanner.probes(criteria, now())
        if (probes.isEmpty()) return
        relaxJob = viewModelScope.launch {
            try {
                val relaxations = RelaxationPlanner.pick(probes, repository.variantPrices(probes.map { it.criteria }))
                if (generation == searchGeneration) _state.update { it.copy(relaxations = relaxations) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Suggestions are optional; the empty state stays as is.
            }
        }
    }

    private fun recommend(preference: ReviewPreference, ids: List<String>, generation: Int) {
        val uid = session.currentUid() ?: return
        recommendJob = viewModelScope.launch {
            try {
                val recommendations = ai.recommend(preference, ids)
                if (session.currentUid() == uid && generation == searchGeneration && state.value.preference == preference) {
                    _state.update { it.copy(recommendations = recommendations.filter { item -> item.venueId in ids }.take(3)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Availability and booking still use the main results.
            }
        }
    }

    private fun now() = Instant.ofEpochMilli(clock.now()).atZone(BookingWindow.ZONE)

    private fun eveningWindow(date: LocalDate): BookingSearchWindow? {
        val time = now()
        val start = if (date == time.toLocalDate()) {
            maxOf(18 * 60, (time.hour * 60 + time.minute) / 30 * 30 + 30)
        } else 18 * 60
        return BookingSearchWindow(date, TimeOfDay(start), TimeOfDay(22 * 60)).takeIf { it.isBookableAt(time) }
    }

    private fun dropInLink(filter: AiCourtFilter): Uri {
        val start = TimeOfDay.parse(filter.start)
        val part = filter.partOfDay ?: start?.hour?.let { hour ->
            when (hour) {
                in 5..10 -> "MORNING"
                in 11..12 -> "NOON"
                in 13..17 -> "AFTERNOON"
                in 18..22 -> "EVENING"
                else -> null
            }
        }
        return Uri.parse("smashnow://dropin/list").buildUpon().apply {
            filter.districtId?.let { appendQueryParameter("districtIds", it) }
            filter.date?.let { appendQueryParameter("date", it) }
            part?.let { appendQueryParameter("timeSlot", it) }
            filter.level?.let { appendQueryParameter("level", it) }
            filter.priceMax?.let { appendQueryParameter("maxPrice", it.toString()) }
        }.build()
    }

    private fun aiNotice(error: Exception): SearchNotice =
        when (parseFunctionErrorCode(((error as? AppError.Unknown)?.cause as? FunctionHttpException)?.body)) {
            "QUOTA_EXCEEDED" -> SearchNotice.QUOTA
            "AI_LOCKED" -> SearchNotice.LOCKED
            "SOURCE_UNAVAILABLE" -> SearchNotice.SOURCE_UNAVAILABLE
            else -> SearchNotice.AI_ERROR
        }

    private fun restoredCriteria(): CourtSearchCriteria = CourtSearchCriteria(
        keyword = savedState["keyword"] ?: "",
        districtId = savedState["districtId"],
        priceMin = savedState["priceMin"],
        priceMax = savedState["priceMax"],
        amenityIds = savedState.get<ArrayList<String>>("amenityIds").orEmpty().toSet(),
        window = BookingSearchWindow.parse(savedState["date"], savedState["startTime"], savedState["endTime"])
            ?.takeIf { it.isBookableAt(now()) },
        sort = CourtSearchSort.entries.firstOrNull { it.name == savedState.get<String>("sort") }
            ?.takeUnless { it == CourtSearchSort.NEAREST } ?: CourtSearchSort.RATING,
    )

    private fun save(criteria: CourtSearchCriteria) {
        savedState["keyword"] = criteria.keyword
        savedState["districtId"] = criteria.districtId
        savedState["priceMin"] = criteria.priceMin
        savedState["priceMax"] = criteria.priceMax
        savedState["amenityIds"] = ArrayList(criteria.amenityIds)
        savedState["date"] = criteria.window?.dateKey()
        savedState["startTime"] = criteria.window?.start?.toHhmm()
        savedState["endTime"] = criteria.window?.end?.toHhmm()
        savedState["sort"] = criteria.sort.name
    }
}
