package com.example.booking_app.feature.community.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupPermissions
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.ManageMember
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Một dòng thành viên kèm menu thao tác theo `GroupPermissions` của người xem (T062). */
data class MemberItem(
    val member: GroupMember,
    val canMakeAdmin: Boolean,
    val canRemoveAdmin: Boolean,
    val canTransferOwner: Boolean,
    val canRemove: Boolean,
) {
    val hasActions: Boolean get() = canMakeAdmin || canRemoveAdmin || canTransferOwner || canRemove
}

sealed interface GroupMembersUiState {
    data object Loading : GroupMembersUiState
    data class Content(
        val items: List<MemberItem>,
        val permissions: GroupPermissions,
        val selectTransferTarget: Boolean,
        val busy: Boolean = false,
    ) : GroupMembersUiState
    data object Empty : GroupMembersUiState
    data class Error(val cause: CommunityError) : GroupMembersUiState
}

sealed interface GroupMembersEvent {
    data object RoleUpdated : GroupMembersEvent
    data object OwnerTransferred : GroupMembersEvent
    data object MemberRemoved : GroupMembersEvent
    data class ShowError(val error: CommunityError) : GroupMembersEvent
}

/**
 * T062 + T063: danh sách thành viên nhóm. Sắp Chủ nhóm → Quản trị viên → Thành viên theo `joinedAt` (AC 5.1);
 * menu thao tác theo [GroupPermissions]; hỗ trợ chế độ `selectTransferTarget` khi chủ nhóm muốn rời nhóm (AC 5.5).
 */
@HiltViewModel
class GroupMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val session: SessionManager,
    private val manageMember: ManageMember,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }
    val selectTransferTarget: Boolean = savedStateHandle.get<Boolean>(ARG_SELECT_TRANSFER) ?: false

    private val _state = MutableStateFlow<GroupMembersUiState>(GroupMembersUiState.Loading)
    val state: StateFlow<GroupMembersUiState> = _state.asStateFlow()

    private val _events = Channel<GroupMembersEvent>(Channel.BUFFERED)
    val events: Flow<GroupMembersEvent> = _events.receiveAsFlow()

    private val busy = MutableStateFlow(false)
    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        _state.value = GroupMembersUiState.Loading
        start()
    }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            val myUid = session.currentUid().orEmpty()
            val groupFlow = groups.observeGroup(groupId).catch { emit(null) }
            combine(groups.observeMembers(groupId), groupFlow, busy) { members, group, isBusy ->
                val sorted = members.sortedWith(MEMBER_ORDER)
                if (sorted.isEmpty()) {
                    GroupMembersUiState.Empty
                } else {
                    val myRole = sorted.firstOrNull { it.userId == myUid }?.role
                    val status = group?.status ?: GroupStatus.ACTIVE
                    val permissions = GroupPermissions(myUid, myRole, status)
                    val items = sorted.map { member ->
                        val isOther = member.userId != myUid
                        MemberItem(
                            member = member,
                            canMakeAdmin = permissions.canManageRoles && isOther && member.role == GroupRole.MEMBER,
                            canRemoveAdmin = permissions.canManageRoles && isOther && member.role == GroupRole.ADMIN,
                            canTransferOwner = permissions.canManageRoles && isOther,
                            canRemove = permissions.canRemove(member.userId, member.role),
                        )
                    }
                    GroupMembersUiState.Content(items, permissions, selectTransferTarget, isBusy)
                }
            }
                .catch { _state.value = GroupMembersUiState.Error(it.toCommunityError()) }
                .collect { _state.value = it }
        }
    }

    fun onMakeAdminConfirmed(userId: String) =
        act({ reqId -> manageMember.setRole(groupId, userId, GroupRole.ADMIN, reqId) }) {
            _events.trySend(GroupMembersEvent.RoleUpdated)
        }

    fun onRemoveAdminConfirmed(userId: String) =
        act({ reqId -> manageMember.setRole(groupId, userId, GroupRole.MEMBER, reqId) }) {
            _events.trySend(GroupMembersEvent.RoleUpdated)
        }

    fun onTransferOwnerConfirmed(userId: String) =
        act({ reqId -> manageMember.transferOwner(groupId, userId, reqId) }) {
            _events.trySend(GroupMembersEvent.OwnerTransferred)
        }

    fun onRemoveConfirmed(userId: String) =
        act({ reqId -> manageMember.remove(groupId, userId, reqId) }) {
            _events.trySend(GroupMembersEvent.MemberRemoved)
        }

    private fun act(call: suspend (String) -> Result<Unit>, onSuccess: () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                call(UUID.randomUUID().toString()).fold(
                    onSuccess = {
                        onSuccess()
                        start()
                    },
                    onFailure = { err ->
                        _events.trySend(GroupMembersEvent.ShowError(err.toCommunityError()))
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        const val ARG_SELECT_TRANSFER = "selectTransferTarget"

        private val MEMBER_ORDER: Comparator<GroupMember> = compareBy<GroupMember> { roleRank(it.role) }
            .thenBy { it.joinedAt ?: Instant.MAX }
            .thenBy { it.userId }

        private fun roleRank(role: GroupRole): Int = when (role) {
            GroupRole.OWNER -> 0
            GroupRole.ADMIN -> 1
            GroupRole.MEMBER -> 2
        }
    }
}
