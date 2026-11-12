package com.example.booking_app.feature.community.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupInput
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.AvatarChange
import com.example.booking_app.feature.community.domain.usecase.CreateGroup
import com.example.booking_app.feature.community.domain.usecase.UpdateGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Lỗi của từng ô nhập (AC 1.2). */
enum class FieldError { REQUIRED, TOO_SHORT, TOO_LONG }

/** Nhóm đã tạo nhưng ảnh đại diện chưa tải được (AC 1.3). */
data class AvatarFailure(val groupId: String)

data class GroupFormState(
    val isEdit: Boolean = false,
    val name: String = "",
    val description: String = "",
    val districtId: String? = null,
    val visibility: GroupVisibility = GroupVisibility.PUBLIC,
    /** Ảnh vừa chọn (Uri nội dung); `null` là chưa chọn. */
    val avatarUri: String? = null,
    /** Ảnh hiện có của nhóm khi sửa (path trong `group-avatars`). */
    val existingAvatarPath: String? = null,
    val removeAvatar: Boolean = false,
    val districts: List<District> = emptyList(),
    val nameError: FieldError? = null,
    val descriptionError: FieldError? = null,
    val districtError: Boolean = false,
    val saving: Boolean = false,
    val avatarFailure: AvatarFailure? = null,
    /** Chế độ sửa: đang nạp dữ liệu nhóm. */
    val loading: Boolean = false,
    /** Chế độ sửa: không nạp được nhóm (`GroupNotFound` khi nhóm không còn). */
    val loadError: CommunityError? = null,
)

sealed interface GroupFormEvent {
    /** Tạo xong (hoặc chọn "Dùng ảnh mặc định"): mở trang nhóm. */
    data class OpenGroup(val groupId: String) : GroupFormEvent

    /** Sửa xong: quay lại trang nhóm. */
    data class Saved(val groupId: String) : GroupFormEvent

    data class ShowError(val error: CommunityError) : GroupFormEvent
}

/**
 * Form tạo/sửa nhóm (T027). `requestId` sinh một lần và giữ trong [SavedStateHandle]: gửi lại sau mất mạng, bận hay
 * xoay màn hình dùng cùng mã (idempotent, AC 1.6). Sau lỗi nghiệp vụ dứt khoát (server đã trả lời) mã được đổi để
 * lần gửi sau với nội dung đã sửa không bị trả kết quả cũ.
 */
@HiltViewModel
class GroupFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val createGroup: CreateGroup,
    private val updateGroup: UpdateGroup,
    private val groups: GroupRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    /** `null` là chế độ tạo. */
    val groupId: String? = savedStateHandle.get<String>(ARG_GROUP_ID)

    private val _state = MutableStateFlow(GroupFormState(isEdit = groupId != null, loading = groupId != null))
    val state: StateFlow<GroupFormState> = _state.asStateFlow()

    private val _events = Channel<GroupFormEvent>(Channel.BUFFERED)
    val events: Flow<GroupFormEvent> = _events.receiveAsFlow()

    private var requestId: String = savedStateHandle.get<String>(KEY_REQUEST_ID) ?: newRequestId()

    init {
        loadDistricts()
        if (groupId != null) loadGroup()
    }

    fun loadDistricts() {
        viewModelScope.launch {
            try {
                val districts = catalog.districts().filter { it.isActive }.sortedBy { it.sortOrder }
                _state.update { it.copy(districts = districts) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Không có danh sách khu vực thì chưa chọn được; submit báo thiếu khu vực.
            }
        }
    }

    /** Chế độ sửa: đọc nhóm một lần để nạp form (nút "Thử lại" của trạng thái lỗi gọi lại hàm này). */
    fun loadGroup() {
        val id = groupId ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val group = groups.observeGroup(id).first()
                if (group == null) {
                    _state.update { it.copy(loading = false, loadError = CommunityError.GroupNotFound()) }
                } else {
                    _state.update { it.prefilledWith(group) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = e.toCommunityError()) }
            }
        }
    }

    private fun GroupFormState.prefilledWith(group: Group) = copy(
        loading = false,
        name = group.name,
        description = group.description,
        districtId = group.districtId,
        visibility = group.visibility,
        existingAvatarPath = group.avatarPath,
    )

    fun onNameChanged(value: String) = _state.update { it.copy(name = value, nameError = null) }

    fun onDescriptionChanged(value: String) = _state.update { it.copy(description = value, descriptionError = null) }

    fun onDistrictSelected(id: String) = _state.update { it.copy(districtId = id, districtError = false) }

    fun onVisibilityChanged(value: GroupVisibility) = _state.update { it.copy(visibility = value) }

    fun onAvatarPicked(uri: String) = _state.update { it.copy(avatarUri = uri, removeAvatar = false) }

    /** Bỏ ảnh vừa chọn; ở chế độ sửa còn đánh dấu xóa ảnh hiện có. */
    fun onAvatarRemoved() = _state.update { it.copy(avatarUri = null, removeAvatar = it.existingAvatarPath != null) }

    fun submit() {
        val current = _state.value
        if (current.saving || current.loading || current.loadError != null) return
        // Đang chờ thử lại ảnh: nhóm đã tạo, bấm gửi nghĩa là thử lại ảnh chứ không tạo nhóm thứ hai.
        if (current.avatarFailure != null) return retryAvatar()

        val validated = current.validated()
        if (validated.nameError != null || validated.descriptionError != null || validated.districtError) {
            _state.value = validated
            return
        }
        _state.value = validated.copy(saving = true)
        val input = GroupInput(
            name = current.name.trim(),
            description = current.description.trim(),
            districtId = current.districtId.orEmpty(),
            visibility = current.visibility,
        )
        viewModelScope.launch {
            val result = try {
                if (groupId == null) {
                    createGroup(input, current.avatarUri, requestId)
                } else {
                    val avatar = when {
                        current.avatarUri != null -> AvatarChange.Replace(current.avatarUri)
                        current.removeAvatar -> AvatarChange.Remove
                        else -> AvatarChange.Keep
                    }
                    updateGroup(groupId, input, avatar, requestId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            onResult(result)
        }
    }

    /** "Thử lại" ảnh đại diện (AC 1.3). */
    fun retryAvatar() {
        val failure = _state.value.avatarFailure ?: return
        val uri = _state.value.avatarUri ?: return useDefaultAvatar()
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = try {
                createGroup.retryAvatar(failure.groupId, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            onResult(result)
        }
    }

    /** "Dùng ảnh mặc định": giữ nhóm vừa tạo, không tải lại ảnh. */
    fun useDefaultAvatar() {
        val failure = _state.value.avatarFailure ?: return
        _state.update { it.copy(avatarFailure = null, avatarUri = null) }
        finish(failure.groupId)
    }

    private suspend fun onResult(result: Result<Group>) {
        val error = result.exceptionOrNull()
        when {
            error == null -> finish(result.getOrThrow().id)
            error is CommunityError.AvatarUploadFailed -> {
                // Nhóm đã tạo: giữ form và mã yêu cầu, cho thử lại ảnh hoặc dùng ảnh mặc định.
                _state.update { it.copy(saving = false, avatarFailure = AvatarFailure(error.groupId)) }
            }
            else -> {
                val communityError = error.toCommunityError()
                // Mất mạng hoặc bận: giữ requestId để gửi lại là cùng một yêu cầu. Lỗi khác: server đã trả lời.
                if (communityError !is CommunityError.Network && communityError !is CommunityError.Busy) {
                    requestId = newRequestId()
                }
                _state.update { it.copy(saving = false) }
                _events.send(GroupFormEvent.ShowError(communityError))
            }
        }
    }

    private fun finish(groupId: String) {
        // Giữ saving = true cho tới khi rời màn hình để nút còn khóa.
        _state.update { it.copy(saving = true, avatarFailure = null) }
        _events.trySend(if (this.groupId == null) GroupFormEvent.OpenGroup(groupId) else GroupFormEvent.Saved(groupId))
    }

    private fun newRequestId(): String = UUID.randomUUID().toString().also { savedStateHandle[KEY_REQUEST_ID] = it }

    private fun GroupFormState.validated(): GroupFormState {
        val trimmed = name.trim()
        return copy(
            nameError = when {
                trimmed.isEmpty() -> FieldError.REQUIRED
                trimmed.length < NAME_MIN -> FieldError.TOO_SHORT
                trimmed.length > NAME_MAX -> FieldError.TOO_LONG
                else -> null
            },
            descriptionError = if (description.trim().length > DESCRIPTION_MAX) FieldError.TOO_LONG else null,
            districtError = districtId.isNullOrBlank(),
        )
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        const val KEY_REQUEST_ID = "requestId"
        const val NAME_MIN = 3
        const val NAME_MAX = 50
        const val DESCRIPTION_MAX = 500
    }
}
