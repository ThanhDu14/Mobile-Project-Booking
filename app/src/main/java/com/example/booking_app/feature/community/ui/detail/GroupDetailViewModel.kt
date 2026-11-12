package com.example.booking_app.feature.community.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupPermissions
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.toCommunityError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState

    /** Nhóm không có, đã `DELETED` hoặc bị Rules từ chối: "Nhóm không còn tồn tại", không phải lỗi chung. */
    data object NotFound : GroupDetailUiState

    data class Content(
        val group: Group,
        val membership: GroupMembership,
        val permissions: GroupPermissions,
    ) : GroupDetailUiState {
        val canEdit: Boolean get() = permissions.canEdit
    }

    data class Error(val cause: CommunityError) : GroupDetailUiState
}

/**
 * T029: khung trang nhóm (US1). Nhóm và quan hệ thành viên nghe realtime. Đọc quan hệ lỗi thì coi như chưa là thành
 * viên, riêng chủ nhóm vẫn nhận ra qua `ownerId` để có nút "Sửa nhóm". Phần tham gia (US2) và bảng tin (US3) thêm sau.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val session: SessionManager,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }

    private val _state = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        _state.value = GroupDetailUiState.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            val uid = session.currentUid().orEmpty()
            val membership = groups.observeMembership(groupId).catch { emit(GroupMembership.NONE) }
            combine(groups.observeGroup(groupId), membership) { group, member ->
                if (group == null) {
                    GroupDetailUiState.NotFound
                } else {
                    val role = member.role ?: GroupRole.OWNER.takeIf { group.ownerId == uid }
                    GroupDetailUiState.Content(group, member, GroupPermissions(uid, role, group.status))
                }
            }
                .catch { _state.value = GroupDetailUiState.Error(it.toCommunityError()) }
                .collect { _state.value = it }
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
    }
}
