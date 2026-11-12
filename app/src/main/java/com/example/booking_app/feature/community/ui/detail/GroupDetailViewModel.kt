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
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.JoinGroup
import com.example.booking_app.feature.community.domain.usecase.LeaveGroup
import com.example.booking_app.feature.community.ui.common.DistrictNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Nút tham gia trên trang nhóm (T038). */
enum class JoinAction {
    /** Ẩn: đã là thành viên hoặc nhóm bị khóa. */
    NONE,

    /** "Tham gia" (nhóm công khai). */
    JOIN,

    /** "Xin tham gia" (nhóm riêng tư). */
    REQUEST,

    /** "Đang chờ duyệt" kèm "Hủy yêu cầu". */
    PENDING,
}

sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState

    /** Nhóm không có, đã `DELETED` hoặc bị Rules từ chối: "Nhóm không còn tồn tại", không phải lỗi chung. */
    data object NotFound : GroupDetailUiState

    data class Content(
        val group: Group,
        val membership: GroupMembership,
        val permissions: GroupPermissions,
        val districtName: String? = null,
        /** Đang gửi tham gia/hủy/rời nhóm: khóa các nút để không gửi hai lần. */
        val busy: Boolean = false,
    ) : GroupDetailUiState {
        val canEdit: Boolean get() = permissions.canEdit

        /**
         * Chế độ nhóm quyết định nhãn nút; việc người từng bị mời ra phải xin duyệt do server quyết định (kết quả
         * `REQUESTED`), app không biết trước.
         */
        val joinAction: JoinAction
            get() = when {
                membership.isMember || group.isLocked -> JoinAction.NONE
                membership.pending -> JoinAction.PENDING
                group.visibility == GroupVisibility.PUBLIC -> JoinAction.JOIN
                else -> JoinAction.REQUEST
            }

        val canLeave: Boolean get() = permissions.canLeave

        /** Nút "Yêu cầu tham gia (N)": chủ nhóm và quản trị viên. */
        val canReviewRequests: Boolean get() = permissions.canModerate
        val pendingRequestCount: Int get() = group.pendingRequestCount
    }

    data class Error(val cause: CommunityError) : GroupDetailUiState
}

sealed interface GroupDetailEvent {
    /** Khách bấm tham gia: mở đăng nhập rồi quay lại [returnTo]. */
    data class RequireLogin(val returnTo: String) : GroupDetailEvent

    data class JoinOutcome(val result: JoinResult) : GroupDetailEvent
    data object RequestCancelled : GroupDetailEvent
    data object Left : GroupDetailEvent

    /** Chủ nhóm rời khi chưa chuyển quyền: mở chọn người nhận quyền (US5). */
    data class OpenTransferOwner(val groupId: String) : GroupDetailEvent

    /** Chủ nhóm là thành viên duy nhất: gợi ý xóa nhóm. */
    data object SuggestDeleteGroup : GroupDetailEvent

    data class ShowError(val error: CommunityError) : GroupDetailEvent
}

/**
 * T029 + T038: trang nhóm. Nhóm và quan hệ thành viên nghe realtime. Đọc quan hệ lỗi thì coi như chưa là thành viên,
 * riêng chủ nhóm vẫn nhận ra qua `ownerId` để có nút "Sửa nhóm". Tham gia, hủy yêu cầu, rời nhóm gửi `requestId` giữ
 * trong [SavedStateHandle]: gửi lại sau mất mạng/bận dùng cùng mã, server đã trả lời thì đổi mã.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val session: SessionManager,
    private val joinGroup: JoinGroup,
    private val leaveGroup: LeaveGroup,
    private val districtNames: DistrictNames,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }

    private val _state = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private val _events = Channel<GroupDetailEvent>(Channel.BUFFERED)
    val events: Flow<GroupDetailEvent> = _events.receiveAsFlow()

    private val busy = MutableStateFlow(false)
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
            val names = districtNames.byId()
            val membership = groups.observeMembership(groupId).catch { emit(GroupMembership.NONE) }
            combine(groups.observeGroup(groupId), membership, busy) { group, member, isBusy ->
                if (group == null) {
                    GroupDetailUiState.NotFound
                } else {
                    val role = member.role ?: GroupRole.OWNER.takeIf { group.ownerId == uid }
                    GroupDetailUiState.Content(
                        group, member, GroupPermissions(uid, role, group.status), names[group.districtId], isBusy,
                    )
                }
            }
                .catch {
                    val error = it.toCommunityError()
                    // Rules từ chối đọc (nhóm bị xóa/không còn) là "Nhóm không còn tồn tại", không phải lỗi chung.
                    _state.value = if (error is CommunityError.Forbidden || error is CommunityError.GroupNotFound) {
                        GroupDetailUiState.NotFound
                    } else {
                        GroupDetailUiState.Error(error)
                    }
                }
                .collect { _state.value = it }
        }
    }

    fun onJoinClicked() {
        if (session.currentUid() == null) {
            _events.trySend(GroupDetailEvent.RequireLogin(deepLink()))
            return
        }
        act(KEY_JOIN_ID, { id -> joinGroup(groupId, id) }) { _events.trySend(GroupDetailEvent.JoinOutcome(it)) }
    }

    fun onCancelRequestClicked() =
        act(KEY_CANCEL_ID, { id -> joinGroup.cancel(groupId, id) }) { _events.trySend(GroupDetailEvent.RequestCancelled) }

    /** Gọi sau khi người dùng xác nhận trong hộp thoại "Rời nhóm?". */
    fun onLeaveConfirmed() = act(
        KEY_LEAVE_ID,
        { id -> leaveGroup(groupId, id) },
        onError = { error ->
            if (error is CommunityError.OwnerMustTransfer) {
                _events.trySend(
                    if (error.soleMember) GroupDetailEvent.SuggestDeleteGroup else GroupDetailEvent.OpenTransferOwner(groupId),
                )
                true
            } else {
                false
            }
        },
    ) { _events.trySend(GroupDetailEvent.Left) }

    /**
     * Chạy một thao tác ghi. Đang bận thì bỏ qua. [onError] trả `true` nếu đã tự xử lý lỗi. `requestId` ở [key] giữ
     * khi lỗi chưa có phản hồi từ server (mạng, bận), đổi mới khi server đã trả lời (thành công hoặc lỗi nghiệp vụ).
     */
    private fun <T> act(
        key: String,
        call: suspend (String) -> Result<T>,
        onError: (CommunityError) -> Boolean = { false },
        onSuccess: (T) -> Unit,
    ) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val requestId = savedStateHandle.get<String>(key) ?: UUID.randomUUID().toString().also { savedStateHandle[key] = it }
                call(requestId).fold(
                    onSuccess = {
                        savedStateHandle[key] = null
                        onSuccess(it)
                    },
                    onFailure = {
                        val error = it.toCommunityError()
                        if (error !is CommunityError.Network && error !is CommunityError.Busy) savedStateHandle[key] = null
                        if (!onError(error)) _events.trySend(GroupDetailEvent.ShowError(error))
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                busy.value = false
            }
        }
    }

    private fun deepLink() = "smashnow://community/group/$groupId"

    companion object {
        const val ARG_GROUP_ID = "groupId"
        private const val KEY_JOIN_ID = "join_request_id"
        private const val KEY_CANCEL_ID = "cancel_request_id"
        private const val KEY_LEAVE_ID = "leave_request_id"
    }
}
