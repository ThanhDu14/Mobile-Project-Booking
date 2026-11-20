package com.example.booking_app.feature.community.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupSearchKeywords
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.SearchGroups
import com.example.booking_app.feature.community.ui.common.DistrictNames
import com.example.booking_app.feature.community.ui.home.GroupCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface GroupSearchUiState {
    data object Loading : GroupSearchUiState

    /** [loadMoreFailed]: tải trang tiếp lỗi, danh sách giữ nguyên và cuộn/bấm lần nữa sẽ thử lại. */
    data class Content(
        val cards: List<GroupCard>,
        val canLoadMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
    ) : GroupSearchUiState

    /** [hasFilter]: có từ khóa hoặc khu vực; hiện "Bỏ bớt bộ lọc" (AC 2.6) cùng "Tạo nhóm của bạn". */
    data class Empty(val hasFilter: Boolean) : GroupSearchUiState

    data class Error(val cause: CommunityError) : GroupSearchUiState
}

/**
 * T037: tab "Khám phá". Từ khóa được chuẩn hóa (bỏ dấu, chữ thường) rồi debounce 300 ms; đổi khu vực tìm ngay. Trang
 * 20 nhóm, tải tiếp theo cursor. Server chỉ lọc theo một từ (từ dài nhất) và một quận, các từ còn lại lọc ở tầng data nên một
 * trang có thể trả ít hơn 20 mà vẫn còn trang sau ([Page.next] khác null): trang đầu rỗng sau lọc thì tự tải tiếp (tối đa
 * [MAX_EMPTY_PAGE_SKIPS] lần) trước khi báo rỗng. Bộ lọc giữ qua [SavedStateHandle].
 *
 * Khu vực điền sẵn từ `favoriteDistrictIds`, chỉ lấy quận đầu tiên vì tầng data chỉ lọc được một quận.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupSearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchGroups: SearchGroups,
    private val profileReader: UserProfileReader,
    private val districtNames: DistrictNames,
) : ViewModel() {

    private val _query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()

    private val _districtIds = MutableStateFlow(savedStateHandle.get<ArrayList<String>>(KEY_DISTRICTS)?.toList().orEmpty())
    val districtIds: StateFlow<List<String>> = _districtIds.asStateFlow()

    private val _state = MutableStateFlow<GroupSearchUiState>(GroupSearchUiState.Loading)
    val state: StateFlow<GroupSearchUiState> = _state.asStateFlow()

    /** Danh sách quận cho hộp chọn khu vực. */
    private val _districts = MutableStateFlow<List<District>>(emptyList())
    val districts: StateFlow<List<District>> = _districts.asStateFlow()

    private val retryTick = MutableStateFlow(0)
    private var names: Map<String, String> = emptyMap()
    private var loaded: List<GroupCard> = emptyList()
    private var cursor: PageCursor? = null
    private var currentQuery = ""
    private var currentDistricts: List<String> = emptyList()
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            if (!savedStateHandle.contains(KEY_DISTRICTS)) prefillDistricts()
            _districts.value = districtNames.active()
            names = districtNames.byId()
            combine(
                _query.map { GroupSearchKeywords.normalizeQuery(it) }
                    .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MS }
                    .distinctUntilChanged(),
                _districtIds,
                retryTick,
            ) { q, d, _ -> q to d }
                .collectLatest { (q, d) -> firstPage(q, d) }
        }
    }

    /** Hồ sơ không đọc được (khách, mạng) thì tìm trên mọi quận. */
    private suspend fun prefillDistricts() {
        val favorites = try {
            withTimeoutOrNull(PROFILE_TIMEOUT_MS) { profileReader.observeMyProfile().firstOrNull() }?.favoriteDistrictIds.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        setDistricts(favorites.filter { it.isNotBlank() }.take(1))
    }

    private fun setDistricts(ids: List<String>) {
        _districtIds.value = ids
        savedStateHandle[KEY_DISTRICTS] = ArrayList(ids)
    }

    fun onQueryChanged(text: String) {
        _query.value = text
        savedStateHandle[KEY_QUERY] = text
    }

    /** `null` là mọi khu vực. */
    fun onDistrictSelected(districtId: String?) = setDistricts(listOfNotNull(districtId))

    fun clearFilters() {
        onQueryChanged("")
        setDistricts(emptyList())
    }

    fun retry() {
        retryTick.value++
    }

    private suspend fun firstPage(query: String, districts: List<String>) {
        loadMoreJob?.cancel()
        currentQuery = query
        currentDistricts = districts
        loaded = emptyList()
        cursor = null
        _state.value = GroupSearchUiState.Loading
        var skips = 0
        while (true) {
            val page = searchGroups(query, districts, cursor).getOrElse {
                _state.value = GroupSearchUiState.Error(it.toCommunityError())
                return
            }
            append(page)
            if (loaded.isNotEmpty() || cursor == null || skips++ >= MAX_EMPTY_PAGE_SKIPS) break
        }
        _state.value = if (loaded.isEmpty() && cursor == null) {
            GroupSearchUiState.Empty(hasFilter = query.isNotEmpty() || districts.isNotEmpty())
        } else {
            content()
        }
    }

    /** Gọi khi cuộn gần cuối danh sách, hoặc khi người dùng bấm "Thử lại" ở chân danh sách sau lỗi. */
    fun loadMore() {
        val current = _state.value as? GroupSearchUiState.Content ?: return
        if (!current.canLoadMore || current.loadingMore) return
        _state.value = current.copy(loadingMore = true, loadMoreFailed = false)
        loadMoreJob = viewModelScope.launch {
            searchGroups(currentQuery, currentDistricts, cursor).fold(
                onSuccess = {
                    append(it)
                    _state.value = content()
                },
                onFailure = { _state.value = content(loadMoreFailed = true) },
            )
        }
    }

    private fun append(page: Page<Group>) {
        val known = loaded.mapTo(HashSet()) { it.group.id }
        loaded = loaded + page.items.filter { known.add(it.id) }
            .map { GroupCard(it, isOwner = false, districtName = names[it.districtId]) }
        cursor = page.next
    }

    private fun content(loadMoreFailed: Boolean = false) =
        GroupSearchUiState.Content(loaded, canLoadMore = cursor != null, loadMoreFailed = loadMoreFailed)

    private companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_DISTRICTS = "search_districts"
        const val DEBOUNCE_MS = 300L
        const val PROFILE_TIMEOUT_MS = 3_000L
        const val MAX_EMPTY_PAGE_SKIPS = 3
    }
}
