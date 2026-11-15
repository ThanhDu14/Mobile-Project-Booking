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
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

enum class SearchNotice { AI_ERROR, QUOTA, LOCKED, SOURCE_UNAVAILABLE, INVALID_WINDOW, INVALID_DATE, DROPPED_FILTERS, OFF_TOPIC, LOCATION_UNAVAILABLE }

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
    val aiHistory: List<String> = emptyList(),
    val aiBusy: Boolean = false,
    val reviewBusy: Boolean = false,
    val usage: AiSearchUsage? = null,
    val clarification: AiCourtParse? = null,
    val preference: ReviewPreference? = null,
    val recommendations: List<ReviewRecommendation> = emptyList(),
    val notice: SearchNotice? = null,
)

sealed interface CourtSearchEffect {
    data class Policy(val topic: String?) : CourtSearchEffect
    data class DropIn(val link: Uri) : CourtSearchEffect
    data class ChooseWindow(val date: LocalDate?) : CourtSearchEffect
    data object RequestLocation : CourtSearchEffect
    data class Source(val comment: String) : CourtSearchEffect
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
    private var origin: GeoPoint? = null
    private var searchGeneration = 0

    init {
        loadCatalog()
        viewModelScope.launch {
            session.sessionState.filter { it is SessionState.SignedIn || it is SessionState.SignedOut }
                .map { (it as? SessionState.SignedIn)?.uid }.distinctUntilChanged().collect { uid ->
                parseJob?.cancel()
                recommendJob?.cancel()
                reviewJob?.cancel()
                _state.update { it.copy(
                    guest = uid == null, usage = null, aiBusy = false, reviewBusy = false,
                    preference = null, recommendations = emptyList(), clarification = null,
                    aiInput = if (uid == null) "" else it.aiInput, notice = null,
                    aiHistory = uid?.let(history::read).orEmpty(),
                ) }
                if (uid == null) savedState["aiInput"] = ""
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

    fun district(id: String?) = change(state.value.criteria.copy(districtId = id))

    fun amenities(ids: Set<String>) = change(state.value.criteria.copy(amenityIds = ids))

    fun price(min: Long?, max: Long?) = change(state.value.criteria.copy(priceMin = min, priceMax = max))

    fun applyFilters(districtId: String?, min: Long?, max: Long?, amenities: Set<String>, window: BookingSearchWindow?): Boolean {
        if (window != null && !window.isBookableAt(now())) return false
        change(state.value.criteria.copy(districtId = districtId, priceMin = min, priceMax = max,
            amenityIds = amenities, window = window))
        return true
    }

    fun window(window: BookingSearchWindow?): Boolean {
        if (window != null && !window.isBookableAt(now())) {
            _state.update { it.copy(notice = SearchNotice.INVALID_WINDOW) }
            return false
        }
        change(state.value.criteria.copy(window = window))
        return true
    }

    fun lockSeconds(usage: AiSearchUsage?): Long = usage?.lockedUntil?.let { value ->
        runCatching { ((Instant.parse(value).toEpochMilli() - clock.now() + 999) / 1000).coerceAtLeast(0) }
            .getOrDefault(0)
    } ?: 0

    fun sort(sort: CourtSearchSort) {
        if (sort != CourtSearchSort.NEAREST) {
            change(state.value.criteria.copy(sort = sort))
            return
        }
        if (!location.hasPermission()) {
            _effects.trySend(CourtSearchEffect.RequestLocation)
            return
        }
        viewModelScope.launch {
            val point = location.current()
            if (point == null) _state.update { it.copy(notice = SearchNotice.LOCATION_UNAVAILABLE) }
            else {
                origin = point
                change(state.value.criteria.copy(sort = sort))
            }
        }
    }

    fun clearFilters() {
        val current = state.value.criteria
        change(CourtSearchCriteria(keyword = current.keyword, districtId = current.districtId, sort = current.sort), null)
    }

    fun removePreference() {
        recommendJob?.cancel()
        reviewJob?.cancel()
        _state.update { it.copy(preference = null, recommendations = emptyList(), reviewBusy = false) }
    }

    fun retry() = search()

    fun loadMore() {
        _state.update { if (it.shownCount >= it.venues.size) it else it.copy(shownCount = it.shownCount + 20) }
    }

    fun input(value: String) {
        if (session.currentUid() == null || value == state.value.aiInput) return
        savedState["aiInput"] = value
        _state.update { it.copy(aiInput = value, clarification = null, notice = null) }
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
            query.isEmpty() || query.codePointCount(0, query.length) > 200) return
        val generation = searchGeneration
        _state.update { it.copy(aiBusy = true, clarification = null, notice = null) }
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

    fun source(venueId: String, source: ReviewSource) {
        runReviewAction(venueId) { CourtSearchEffect.Source(ai.source(venueId, source)) to null }
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
        search(debounce)
    }

    private fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        recommendJob?.cancel()
        reviewJob?.cancel()
        val generation = ++searchGeneration
        val criteria = state.value.criteria
        val preference = state.value.preference
        _state.update { it.copy(
            venues = emptyList(), shownCount = 20, loading = true, complete = false,
            failed = false, capped = false, recommendations = emptyList(), reviewBusy = false,
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
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == searchGeneration) _state.update { it.copy(loading = false, failed = true) }
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
