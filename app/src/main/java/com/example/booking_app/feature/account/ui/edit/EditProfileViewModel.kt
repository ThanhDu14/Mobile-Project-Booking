package com.example.booking_app.feature.account.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionAuthSource
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.account.domain.AvatarImageProcessor
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import com.example.booking_app.feature.account.domain.model.ProfileDraft
import com.example.booking_app.feature.account.domain.usecase.ChangeAvatarUseCase
import com.example.booking_app.feature.account.domain.usecase.RemoveAvatarUseCase
import com.example.booking_app.feature.account.domain.usecase.SaveProfileUseCase
import com.example.booking_app.feature.account.domain.validation.DistrictSelectionRule
import com.example.booking_app.feature.auth.domain.validation.NameRule
import com.example.booking_app.feature.auth.domain.validation.Validated
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DistrictsState {
    data object Loading : DistrictsState

    data object Error : DistrictsState

    data class Content(val districts: List<District>) : DistrictsState
}

/** Trạng thái ảnh đại diện. Supabase không báo tiến độ tải lên nên `Uploading` hiện vòng xoay không xác định. */
sealed interface AvatarState {
    data object Idle : AvatarState

    data object Processing : AvatarState

    data object Uploading : AvatarState

    data class Error(val error: Throwable) : AvatarState
}

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val draft: ProfileDraft = EMPTY_DRAFT,
    val saved: ProfileDraft = EMPTY_DRAFT,
    val nameError: Boolean = false,
    val email: String? = null,
    val emailVerified: Boolean = true,
    val avatarPath: String? = null,
    val avatar: AvatarState = AvatarState.Idle,
    val districtsState: DistrictsState = DistrictsState.Loading,
    /** Tên các khu vực đã chọn trước đó, kể cả khu vực đã ngừng. */
    val knownDistricts: Map<String, District> = emptyMap(),
    val isSaving: Boolean = false,
) {
    /** Khác bản đã lưu (FR-006); họ tên so sau khi `trim`. */
    val isDirty: Boolean get() = draft.normalized() != saved.normalized()

    val canAddDistrict: Boolean get() = DistrictSelectionRule.canAdd(draft.districtIds)

    val isAvatarBusy: Boolean get() = avatar == AvatarState.Processing || avatar == AvatarState.Uploading

    /** Chip khu vực theo thứ tự đã chọn; ID không còn trong danh mục thì bỏ qua. */
    val selectedDistricts: List<District>
        get() {
            val active = (districtsState as? DistrictsState.Content)?.districts.orEmpty().associateBy { it.id }
            return draft.districtIds.mapNotNull { active[it] ?: knownDistricts[it] }
        }

    private companion object {
        val EMPTY_DRAFT = ProfileDraft("", null, emptyList())
    }
}

private fun ProfileDraft.normalized() = copy(displayName = displayName.trim())

sealed interface EditProfileEvent {
    data object Saved : EditProfileEvent

    data class SaveFailed(val error: Throwable) : EditProfileEvent

    data object ConfirmDiscard : EditProfileEvent

    data object Exit : EditProfileEvent
}

/** Sửa hồ sơ (US1, contracts/ui-screens.md §3). `draft` sống qua xoay màn hình và process death nhờ [SavedStateHandle]. */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val profileReader: UserProfileReader,
    private val authSource: SessionAuthSource,
    private val districtRepository: DistrictRepository,
    private val saveProfile: SaveProfileUseCase,
    private val changeAvatar: ChangeAvatarUseCase,
    private val removeAvatar: RemoveAvatarUseCase,
    private val imageProcessor: AvatarImageProcessor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditProfileEvent>(Channel.BUFFERED)
    val events: Flow<EditProfileEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(profileReader.observeMyProfile().filterNotNull(), authSource.authState) { profile, user ->
                val verified = user == null || user.isEmailVerified || PASSWORD_PROVIDER !in user.providers
                profile to verified
            }.collect { (profile, verified) -> onProfile(profile, verified) }
        }
        loadDistricts()
    }

    private fun onProfile(profile: MyProfile, emailVerified: Boolean) {
        val saved = ProfileDraft(profile.displayName, PlayerLevel.fromValue(profile.level), profile.favoriteDistrictIds)
        val firstLoad = _uiState.value.isLoading
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                saved = saved,
                draft = if (firstLoad) restoredDraft() ?: saved else state.draft,
                email = profile.email,
                emailVerified = emailVerified,
                // Đang đổi ảnh thì giữ đường dẫn của màn hình, listener sẽ bắt kịp sau khi ghi xong.
                avatarPath = if (state.isAvatarBusy) state.avatarPath else profile.avatarPath,
            )
        }
        if (firstLoad) loadKnownDistricts(saved.districtIds)
    }

    fun onNameChange(name: String) {
        updateDraft { it.copy(displayName = name) }
        _uiState.update { it.copy(nameError = false) }
    }

    /** `null` là bỏ chọn (nút đang chọn được bấm lại). */
    fun onLevelSelected(level: PlayerLevel?) = updateDraft { it.copy(level = level) }

    /** Kết quả từ `DistrictPickerBottomSheet`; giữ tối đa 5, bỏ trùng. */
    fun onDistrictsPicked(ids: List<String>) =
        updateDraft { it.copy(districtIds = ids.distinct().take(DistrictSelectionRule.MAX)) }

    fun onRemoveDistrict(id: String) = updateDraft { it.copy(districtIds = it.districtIds - id) }

    fun retryDistricts() = loadDistricts()

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        if (NameRule.validate(state.draft.displayName) !is Validated.Valid) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        if (!DistrictSelectionRule.isValid(state.draft.districtIds)) return
        _uiState.update { it.copy(isSaving = true) }
        val draft = state.draft.normalized()
        viewModelScope.launch {
            try {
                saveProfile(state.saved, draft)
                _uiState.update { it.copy(saved = draft, draft = draft) }
                persistDraft(draft)
                _events.send(EditProfileEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(EditProfileEvent.SaveFailed(e))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onBack() {
        val event = if (_uiState.value.isDirty) EditProfileEvent.ConfirmDiscard else EditProfileEvent.Exit
        viewModelScope.launch { _events.send(event) }
    }

    fun onDiscardConfirmed() {
        viewModelScope.launch { _events.send(EditProfileEvent.Exit) }
    }

    /** Ảnh được lưu ngay khi xác nhận cắt, không chờ nút "Lưu thay đổi" (ui-screens §2). */
    fun onAvatarCropped(uri: String) {
        if (_uiState.value.isAvatarBusy) return
        _uiState.update { it.copy(avatar = AvatarState.Processing) }
        viewModelScope.launch {
            runAvatarTask {
                val jpeg = imageProcessor.process(uri)
                _uiState.update { it.copy(avatar = AvatarState.Uploading) }
                val path = changeAvatar(jpeg, _uiState.value.avatarPath)
                _uiState.update { it.copy(avatarPath = path, avatar = AvatarState.Idle) }
            }
        }
    }

    fun onRemoveAvatar() {
        val state = _uiState.value
        if (state.isAvatarBusy || state.avatarPath == null) return
        _uiState.update { it.copy(avatar = AvatarState.Uploading) }
        viewModelScope.launch {
            runAvatarTask {
                removeAvatar(state.avatarPath)
                _uiState.update { it.copy(avatarPath = null, avatar = AvatarState.Idle) }
            }
        }
    }

    fun onAvatarErrorShown() {
        if (_uiState.value.avatar is AvatarState.Error) _uiState.update { it.copy(avatar = AvatarState.Idle) }
    }

    /** [block] tự đặt `avatar = Idle` cùng lúc với đường dẫn mới; ở đây chỉ chuyển lỗi thành `AvatarState.Error`. */
    private suspend fun runAvatarTask(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(avatar = AvatarState.Error(e)) }
        }
    }

    private fun loadDistricts() {
        _uiState.update { it.copy(districtsState = DistrictsState.Loading) }
        viewModelScope.launch {
            val state = try {
                DistrictsState.Content(districtRepository.activeDistricts())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DistrictsState.Error
            }
            _uiState.update { it.copy(districtsState = state) }
        }
    }

    private fun loadKnownDistricts(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val known = districtRepository.districtsByIds(ids)
                _uiState.update { it.copy(knownDistricts = it.knownDistricts + known) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Danh mục lỗi đã hiện ở mục khu vực; chip chưa có tên thì chưa hiện.
            }
        }
    }

    private fun updateDraft(transform: (ProfileDraft) -> ProfileDraft) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(draft = transform(it.draft)) }
        persistDraft(_uiState.value.draft)
    }

    private fun persistDraft(draft: ProfileDraft) {
        savedStateHandle[KEY_NAME] = draft.displayName
        savedStateHandle[KEY_LEVEL] = draft.level?.name
        savedStateHandle[KEY_DISTRICTS] = ArrayList(draft.districtIds)
    }

    private fun restoredDraft(): ProfileDraft? {
        val name = savedStateHandle.get<String>(KEY_NAME) ?: return null
        return ProfileDraft(
            displayName = name,
            level = PlayerLevel.fromValue(savedStateHandle[KEY_LEVEL]),
            districtIds = savedStateHandle.get<ArrayList<String>>(KEY_DISTRICTS).orEmpty(),
        )
    }

    private companion object {
        const val PASSWORD_PROVIDER = "password"
        const val KEY_NAME = "draft_name"
        const val KEY_LEVEL = "draft_level"
        const val KEY_DISTRICTS = "draft_districts"
    }
}
