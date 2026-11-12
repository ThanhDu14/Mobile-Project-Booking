package com.example.booking_app.feature.community.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.ObserveMyGroups
import com.example.booking_app.feature.community.ui.common.DistrictNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CommunityHomeTab { MY_GROUPS, DISCOVER }

/** Thẻ một nhóm trong "Nhóm của tôi"; [isOwner] suy ra từ `ownerId` (UID hiện tại), [isLocked] hiện nhãn "tạm khóa". */
data class GroupCard(val group: Group, val isOwner: Boolean, val districtName: String? = null) {
    val isLocked: Boolean get() = group.isLocked
}

sealed interface MyGroupsUiState {
    data object Loading : MyGroupsUiState
    data class Content(val cards: List<GroupCard>) : MyGroupsUiState
    data object Empty : MyGroupsUiState
    data class Error(val cause: CommunityError) : MyGroupsUiState
}

sealed interface CommunityHomeEvent {
    data object OpenCreateForm : CommunityHomeEvent
    data class OpenGroup(val groupId: String) : CommunityHomeEvent

    /** Khách bấm "Tạo nhóm". */
    data object RequireLogin : CommunityHomeEvent
}

/** T028: màn "Cộng đồng"; tab "Nhóm của tôi" realtime, tab "Khám phá" làm ở US2. */
@HiltViewModel
class CommunityHomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeMyGroups: ObserveMyGroups,
    private val session: SessionManager,
    private val districtNames: DistrictNames,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(
        savedStateHandle.get<String>(KEY_TAB)?.let { runCatching { CommunityHomeTab.valueOf(it) }.getOrNull() }
            ?: CommunityHomeTab.MY_GROUPS,
    )
    val selectedTab: StateFlow<CommunityHomeTab> = _selectedTab.asStateFlow()

    private val _myGroups = MutableStateFlow<MyGroupsUiState>(MyGroupsUiState.Loading)
    val myGroups: StateFlow<MyGroupsUiState> = _myGroups.asStateFlow()

    private val _events = Channel<CommunityHomeEvent>(Channel.BUFFERED)
    val events: Flow<CommunityHomeEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        _myGroups.value = MyGroupsUiState.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            val uid = session.currentUid()
            val names = districtNames.byId()
            observeMyGroups()
                .catch { _myGroups.value = MyGroupsUiState.Error(it.toCommunityError()) }
                .collect { groups ->
                    val cards = groups.filter { it.status != GroupStatus.DELETED }.map { GroupCard(it, isOwner = it.ownerId == uid, districtName = names[it.districtId]) }
                    _myGroups.value = if (cards.isEmpty()) MyGroupsUiState.Empty else MyGroupsUiState.Content(cards)
                }
        }
    }

    fun selectTab(tab: CommunityHomeTab) {
        _selectedTab.value = tab
        savedStateHandle[KEY_TAB] = tab.name
    }

    fun onCreateClicked() {
        _events.trySend(if (session.currentUid() == null) CommunityHomeEvent.RequireLogin else CommunityHomeEvent.OpenCreateForm)
    }

    fun onGroupClicked(groupId: String) {
        _events.trySend(CommunityHomeEvent.OpenGroup(groupId))
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
